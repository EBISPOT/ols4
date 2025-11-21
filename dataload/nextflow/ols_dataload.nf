
nextflow.enable.dsl=2

import groovy.json.JsonSlurper
jsonSlurper = new JsonSlurper()

import groovy.yaml.YamlSlurper
yamlSlurper = new YamlSlurper()

params.config = "$OLS_CONFIG"
params.out = "$OLS_OUT_DIR"
params.solr_mem = "32g"
params.neo_mem = "32g"
params.embeddings_path = "$OLS_EMBEDDINGS_PATH"

workflow {

    config_path = params.config
    config = (new JsonSlurper().parse(new File(config_path)))

    ontologies = Channel.from(config.ontologies)
    ontology_ids = ontologies.map { it.id }

    ontology_jsons = rdf2json(config_path, ontology_ids)

    linker_manifest = linker__create_manifest(ontology_jsons.collect())
    linked_ontologies = linker__link_ontologies(linker_manifest, ontology_jsons)

    neo_csvs = json2neo(linker_manifest, linked_ontologies)
    solr_jsonls = json2solr(linked_ontologies, params.embeddings_path)

    // neo = create_neo(neo_csvs.collect())
    solr = create_solr(solr_jsonls.collect(), linker_manifest, params.embeddings_path)
}

process rdf2json {
    cache "lenient"
    memory { 16.GB }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
    maxRetries 5
    
    input:
    path(config_path)
    val(ontology_id)

    output:
    path("${ontology_id}.json")

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
    memory { 48.GB }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
    maxRetries 5
    
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
    memory { 48.GB }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
    maxRetries 5
    
    input:
    path("linker_manifest.json")
    path(ontology_json)

    output:
    path("${ontology_json.name.replace('.json', '_linked.json')}")

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
    memory { 48.GB }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
    maxRetries 5
    
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
    memory { 48.GB }
    time { 1.hour + 8.hour * (task.attempt-1) }
    errorStrategy { task.exitStatus in 137..140 ? 'retry' : 'terminate' }
    maxRetries 5
    
    input:
    path(ontology_json)
    path(embeddings_path)

    output:
    path("*.jsonl")

    script:
    """
    #!/usr/bin/env bash
    set -Eeuo pipefail
    java -jar /opt/ols/dataload/json2solr/target/json2solr-1.0-SNAPSHOT.jar \
        --input ${ontology_json} \
        --outDir . \
        --embeddingDbsPath ${embeddings_path}
    """
}

process create_neo {
    cache "lenient"
    memory { 48.GB }
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
    memory { 48.GB }
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
        --outDir solr \

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
