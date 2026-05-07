package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.INFORMATION_SCHEMA_COLUMNS;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_EMBEDDING_NODES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_ENTITIES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContains;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.castAsText;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.field;
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

    private static String sanitizeCentroidColumnName(String modelName) {
        if (modelName == null || !SAFE_MODEL_NAME.matcher(modelName).matches()) {
            throw new IllegalArgumentException("Invalid embedding model name: " + modelName);
        }
        return "descendants_centroid_" + modelName;
    }

    static double normalizeCosineSimilarity(double similarity) {
        return clampUnitInterval((similarity + 1.0) / 2.0);
    }

    static double normalizeCosineDistance(double distance) {
        return clampUnitInterval(1.0 - (distance / 2.0));
    }

    private static double clampUnitInterval(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    @Autowired
    PostgresClient postgresClient;

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
                    .and(arrayContains(e2Sources, e1Iri))
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
                            .join(e2).on(arrayContains(e2Sources, e1Iri).and(e2OntologyId.eq(e1OntologyId)))
                            .where(where)
                            .fetchOne(0, Long.class))
                    .orElse(0L);

            var records = dsl.select(e2Json)
                    .from(e1)
                    .join(e2).on(arrayContains(e2Sources, e1Iri).and(e2OntologyId.eq(e1OntologyId)))
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
            if ("isObsolete".equals(entry.getKey())) {
                condition = condition.and(field(qualifier, "is_obsolete", Boolean.class).eq("true".equals(entry.getValue())));
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

    public List<Double> getCentroidVector(String type, String iri, String modelName) {
        String centroidColumn = sanitizeCentroidColumnName(modelName);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Field<Object> centroidField = field(centroidColumn, Object.class);
            Record record = dsl.select(castAsText(centroidField).as("centroid"))
                    .from(OLS_ENTITIES)
                    .where(ENTITY_IRI.eq(iri))
                    .and(ENTITY_TYPE.eq(type))
                    .and(centroidField.isNotNull())
                    .limit(1)
                    .fetchOne();

            if (record != null) {
                String vecStr = record.get("centroid", String.class).trim();
                if (vecStr.startsWith("[")) vecStr = vecStr.substring(1);
                if (vecStr.endsWith("]")) vecStr = vecStr.substring(0, vecStr.length() - 1);
                return Arrays.stream(vecStr.split(","))
                        .map(String::trim)
                        .map(Double::parseDouble)
                        .collect(Collectors.toList());
            }
        } catch (SQLException e) {
            throw new RuntimeException("getCentroidVector failed", e);
        }
        throw new ResourceNotFoundException("entity not found");
    }

    public Page<JsonElement> searchByCentroidVector(String type, List<Double> vector, Pageable pageable, String modelName) {
        String centroidColumn = sanitizeCentroidColumnName(modelName);
        int limit = pageable.getPageSize();
        boolean filterByType = hasConcreteEntityType(type);
        String vecLiteral = vectorLiteral(vector);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Field<Object> centroidField = field(centroidColumn, Object.class);
            Field<Double> distance = vectorDistance(centroidField, vecLiteral).as("dist");

            Condition where = centroidField.isNotNull();
            if (filterByType) {
                where = where.and(ENTITY_TYPE.eq(type));
            }

            var records = dsl.select(ENTITY_JSON, distance)
                    .from(OLS_ENTITIES)
                    .where(where)
                    .orderBy(distance.asc())
                    .limit(limit)
                    .fetch();

            List<JsonElement> results = new ArrayList<>();
            for (Record record : records) {
                JsonObject json = JsonParser.parseString(PostgresClient.decompressJson(record.get(ENTITY_JSON))).getAsJsonObject();
                json.addProperty("score", normalizeCosineDistance(record.get("dist", Double.class)));
                results.add(json);
            }
            return new PageImpl<>(results, pageable, results.size());
        } catch (SQLException e) {
            throw new RuntimeException("searchByCentroidVector failed", e);
        }
    }

    public Page<JsonElement> searchByCentroidVectorInOntology(String type, List<Double> vector, Pageable pageable,
            String modelName, String ontologyId, boolean isDefiningOntology) {
        String centroidColumn = sanitizeCentroidColumnName(modelName);
        int limit = pageable.getPageSize();
        boolean filterByType = hasConcreteEntityType(type);
        String normalizedOntologyId = ontologyId.toLowerCase();
        String vecLiteral = vectorLiteral(vector);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Field<Object> centroidField = field(centroidColumn, Object.class);
            Field<Double> distance = vectorDistance(centroidField, vecLiteral).as("dist");

            Condition where = centroidField.isNotNull()
                    .and(ENTITY_ONTOLOGY_ID.eq(normalizedOntologyId));
            if (filterByType) {
                where = where.and(ENTITY_TYPE.eq(type));
            }
            if (isDefiningOntology) {
                where = where.and(ENTITY_IS_DEFINING_ONTOLOGY.isTrue());
            }

            var records = dsl.select(ENTITY_JSON, distance)
                    .from(OLS_ENTITIES)
                    .where(where)
                    .orderBy(distance.asc())
                    .limit(limit)
                    .fetch();

            List<JsonElement> results = new ArrayList<>();
            for (Record record : records) {
                JsonObject json = JsonParser.parseString(PostgresClient.decompressJson(record.get(ENTITY_JSON))).getAsJsonObject();
                json.addProperty("score", normalizeCosineDistance(record.get("dist", Double.class)));
                results.add(json);
            }
            return new PageImpl<>(results, pageable, results.size());
        } catch (SQLException e) {
            throw new RuntimeException("searchByCentroidVectorInOntology failed", e);
        }
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
            Select<Record3<String, byte[], Double>> candidates =
                    fetchVectorCandidatesSelect(dsl, embeddingColumn, vecLiteral, "LabelEmbedding", filterByType ? type : null, candidateLimit);
            if (includeCurations) {
                candidates = candidates.unionAll(
                        fetchVectorCandidatesSelect(dsl, embeddingColumn, vecLiteral, "CurationEmbedding", filterByType ? type : null, candidateLimit));
            }

            return toVectorSearchPage(dsl, candidates, pageable, limit);
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
        int candidateLimit = nearestNeighborCandidateLimit(limit, filterByType, true);
        String normalizedOntologyId = ontologyId.toLowerCase();
        String vecLiteral = vectorLiteral(vector);

        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Select<Record3<String, byte[], Double>> candidates = fetchVectorCandidatesInOntologySelect(
                    dsl, embeddingColumn, vecLiteral, "LabelEmbedding", filterByType ? type : null,
                    normalizedOntologyId, isDefiningOntology, candidateLimit);
            if (includeCurations) {
                candidates = candidates.unionAll(fetchVectorCandidatesInOntologySelect(
                        dsl, embeddingColumn, vecLiteral, "CurationEmbedding", filterByType ? type : null,
                        normalizedOntologyId, isDefiningOntology, candidateLimit));
            }

            return toVectorSearchPage(dsl, candidates, pageable, limit);
        } catch (SQLException e) {
            throw new RuntimeException("searchByVectorInOntology failed", e);
        }
    }

    private Select<Record3<String, byte[], Double>> fetchVectorCandidatesSelect(DSLContext dsl, String embeddingColumn, String vecLiteral,
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

        return dsl.select(field("en", "id", String.class).as("id"), field("en", "_json", byte[].class).as("_json"), distance)
                .from(emb)
                .join(en).on(field("en", "id", String.class).eq(field("emb", "entity_id", String.class)))
                .where(where)
                .orderBy(distance.asc())
                .limit(candidateLimit);
    }

    private Select<Record3<String, byte[], Double>> fetchVectorCandidatesInOntologySelect(DSLContext dsl, String embeddingColumn, String vecLiteral,
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

            return dsl.select(field("en", "id", String.class).as("id"), field("en", "_json", byte[].class).as("_json"), distance)
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

        return dsl.select(field("target", "id", String.class).as("id"), field("target", "_json", byte[].class).as("_json"), distance)
                .from(emb)
                .join(defining).on(field("defining", "id", String.class).eq(field("emb", "entity_id", String.class)))
                .join(target).on(field("target", "iri", String.class).eq(field("defining", "iri", String.class))
                        .and(field("target", "type", String.class).eq(field("defining", "type", String.class))))
                .where(where)
                .orderBy(distance.asc())
                .limit(candidateLimit);
    }

    private Page<JsonElement> toVectorSearchPage(DSLContext dsl, Select<Record3<String, byte[], Double>> candidates, Pageable pageable, int limit) throws SQLException {
        Table<?> sub = candidates.asTable("sub");
        Field<String> subId = field("sub", "id", String.class);
        Field<byte[]> subJson = field("sub", "_json", byte[].class);
        Field<Double> subDistance = field("sub", "dist", Double.class);
        Field<Double> bestDistance = DSL.min(subDistance).as("dist");

        var records = dsl.select(subId, subJson, bestDistance)
                .from(sub)
                .groupBy(subId, subJson)
                .orderBy(bestDistance.asc())
                .limit(limit)
                .fetch();

        List<JsonElement> results = toScoredEntities(records, subId, subJson, "dist").stream()
                .map(scored -> {
                    JsonObject json = JsonParser.parseString(unsafeDecompress(scored.json)).getAsJsonObject();
                    json.addProperty("score", normalizeCosineDistance(scored.distance));
                    return (JsonElement) json;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, results.size());
    }

    private String unsafeDecompress(byte[] json) {
        try {
            return PostgresClient.decompressJson(json);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to decode JSON payload", e);
        }
    }

    private List<ScoredEntity> toScoredEntities(org.jooq.Result<? extends Record> records, Field<String> idField, Field<byte[]> jsonField, String scoreField) {
        List<ScoredEntity> results = new ArrayList<>(records.size());
        for (Record record : records) {
            results.add(new ScoredEntity(record.get(idField), record.get(jsonField), record.get(scoreField, Double.class)));
        }
        return results;
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

    private static class ScoredEntity {
        final String id;
        final byte[] json;
        final double distance;

        private ScoredEntity(String id, byte[] json, double distance) {
            this.id = id;
            this.json = json;
            this.distance = distance;
        }
    }
}
