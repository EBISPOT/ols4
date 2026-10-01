nextflow.enable.dsl=2

// Import production processes so changes to their directives/scripts are tested.
include { rdf2json; json2postgres } from '../../nextflow/ols_dataload.nf'

workflow {
    parsed = rdf2json(Channel.value(file(params.fixture_config)), Channel.fromList(params.ids.tokenize(',')))
    jsons = parsed.map { id, json, status -> tuple(id, json, []) }
    json2postgres(jsons, Channel.value(file(params.no_file)))
}
