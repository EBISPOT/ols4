package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.Gson;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import javax.management.relation.Relation;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class V1GraphRepository {

    Gson gson = new Gson();


    // Note this is the raw Neo4jClient, NOT the OlsNeo4jClient abstraction we use everywhere else.
    // This is because the OLS3 graph API has a very specific Neo4j query we need to run, and it's
    // not worth abstracting over (at least until OLS4 needs something similar.)
    //
    @Autowired
    Neo4jClient neo4jClient;

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

//        String parentsQuery =
//                "MATCH path = (n:OntologyClass)-[r:directParent|relatedTo]-(parent)\n"
//                        + "WHERE n.id=\"" + thisEntityId + "\"\n"
//                        + "UNWIND nodes(path) as p\n"
//                        + "UNWIND relationships(path) as r1\n"
//                        + "RETURN {nodes: collect( distinct {iri: p.iri, label: head(p.label)})[0..200],\n"
//                        + "edges: collect (distinct {source: startNode(r1).iri, target: endNode(r1).iri, relationship: r1 }  )[0..200]} as result\n";

        Map<String,Object> parentsAndRelatedTo = getParentsAndRelatedTo(thisEntityId);
        Map<String,Object> relatedFrom = getRelatedFrom(thisEntityId);

        Set<Node> allNodes = new LinkedHashSet<>();
//        allNodes.add( (Node) parentsAndRelatedTo.get("startNode") );
        allNodes.addAll( (List<Node>) parentsAndRelatedTo.get("nodes") );
//        allNodes.add( (Node) relatedFrom.get("startNode") );
        allNodes.addAll( (List<Node>) relatedFrom.get("nodes") );

        List<Map<String,Object>> allEdges = new ArrayList<>();
        allEdges.addAll( (List<Map<String,Object>>) parentsAndRelatedTo.get("edges") );
        allEdges.addAll( (List<Map<String,Object>>) relatedFrom.get("edges") );

        List<Map<String,Object>> nodes = allNodes.stream().map(node -> {

            Map<String, Object> ontologyNodeObject = getOntologyNodeJson(node, lang);

            Map<String, Object> nodeRes = new LinkedHashMap<>();
            nodeRes.put("iri", ontologyNodeObject.get("iri"));
            nodeRes.put("label", ontologyNodeObject.get("label"));
            return nodeRes;

        }).collect(Collectors.toList());

        List<Map<String,Object>> edges = allEdges.stream().map(result -> {

            Relationship relationship = (Relationship) result.get("relationship");

            Map<String, Object> edgeRes = new LinkedHashMap<>();
            edgeRes.put("source", result.get("source"));
            edgeRes.put("target", result.get("target"));

            Map<String, Object> ontologyEdgeObject = getOntologyEdgeJson(relationship, lang);

            String uri = (String) ontologyEdgeObject.get("property");
            if (uri == null) {
                uri = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
            }
            edgeRes.put("uri", uri);

            String propertyLabel = "is a";

            Map<String, Object> iriToLabels = (Map<String, Object>) ontologyEdgeObject.get("iriToLabels");
            if (iriToLabels != null) {
		String label;
		if(iriToLabels instanceof Collection) {
			label = ((Collection<String>) iriToLabels).iterator().next();
		} else {
                	label = (String) iriToLabels.get(uri);
		}
                if (label != null) {
                    propertyLabel = label;
                }
            }

            edgeRes.put("label", propertyLabel);

            return edgeRes;

        }).collect(Collectors.toList());

        Map<String, Object> resGraph = new LinkedHashMap<>();
        resGraph.put("nodes", nodes);
        resGraph.put("edges", edges);
        return resGraph;
    }

    Map<String,Object> getParentsAndRelatedTo(String entityId) {

        String query =
                "MATCH path = (n:OntologyClass)-[r:relatedTo|directParent]-(x)\n"
                        + "WHERE n.id=\"" + entityId + "\"\n"
                        + "UNWIND nodes(path) as p\n"
                        + "UNWIND relationships(path) as r1\n"
                        + "RETURN { nodes: collect(distinct p),\n"
                        + "edges: collect(distinct { source: startNode(r1).iri, target: endNode(r1).iri, relationship: r1 })\n"
                        + "} AS result";

        List<Map<String,Object>> results = neo4jClient.rawQuery(query);
        return (Map<String,Object>) results.get(0).get("result");
    }

    Map<String,Object> getRelatedFrom(String entityId) {

        String query =
                "MATCH path = (x)-[r:relatedTo]->(n:OntologyClass)\n"
                        + "WHERE n.id=\"" + entityId + "\"\n"
                        + "RETURN { nodes: collect(distinct x),\n"
                        + "edges: collect({ source: startNode(r).iri, target: endNode(r).iri, relationship: r })\n"
                        + "} AS result";

        List<Map<String,Object>> results = neo4jClient.rawQuery(query);
        return (Map<String,Object>) results.get(0).get("result");
    }


    Map<String, Object> getOntologyNodeJson(Node node, String lang) {

        Map<String, Object> ontologyNodeObject = (Map<String, Object>)
                gson.fromJson((String) node.asMap().get("_json"), Object.class);

        return GenericLocalizer.localize(ontologyNodeObject, lang);
    }

    Map<String, Object> getOntologyEdgeJson(Relationship r, String lang) {

        Map<String, Object> ontologyEdgeObject = (Map<String, Object>)
                gson.fromJson((String) r.asMap().get("_json"), Object.class);

        return GenericLocalizer.localize(ontologyEdgeObject, lang);
    }

}

