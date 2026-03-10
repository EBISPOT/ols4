
nextflow.enable.dsl=2

import groovy.json.JsonSlurper
jsonSlurper = new JsonSlurper()

import groovy.yaml.YamlSlurper
yamlSlurper = new YamlSlurper()

include { embeddings } from './ols_embeddings.nf'

params.config_branch = "stable"  // Branch to fetch configs from (stable or dev)
params.config_files  = ''         // Comma-separated local config paths; if set, skips NFS fetch (used in CI)
params.last_run_dir  = ''         // Directory of per-ontology JSONs from last successful run; enables fallback on failure
params.out = "$OLS_OUT_DIR"
params.solr_mem = "8g"
params.neo_mem = "16g"
params.embeddings_path = "$OLS_EMBEDDINGS_PATH"
params.max_rows_per_file = "100000"
params.dataload_args = System.getenv('OLS4_DATALOAD_ARGS') ?: ''
params.enable_embeddings = false

// Production-only features — disabled by default, enabled via nextflow_prod.config
params.enable_ftp_copy          = false  // copy tarballs to FTP (requires datamover partition)
params.enable_ontology_tarballs = false  // create ontology_jsons.tgz and ontology_jsons_linked.tgz
params.copy_script     = ''     // path to copy_tarballs.sh on the NFS server


process fetch_configs {
    cache "lenient"
    memory { 1.GB }
    time "10m"

    output:
    path("*.json")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail

    # Run shared get-configs script with branch parameter
    # Script will download configs to current directory
    bash /nfs/production/parkinso/spot/ols4/configs/get-configs.sh ${params.config_branch}
    """
}

workflow {

    // Fetch configs: use local paths when provided (CI/local), otherwise fetch from NFS (prod)
    if (params.config_files) {
        config_files = Channel.fromPath(params.config_files.tokenize(',')).collect()
    } else {
        config_files = fetch_configs().collect()
    }

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
        pca_parquets = embeddings.out.pca_parquets
            .map { it[1] }
            .collect()
        embedding_parquets = pca_parquets
            .map { list -> list.isEmpty() ? [file('NO_FILE')] : list }
            .ifEmpty([file('NO_FILE')])
        // Persist PCA parquets to embeddings_path so the next incremental embeddings run can reuse them
        if (params.embeddings_path && params.embeddings_path != '' && params.embeddings_path != 'NO_DIR') {
            update_embeddings_path(pca_parquets)
        }
    } else if (params.embeddings_path && params.embeddings_path != '' && params.embeddings_path != 'NO_DIR') {
        // Exclude umap parquets — they are visualization-only and have no embedding column
        // ifEmpty ensures json2neo still runs when the directory exists but has no parquets
        embedding_parquets = Channel.fromPath("${params.embeddings_path}/*.parquet")
            .filter { !it.name.contains('_umap') }
            .collect()
            .ifEmpty([file('NO_FILE')])
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

    // ── Ontology JSON tarballs (prod only — enabled via params.enable_ontology_tarballs) ──
    if (params.enable_ontology_tarballs) {
        ontology_jsons_tgz        = create_ontology_jsons_tarball(ontology_jsons_by_id.map { it[1] }.collect())
        ontology_jsons_linked_tgz = create_linked_jsons_tarball(all_linked_jsons)
    }

    // ── SSSOM ───────────────────────────────────────────────────────────────
    sssom = extract_sssom(all_linked_jsons)

    // ── Neo4j data check ────────────────────────────────────────────────────
    check_neo4j_data_exists(neo.neo_dir)

    // ── Copy to FTP (prod only — enabled via params.enable_ftp_copy) ───────
    if (params.enable_ftp_copy) {
        copy_tarballs_to_ftp(
            neo.neo_tgz,
            solr.solr_tgz,
            sssom.sssom_tgz,
            ontology_jsons_tgz,
            ontology_jsons_linked_tgz
        )
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

    // Save each ontology JSON to last_run_dir after success, so it can be used as fallback next run
    publishDir params.last_run_dir, mode: 'copy', enabled: params.last_run_dir as boolean, saveAs: { fn -> fn.endsWith('.status.json') ? null : fn }
    publishDir "${params.out}/ontology_jsons", overwrite: true, saveAs: { fn -> fn.endsWith('.status.json') ? null : fn }

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
    def last_run_dir = params.last_run_dir ?: ''
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail

    MERGE_ARG=""
    if [ -n "${last_run_dir}" ] && [ -f "${last_run_dir}/${ontology_id}.json" ]; then
        MERGE_ARG="--mergeOutputWith ${last_run_dir}/${ontology_id}.json"
    fi

    java -Xms${mem_mb}m -Xmx${mem_mb}m \
        -DentityExpansionLimit=0 -DtotalEntitySizeLimit=0 \
        -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 \
        -jar /opt/ols/dataload/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar \
        --config ${config_path} \
        --ontologyIds ${ontology_id} \
        --output ${ontology_id}.json \
        ${base_path_arg} \
        ${extra_args} \
        \$MERGE_ARG
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
    publishDir "${params.out}/ontology_jsons_linked", overwrite: true

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

process create_ontology_jsons_tarball {
    cache "lenient"
    memory { 8.GB }
    time "2h"

    publishDir "${params.out}", overwrite: true

    input:
    path(jsons)

    output:
    path("ontology_jsons.tgz")

    script:
    def json_list = (jsons instanceof List) ? jsons : [jsons]
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    mkdir ontology_jsons
    for f in ${json_list.join(' ')}; do cp "\$f" ontology_jsons/; done
    tar --use-compress-program="pigz -f" -cvf ontology_jsons.tgz ontology_jsons
    """
}

process create_linked_jsons_tarball {
    cache "lenient"
    memory { 8.GB }
    time "2h"

    publishDir "${params.out}", overwrite: true

    input:
    path(linked_jsons)

    output:
    path("ontology_jsons_linked.tgz")

    script:
    def json_list = (linked_jsons instanceof List) ? linked_jsons : [linked_jsons]
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    mkdir ontology_jsons_linked
    for f in ${json_list.join(' ')}; do cp "\$f" ontology_jsons_linked/; done
    tar --use-compress-program="pigz -f" -cvf ontology_jsons_linked.tgz ontology_jsons_linked
    """
}

// ─────────────────────────────────────────────────────────────────────────────
// Prod-only processes
// ─────────────────────────────────────────────────────────────────────────────

// Extracts SSSOM mappings from individual linked ontology JSON files.
// Processes each ontology file independently without requiring a merge step.
// Equivalent to the Jenkins 'Extract SSSOM mappings' stage.
process extract_sssom {
    cache "lenient"
    memory { 96.GB }
    time "12h"

    publishDir "${params.out}", overwrite: true

    input:
    path(linked_jsons, stageAs: 'input_jsons/*')

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
        --input input_jsons \
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

    DB_PATH="${neo_dir}/data/databases/neo4j"
    TX_PATH="${neo_dir}/data/transactions/neo4j"

    echo "Neo4j Data Check"    | tee neo4j_check.log
    echo "================" | tee -a neo4j_check.log

    STATUS=0

    if [ -e "\$DB_PATH" ]; then
        echo "✓ Neo4j database exists at: \$DB_PATH"       | tee -a neo4j_check.log
    else
        echo "✗ ERROR: Neo4j database does not exist at: \$DB_PATH" | tee -a neo4j_check.log
        STATUS=1
    fi

    if [ -e "\$TX_PATH" ]; then
        echo "✓ Neo4j transaction data exists at: \$TX_PATH" | tee -a neo4j_check.log
    else
        echo "✗ ERROR: Neo4j transaction data does not exist at: \$TX_PATH" | tee -a neo4j_check.log
        STATUS=1
    fi

    echo "================" | tee -a neo4j_check.log

    if [ \$STATUS -eq 0 ]; then
        echo "STATUS: All Neo4j data exists ✓" | tee -a neo4j_check.log
    else
        echo "STATUS: Some Neo4j data is missing ✗" | tee -a neo4j_check.log
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
    path(sssom_tgz)
    path(ontology_jsons_tgz)
    path(ontology_jsons_linked_tgz)

    output:
    path("copy_report.log")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    bash ${params.copy_script} \
        ${neo_tgz} \
        ${solr_tgz} \
        ${sssom_tgz} \
        ${ontology_jsons_tgz} \
        ${ontology_jsons_linked_tgz} \
        2>&1 | tee copy_report.log
    """
}

// Persists PCA parquets to params.embeddings_path so the next incremental embeddings
// run can reuse them as a base. Copies to out/ subdir first so Nextflow treats them
// as fresh outputs (staged input symlinks are excluded from output matching).
process update_embeddings_path {
    cache false
    memory { 4.GB }
    time '30m'
    publishDir params.embeddings_path, mode: 'copy', overwrite: true, saveAs: { fn -> fn.replaceFirst('^out/', '') }

    input:
    path(parquets)

    output:
    path("out/*.parquet")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    mkdir out
    for f in *.parquet; do
        cp -L "\$f" "out/\$f"
    done
    echo "Persisted \$(ls out/*.parquet | wc -l) PCA parquets to ${params.embeddings_path}"
    """
}
