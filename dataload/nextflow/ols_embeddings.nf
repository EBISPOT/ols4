
nextflow.enable.dsl=2

import groovy.json.JsonSlurper

params.out                     = "$OLS_OUT_DIR"
params.embeddings_config       = "$OLS_EMBEDDINGS_CONFIG"
params.embeddings_prev         = "$OLS_EMBEDDINGS_PREV"
params.embeddings_batch_size   = 10000
params.embeddings_pca_components = 512
params.embed_image             = ""

workflow embeddings {

    take:
    terms_tsv  // path to terms.tsv (output of ols_to_tsv from the dataload pipeline)

    main:

    config = new JsonSlurper().parse(new File(params.embeddings_config))
    models = Channel.from(config.models)

    // Deduplicate the terms TSV (already produced by the dataload pipeline)
    deduped = dedupe_by_hash(terms_tsv)

    // For each model, look for a corresponding previous embeddings Parquet file
    prev_dir = params.embeddings_prev ?: 'NO_DIR'
    models_with_prev = models.map { model ->
        def model_short        = model.split('/')[1]
        def prev_parquet_path  = new File(prev_dir, "${model_short}.parquet")
        def prev_parquet_file  = prev_parquet_path.exists() ? file(prev_parquet_path) : file('NO_FILE')
        tuple(model, prev_parquet_file)
    }

    // Filter out already embedded terms for each model
    filtered_per_model = filter_existing(models_with_prev, deduped)

    tsvs = split_tsv(filtered_per_model).map { model, files ->
        def list = (files instanceof List) ? files : [files]
        list.collect { f -> tuple(model, f) }
    }.flatMap()

    local_tsvs  = tsvs.filter { it[0] && !it[0].toString().startsWith('openai/') }
    openai_tsvs = tsvs.filter { it[0] &&  it[0].toString().startsWith('openai/') }

    local_embeddings  = embed(local_tsvs)
    openai_embeddings = embed_openai(openai_tsvs)

    // Collect all new embedding parquets.  .ifEmpty([]) ensures that when
    // embed/embed_openai produce nothing (all terms already embedded), we
    // still emit an empty list so every configured model reaches
    // join_embeddings.
    all_new_embeddings = local_embeddings
        .mix(openai_embeddings)
        .collect(flat: false)
        .ifEmpty([])

    // Build join_embeddings inputs for EVERY model.  Models that received
    // new embeddings get those parquets; models that didn't get NO_FILE.
    embeddings_by_model_with_prev = all_new_embeddings.flatMap { new_emb_list ->
        // Group new parquets by model name
        def new_by_model = [:].withDefault { [] }
        new_emb_list.each { item ->
            new_by_model[item[0]] << item[1]
        }
        config.models.collect { model ->
            def model_short       = model.split('/')[1]
            def prev_parquet_path = new File(prev_dir, "${model_short}.parquet")
            def has_prev          = prev_parquet_path.exists()
            def prev_parquet_file = has_prev ? file(prev_parquet_path) : file('NO_FILE')
            def has_new           = new_by_model.containsKey(model)
            def new_pqs           = has_new ? new_by_model[model] : [file('NO_FILE')]
            tuple(model, has_prev, prev_parquet_file, has_new, new_pqs)
        }
    }

    join_embeddings(
        embeddings_by_model_with_prev,
        terms_tsv
    )

    // Build ontology pairs for semsim
    def pairs = new LinkedHashSet<Tuple>()
    config.semsim_groups.each { group ->
        group.withIndex().each { a, i ->
            group.withIndex().each { b, j ->
                if (j >= i) {                    // ensures (a,b) but not (b,a)
                    pairs << tuple(a, b)
                }
            }
        }
    }

    pca_inputs = join_embeddings.out.combine(Channel.from(params.embeddings_pca_components, 16))
    pca(pca_inputs)

    visualize_embeddings(pca.out.pca_parquets.filter { it[0].endsWith('_pca16') })

    models_and_parquets = join_embeddings.out.concat( pca.out.pca_parquets )
    run_semsim(models_and_parquets.combine(Channel.from(pairs)), config.semsim_thresholds)

    emit:
    // Emit the PCA parquet files (for use in json2neo)
    pca_parquets = pca.out.pca_parquets
    // Emit the PCA JSON model files (for loading in the backend)
    pca_jsons = pca.out.pca_jsons
}




process dedupe_by_hash {

    container params.embed_image
    cache "lenient"
    memory '64 GB'
    time '10m'
    cpus "8"

    input:
    path("terms.tsv")

    output:
    path("deduped.tsv")

    script:
    """
    duckdb -c "COPY (
        SELECT hash, text_to_embed
        FROM read_csv_auto('terms.tsv', delim='\t', quote='', header=1)
        QUALIFY row_number() OVER (PARTITION BY hash)=1
    ) TO 'deduped.tsv' (HEADER false, DELIMITER '\t');"
    """
}

process split_tsv {

    container params.embed_image
    cache "lenient"
    memory '64 GB'
    time '10m'
    cpus "4"

    input:
    tuple val(model), path(tsv)

    output:
    tuple val(model), path("split.tsv.*"), optional: true

    script:
    """
    cat ${tsv} | split -a 6 -d -l ${params.embeddings_batch_size} - split.tsv.
    """
}

process filter_existing {

    container params.embed_image
    cache "lenient"
    memory '64 GB'
    time '30m'
    cpus "8"

    input:
    tuple val(model), path(prev_parquet)
    path(deduped_tsv)

    output:
    tuple val(model), path("filtered_${model.split('/')[1]}.tsv")

    script:
    def model_short = model.split('/')[1]
    def filter_cmd = prev_parquet.name != 'NO_FILE' ?
        """
        duckdb << 'EOF'
        COPY (
            SELECT new.hash, new.text_to_embed
            FROM read_csv_auto('${deduped_tsv}', delim='\t', quote='', header=0,
                               names=['hash', 'text_to_embed']) AS new
            LEFT JOIN (
                SELECT DISTINCT hash FROM read_parquet('${prev_parquet}')
            ) AS prev
            ON new.hash = prev.hash
            WHERE prev.hash IS NULL
        ) TO 'filtered_${model_short}.tsv' (HEADER false, DELIMITER '\t');
        EOF
        """ :
        "cp ${deduped_tsv} filtered_${model_short}.tsv"

    """
    ${filter_cmd}
    """
}

process embed {

    container params.embed_image
    cache "lenient"
    memory '32 GB'
    time '1h'
    cpus "8"
    clusterOptions = '--gres=gpu:a100:1'
    errorStrategy "retry"
    maxRetries 100

    input:
    tuple val(model), path(split_tsv)

    output:
    tuple val(model), path("embedded_${model.split('/')[1]}_${task.index}.parquet")

    script:
    def model_short = model.split('/')[1]

    """
    python3 /opt/ols_embed/embed2.py \
       --input-tsv ${split_tsv} \
       --output-parquet embedded_${model_short}_${task.index}.parquet \
       --model-name ${model} \
       --batch-size 200 \
       --device cuda
    """
}

process embed_openai {

    container params.embed_image
    cache "lenient"
    memory '16 GB'
    time '2h'
    cpus "4"

    input:
    tuple val(model), path(split_tsv)

    output:
    tuple val(model), path("embedded_${model.split('/')[1]}_${task.index}.parquet")

    script:
    def model_short = model.split('/')[1]

    """
    python3 /opt/ols_embed/embed_openai.py \
       --input-tsv ${split_tsv} \
       --output-parquet embedded_${model_short}_${task.index}.parquet \
       --model-name ${model_short} \
       --batch-size 2000
    """
}

process join_embeddings {

  container params.embed_image
  cache "lenient"
  memory '1500 GB'
  time '4h'
  cpus 32

  publishDir "${params.out}/embeddings", overwrite: true

  input:
  tuple val(model), val(has_prev), path(prev_pq, stageAs: 'prev.parquet'), val(has_new_pq), path(new_pq)
  path terms_tsv

  output:
  tuple val(model), path("${model.split('/')[1]}.parquet")

  script:
  def model_short = model.split('/')[1]

  def new_pq_files = (new_pq instanceof List ? new_pq : [new_pq])
  def new_list_sql = has_new_pq ? new_pq_files.collect { "'${it.toString()}'" }.join(', ') : ''

  def prev_sql = !has_prev
    ? """
      SELECT
        NULL::BIGINT   AS pk,
        NULL::VARCHAR  AS ontology_id,
        NULL::VARCHAR  AS entity_type,
        NULL::VARCHAR  AS iri,
        NULL::VARCHAR  AS label,
        NULL::VARCHAR  AS hash,
        NULL::VARCHAR  AS text_to_embed,
        NULL::FLOAT[]  AS embedding
      WHERE FALSE
      """
    : "SELECT * FROM read_parquet('prev.parquet')"

  """
  duckdb /dev/shm/terms_embedded.duckdb -c "
    PRAGMA threads=${task.cpus};
    PRAGMA memory_limit='1200GB';
    PRAGMA temp_directory='.';

    COPY (
      WITH
      terms AS (
        SELECT
          pk, ontology_id, entity_type, iri, label, hash, text_to_embed
        FROM read_csv_auto('${terms_tsv}', delim='\\t', header=true)
      ),
      new_emb AS (
        ${has_new_pq ? """
        SELECT
          hash,
          any_value(embedding) AS embedding
        FROM read_parquet([${new_list_sql}])
        GROUP BY hash
        """ : """
        SELECT NULL::VARCHAR AS hash, NULL::FLOAT[] AS embedding
        WHERE FALSE
        """}
      ),
      prev_terms AS (
        ${prev_sql}
      ),
      prev_emb AS (
        SELECT
          hash,
          any_value(embedding) AS embedding
        FROM prev_terms
        WHERE embedding IS NOT NULL
        GROUP BY hash
      ),
      joined_terms AS (
        SELECT
          t.pk,
          t.ontology_id,
          t.entity_type,
          t.iri,
          t.label,
          t.hash,
          t.text_to_embed,
          COALESCE(n.embedding, p.embedding) AS embedding
        FROM terms t
        LEFT JOIN new_emb  n ON n.hash = t.hash
        LEFT JOIN prev_emb p ON p.hash = t.hash
      ),
      carryover AS (
        SELECT
          pt.pk,
          pt.ontology_id,
          pt.entity_type,
          pt.iri,
          pt.label,
          pt.hash,
          pt.text_to_embed,
          pt.embedding
        FROM prev_terms pt
        LEFT JOIN terms t USING (pk)
        WHERE t.pk IS NULL
      )
      SELECT * FROM joined_terms
      UNION ALL
      SELECT * FROM carryover
    )
    TO '${model_short}.parquet'
    (FORMAT PARQUET, COMPRESSION ZSTD);
  "
  """
}

process pca {

    container params.embed_image
    cache "lenient"
    memory '1500 GB'
    time '4h'
    cpus "32"

    publishDir "${params.out}/embeddings", overwrite: true

    input:
    tuple val(model), path(parquet), val(n_components)

    output:
    tuple val("${model}_pca${n_components}"), path("${model.split('/')[1]}_pca${n_components}.parquet"), emit: pca_parquets
    path("${model.split('/')[1]}_pca${n_components}.json"), emit: pca_jsons

    script:
    """
    python3 /opt/ols_embed/pca.py ${parquet} ${model.split('/')[1]}_pca${n_components}.parquet ${n_components} \
        --pca-model-out ${model.split('/')[1]}_pca${n_components}.joblib \
        --pca-json-out ${model.split('/')[1]}_pca${n_components}.json
    """
}

process visualize_embeddings {

    container params.embed_image
    cache "lenient"
    memory '400 GB'
    time '1h'
    cpus "32"
    clusterOptions = '--gres=gpu:a100:1'

    publishDir "${params.out}/embeddings", overwrite: true

    input:
    tuple val(model), path(parquet)

    output:
    path("${model.split('/')[1]}_umap.parquet")
    path("${model.split('/')[1]}_umap.png")

    script:
    def model_short = model.split('/')[1]
    """
    python3 /opt/ols_embed/visualize_embeddings.py ${parquet} \\
        --output-parquet ${model_short}_umap.parquet \\
        --output-plot ${model_short}_umap.png
    """
}

process run_semsim {

    container params.embed_image
    cache "lenient"
    memory '256 GB'
    time '40h'
    cpus "32"

    publishDir "${params.out}/embeddings/semsim/${model.split('/')[1]}", overwrite: true

    input:
    tuple val(model), path(parquet), val(ont_a), val(ont_b)
    val(semsim_thresholds)

    output:
    path("${ont_a}_${ont_b}__${model.split('/')[1]}__${semsim_thresholds[model]}.tsv.gz")

    script:
    """
    ols_semsim --parquet ${parquet} --a ${ont_a} --b ${ont_b} --threshold ${semsim_thresholds[model]} \\
        | pigz --best > ${ont_a}_${ont_b}__${model.split('/')[1]}__${semsim_thresholds[model]}.tsv.gz
    """
}