
nextflow.enable.dsl=2

import groovy.json.JsonSlurper
jsonSlurper = new JsonSlurper()

import groovy.yaml.YamlSlurper
yamlSlurper = new YamlSlurper()

include { embeddings } from './ols_embeddings.nf'

params.configs = "$OLS4_CONFIG"
params.out = "$OLS_OUT_DIR"
params.solr_mem = "8g"
params.neo_mem = "16g"
params.embeddings_path = "$OLS_EMBEDDINGS_PATH"
params.max_rows_per_file = "100000"
params.dataload_args = System.getenv('OLS4_DATALOAD_ARGS') ?: ''
params.enable_embeddings = false

// Production-only features — disabled by default, enabled via nextflow_prod.config
params.enable_sssom    = false  // extract SSSOM mappings from linked ontologies
params.check_neo4j     = false  // verify Neo4j database has data after build
params.enable_ftp_copy = false  // copy tarballs to FTP (requires datamover partition)
params.copy_script     = ''     // path to copy_tarballs.sh on the NFS server

workflow {

    config_files = Channel.fromPath(params.configs.split(',').collect { it.trim() })
        .collect()
    
    merged_config_file = merge_configs(config_files)
    
    merged_config = merged_config_file.map { Path configFile ->
        new JsonSlurper().parse(configFile)
    }   

    ontologies = merged_config.flatMap { it.ontologies }
    ontology_ids = ontologies.map { it.id }

    ontology_jsons_and_status = rdf2json(merged_config_file, ontology_ids)
    ontology_jsons_by_id = ontology_jsons_and_status.map { id, json, status -> [id, json] }
    status_files = ontology_jsons_and_status.map { id, json, status -> status }.collect()

    linker_manifest = linker__create_manifest(ontology_jsons_by_id.map { it[1] }.collect())
    linked_ontologies_by_id = linker__link_ontologies(linker_manifest, ontology_jsons_by_id)

    // Build text tagger database from linked ontology JSONs
    all_linked_jsons = linked_ontologies_by_id.map { it[1] }.collect()
    terms_tsv = extract_strings_from_terms(all_linked_jsons)
    text_tagger_db = build_text_tagger_db(terms_tsv)

    // Run embeddings pipeline if enabled
    if (params.enable_embeddings) {
        embeddings(terms_tsv)
        embedding_parquets = embeddings.out.pca_parquets
            .map { it[1] }
            .collect()
            .map { list -> list.isEmpty() ? [file('NO_FILE')] : list }
            .ifEmpty([file('NO_FILE')])
    } else if (params.embeddings_path && params.embeddings_path != '' && params.embeddings_path != 'NO_DIR') {
        embedding_parquets = Channel.fromPath("${params.embeddings_path}/*.parquet").collect()
    } else {
        embedding_parquets = Channel.of(file('NO_FILE'))
    }

    neo_csvs = json2neo(linker_manifest, linked_ontologies_by_id, embedding_parquets)
    solr_jsonls = json2solr(linked_ontologies_by_id)

    neo = create_neo(neo_csvs.collect(), embedding_parquets)
    solr = create_solr(solr_jsonls.collect(), linker_manifest)

    // check_api_works(neo.neo_dir, solr.solr_dir)

    // Generate loading report after all ontologies have been processed
    report = generate_loading_report(merged_config_file, status_files)

    // ── SSSOM (prod only — enabled via params.enable_sssom) ────────────────
    if (params.enable_sssom) {
        extract_sssom(merge_linked_ontologies(all_linked_jsons))
    }

    // ── Neo4j data check (prod only — enabled via params.check_neo4j) ──────
    if (params.check_neo4j) {
        check_neo4j_data_exists(neo.neo_dir)
    }

    // ── Copy to FTP (prod only — enabled via params.enable_ftp_copy) ───────
    if (params.enable_ftp_copy) {
        copy_tarballs_to_ftp(neo.neo_tgz, solr.solr_tgz)
    }
}


process merge_configs {
    cache "lenient"
    memory { 1.GB }
    time "10m"
    
    input:
    path(config_files, stageAs: '?/*')

    output:
    path("merged_config.json")

    script:
    def mem_mb = (task.memory.toMega() * 0.9).intValue()
    def config_list = (config_files instanceof List) ? config_files : [config_files]
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -Xms${mem_mb}m -Xmx${mem_mb}m -jar /opt/ols/dataload/merge_configs/target/merge_configs-1.0-SNAPSHOT.jar \
        --config ${config_list.collect{ it.toString() }.join(',')} \
        --output merged_config.json
    """
}

process rdf2json {
    cache "lenient"
    memory { 64.GB + 128.GB * (task.attempt-1) }
    time "4h"
    errorStrategy 'retry'
    maxRetries 5
    
    input:
    path(config_path)
    val(ontology_id)

    output:
    tuple val(ontology_id), path("${ontology_id}.json"), path("${ontology_id}.status.json")

    script:
    def mem_mb = (task.memory.toMega() * 0.9).intValue()
    def extra_args = params.dataload_args ?: ''
    def ols_home = System.getenv('OLS_HOME')
    def base_path_arg = ols_home ? "--basePath ${ols_home}" : ''
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -Xms${mem_mb}m -Xmx${mem_mb}m \
        -DentityExpansionLimit=0 -DtotalEntitySizeLimit=0 \
        -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 \
        -jar /opt/ols/dataload/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar \
        --config ${config_path} \
        --ontologyIds ${ontology_id} \
        --output ${ontology_id}.json \
        ${base_path_arg} \
        ${extra_args}
    """
}

process linker__create_manifest {
    cache "lenient"
    memory { 16.GB }
    time "4h"
    
    input:
    path(ontology_jsons)

    output:
    path("linker_manifest.json")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ols_create_manifest \
        --input ${ontology_jsons.join(',')} \
        --output "linker_manifest.json"
    """
}

process linker__link_ontologies {
    cache "lenient"
    memory { 128.GB + 128.GB * (task.attempt-1) }
    time "4h"
    errorStrategy 'retry'
    maxRetries 5

    input:
    path("linker_manifest.json")
    tuple val(ontology_id), path(ontology_json)

    output:
    tuple val(ontology_id), path("${ontology_json.name.replace('.json', '_linked.json')}")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ols_link \
        --input ${ontology_json} \
        --manifest "linker_manifest.json" \
        --output "${ontology_json.name.replace('.json', '_linked.json')}"
    """
}

process json2neo {
    cache "lenient"
    memory { 16.GB + 128.GB * (task.attempt-1) }
    time "8h"
    errorStrategy 'retry'
    maxRetries 5

    input:
    path(manifest)
    tuple val(ontology_id), path(ontology_json)
    path(embedding_parquets)

    output:
    path("*.csv"), optional: true

    script:
    def parquets = (embedding_parquets instanceof List ? embedding_parquets : [embedding_parquets])
    def has_embeddings = !parquets.any { it.name == 'NO_FILE' }
    def parquet_args = has_embeddings ? "--embeddingParquets ${parquets.join(' ')}" : ''
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ols_json2neo \
        --input ${ontology_json} \
        --ontology-id ${ontology_id} \
        --outDir . \
        --manifest ${manifest} \
        ${parquet_args}
    """
}

process json2solr {
    cache "lenient"
    memory { 16.GB + 16.GB * (task.attempt-1) }
    time "8h"
    errorStrategy 'retry'
    maxRetries 5
    
    input:
    tuple val(ontology_id), path(ontology_json)

    output:
    path("*.jsonl"), optional: true

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ols_json2solr \
        --input ${ontology_json} \
        --ontology-id ${ontology_id} \
        --outDir . \
        --maxRowsPerFile ${params.max_rows_per_file}
    """
}

process create_neo {
    cache "lenient"
    memory { 16.GB }
    time "8h"

    publishDir "${params.out}", overwrite: true
    
    input:
    path(neo_csvs)
    path(embedding_parquets)

    output:
    path("neo4j"), emit: neo_dir
    path("neo4j.tgz"), emit: neo_tgz

    script:
    def parquets = (embedding_parquets instanceof List ? embedding_parquets : [embedding_parquets])
    def has_embeddings = !parquets.any { it.name == 'NO_FILE' }
    def parquet_list = has_embeddings ? parquets.collect { it.toString() }.join(' ') : ''
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    cp -r /opt/neo4j .
    /opt/ols/dataload/load_into_neo4j.sh ./neo4j . ${params.neo_mem} ${parquet_list}
    tar -chf neo4j.tgz --use-compress-program="pigz --fast" -C neo4j/data databases transactions
    """
}

process create_solr {
    cache "lenient"
    memory { 16.GB }
    time "23h"

    publishDir "${params.out}", overwrite: true
    
    input:
    path(solr_jsonls, stageAs: '?/*')
    path(manifest)

    output:
    path("solr"), emit: solr_dir
    path("solr.tgz"), emit: solr_tgz

    script:
    def mem_mb = (task.memory.toMega() * 0.5).intValue()
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    cp -r /opt/solr .

    java -Xms${mem_mb}m -Xmx${mem_mb}m -jar /opt/ols/dataload/solr_config_builder/target/solr_config_builder-1.0-SNAPSHOT.jar \
        --manifestPath ${manifest} \
        --solrConfigTemplatePath /opt/ols/dataload/solr_config_template \
        --outDir solr/server/solr \

    python3 /opt/ols/dataload/solr_import.py ./solr 8983 ${params.solr_mem}

    tar -chf solr.tgz --use-compress-program="pigz --fast" solr 
    """
}

process generate_loading_report {
    cache "lenient"
    memory { 4.GB }
    time "30m"

    publishDir "${params.out}", overwrite: true
    
    input:
    path(config_path)
    path(status_files)

    output:
    path("loading_report.txt")

    script:
    def mem_mb = (task.memory.toMega() * 0.9).intValue()
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    
    # Create a directory for status files
    mkdir -p status_files
    
    # Copy all status files to the directory
    for f in ${status_files}; do
        cp "\$f" status_files/
    done
    
    # Generate the report
    java -Xms${mem_mb}m -Xmx${mem_mb}m -jar /opt/ols/dataload/reporting/target/reporting-1.0-SNAPSHOT.jar \
        --config ${config_path} \
        --statusDir status_files \
        --reportFile loading_report.txt
    """
}

def parseJson(json) {
    return new JsonSlurper().parseText(json)
}

def basename(filename) {
    return new File(filename).name
}

process extract_strings_from_terms {
    cache "lenient"
    memory '8 GB'
    time '1h'
    cpus "4"

    input:
    path(linked_jsons)

    output:
    path("terms.tsv")

    script:
    def json_list = (linked_jsons instanceof List) ? linked_jsons : [linked_jsons]
    """
    extract_strings_from_terms ${json_list.join(' ')} > terms.tsv
    """
}

process build_text_tagger_db {
    cache "lenient"
    memory '8 GB'
    time '1h'

    publishDir "${params.out}", overwrite: true

    input:
    path(terms_tsv)

    output:
    path("text_tagger_db.bin")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ols_text_tagger build --output text_tagger_db.bin < ${terms_tsv}
    """
}

// ─────────────────────────────────────────────────────────────────────────────
// Prod-only processes
// ─────────────────────────────────────────────────────────────────────────────

// Merges all per-ontology linked JSONs into one ontologies_linked.json.
// Required by extract_sssom which expects the same monolithic format as Jenkins.
process merge_linked_ontologies {
    cache "lenient"
    memory { 96.GB }
    time "2h"

    publishDir "${params.out}", overwrite: true

    input:
    path(linked_jsons, stageAs: '?/*')

    output:
    path("ontologies_linked.json")

    script:
    def json_list = (linked_jsons instanceof List) ? linked_jsons : [linked_jsons]
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    python3 -c "
import json, sys
merged = {'ontologies': []}
for f in sys.argv[1:]:
    with open(f) as fp:
        data = json.load(fp)
    merged['ontologies'].extend(data.get('ontologies', []))
with open('ontologies_linked.json', 'w') as fp:
    json.dump(merged, fp)
" ${json_list.collect{ it.toString() }.join(' ')}
    """
}

// Extracts SSSOM mappings from the merged ontologies_linked.json.
// Equivalent to the Jenkins 'Extract SSSOM mappings' stage.
process extract_sssom {
    cache "lenient"
    memory { 96.GB }
    time "12h"

    publishDir "${params.out}", overwrite: true

    input:
    path(ontologies_linked_json)

    output:
    path("sssom"),     emit: sssom_dir
    path("sssom.tgz"), emit: sssom_tgz

    script:
    def mem_mb = (task.memory.toMega() * 0.9).intValue()
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    mkdir -p sssom
    java -Xms${mem_mb}m -Xmx${mem_mb}m \
        -jar /opt/ols/dataload/extras/json2sssom/target/json2sssom-1.0-SNAPSHOT.jar \
        --input ${ontologies_linked_json} \
        --outDir sssom
    tar --use-compress-program="pigz -f" -cvf sssom.tgz -C sssom .
    """
}

// Verifies that the Neo4j database was built and contains data.
// Equivalent to the Jenkins 'Check Neo4j data exists' stage.
process check_neo4j_data_exists {
    cache "lenient"
    memory { 8.GB }
    time "30m"

    publishDir "${params.out}", overwrite: true

    input:
    path(neo_dir)

    output:
    path("neo4j_check.log")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    if [ -d "${neo_dir}/data/databases" ] && [ "\$(ls -A "${neo_dir}/data/databases")" ]; then
        echo "Neo4j data check: PASSED" | tee neo4j_check.log
        echo "Databases found:" | tee -a neo4j_check.log
        ls -lh "${neo_dir}/data/databases/" | tee -a neo4j_check.log
    else
        echo "ERROR: Neo4j data is empty or missing at ${neo_dir}/data/databases" | tee neo4j_check.log
        exit 1
    fi
    """
}

// Copies the final tarballs (Neo4j, Solr) to the FTP server.
// Runs on the 'datamover' SLURM partition — equivalent to Jenkins '-p datamover'.
// params.copy_script must point to copy_tarballs.sh on the NFS server.
process copy_tarballs_to_ftp {
    cache false
    memory { 16.GB }
    time "12h"

    publishDir "${params.out}", overwrite: true

    input:
    path(neo_tgz)
    path(solr_tgz)

    output:
    path("copy_report.log")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    bash ${params.copy_script} \
        ${neo_tgz} \
        ${solr_tgz} \
        2>&1 | tee copy_report.log
    """
}
