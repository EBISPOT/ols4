nextflow.enable.dsl=2

// Import production processes so changes to their directives/scripts are tested.
include { rdf2json; json2postgres; create_postgres } from '../../nextflow/ols_dataload.nf'

process verify_persisted {
    memory 512.MB
    cpus 1
    publishDir "${params.results}/persisted", mode: 'copy'
    input:
    path(postgres)
    path(verifier)
    output:
    path('loaded.json')
    script:
    """
    python3 ${verifier} ${postgres}
    """
}

workflow {
    parsed = rdf2json(Channel.value(file(params.fixture_config)), Channel.fromList(params.ids.tokenize(',')))
    jsons = parsed.map { id, json, status -> tuple(id, json, []) }
    no_embeddings = Channel.value(file(params.no_file))
    binaries = json2postgres(jsons, no_embeddings)
    // Match production's collect boundary: surviving ontology outputs load together.
    pg = create_postgres(binaries.collect(), no_embeddings, Channel.value([]),
        Channel.value(file(params.no_pca)), Channel.value(file(params.tagger)))
    verify_persisted(pg.pg_dir, Channel.value(file("${projectDir}/verify_loaded.py")))
}
