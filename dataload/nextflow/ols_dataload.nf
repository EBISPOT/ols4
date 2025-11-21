
nextflow.enable.dsl=2

import groovy.json.JsonSlurper
jsonSlurper = new JsonSlurper()

import groovy.yaml.YamlSlurper
yamlSlurper = new YamlSlurper()

params.configs_dir = "$OLS_CONFIGS_DIR"
params.out = "$OLS_OUT_DIR"
params.solr_mem = "8g"
params.neo_mem = "16g"
params.embeddings_path = "$OLS_EMBEDDINGS_PATH"
params.max_rows_per_file = "100"

workflow {

    config_files = Channel.fromPath("${params.configs_dir}/*.json").collect()
    
    merged_config_file = merge_configs(config_files)
    
    merged_config = merged_config_file.map { Path configFile ->
        new JsonSlurper().parse(configFile)
    }   

    ontologies = merged_config.flatMap { it.ontologies }
    ontology_ids = ontologies.map { it.id }

    ontology_jsons_by_id = rdf2json(merged_config_file, ontology_ids)

    linker_manifest = linker__create_manifest(ontology_jsons_by_id.map { it[1] }.collect())
    linked_ontologies_by_id = linker__link_ontologies(linker_manifest, ontology_jsons_by_id)

    neo_csvs = json2neo(linker_manifest, linked_ontologies_by_id.map { it[1] })
    solr_jsonls = json2solr(linked_ontologies_by_id, params.embeddings_path)

    neo = create_neo(neo_csvs.collect())
    solr = create_solr(solr_jsonls.collect(), linker_manifest, params.embeddings_path)
}


process merge_configs {
    cache "lenient"
    memory { 1.GB }
    time "10m"
    
    input:
    path(config_files)

    output:
    path("merged_config.json")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -jar /opt/ols/dataload/merge_configs/target/merge_configs-1.0-SNAPSHOT.jar \
        --config ${config_files.join(',')} \
        --output merged_config.json
    """
}

process rdf2json {
    cache "lenient"
    memory { 16.GB + 8.GB * (task.attempt-1) }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
    maxRetries 5
    
    input:
    path(config_path)
    val(ontology_id)

    output:
    tuple val(ontology_id), path("${ontology_id}.json")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -jar /opt/ols/dataload/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar \
        --config ${config_path} \
        --ontologyIds ${ontology_id} \
        --output ${ontology_id}.json
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
    java -jar /opt/ols/dataload/linker/create_manifest/target/create-manifest-1.0-SNAPSHOT.jar \
        --input ${ontology_jsons.join(',')} \
        --output "linker_manifest.json"
    """
}

process linker__link_ontologies {
    cache "lenient"
    memory { 16.GB + 32.GB * (task.attempt-1) }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
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
    java -jar /opt/ols/dataload/linker/link/target/link-1.0-SNAPSHOT.jar \
        --input ${ontology_json} \
        --manifest "linker_manifest.json" \
        --output "${ontology_json.name.replace('.json', '_linked.json')}"
    """
}

process json2neo {
    cache "lenient"
    memory { 16.GB }
    
    input:
    path(manifest)
    path(ontology_json)

    output:
    path("*.csv")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -jar /opt/ols/dataload/json2neo/target/json2neo-1.0-SNAPSHOT.jar \
        --input ${ontology_json} \
        --outDir . \
        --manifest ${manifest}
    """
}

process json2solr {
    cache "lenient"
    memory { 16.GB }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
    maxRetries 5
    
    input:
    tuple val(ontology_id), path(ontology_json)
    path(embeddings_path)

    output:
    path("*.jsonl")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -jar /opt/ols/dataload/json2solr/target/json2solr-1.0-SNAPSHOT.jar \
        --input ${ontology_json} \
        --ontologyId ${ontology_id} \
        --outDir . \
        --embeddingDbsPath ${embeddings_path} \
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

    output:
    path("neo4j.tgz")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    cp -r /opt/neo4j .
    /opt/ols/dataload/load_into_neo4j.sh ./neo4j . ${params.neo_mem}
    tar -chf neo4j.tgz --use-compress-program="pigz --fast" neo4j 
    """
}

process create_solr {
    cache "lenient"
    memory { 16.GB }
    time "8h"

    publishDir "${params.out}", overwrite: true
    
    input:
    path(solr_jsonls)
    path(manifest)
    path(embeddings_path)

    output:
    path("solr.tgz")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    cp -r /opt/solr .

    java -jar /opt/ols/dataload/solr_config_builder/target/solr_config_builder-1.0-SNAPSHOT.jar \
        --manifestPath ${manifest} \
        --solrConfigTemplatePath /opt/ols/dataload/solr_config_template \
        --embeddingDbsPath ${embeddings_path} \
        --outDir solr/server/solr \

    python3 /opt/ols/dataload/solr_import.py ./solr 8983 ${params.solr_mem}

    tar -chf solr.tgz --use-compress-program="pigz --fast" solr 
    """
}

def parseJson(json) {
    return new JsonSlurper().parseText(json)
}

def basename(filename) {
    return new File(filename).name
}
