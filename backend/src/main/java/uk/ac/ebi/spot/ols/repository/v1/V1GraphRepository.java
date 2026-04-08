package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.JsonHelper;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.service.PostgresClient;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.sql.*;
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
        // Get edges where this entity is either start or end, of type relatedTo or directParent
        String sql = "SELECT e2._json AS node_json, e2.iri AS node_iri, "
                + "e.start_id, e.end_id, e._json AS edge_json, "
                + "s.iri AS source_iri, t.iri AS target_iri "
                + "FROM ols_edges e "
                + "JOIN ols_entities e2 ON (e2.id = e.end_id OR e2.id = e.start_id) AND e2.id != ? "
                + "JOIN ols_entities s ON s.id = e.start_id "
                + "JOIN ols_entities t ON t.id = e.end_id "
                + "WHERE (e.start_id = ? OR e.end_id = ?) "
                + "AND e.type IN ('relatedTo', 'directParent') "
                + "LIMIT 200";

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entityId);
            stmt.setString(2, entityId);
            stmt.setString(3, entityId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    outNodes.add(new GraphNode(rs.getString("node_iri"), rs.getString("node_json")));
                    outEdges.add(new GraphEdge(
                            rs.getString("source_iri"),
                            rs.getString("target_iri"),
                            rs.getString("edge_json")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("getParentsAndRelatedTo failed", e);
        }
    }

    void getRelatedFrom(String entityId, List<GraphNode> outNodes, List<GraphEdge> outEdges) {
        // Get incoming relatedTo edges (x)-[relatedTo]->(this)
        String sql = "SELECT e2._json AS node_json, e2.iri AS node_iri, "
                + "e._json AS edge_json, "
                + "e2.iri AS source_iri, t.iri AS target_iri "
                + "FROM ols_edges e "
                + "JOIN ols_entities e2 ON e2.id = e.start_id "
                + "JOIN ols_entities t ON t.id = e.end_id "
                + "WHERE e.end_id = ? AND e.type = 'relatedTo' "
                + "LIMIT 200";

        try (Connection conn = postgresClient.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entityId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    outNodes.add(new GraphNode(rs.getString("node_iri"), rs.getString("node_json")));
                    outEdges.add(new GraphEdge(
                            rs.getString("source_iri"),
                            rs.getString("target_iri"),
                            rs.getString("edge_json")
                    ));
                }
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

