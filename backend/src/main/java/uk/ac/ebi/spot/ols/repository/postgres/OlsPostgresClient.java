package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jooq.*;
import org.jooq.Record;
import static org.jooq.impl.DSL.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class OlsPostgresClient {

    @Autowired
    PostgresClient postgresClient;

    Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(OlsPostgresClient.class);

    private static final Set<String> DIRECT_PARENT_EDGES = Set.of("directParent");
    private static final Set<String> HIERARCHICAL_PARENT_EDGES = Set.of("hierarchicalParent");

    private DSLContext dsl() {
        return postgresClient.dsl();
    }

    public long getDatabaseNodeCount() {
        return postgresClient.returnNodeCount();
    }

    public Page<JsonElement> getAll(String type, Map<String, String> properties, Pageable pageable) {
        Condition condition = field("type", String.class).eq(type);

        for (var entry : properties.entrySet()) {
            switch (entry.getKey()) {
                case "id" -> condition = condition.and(field("id", String.class).eq(entry.getValue()));
                case "iri" -> condition = condition.and(field("iri", String.class).eq(entry.getValue()));
                case "ontologyId" -> condition = condition.and(field("ontology_id", String.class).eq(entry.getValue()));
            }
        }

        var dataQuery = dsl().select(field("_json"), field("iri"))
                .from("ols_entities")
                .where(condition)
                .orderBy(field("iri").asc())
                .offset((int) pageable.getOffset())
                .limit(pageable.getPageSize());

        var countQuery = dsl().selectCount()
                .from("ols_entities")
                .where(condition);

        return postgresClient.queryPaginated(dataQuery, countQuery, pageable);
    }

    public JsonElement getOne(String type, Map<String, String> properties) {
        Page<JsonElement> results = getAll(type, properties, PageRequest.of(0, 10));

        if (results.getTotalElements() != 1) {
            throw new RuntimeException("expected exactly one result for getOne, but got " + results.getTotalElements());
        }

        return results.getContent().iterator().next();
    }

    public Page<JsonElement> traverseOutgoingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> targetNodeProps, Pageable pageable) {

        Field<String> joinColumn = field(name("e", "end_id"), String.class);
        Condition condition = buildEdgeCondition(id, edgeIRIs, edgeProps, targetNodeProps, true);

        var dataQuery = dsl().selectDistinct(field(name("e2", "_json")), field(name("e2", "iri")))
                .from(table("ols_edges").as("e"))
                .join(table("ols_entities").as("e2")).on(joinColumn.eq(field(name("e2", "id"), String.class)))
                .where(condition)
                .orderBy(field(name("e2", "iri")).asc())
                .offset((int) pageable.getOffset())
                .limit(pageable.getPageSize());

        var countQuery = dsl().select(countDistinct(field(name("e2", "id"))))
                .from(table("ols_edges").as("e"))
                .join(table("ols_entities").as("e2")).on(joinColumn.eq(field(name("e2", "id"), String.class)))
                .where(condition);

        return postgresClient.queryPaginated(dataQuery, countQuery, pageable);
    }

    public Page<JsonElement> traverseIncomingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> sourceNodeProps, Pageable pageable, String searchQuery) {

        Field<String> joinColumn = field(name("e", "start_id"), String.class);
        Condition condition = buildEdgeCondition(id, edgeIRIs, edgeProps, sourceNodeProps, false);

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            condition = condition.and(
                    condition("EXISTS(SELECT 1 FROM unnest({0}) l WHERE lower(l) LIKE '%' || lower({1}) || '%')",
                            field(name("e2", "label")),
                            val(searchQuery)));
        }

        var dataQuery = dsl().selectDistinct(field(name("e2", "_json")), field(name("e2", "iri")))
                .from(table("ols_edges").as("e"))
                .join(table("ols_entities").as("e2")).on(joinColumn.eq(field(name("e2", "id"), String.class)))
                .where(condition)
                .orderBy(field(name("e2", "iri")).asc())
                .offset((int) pageable.getOffset())
                .limit(pageable.getPageSize());

        var countQuery = dsl().select(countDistinct(field(name("e2", "id"))))
                .from(table("ols_edges").as("e"))
                .join(table("ols_entities").as("e2")).on(joinColumn.eq(field(name("e2", "id"), String.class)))
                .where(condition);

        return postgresClient.queryPaginated(dataQuery, countQuery, pageable);
    }

    public Page<JsonElement> traverseIncomingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> sourceNodeProps, Pageable pageable) {
        return traverseIncomingEdges(type, id, edgeIRIs, edgeProps, sourceNodeProps, pageable, null);
    }

    public Page<JsonElement> recursivelyTraverseOutgoingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> targetNodeProps, Pageable pageable) {

        if (isDirectParentTraversal(edgeIRIs)) {
            return ancestorQuery(id, "direct_ancestors", targetNodeProps, pageable);
        } else if (isHierarchicalParentTraversal(edgeIRIs)) {
            return ancestorQuery(id, "hierarchical_ancestors", targetNodeProps, pageable);
        }

        return recursiveEdgeTraversal(id, edgeIRIs, edgeProps, targetNodeProps, true, pageable);
    }

    public Page<JsonElement> recursivelyTraverseIncomingEdges(String type, String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> sourceNodeProps, Pageable pageable) {

        if (isDirectParentTraversal(edgeIRIs)) {
            return descendantQuery(id, "direct_ancestors", sourceNodeProps, pageable);
        } else if (isHierarchicalParentTraversal(edgeIRIs)) {
            return descendantQuery(id, "hierarchical_ancestors", sourceNodeProps, pageable);
        }

        return recursiveEdgeTraversal(id, edgeIRIs, edgeProps, sourceNodeProps, false, pageable);
    }


    // --- Hierarchy queries using materialized ancestor arrays ---

    private Page<JsonElement> ancestorQuery(String id, String ancestorColumn,
            Map<String, String> targetNodeProps, Pageable pageable) {

        Condition targetCondition = buildNodePropCondition("e2", targetNodeProps);

        var dataQuery = dsl().select(field(name("e2", "_json")), field(name("e2", "iri")))
                .from(table("ols_entities").as("e1"))
                .join(table("ols_entities").as("e2")).on(
                        condition("{0} = ANY({1})", field(name("e2", "iri")), field(name("e1", ancestorColumn)))
                                .and(field(name("e2", "ontology_id")).eq(field(name("e1", "ontology_id")))))
                .where(field(name("e1", "id"), String.class).eq(id))
                .and(targetCondition)
                .orderBy(field(name("e2", "iri")).asc())
                .offset((int) pageable.getOffset())
                .limit(pageable.getPageSize());

        var countQuery = dsl().selectCount()
                .from(table("ols_entities").as("e1"))
                .join(table("ols_entities").as("e2")).on(
                        condition("{0} = ANY({1})", field(name("e2", "iri")), field(name("e1", ancestorColumn)))
                                .and(field(name("e2", "ontology_id")).eq(field(name("e1", "ontology_id")))))
                .where(field(name("e1", "id"), String.class).eq(id))
                .and(targetCondition);

        return postgresClient.queryPaginated(dataQuery, countQuery, pageable);
    }

    private Page<JsonElement> descendantQuery(String id, String ancestorColumn,
            Map<String, String> sourceNodeProps, Pageable pageable) {

        Condition sourceCondition = buildNodePropCondition("e2", sourceNodeProps);

        var dataQuery = dsl().select(field(name("e2", "_json")), field(name("e2", "iri")))
                .from(table("ols_entities").as("e1"))
                .join(table("ols_entities").as("e2")).on(
                        condition("{0} = ANY({1})", field(name("e1", "iri")), field(name("e2", ancestorColumn)))
                                .and(field(name("e2", "ontology_id")).eq(field(name("e1", "ontology_id")))))
                .where(field(name("e1", "id"), String.class).eq(id))
                .and(sourceCondition)
                .orderBy(field(name("e2", "iri")).asc())
                .offset((int) pageable.getOffset())
                .limit(pageable.getPageSize());

        var countQuery = dsl().selectCount()
                .from(table("ols_entities").as("e1"))
                .join(table("ols_entities").as("e2")).on(
                        condition("{0} = ANY({1})", field(name("e1", "iri")), field(name("e2", ancestorColumn)))
                                .and(field(name("e2", "ontology_id")).eq(field(name("e1", "ontology_id")))))
                .where(field(name("e1", "id"), String.class).eq(id))
                .and(sourceCondition);

        return postgresClient.queryPaginated(dataQuery, countQuery, pageable);
    }


    // --- Edge condition builder ---

    private Condition buildEdgeCondition(String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> nodeProps, boolean outgoing) {

        Field<String> meColumn = outgoing
                ? field(name("e", "start_id"), String.class)
                : field(name("e", "end_id"), String.class);

        Condition condition = meColumn.eq(id)
                .and(field(name("e", "type"), String.class).in(edgeIRIs));

        for (var entry : edgeProps.entrySet()) {
            condition = condition.and(
                    condition("{0} = ANY({1})", val(entry.getValue()), field(name("e", "property"))));
        }

        for (var entry : nodeProps.entrySet()) {
            if ("isObsolete".equals(entry.getKey())) {
                condition = condition.and(
                        field(name("e2", "is_obsolete"), Boolean.class).eq("true".equals(entry.getValue())));
            }
        }

        return condition;
    }

    private Condition buildNodePropCondition(String alias, Map<String, String> nodeProps) {
        Condition condition = trueCondition();
        for (var entry : nodeProps.entrySet()) {
            if ("isObsolete".equals(entry.getKey())) {
                condition = condition.and(
                        field(name(alias, "is_obsolete"), Boolean.class).eq("true".equals(entry.getValue())));
            }
        }
        return condition;
    }


    // --- Recursive CTE for non-hierarchy multi-hop edges ---

    private Page<JsonElement> recursiveEdgeTraversal(String id, List<String> edgeIRIs,
            Map<String, String> edgeProps, Map<String, String> nodeProps, boolean outgoing, Pageable pageable) {

        String targetCol = outgoing ? "end_id" : "start_id";
        String sourceCol = outgoing ? "start_id" : "end_id";

        CommonTableExpression<?> traverse = name("traverse").fields("target_id").as(
                select(field(name(targetCol), String.class).as("target_id"))
                        .from("ols_edges")
                        .where(field(name(sourceCol), String.class).eq(id))
                        .and(field("type", String.class).in(edgeIRIs))
                        .unionAll(
                                select(field(name("e", targetCol), String.class).as("target_id"))
                                        .from(table(name("traverse")).as("t"))
                                        .join(table("ols_edges").as("e"))
                                        .on(field(name("e", sourceCol), String.class).eq(field(name("t", "target_id"), String.class)))
                                        .where(field(name("e", "type"), String.class).in(edgeIRIs))));

        var dataQuery = dsl().withRecursive(traverse)
                .selectDistinct(field(name("e", "_json")), field(name("e", "iri")))
                .from(table("ols_entities").as("e"))
                .where(field(name("e", "id"), String.class).in(
                        selectDistinct(field("target_id", String.class)).from(table(name("traverse")))))
                .orderBy(field(name("e", "iri")).asc())
                .offset((int) pageable.getOffset())
                .limit(pageable.getPageSize());

        var countQuery = dsl().withRecursive(traverse)
                .select(countDistinct(field("target_id")))
                .from(table(name("traverse")));

        return postgresClient.queryPaginated(dataQuery, countQuery, pageable);
    }


    // --- Hierarchy edge type checks ---

    private boolean isDirectParentTraversal(List<String> edgeIRIs) {
        return edgeIRIs.size() == 1 && DIRECT_PARENT_EDGES.contains(edgeIRIs.get(0));
    }

    private boolean isHierarchicalParentTraversal(List<String> edgeIRIs) {
        return edgeIRIs.size() == 1 && HIERARCHICAL_PARENT_EDGES.contains(edgeIRIs.get(0));
    }


    // --- pgvector helpers ---

    private static Field<Double> pgvectorDistance(Field<?> a, Field<?> b) {
        return field("{0} <=> {1}", Double.class, a, b);
    }

    private static Field<?> pgvectorCast(Object value) {
        return field("{0}::vector", Object.class, val(value));
    }

    private static Field<?> embeddingField(String alias, String columnName) {
        return field(name(alias, columnName));
    }


    // --- Vector search methods ---

    public static class SimilarResult {
        public JsonElement entity;
        public double score;
    }

    public Page<JsonElement> getSimilar(String type, String iri, Pageable pageable, String modelName) {
        String embColName = "embeddings_" + modelName;

        CommonTableExpression<?> source = name("source").fields("vec").as(
                select(field(name(embColName)).as("vec"))
                        .from("ols_entities")
                        .where(field("iri", String.class).eq(iri))
                        .and(field("type", String.class).eq(type))
                        .and(field(name(embColName)).isNotNull())
                        .limit(1));

        Field<Double> distance = pgvectorDistance(
                embeddingField("e", embColName),
                field(name("s", "vec")));
        Field<Double> score = inline(1.0).minus(distance).as("score");

        var query = dsl().with(source)
                .select(field(name("e", "_json")), score)
                .from(table("ols_entities").as("e"), table(name("source")).as("s"))
                .where(field(name("e", "type"), String.class).eq(type))
                .and(embeddingField("e", embColName).isNotNull())
                .orderBy(distance)
                .limit(pageable.getPageSize());

        List<JsonElement> results = new ArrayList<>();
        for (Record record : dsl().fetch(query)) {
            var json = JsonParser.parseString(record.get("_json", String.class)).getAsJsonObject();
            json.addProperty("score", record.get("score", Double.class));
            results.add(json);
        }
        return new PageImpl<>(results, pageable, results.size());
    }

    public double getSimilarity(String type, String iri, String iri2, String modelName) {
        String embColName = "embeddings_" + modelName;

        Field<Double> score = inline(1.0).minus(
                pgvectorDistance(embeddingField("a", embColName), embeddingField("b", embColName))).as("score");

        var query = dsl().select(score)
                .from(table("ols_entities").as("a"), table("ols_entities").as("b"))
                .where(field(name("a", "iri"), String.class).eq(iri))
                .and(field(name("a", "type"), String.class).eq(type))
                .and(embeddingField("a", embColName).isNotNull())
                .and(field(name("b", "iri"), String.class).eq(iri2))
                .and(field(name("b", "type"), String.class).eq(type))
                .and(embeddingField("b", embColName).isNotNull())
                .limit(1);

        Record record = dsl().fetchOne(query);
        if (record != null) {
            return record.get("score", Double.class);
        }
        throw new ResourceNotFoundException("entity not found");
    }

    public List<Double> getEmbeddingVector(String type, String iri, String modelName) {
        String embColName = "embeddings_" + modelName;

        var query = dsl().select(field("{0}::text", String.class, field(name(embColName))).as("embeddings"))
                .from("ols_entities")
                .where(field("iri", String.class).eq(iri))
                .and(field("type", String.class).eq(type))
                .and(field(name(embColName)).isNotNull())
                .limit(1);

        Record record = dsl().fetchOne(query);
        if (record != null) {
            String vecStr = record.get("embeddings", String.class).trim();
            if (vecStr.startsWith("[")) vecStr = vecStr.substring(1);
            if (vecStr.endsWith("]")) vecStr = vecStr.substring(0, vecStr.length() - 1);
            return Arrays.stream(vecStr.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
        }
        throw new ResourceNotFoundException("entity not found");
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName) {
        return searchByVector(type, vector, pageable, modelName, true);
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName, boolean includeCurations) {
        String embColName = "embedding_" + modelName;
        int limit = pageable.getPageSize();
        String vecLiteral = "[" + vector.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";

        Field<Double> distance = pgvectorDistance(embeddingField("emb", embColName), pgvectorCast(vecLiteral));

        var labelQuery = select(
                field(name("en", "id")), field(name("en", "_json")),
                distance.as("min_dist"))
                .from(table("ols_embedding_nodes").as("emb"))
                .join(table("ols_entities").as("en")).on(field(name("emb", "entity_id")).eq(field(name("en", "id"))))
                .where(field(name("emb", "type"), String.class).eq("LabelEmbedding"))
                .and(embeddingField("emb", embColName).isNotNull())
                .and(field(name("en", "type"), String.class).eq(type));

        Select<?> combined;
        if (includeCurations) {
            var curationQuery = select(
                    field(name("en", "id")), field(name("en", "_json")),
                    distance.as("min_dist"))
                    .from(table("ols_embedding_nodes").as("emb"))
                    .join(table("ols_entities").as("en")).on(field(name("emb", "entity_id")).eq(field(name("en", "id"))))
                    .where(field(name("emb", "type"), String.class).eq("CurationEmbedding"))
                    .and(embeddingField("emb", embColName).isNotNull())
                    .and(field(name("en", "type"), String.class).eq(type));
            combined = labelQuery.unionAll(curationQuery);
        } else {
            combined = labelQuery;
        }

        Table<?> sub = combined.asTable("sub");
        var query = dsl().select(sub.field("_json"), min(sub.field("min_dist", Double.class)).as("score"))
                .from(sub)
                .groupBy(sub.field("id"), sub.field("_json"))
                .orderBy(min(sub.field("min_dist", Double.class)).asc())
                .limit(limit);

        List<JsonElement> results = new ArrayList<>();
        for (Record record : dsl().fetch(query)) {
            var json = JsonParser.parseString(record.get("_json", String.class)).getAsJsonObject();
            json.addProperty("score", 1.0 - record.get("score", Double.class));
            results.add(json);
        }
        return new PageImpl<>(results, pageable, results.size());
    }

    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable,
            String modelName, String ontologyId, boolean isDefiningOntology) {
        return searchByVectorInOntology(type, vector, pageable, modelName, ontologyId, isDefiningOntology, true);
    }

    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable,
            String modelName, String ontologyId, boolean isDefiningOntology, boolean includeCurations) {
        String embColName = "embedding_" + modelName;
        int limit = pageable.getPageSize();
        String vecLiteral = "[" + vector.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";

        Select<?> combined;

        if (isDefiningOntology) {
            combined = buildDefiningOntologySubquery(embColName, vecLiteral, type, ontologyId, includeCurations);
        } else {
            combined = buildNonDefiningOntologySubquery(embColName, vecLiteral, ontologyId, includeCurations);
        }

        Table<?> sub = combined.asTable("sub");
        var query = dsl().select(sub.field("_json"), min(sub.field("dist", Double.class)).as("score"))
                .from(sub)
                .groupBy(sub.field("id"), sub.field("_json"))
                .orderBy(min(sub.field("dist", Double.class)).asc())
                .limit(limit);

        List<JsonElement> results = new ArrayList<>();
        for (Record record : dsl().fetch(query)) {
            var json = JsonParser.parseString(record.get("_json", String.class)).getAsJsonObject();
            json.addProperty("score", 1.0 - record.get("score", Double.class));
            results.add(json);
        }
        return new PageImpl<>(results, pageable, results.size());
    }

    private Select<?> buildDefiningOntologySubquery(String embColName, String vecLiteral,
            String type, String ontologyId, boolean includeCurations) {

        Field<Double> distance = pgvectorDistance(embeddingField("emb", embColName), pgvectorCast(vecLiteral));

        var labelQuery = select(
                field(name("en", "_json")), field(name("en", "id")),
                distance.as("dist"))
                .from(table("ols_embedding_nodes").as("emb"))
                .join(table("ols_entities").as("en")).on(field(name("emb", "entity_id")).eq(field(name("en", "id"))))
                .where(field(name("emb", "type"), String.class).eq("LabelEmbedding"))
                .and(embeddingField("emb", embColName).isNotNull())
                .and(field(name("en", "type"), String.class).eq(type))
                .and(field(name("en", "ontology_id"), String.class).eq(ontologyId.toLowerCase()));

        if (!includeCurations) {
            return labelQuery;
        }

        var curationQuery = select(
                field(name("en", "_json")), field(name("en", "id")),
                distance.as("dist"))
                .from(table("ols_embedding_nodes").as("emb"))
                .join(table("ols_entities").as("en")).on(field(name("emb", "entity_id")).eq(field(name("en", "id"))))
                .where(field(name("emb", "type"), String.class).eq("CurationEmbedding"))
                .and(embeddingField("emb", embColName).isNotNull())
                .and(field(name("en", "type"), String.class).eq(type))
                .and(field(name("en", "ontology_id"), String.class).eq(ontologyId.toLowerCase()));

        return labelQuery.unionAll(curationQuery);
    }

    private Select<?> buildNonDefiningOntologySubquery(String embColName, String vecLiteral,
            String ontologyId, boolean includeCurations) {

        Field<Double> distance = pgvectorDistance(embeddingField("emb", embColName), pgvectorCast(vecLiteral));

        var labelQuery = select(
                field(name("target", "_json")), field(name("target", "id")),
                distance.as("dist"))
                .from(table("ols_embedding_nodes").as("emb"))
                .join(table("ols_entities").as("defining")).on(field(name("emb", "entity_id")).eq(field(name("defining", "id"))))
                .join(table("ols_entities").as("target")).on(
                        field(name("target", "iri")).eq(field(name("defining", "iri")))
                                .and(field(name("target", "type")).eq(field(name("defining", "type")))))
                .where(field(name("emb", "type"), String.class).eq("LabelEmbedding"))
                .and(embeddingField("emb", embColName).isNotNull())
                .and(field(name("target", "ontology_id"), String.class).eq(ontologyId.toLowerCase()));

        if (!includeCurations) {
            return labelQuery;
        }

        var curationQuery = select(
                field(name("target", "_json")), field(name("target", "id")),
                distance.as("dist"))
                .from(table("ols_embedding_nodes").as("emb"))
                .join(table("ols_entities").as("defining")).on(field(name("emb", "entity_id")).eq(field(name("defining", "id"))))
                .join(table("ols_entities").as("target")).on(
                        field(name("target", "iri")).eq(field(name("defining", "iri")))
                                .and(field(name("target", "type")).eq(field(name("defining", "type")))))
                .where(field(name("emb", "type"), String.class).eq("CurationEmbedding"))
                .and(embeddingField("emb", embColName).isNotNull())
                .and(field(name("target", "ontology_id"), String.class).eq(ontologyId.toLowerCase()));

        return labelQuery.unionAll(curationQuery);
    }

    public List<String> getEmbeddingModels() {
        return getEmbeddingModelsInNeo4j();
    }

    public List<String> getEmbeddingModelsInNeo4j() {
        var query = dsl().select(field("column_name", String.class))
                .from("information_schema.columns")
                .where(field("table_name", String.class).eq("ols_entities"))
                .and(field("column_name", String.class).like("embeddings\\_%"));

        List<String> models = new ArrayList<>();
        for (Record record : dsl().fetch(query)) {
            String colName = record.get("column_name", String.class);
            String modelNameStr = colName.substring("embeddings_".length());
            if (!modelNameStr.contains("pca16")) {
                models.add(modelNameStr);
            }
        }
        return models;
    }
}
