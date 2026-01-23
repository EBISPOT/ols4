
nextflow.enable.dsl=2

import groovy.json.JsonSlurper
jsonSlurper = new JsonSlurper()

import groovy.yaml.YamlSlurper
yamlSlurper = new YamlSlurper()

params.configs = "$OLS4_CONFIG"
params.out = "$OLS_OUT_DIR"
params.solr_mem = "8g"
params.neo_mem = "16g"
params.embeddings_path = "$OLS_EMBEDDINGS_PATH"
params.max_rows_per_file = "100000"
params.dataload_args = System.getenv('OLS4_DATALOAD_ARGS') ?: ''

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

    neo_csvs = json2neo(linker_manifest, linked_ontologies_by_id, params.embeddings_path)
    solr_jsonls = json2solr(linked_ontologies_by_id)

    neo = create_neo(neo_csvs.collect(), params.embeddings_path)
    solr = create_solr(solr_jsonls.collect(), linker_manifest, params.embeddings_path)

    // check_api_works(neo.neo_dir, solr.solr_dir)

    // Generate loading report after all ontologies have been processed
    report = generate_loading_report(merged_config_file, status_files)
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
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -Xms${mem_mb}m -Xmx${mem_mb}m -jar /opt/ols/dataload/merge_configs/target/merge_configs-1.0-SNAPSHOT.jar \
        --config ${config_files.join(',')} \
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
    def mem_mb = (task.memory.toMega() * 0.9).intValue()
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -Xms${mem_mb}m -Xmx${mem_mb}m -jar /opt/ols/dataload/linker/create_manifest/target/create-manifest-1.0-SNAPSHOT.jar \
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
    def mem_mb = (task.memory.toMega() * 0.9).intValue()
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -Xms${mem_mb}m -Xmx${mem_mb}m -jar /opt/ols/dataload/linker/link/target/link-1.0-SNAPSHOT.jar \
        --input ${ontology_json} \
        --manifest "linker_manifest.json" \
        --output "${ontology_json.name.replace('.json', '_linked.json')}"
    """
}

process json2neo {
    cache "lenient"
    memory { 128.GB + 128.GB * (task.attempt-1) }
    time "8h"
    
    input:
    path(manifest)
    tuple val(ontology_id), path(ontology_json)
    path(embeddings_path)

    output:
    path("*.csv"), optional: true

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    ols_json2neo \
        --input ${ontology_json} \
        --ontology-id ${ontology_id} \
        --outDir . \
        --manifest ${manifest} \
        --embeddingDbsPath ${embeddings_path}
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
    def mem_mb = (task.memory.toMega() * 0.9).intValue()
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -Xms${mem_mb}m -Xmx${mem_mb}m -jar /opt/ols/dataload/json2solr/target/json2solr-1.0-SNAPSHOT.jar \
        --input ${ontology_json} \
        --ontologyId ${ontology_id} \
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
    path(embeddings_path)

    output:
    path("neo4j"), emit: neo_dir
    path("neo4j.tgz"), emit: neo_tgz

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    cp -r /opt/neo4j .
    /opt/ols/dataload/load_into_neo4j.sh ./neo4j . ${params.neo_mem} ${embeddings_path}
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
    path(embeddings_path)

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
        --embeddingDbsPath ${embeddings_path} \
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
