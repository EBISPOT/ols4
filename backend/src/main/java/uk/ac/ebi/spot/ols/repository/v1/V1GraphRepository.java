package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jooq.Record;
import static org.jooq.impl.DSL.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.JsonHelper;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

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

    private Map<String, Object> getGraphForEntity(String iri, String type, String neo4jType, String ontologyId, String lang) {

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
        var query = postgresClient.dsl()
                .select(
                        field(name("e2", "_json")).as("node_json"),
                        field(name("e2", "iri")).as("node_iri"),
                        field(name("e", "_json")).as("edge_json"),
                        field(name("s", "iri")).as("source_iri"),
                        field(name("t", "iri")).as("target_iri"))
                .from(table("ols_edges").as("e"))
                .join(table("ols_entities").as("e2")).on(
                        field(name("e2", "id")).eq(field(name("e", "end_id")))
                                .or(field(name("e2", "id")).eq(field(name("e", "start_id"))))
                                .and(field(name("e2", "id"), String.class).ne(entityId)))
                .join(table("ols_entities").as("s")).on(field(name("s", "id")).eq(field(name("e", "start_id"))))
                .join(table("ols_entities").as("t")).on(field(name("t", "id")).eq(field(name("e", "end_id"))))
                .where(field(name("e", "start_id"), String.class).eq(entityId)
                        .or(field(name("e", "end_id"), String.class).eq(entityId)))
                .and(field(name("e", "type"), String.class).in("relatedTo", "directParent"))
                .limit(200);

        for (Record record : postgresClient.dsl().fetch(query)) {
            outNodes.add(new GraphNode(record.get("node_iri", String.class), record.get("node_json", String.class)));
            outEdges.add(new GraphEdge(
                    record.get("source_iri", String.class),
                    record.get("target_iri", String.class),
                    record.get("edge_json", String.class)));
        }
    }

    void getRelatedFrom(String entityId, List<GraphNode> outNodes, List<GraphEdge> outEdges) {
        var query = postgresClient.dsl()
                .select(
                        field(name("e2", "_json")).as("node_json"),
                        field(name("e2", "iri")).as("node_iri"),
                        field(name("e", "_json")).as("edge_json"),
                        field(name("e2", "iri")).as("source_iri"),
                        field(name("t", "iri")).as("target_iri"))
                .from(table("ols_edges").as("e"))
                .join(table("ols_entities").as("e2")).on(field(name("e2", "id")).eq(field(name("e", "start_id"))))
                .join(table("ols_entities").as("t")).on(field(name("t", "id")).eq(field(name("e", "end_id"))))
                .where(field(name("e", "end_id"), String.class).eq(entityId))
                .and(field(name("e", "type"), String.class).eq("relatedTo"))
                .limit(200);

        for (Record record : postgresClient.dsl().fetch(query)) {
            outNodes.add(new GraphNode(record.get("node_iri", String.class), record.get("node_json", String.class)));
            outEdges.add(new GraphEdge(
                    record.get("source_iri", String.class),
                    record.get("target_iri", String.class),
                    record.get("edge_json", String.class)));
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

