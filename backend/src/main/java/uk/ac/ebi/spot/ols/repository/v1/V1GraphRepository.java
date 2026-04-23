package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.JsonHelper;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import static uk.ac.ebi.ols.shared.DefinedFields.*;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.OLS_ENTITIES;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.arrayContains;
import static uk.ac.ebi.spot.ols.repository.postgres.JooqSupport.field;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class V1GraphRepository {

    Gson gson = new Gson();

    @Autowired
    PostgresClient postgresClient;

    public Map<String, Object> getGraphForClass(String iri, String ontologyId, String lang) {
        return getGraphForEntity(iri, "class", "OntologyClass", ontologyId, lang);
    }

    public Map<String, Object> getGraphForProperty(String iri, String ontologyId, String lang) {
        return getGraphForEntity(iri, "property", "OntologyProperty", ontologyId, lang);
    }

    public Map<String, Object> getGraphForIndividual(String iri, String ontologyId, String lang) {
        return getGraphForEntity(iri, "individual", "OntologyIndividual", ontologyId, lang);
    }

    private Map<String, Object> getGraphForEntity(String iri, String type, String entityType, String ontologyId, String lang) {

        String thisEntityId = ontologyId + "+" + type + "+" + iri;

        List<GraphNode> parentsAndRelatedToNodes = new ArrayList<>();
        List<GraphEdge> parentsAndRelatedToEdges = new ArrayList<>();
        getParentsAndRelatedTo(thisEntityId, parentsAndRelatedToNodes, parentsAndRelatedToEdges);

        List<GraphNode> relatedFromNodes = new ArrayList<>();
        List<GraphEdge> relatedFromEdges = new ArrayList<>();
        getRelatedFrom(thisEntityId, relatedFromNodes, relatedFromEdges);

        // Deduplicate nodes by IRI
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        for (GraphNode n : parentsAndRelatedToNodes) nodeMap.putIfAbsent(n.iri, n);
        for (GraphNode n : relatedFromNodes) nodeMap.putIfAbsent(n.iri, n);

        List<GraphEdge> allEdges = new ArrayList<>();
        allEdges.addAll(parentsAndRelatedToEdges);
        allEdges.addAll(relatedFromEdges);

        Map<String, String> iriToLabel = new HashMap<>();

        List<Map<String, Object>> nodes = nodeMap.values().stream().map(node -> {

            JsonObject ontologyNodeObject = transformJson(node.json, lang);

            JsonObject linkedEntities = ontologyNodeObject.getAsJsonObject("linkedEntities");
            if (linkedEntities != null) {
                for (String referencedIri : linkedEntities.keySet()) {
                    JsonObject reference = linkedEntities.getAsJsonObject(referencedIri);
                    if (!iriToLabel.containsKey(referencedIri))
                        iriToLabel.put(referencedIri, JsonHelper.getString(reference, LABEL.getText()));
                }
            }

            Map<String, Object> nodeRes = new LinkedHashMap<>();
            nodeRes.put("iri", JsonHelper.getString(ontologyNodeObject, "iri"));
            nodeRes.put(LABEL.getText(), JsonHelper.getString(ontologyNodeObject, LABEL.getText()));
            return nodeRes;

        }).collect(Collectors.toList());


        List<Map<String, Object>> edges = allEdges.stream().map(edge -> {

            Map<String, Object> edgeRes = new LinkedHashMap<>();
            edgeRes.put("source", edge.sourceIri);
            edgeRes.put("target", edge.targetIri);

            JsonObject ontologyEdgeObject = transformJson(edge.json, lang);

            String uri = JsonHelper.getString(ontologyEdgeObject, "property");
            if (uri == null) {
                uri = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
            }

            String propertyLabel = iriToLabel.get(uri);
            if (propertyLabel == null)
                propertyLabel = "is a";

            edgeRes.put(LABEL.getText(), propertyLabel);
            edgeRes.put("uri", uri);

            return edgeRes;

        }).collect(Collectors.toList());

        Map<String, Object> resGraph = new LinkedHashMap<>();
        resGraph.put("nodes", nodes);
        resGraph.put("edges", edges);
        return resGraph;
    }

    void getParentsAndRelatedTo(String entityId, List<GraphNode> outNodes, List<GraphEdge> outEdges) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Table<?> e1 = OLS_ENTITIES.as("e1");
            Table<?> e2 = OLS_ENTITIES.as("e2");
            Field<byte[]> nodeJson = field("node_json", byte[].class);
            Field<String> nodeIri = field("node_iri", String.class);
            Field<String> relType = field("rel_type", String.class);
            Field<String> sourceIri = field("source_iri", String.class);

            var records = dsl.select(
                            field("e2", "_json", byte[].class).as("node_json"),
                            field("e2", "iri", String.class).as("node_iri"),
                            DSL.inline("parent").as("rel_type"),
                            field("e1", "iri", String.class).as("source_iri"))
                    .from(e1)
                    .join(e2).on(arrayContains(field("e1", "direct_ancestors", String[].class), field("e2", "iri", String.class))
                            .and(field("e2", "ontology_id", String.class).eq(field("e1", "ontology_id", String.class))))
                    .where(field("e1", "id", String.class).eq(entityId))
                    .unionAll(
                            dsl.select(
                                            field("e2", "_json", byte[].class).as("node_json"),
                                            field("e2", "iri", String.class).as("node_iri"),
                                            DSL.inline("relatedTo").as("rel_type"),
                                            field("e1", "iri", String.class).as("source_iri"))
                                    .from(e1)
                                    .join(e2).on(arrayContains(field("e1", "related_to", String[].class), field("e2", "iri", String.class))
                                            .and(field("e2", "ontology_id", String.class).eq(field("e1", "ontology_id", String.class))))
                                    .where(field("e1", "id", String.class).eq(entityId)))
                    .limit(200)
                    .fetch();

            for (Record record : records) {
                String currentNodeIri = record.get(nodeIri);
                String currentSourceIri = record.get(sourceIri);
                String currentRelType = record.get(relType);

                outNodes.add(new GraphNode(currentNodeIri, PostgresClient.decompressJson(record.get(nodeJson))));

                if ("parent".equals(currentRelType)) {
                    outEdges.add(new GraphEdge(currentSourceIri, currentNodeIri,
                            "{\"property\":\"http://www.w3.org/2000/01/rdf-schema#subClassOf\"}"));
                } else {
                    outEdges.add(new GraphEdge(currentSourceIri, currentNodeIri, "{}"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("getParentsAndRelatedTo failed", e);
        }
    }

    void getRelatedFrom(String entityId, List<GraphNode> outNodes, List<GraphEdge> outEdges) {
        try (Connection conn = postgresClient.getConnection()) {
            DSLContext dsl = postgresClient.dsl(conn);
            Table<?> e1 = OLS_ENTITIES.as("e1");
            Table<?> e2 = OLS_ENTITIES.as("e2");
            Field<byte[]> nodeJson = field("node_json", byte[].class);
            Field<String> nodeIri = field("node_iri", String.class);
            Field<String> targetIri = field("target_iri", String.class);

            var records = dsl.select(
                            field("e2", "_json", byte[].class).as("node_json"),
                            field("e2", "iri", String.class).as("node_iri"),
                            field("e1", "iri", String.class).as("target_iri"))
                    .from(e1)
                    .join(e2).on(arrayContains(field("e2", "related_to", String[].class), field("e1", "iri", String.class))
                            .and(field("e2", "ontology_id", String.class).eq(field("e1", "ontology_id", String.class))))
                    .where(field("e1", "id", String.class).eq(entityId))
                    .limit(200)
                    .fetch();

            for (Record record : records) {
                String currentNodeIri = record.get(nodeIri);
                String currentTargetIri = record.get(targetIri);

                outNodes.add(new GraphNode(currentNodeIri, PostgresClient.decompressJson(record.get(nodeJson))));
                outEdges.add(new GraphEdge(currentNodeIri, currentTargetIri, "{}"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getRelatedFrom failed", e);
        }
    }

    JsonObject transformJson(String json, String lang) {
        JsonElement element = JsonParser.parseString(json);
        return RemoveLiteralDatatypesTransform.transform(
                LocalizationTransform.transform(element, lang)
        ).getAsJsonObject();
    }

    private static class GraphNode {
        final String iri;
        final String json;
        GraphNode(String iri, String json) { this.iri = iri; this.json = json; }
    }

    private static class GraphEdge {
        final String sourceIri;
        final String targetIri;
        final String json;
        GraphEdge(String sourceIri, String targetIri, String json) {
            this.sourceIri = sourceIri;
            this.targetIri = targetIri;
            this.json = json;
        }
    }

}
