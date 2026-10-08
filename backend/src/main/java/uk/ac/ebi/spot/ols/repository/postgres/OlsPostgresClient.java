package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.INFORMATION_SCHEMA_COLUMNS;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_EMBEDDING_NODES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_ENTITIES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.PG_ATTRIBUTE;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.PG_EXTENSION;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContains;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContainsField;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.castAsText;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.field;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.setLocal;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.unnest;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.vectorDistance;

@Component
public class OlsPostgresClient {

    private static final Pattern SAFE_MODEL_NAME = Pattern.compile("^[a-zA-Z0-9_.-]+$");
    private static final Field<byte[]> ENTITY_JSON = field("_json", byte[].class);
    private static final Field<String> ENTITY_ID = field("id", String.class);
    private static final Field<String> ENTITY_IRI = field("iri", String.class);
    private static final Field<String> ENTITY_ONTOLOGY_ID = field("ontology_id", String.class);
    private static final Field<String> ENTITY_TYPE = field("type", String.class);
    private static final Field<Boolean> ENTITY_IS_OBSOLETE = field("is_obsolete", Boolean.class);
    private static final Field<Boolean> ENTITY_IS_DEFINING_ONTOLOGY = field("is_defining_ontology", Boolean.class);
    private static final Field<String> COLUMN_NAME = field("column_name", String.class);

    private static final String LABEL_EMBEDDING = "LabelEmbedding";
    private static final String CURATION_EMBEDDING = "CurationEmbedding";

    // Row limits for a scoped HNSW scan: see approximateNearestInScope.
    private static final int HNSW_ROWS_PER_RESULT = 5;
    private static final int HNSW_MIN_ROWS = 40;
    private static final int HNSW_ROW_LIMIT_GROWTH = 4;
    private static final int HNSW_MAX_WIDENINGS = 2;

    private static final Pattern LEADING_VERSION_NUMBERS = Pattern.compile("^(\\d+)(?:\\.(\\d+))?");

    private static String sanitizeEmbeddingColumnName(String modelName) {
        if (modelName == null || !SAFE_MODEL_NAME.matcher(modelName).matches()) {
            throw new IllegalArgumentException("Invalid embedding model name: " + modelName);
        }
        return "embeddings_" + modelName;
    }

    private static String sanitizeEmbeddingNodeColumnName(String modelName) {
        if (modelName == null || !SAFE_MODEL_NAME.matcher(modelName).matches()) {
            throw new IllegalArgumentException("Invalid embedding model name: " + modelName);
        }
        return "embedding_" + modelName;
    }

    static double normalizeCosineSimilarity(double similarity) {
        return Math.round(clampUnitInterval((similarity + 1.0) / 2.0) * 1_000_000.0) / 1_000_000.0;
    }

    static double normalizeCosineDistance(double distance) {
        return Math.round(clampUnitInterval(1.0 - (distance / 2.0)) * 1_000_000.0) / 1_000_000.0;
    }

    private static double clampUnitInterval(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Whether a pgvector version has iterative index scans ({@code hnsw.iterative_scan}), which
     * arrived in 0.8.0. Anything that does not start with a version number counts as not having
     * them.
     */
    static boolean supportsIterativeScans(String pgvectorVersion) {
        if (pgvectorVersion == null) {
            return false;
        }
        Matcher version = LEADING_VERSION_NUMBERS.matcher(pgvectorVersion.trim());
        if (!version.find()) {
            return false;
        }
        try {
            int major = Integer.parseInt(version.group(1));
            int minor = version.group(2) == null ? 0 : Integer.parseInt(version.group(2));
            return major > 0 || minor >= 8;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Whether a scope holding this many vectors is ranked exactly (true) or read from the HNSW
     * index (false). See nearestEntitiesDefinedInOntology.
     */
    static boolean ranksExactly(long vectorsInScope, long exactScanMaxVectors, boolean hnswIterativeScans) {
        return vectorsInScope <= exactScanMaxVectors || !hnswIterativeScans;
    }

    /**
     * The smaller of two distances, by the rule SQL's {@code min()} and {@code ORDER BY} follow:
     * NaN, which is the cosine distance to a zero vector, is further away than any number.
     * {@link Math#min} would return NaN instead.
     */
    static double nearer(double a, double b) {
        return Double.compare(a, b) <= 0 ? a : b;
    }

    @Autowired
    PostgresClient postgresClient;

    /**
     * The most vectors an ontology-scoped search will rank exactly. A scope with more is read from
     * the HNSW index instead (see nearestEntitiesDefinedInOntology).
     *
     * <p>The count is of one kind of embedding (label or curation) of the entities searched, so
     * a search for one entity type counts only that type's vectors.
     *
     * <p>Ranking exactly reads every vector in scope, mostly in the order they are stored, so its
     * cost grows in step with the scope; its results are exact. Reading the index costs scattered
     * page reads in proportion to how far the walk has to go before it has enough rows in scope:
     * a few hundred when the query is about what the ontology is about, tens of thousands when it
     * is not, whatever the size of the ontology. Its results are approximate, and a query far
     * from everything in the ontology can come back with a short page. So this should be as high
     * as the wait for an exact scan is acceptable. The default puts MONDO, which had about
     * 135,000 label vectors when GitHub issue #1445 was written, on the exact side with room to
     * grow.
     */
    @Value("${ols.vector-search.exact-scan-max-vectors:250000}")
    int exactScanMaxVectors = 250_000;

    private volatile Boolean hnswIterativeScans;

    Gson gson = new Gson();

    public long getDatabaseNodeCount() {
        return postgresClient.returnNodeCount();
    }

    public Page<JsonElement> getAll(String type, Map<String, String> properties, Pageable pageable) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Condition where = ENTITY_TYPE.eq(type);

            for (var entry : properties.entrySet()) {
                switch (entry.getKey()) {
                    case "id" -> where = where.and(ENTITY_ID.eq(entry.getValue()));
                    case "iri" -> where = where.and(ENTITY_IRI.eq(entry.getValue()));
                    case "ontologyId" -> where = where.and(ENTITY_ONTOLOGY_ID.eq(entry.getValue()));
                    default -> {
                    }
                }
            }

            long count = Optional.ofNullable(dsl.selectCount()
                            .from(OLS_ENTITIES)
                            .where(where)
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            var records = dsl.select(ENTITY_JSON)
                    .from(OLS_ENTITIES)
                    .where(where)
                    .orderBy(ENTITY_IRI.asc())
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();

            return new PageImpl<>(readJsonElements(records, ENTITY_JSON), pageable, count);
        } catch (SQLException e) {
            throw new RuntimeException("getAll failed", e);
        }
    }

    public JsonElement getOne(String type, Map<String, String> properties) {
        Page<JsonElement> results = getAll(type, properties, PageRequest.of(0, 10));

        if (results.getTotalElements() != 1) {
            throw new RuntimeException("expected exactly one result for getOne, but got " + results.getTotalElements());
        }

        return results.getContent().iterator().next();
    }

    public Page<JsonElement> getDirectParents(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "direct_parents", nodeProps, pageable);
    }

    public Page<JsonElement> getDirectChildren(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "direct_parents", nodeProps, pageable, null);
    }

    public Page<JsonElement> getDirectChildren(String id, Map<String, String> nodeProps, Pageable pageable, String search) {
        return lookupArraySources(id, "direct_parents", nodeProps, pageable, search);
    }

    public Page<JsonElement> getHierarchicalParents(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "hierarchical_parents", nodeProps, pageable);
    }

    public Page<JsonElement> getHierarchicalChildren(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "hierarchical_parents", nodeProps, pageable, null);
    }

    public Page<JsonElement> getAncestors(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "direct_ancestors", nodeProps, pageable);
    }

    public Page<JsonElement> getDescendants(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "direct_ancestors", nodeProps, pageable, null);
    }

    public Page<JsonElement> getHierarchicalAncestors(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "hierarchical_ancestors", nodeProps, pageable);
    }

    public Page<JsonElement> getHierarchicalDescendants(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "hierarchical_ancestors", nodeProps, pageable, null);
    }

    public Page<JsonElement> getRelatedTo(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArrayTargets(id, "related_to", nodeProps, pageable);
    }

    public Page<JsonElement> getRelatedFrom(String id, Map<String, String> nodeProps, Pageable pageable) {
        return lookupArraySources(id, "related_to", nodeProps, pageable, null);
    }

    private Page<JsonElement> lookupArrayTargets(String id, String column, Map<String, String> nodeProps, Pageable pageable) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Table<?> e1 = OLS_ENTITIES.as("e1");
            Table<?> e2 = OLS_ENTITIES.as("e2");

            Field<String> e1Id = field("e1", "id", String.class);
            Field<String[]> e1Targets = field("e1", column, String[].class);
            Field<String> e1OntologyId = field("e1", "ontology_id", String.class);
            Field<String> e2Iri = field("e2", "iri", String.class);
            Field<String> e2OntologyId = field("e2", "ontology_id", String.class);
            Field<byte[]> e2Json = field("e2", "_json", byte[].class);

            Condition where = e1Id.eq(id)
                    .and(arrayContains(e1Targets, e2Iri))
                    .and(e2OntologyId.eq(e1OntologyId))
                    .and(buildNodePropCondition("e2", nodeProps));

            long count = Optional.ofNullable(dsl.selectCount()
                            .from(e1)
                            .join(e2).on(arrayContains(e1Targets, e2Iri).and(e2OntologyId.eq(e1OntologyId)))
                            .where(e1Id.eq(id).and(buildNodePropCondition("e2", nodeProps)))
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            var records = dsl.select(e2Json)
                    .from(e1)
                    .join(e2).on(arrayContains(e1Targets, e2Iri).and(e2OntologyId.eq(e1OntologyId)))
                    .where(where)
                    .orderBy(e2Iri.asc())
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();

            return new PageImpl<>(readJsonElements(records, e2Json), pageable, count);
        } catch (SQLException e) {
            throw new RuntimeException("lookupArrayTargets failed", e);
        }
    }

    private Page<JsonElement> lookupArraySources(String id, String column, Map<String, String> nodeProps, Pageable pageable, String search) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Table<?> e1 = OLS_ENTITIES.as("e1");
            Table<?> e2 = OLS_ENTITIES.as("e2");

            Field<String> e1Id = field("e1", "id", String.class);
            Field<String> e1Iri = field("e1", "iri", String.class);
            Field<String> e1OntologyId = field("e1", "ontology_id", String.class);
            Field<String[]> e2Sources = field("e2", column, String[].class);
            Field<String[]> e2Labels = field("e2", "label", String[].class);
            Field<String> e2Iri = field("e2", "iri", String.class);
            Field<String> e2OntologyId = field("e2", "ontology_id", String.class);
            Field<byte[]> e2Json = field("e2", "_json", byte[].class);

            Condition where = e1Id.eq(id)
                    .and(arrayContainsField(e2Sources, e1Iri))
                    .and(e2OntologyId.eq(e1OntologyId))
                    .and(buildNodePropCondition("e2", nodeProps));

            if (search != null && !search.trim().isEmpty()) {
                Table<?> labels = unnest(e2Labels, "labels", "l");
                Field<String> labelValue = field("labels", "l", String.class);
                where = where.andExists(
                        dsl.selectOne()
                                .from(labels)
                                .where(DSL.lower(labelValue).like(DSL.inline("%")
                                        .concat(DSL.lower(DSL.val(search)))
                                        .concat(DSL.inline("%")))));
            }

            long count = Optional.ofNullable(dsl.selectCount()
                            .from(e1)
                            .join(e2).on(arrayContainsField(e2Sources, e1Iri).and(e2OntologyId.eq(e1OntologyId)))
                            .where(where)
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            var records = dsl.select(e2Json)
                    .from(e1)
                    .join(e2).on(arrayContainsField(e2Sources, e1Iri).and(e2OntologyId.eq(e1OntologyId)))
                    .where(where)
                    .orderBy(e2Iri.asc())
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();

            return new PageImpl<>(readJsonElements(records, e2Json), pageable, count);
        } catch (SQLException e) {
            throw new RuntimeException("lookupArraySources failed", e);
        }
    }

    private Condition buildNodePropCondition(String qualifier, Map<String, String> nodeProps) {
        Condition condition = DSL.trueCondition();
        for (var entry : nodeProps.entrySet()) {
            switch (entry.getKey()) {
                case "isObsolete" -> condition = condition.and(
                        field(qualifier, "is_obsolete", Boolean.class)
                                .eq("true".equals(entry.getValue())));
                case "type" -> condition = condition.and(
                        field(qualifier, "type", String.class).eq(entry.getValue()));
                default -> {
                }
            }
        }
        return condition;
    }

    public static class SimilarResult {
        public JsonElement entity;
        public double score;
    }

    public Page<JsonElement> getSimilar(String type, String iri, Pageable pageable, String modelName) {
        String embeddingColumn = sanitizeEmbeddingColumnName(modelName);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Field<Object> embeddingField = field(embeddingColumn, Object.class);
            Field<String> embeddingText = castAsText(embeddingField).as("embedding");

            Record sourceRecord = dsl.select(ENTITY_ID, embeddingText)
                    .from(OLS_ENTITIES)
                    .where(ENTITY_IRI.eq(iri))
                    .and(ENTITY_TYPE.eq(type))
                    .and(ENTITY_IS_OBSOLETE.isFalse())
                    .and(embeddingField.isNotNull())
                    .orderBy(ENTITY_IS_DEFINING_ONTOLOGY.desc().nullsLast(), ENTITY_ID.asc())
                    .limit(1)
                    .fetchOne();

            if (sourceRecord == null) {
                throw new ResourceNotFoundException("entity not found");
            }

            String sourceId = sourceRecord.get(ENTITY_ID);
            String sourceVector = sourceRecord.get("embedding", String.class);
            Field<Double> distance = vectorDistance(embeddingField, sourceVector);
            Field<Double> rawScore = DSL.field("{0} - ({1})", Double.class, DSL.inline(1.0), distance).as("score");

            var records = dsl.select(ENTITY_JSON, rawScore)
                    .from(OLS_ENTITIES)
                    .where(ENTITY_TYPE.eq(type))
                    .and(embeddingField.isNotNull())
                    .and(ENTITY_ID.ne(sourceId))
                    .orderBy(distance.asc())
                    .limit(pageable.getPageSize())
                    .fetch();

            List<JsonElement> results = new ArrayList<>();
            for (Record record : records) {
                JsonObject json = JsonParser.parseString(PostgresClient.decompressJson(record.get(ENTITY_JSON))).getAsJsonObject();
                json.addProperty("score", normalizeCosineSimilarity(record.get("score", Double.class)));
                results.add(json);
            }

            return new PageImpl<>(results, pageable, results.size());
        } catch (SQLException e) {
            throw new RuntimeException("getSimilar failed", e);
        }
    }

    public double getSimilarity(String type, String iri, String iri2, String modelName) {
        String embeddingColumn = sanitizeEmbeddingColumnName(modelName);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Table<?> a = OLS_ENTITIES.as("a");
            Table<?> b = OLS_ENTITIES.as("b");
            Field<Object> aEmbedding = field("a", embeddingColumn, Object.class);
            Field<Object> bEmbedding = field("b", embeddingColumn, Object.class);
            Field<Double> score = DSL.field("{0} - ({1})", Double.class, DSL.inline(1.0), vectorDistance(aEmbedding, bEmbedding)).as("score");

            Record record = dsl.select(score)
                    .from(a)
                    .crossJoin(b)
                    .where(field("a", "iri", String.class).eq(iri))
                    .and(field("a", "type", String.class).eq(type))
                    .and(aEmbedding.isNotNull())
                    .and(field("b", "iri", String.class).eq(iri2))
                    .and(field("b", "type", String.class).eq(type))
                    .and(bEmbedding.isNotNull())
                    .limit(1)
                    .fetchOne();

            if (record != null) {
                return normalizeCosineSimilarity(record.get("score", Double.class));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getSimilarity failed", e);
        }
        throw new ResourceNotFoundException("entity not found");
    }

    public List<Double> getEmbeddingVector(String type, String iri, String modelName) {
        String embeddingColumn = sanitizeEmbeddingColumnName(modelName);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Field<Object> embeddingField = field(embeddingColumn, Object.class);
            Record record = dsl.select(castAsText(embeddingField).as("embeddings"))
                    .from(OLS_ENTITIES)
                    .where(ENTITY_IRI.eq(iri))
                    .and(ENTITY_TYPE.eq(type))
                    .and(embeddingField.isNotNull())
                    .limit(1)
                    .fetchOne();

            if (record != null) {
                String vecStr = record.get("embeddings", String.class).trim();
                if (vecStr.startsWith("[")) vecStr = vecStr.substring(1);
                if (vecStr.endsWith("]")) vecStr = vecStr.substring(0, vecStr.length() - 1);
                return Arrays.stream(vecStr.split(","))
                        .map(String::trim)
                        .map(Double::parseDouble)
                        .collect(Collectors.toList());
            }
        } catch (SQLException e) {
            throw new RuntimeException("getEmbeddingVector failed", e);
        }
        throw new ResourceNotFoundException("entity not found");
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName) {
        return searchByVector(type, vector, pageable, modelName, true);
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName, boolean includeCurations) {
        String embeddingColumn = sanitizeEmbeddingNodeColumnName(modelName);
        int limit = pageable.getPageSize();
        boolean filterByType = hasConcreteEntityType(type);
        int candidateLimit = nearestNeighborCandidateLimit(limit, filterByType, false);
        String vecLiteral = vectorLiteral(vector);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Select<Record2<String, Double>> candidates =
                    fetchVectorCandidatesSelect(dsl, embeddingColumn, vecLiteral, LABEL_EMBEDDING, filterByType ? type : null, candidateLimit);
            if (includeCurations) {
                candidates = candidates.unionAll(
                        fetchVectorCandidatesSelect(dsl, embeddingColumn, vecLiteral, CURATION_EMBEDDING, filterByType ? type : null, candidateLimit));
            }

            return toVectorSearchPage(dsl, nearestEntities(dsl, candidates, limit), pageable);
        } catch (SQLException e) {
            throw new RuntimeException("searchByVector failed", e);
        }
    }

    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable,
            String modelName, String ontologyId, boolean isDefiningOntology) {
        return searchByVectorInOntology(type, vector, pageable, modelName, ontologyId, isDefiningOntology, true);
    }

    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable,
            String modelName, String ontologyId, boolean isDefiningOntology, boolean includeCurations) {
        String embeddingColumn = sanitizeEmbeddingNodeColumnName(modelName);
        int limit = pageable.getPageSize();
        boolean filterByType = hasConcreteEntityType(type);
        String normalizedOntologyId = ontologyId.toLowerCase();
        String vecLiteral = vectorLiteral(vector);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            if (isDefiningOntology && embeddingNodesCarryScope(dsl)) {
                List<ScoredId> nearest = nearestEntitiesDefinedInOntology(dsl, embeddingColumn, vecLiteral,
                        filterByType ? type : null, normalizedOntologyId, includeCurations, limit);
                return toVectorSearchPage(dsl, nearest, pageable);
            }

            // Imported terms have no embedding rows of their own: their vectors belong to the
            // defining ontology's copy of the term, so this scope cannot be expressed as a filter on
            // ols_embedding_nodes. Nor can any scope on a database loaded before issue #1445, which
            // has no ontology on its embedding rows. Both reach the ontology through ols_entities,
            // as every scoped search did before.
            int candidateLimit = nearestNeighborCandidateLimit(limit, filterByType, true);
            Select<Record2<String, Double>> candidates = fetchVectorCandidatesInOntologySelect(
                    dsl, embeddingColumn, vecLiteral, LABEL_EMBEDDING, filterByType ? type : null,
                    normalizedOntologyId, isDefiningOntology, candidateLimit);
            if (includeCurations) {
                candidates = candidates.unionAll(fetchVectorCandidatesInOntologySelect(
                        dsl, embeddingColumn, vecLiteral, CURATION_EMBEDDING, filterByType ? type : null,
                        normalizedOntologyId, isDefiningOntology, candidateLimit));
            }

            return toVectorSearchPage(dsl, nearestEntities(dsl, candidates, limit), pageable);
        } catch (SQLException e) {
            throw new RuntimeException("searchByVectorInOntology failed", e);
        }
    }

    private Select<Record2<String, Double>> fetchVectorCandidatesSelect(DSLContext dsl, String embeddingColumn, String vecLiteral,
            String embeddingType, String entityType, int candidateLimit) {
        Table<?> emb = OLS_EMBEDDING_NODES.as("emb");
        Table<?> en = OLS_ENTITIES.as("en");
        Field<Object> embedding = field("emb", embeddingColumn, Object.class);
        Field<Double> distance = vectorDistance(embedding, vecLiteral).as("dist");

        Condition where = field("emb", "type", String.class).eq(embeddingType)
                .and(embedding.isNotNull());
        if (entityType != null) {
            where = where.and(field("en", "type", String.class).eq(entityType));
        }

        return dsl.select(field("en", "id", String.class).as("id"), distance)
                .from(emb)
                .join(en).on(field("en", "id", String.class).eq(field("emb", "entity_id", String.class)))
                .where(where)
                .orderBy(distance.asc())
                .limit(candidateLimit);
    }

    private Select<Record2<String, Double>> fetchVectorCandidatesInOntologySelect(DSLContext dsl, String embeddingColumn, String vecLiteral,
            String embeddingType, String entityType, String ontologyId, boolean isDefiningOntology, int candidateLimit) {
        Table<?> emb = OLS_EMBEDDING_NODES.as("emb");
        Field<Object> embedding = field("emb", embeddingColumn, Object.class);
        Field<Double> distance = vectorDistance(embedding, vecLiteral).as("dist");

        if (isDefiningOntology) {
            Table<?> en = OLS_ENTITIES.as("en");
            Condition where = field("emb", "type", String.class).eq(embeddingType)
                    .and(embedding.isNotNull())
                    .and(field("en", "ontology_id", String.class).eq(ontologyId));
            if (entityType != null) {
                where = where.and(field("en", "type", String.class).eq(entityType));
            }

            return dsl.select(field("en", "id", String.class).as("id"), distance)
                    .from(emb)
                    .join(en).on(field("en", "id", String.class).eq(field("emb", "entity_id", String.class)))
                    .where(where)
                    .orderBy(distance.asc())
                    .limit(candidateLimit);
        }

        Table<?> defining = OLS_ENTITIES.as("defining");
        Table<?> target = OLS_ENTITIES.as("target");
        Condition where = field("emb", "type", String.class).eq(embeddingType)
                .and(embedding.isNotNull())
                .and(field("target", "ontology_id", String.class).eq(ontologyId));
        if (entityType != null) {
            where = where.and(field("target", "type", String.class).eq(entityType));
        }

        return dsl.select(field("target", "id", String.class).as("id"), distance)
                .from(emb)
                .join(defining).on(field("defining", "id", String.class).eq(field("emb", "entity_id", String.class)))
                .join(target).on(field("target", "iri", String.class).eq(field("defining", "iri", String.class))
                        .and(field("target", "type", String.class).eq(field("defining", "type", String.class))))
                .where(where)
                .orderBy(distance.asc())
                .limit(candidateLimit);
    }

    /**
     * Collapses candidate embedding rows to the {@code limit} nearest entities. Candidates carry
     * only an entity id and a distance: the entity JSON is TOASTed, and grouping by it pulled
     * every candidate's JSON through the aggregate just to return one page (GitHub issue #1445).
     */
    private List<ScoredId> nearestEntities(DSLContext dsl, Select<Record2<String, Double>> candidates, int limit) {
        Table<?> sub = candidates.asTable("sub");
        Field<String> subId = field("sub", "id", String.class);
        Field<Double> bestDistance = DSL.min(field("sub", "dist", Double.class)).as("dist");

        return dsl.select(subId, bestDistance)
                .from(sub)
                .groupBy(subId)
                .orderBy(bestDistance.asc(), subId.asc())
                .limit(limit)
                .fetch(record -> new ScoredId(record.get(subId), record.get("dist", Double.class)));
    }

    /**
     * The {@code limit} nearest entities among those an ontology defines, using the ontology id and
     * entity type copied onto every embedding row (GitHub issue #1445).
     *
     * <p>With the ontology filter on {@code ols_entities}, behind a join, an HNSW scan cannot apply
     * it, and the planner's alternative is to walk every entity of the ontology and fetch each of
     * its vectors through a second index. With the filter on the embedding rows there are two
     * cheaper ways to answer, and which is cheaper depends on how many vectors are in scope:
     *
     * <ul>
     *   <li>up to {@link #exactScanMaxVectors}: rank all of them. They are found through a small
     *       index and read in the order they are stored, and the result is exact;</li>
     *   <li>more: walk the HNSW index outwards from the query and keep the rows in scope. The work
     *       depends on how far the walk has to go, not on the size of the ontology, and the result
     *       is approximate.</li>
     * </ul>
     *
     * <p>Label and curation embeddings are sized and searched separately: they are in separate
     * HNSW indexes, and an ontology usually has far fewer curations than labels. Taking the
     * {@code limit} nearest entities of each and merging them loses nothing: an entity that belongs
     * on the page by its label distance is among the {@code limit} nearest by label, and likewise
     * for curations.
     */
    private List<ScoredId> nearestEntitiesDefinedInOntology(DSLContext dsl, String embeddingColumn, String vecLiteral,
            String entityType, String ontologyId, boolean includeCurations, int limit) {
        Table<?> emb = OLS_EMBEDDING_NODES.as("emb");
        Field<Object> embedding = field("emb", embeddingColumn, Object.class);
        Map<String, Double> bestDistanceById = new HashMap<>();

        for (String embeddingType : includeCurations
                ? List.of(LABEL_EMBEDDING, CURATION_EMBEDDING) : List.of(LABEL_EMBEDDING)) {
            // The embedding type is inlined, not bound, so that the predicates of the partial
            // indexes (WHERE type = '...') are provably implied whatever plan Postgres caches.
            Condition scope = field("emb", "type", String.class).eq(DSL.inline(embeddingType))
                    .and(embedding.isNotNull())
                    .and(field("emb", "ontology_id", String.class).eq(ontologyId));
            if (entityType != null) {
                scope = scope.and(field("emb", "entity_type", String.class).eq(entityType));
            }

            // Counted from the scope index alone, and no further than is needed to choose.
            long exactScanMax = Math.max(exactScanMaxVectors, 0);
            long vectorsInScope = Optional.ofNullable(dsl.selectCount()
                            .from(DSL.selectOne().from(emb).where(scope).limit(exactScanMax + 1).asTable("scoped"))
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            List<ScoredId> candidates;
            if (vectorsInScope == 0) {
                candidates = List.of();
            } else if (ranksExactly(vectorsInScope, exactScanMax, hnswIterativeScans(dsl))) {
                candidates = exactNearestInScope(dsl, emb, embedding, vecLiteral, scope, limit);
            } else {
                candidates = approximateNearestInScope(dsl, emb, embedding, vecLiteral, scope, limit);
            }
            for (ScoredId candidate : candidates) {
                bestDistanceById.merge(candidate.id(), candidate.distance(), OlsPostgresClient::nearer);
            }
        }

        return bestDistanceById.entrySet().stream()
                .map(entry -> new ScoredId(entry.getKey(), entry.getValue()))
                .sorted(ScoredId.NEAREST_FIRST)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * The {@code limit} nearest entities in scope, from the distance to every vector in scope.
     *
     * <p>Distances are computed in a subquery that {@code OFFSET 0} stops Postgres from
     * flattening, so that each is computed as the scan reads its row. Flattened, a large scope is
     * planned as sort-by-entity then aggregate, and the vectors are only fetched from TOAST while
     * aggregating, in entity id order instead of the order they are stored in.
     *
     * <p>Nothing here orders by distance, which also keeps the HNSW index out of the plan. That is
     * deliberate: with {@code hnsw.iterative_scan} off (the server default) an HNSW scan returns
     * the rows of its {@code hnsw.ef_search} nearest vectors and the scope filter is applied to
     * those afterwards, which for a small scope usually leaves none.
     */
    private List<ScoredId> exactNearestInScope(DSLContext dsl, Table<?> emb, Field<Object> embedding, String vecLiteral,
            Condition scope, int limit) {
        Table<?> scoped = DSL.select(field("emb", "entity_id", String.class).as("id"),
                        vectorDistance(embedding, vecLiteral).as("dist"))
                .from(emb)
                .where(scope)
                .offset(DSL.inline(0))
                .asTable("scoped");
        Field<String> id = field("scoped", "id", String.class);
        Field<Double> bestDistance = DSL.min(field("scoped", "dist", Double.class)).as("dist");

        return scoredIds(dsl.select(id.as("id"), bestDistance)
                .from(scoped)
                .groupBy(id)
                .orderBy(bestDistance.asc(), id.asc())
                .limit(limit)
                .fetch());
    }

    /**
     * Nearest embedding rows in scope, read from the HNSW index in distance order.
     *
     * <p>Two settings are applied for the duration of the query's transaction:
     *
     * <ul>
     *   <li>{@code hnsw.iterative_scan = relaxed_order} (pgvector 0.8+). An HNSW scan hands back the
     *       rows of its {@code hnsw.ef_search} (default 40) nearest vectors and stops, and the
     *       scope filter is applied to those afterwards. Unless the ontology dominates the neighbourhood of the
     *       query, that leaves a handful of rows or none. With iterative scans the index keeps
     *       going until the LIMIT is met, {@code hnsw.max_scan_tuples} have been visited, or the
     *       scan's memory allowance is used up. Relaxed order lets it return a row as soon as it is
     *       found; rows are re-ranked by their real distance here anyway.</li>
     *   <li>{@code enable_sort = off}. The index scan is the only plan that needs no sort, so this
     *       is what selects it. Left to its cost model the planner often prefers to read the whole
     *       scope and sort it, because it prices fetching a TOASTed vector at nothing, and that
     *       scan is the one this method exists to avoid. If the model has no HNSW index the
     *       planner still sorts, and the result is merely exact.</li>
     * </ul>
     *
     * <p>An entity usually has several embedding rows (its label and each synonym) and they tend to
     * be close to one another, so filling a page of {@code limit} entities takes more than
     * {@code limit} rows. The row limit starts at a small multiple of the page size and is widened
     * if those rows turn out to belong to too few entities. A widened scan revisits pages the
     * previous one has just read, so it costs little beyond the extra distance it covers.
     *
     * <p>What comes back is the index's answer, not the exact one. It is close to exact when the
     * query is about what the ontology is about. The further the query is from the ontology, the
     * more rows of other ontologies the walk has to pass, and the more of the ontology's own
     * nearest rows it misses; if it runs out of its allowance first, the page comes back short,
     * or empty.
     */
    private List<ScoredId> approximateNearestInScope(DSLContext dsl, Table<?> emb, Field<Object> embedding, String vecLiteral,
            Condition scope, int limit) {
        Field<Double> distance = vectorDistance(embedding, vecLiteral).as("dist");

        long rowLimit = Math.max((long) limit * HNSW_ROWS_PER_RESULT, HNSW_MIN_ROWS);
        for (int widenings = 0; ; widenings++) {
            List<ScoredId> nearestRows = scoredIds(fetchWithLocalSettings(dsl,
                    DSL.select(field("emb", "entity_id", String.class).as("id"), distance)
                            .from(emb)
                            .where(scope)
                            .orderBy(distance.asc())
                            .limit(rowLimit),
                    setLocal("hnsw.iterative_scan", "relaxed_order"), setLocal("enable_sort", "off")));

            // Fewer rows than asked for means the index has nothing more to give within its scan
            // budget, so asking for more would only repeat the same scan.
            boolean indexExhausted = nearestRows.size() < rowLimit;
            if (indexExhausted || widenings == HNSW_MAX_WIDENINGS
                    || nearestRows.stream().map(ScoredId::id).distinct().count() >= limit) {
                return nearestRows;
            }
            rowLimit *= HNSW_ROW_LIMIT_GROWTH;
        }
    }

    /**
     * Runs a query in a transaction of its own with the given {@link JooqSupport#setLocal}
     * settings in force. They end with the transaction, whichever way it ends, so the pooled
     * connection goes back as it came.
     */
    @SafeVarargs
    private static Result<Record2<String, Double>> fetchWithLocalSettings(DSLContext dsl,
            Select<Record2<String, Double>> query, Field<String>... settings) {
        return dsl.transactionResult(transaction -> {
            DSLContext tx = DSL.using(transaction);
            tx.select(settings).fetch();
            return tx.fetch(query);
        });
    }

    private static List<ScoredId> scoredIds(Result<Record2<String, Double>> rows) {
        return rows.map(row -> new ScoredId(row.value1(), row.value2()));
    }

    /**
     * Whether {@code ols_embedding_nodes} has the {@code ontology_id} and {@code entity_type}
     * columns added for GitHub issue #1445. A database loaded by an older dataload has neither,
     * and the loader replaces the tables of a database in place, so the answer can change in either
     * direction under a running backend. It is one catalog lookup, and it is asked on every search
     * for the entities an ontology defines.
     */
    private boolean embeddingNodesCarryScope(DSLContext dsl) {
        // to_regclass resolves the table through the search path exactly as the queries do.
        return dsl.fetchCount(PG_ATTRIBUTE,
                DSL.condition("{0} = to_regclass({1})", field("attrelid", Object.class), DSL.inline("ols_embedding_nodes"))
                        .and(field("attname", String.class).in(DSL.inline("ontology_id"), DSL.inline("entity_type")))
                        .and(DSL.not(field("attisdropped", Boolean.class)))) == 2;
    }

    /**
     * Whether the installed pgvector can continue an HNSW scan past its first batch of rows, which
     * approximateNearestInScope depends on. Asked once; without it every scope is ranked exactly.
     */
    private boolean hnswIterativeScans(DSLContext dsl) {
        Boolean supported = hnswIterativeScans;
        if (supported == null) {
            supported = supportsIterativeScans(dsl.select(field("extversion", String.class))
                    .from(PG_EXTENSION)
                    .where(field("extname", String.class).eq(DSL.inline("vector")))
                    .fetchOne(0, String.class));
            hnswIterativeScans = supported;
        }
        return supported;
    }

    private Page<JsonElement> toVectorSearchPage(DSLContext dsl, List<ScoredId> nearest, Pageable pageable) throws SQLException {
        if (nearest.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        Map<String, byte[]> jsonById = dsl.select(ENTITY_ID, ENTITY_JSON)
                .from(OLS_ENTITIES)
                .where(ENTITY_ID.in(nearest.stream().map(ScoredId::id).collect(Collectors.toList())))
                .fetchMap(ENTITY_ID, ENTITY_JSON);

        List<JsonElement> results = new ArrayList<>(nearest.size());
        for (ScoredId scored : nearest) {
            byte[] compressedJson = jsonById.get(scored.id());
            if (compressedJson == null) {
                // An embedding row whose entity is missing. The loader writes both from the same
                // entity, so this would take a broken load; the old inner join dropped such rows too.
                continue;
            }
            JsonObject json = JsonParser.parseString(PostgresClient.decompressJson(compressedJson)).getAsJsonObject();
            json.addProperty("score", normalizeCosineDistance(scored.distance()));
            results.add(json);
        }

        return new PageImpl<>(results, pageable, results.size());
    }

    private boolean hasConcreteEntityType(String type) {
        return type != null && !type.isBlank() && !"OntologyEntity".equals(type);
    }

    private int nearestNeighborCandidateLimit(int requestedLimit, boolean filterByType, boolean filterByOntology) {
        int candidateLimit = Math.max(requestedLimit * 20, 100);
        if (filterByType) {
            candidateLimit = Math.max(candidateLimit, requestedLimit * 100);
        }
        if (filterByOntology) {
            candidateLimit = Math.max(candidateLimit, requestedLimit * 500);
        }
        return Math.min(candidateLimit, 20_000);
    }

    private String vectorLiteral(List<Double> vector) {
        return "[" + vector.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    public List<String> getEmbeddingModels() {
        List<String> models = new ArrayList<>();

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            var records = dsl.select(COLUMN_NAME)
                    .from(INFORMATION_SCHEMA_COLUMNS)
                    .where(field("table_name", String.class).eq("ols_entities"))
                    .and(COLUMN_NAME.like("embeddings\\_%", '\\'))
                    .fetch();

            for (Record record : records) {
                String colName = record.get(COLUMN_NAME);
                String modelName = colName.substring("embeddings_".length());
                if (!modelName.contains("pca16")) {
                    models.add(modelName);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("getEmbeddingModels failed", e);
        }

        return models;
    }

    private List<JsonElement> readJsonElements(org.jooq.Result<? extends Record> records, Field<byte[]> jsonField) throws SQLException {
        List<JsonElement> results = new ArrayList<>(records.size());
        for (Record record : records) {
            results.add(JsonParser.parseString(PostgresClient.decompressJson(record.get(jsonField))));
        }
        return results;
    }

    private record ScoredId(String id, double distance) {
        static final Comparator<ScoredId> NEAREST_FIRST =
                Comparator.comparingDouble(ScoredId::distance).thenComparing(ScoredId::id);
    }
}
