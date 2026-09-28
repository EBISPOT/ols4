package uk.ac.ebi.spot.ols.config;

/**
 * Default embedding model used by the llm_* endpoints when the caller does not specify one.
 *
 * Override with the {@code ols.embedding.default-model} property (env var OLS_EMBEDDING_DEFAULT_MODEL).
 * The value must match an {@code embeddings_<model>} column on {@code ols_entities}.
 */
public class EmbeddingDefaults {

    public static final String DEFAULT_MODEL = "harrier-oss-v1-27b_pca512";

    // Spring placeholder, usable in annotation attributes such as @Value and @RequestParam(defaultValue)
    public static final String DEFAULT_MODEL_PROPERTY = "${ols.embedding.default-model:" + DEFAULT_MODEL + "}";
}
