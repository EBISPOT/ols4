package uk.ac.ebi.spot.ols.repository.v1;

import com.google.gson.*;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.model.v1.V1Term;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1IndividualMapper;
import uk.ac.ebi.spot.ols.repository.v1.mappers.V1TermMapper;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import java.util.*;
import java.util.stream.Collectors;

import static org.neo4j.driver.Values.parameters;

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

        Map<String,String> iriToLabel = new HashMap<>();

        List<Map<String,Object>> nodes = allNodes.stream().map(node -> {

            JsonObject ontologyNodeObject = getOntologyNodeJson(node, lang);

            JsonObject linkedEntities = ontologyNodeObject.getAsJsonObject("linkedEntities");
            if(linkedEntities != null) {
                for(String referencedIri : linkedEntities.keySet()) {
                    JsonObject reference = linkedEntities.getAsJsonObject(referencedIri);
                    if(!iriToLabel.containsKey(referencedIri))
                        iriToLabel.put(referencedIri, JsonHelper.getString(reference, "label"));
                }
            }

            Map<String, Object> nodeRes = new LinkedHashMap<>();
            nodeRes.put("iri", JsonHelper.getString(ontologyNodeObject, "iri"));
            nodeRes.put("label", JsonHelper.getString(ontologyNodeObject, "label"));
            return nodeRes;

        }).collect(Collectors.toList());


        List<Map<String,Object>> edges = allEdges.stream().map(result -> {

            Relationship relationship = (Relationship) result.get("relationship");

            Map<String, Object> edgeRes = new LinkedHashMap<>();
            edgeRes.put("source", result.get("source"));
            edgeRes.put("target", result.get("target"));

            JsonObject ontologyEdgeObject = getOntologyEdgeJson(relationship, lang);

            String uri = JsonHelper.getString(ontologyEdgeObject, "property");
            if (uri == null) {
                uri = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
            }

            String propertyLabel = iriToLabel.get(uri);
            if(propertyLabel == null)
                propertyLabel = "is a";

            edgeRes.put("label", propertyLabel);
            edgeRes.put("uri", uri);

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

    public Page<V1Term> getRelatedFromPaginated(String entityId, String lang, Pageable pageable) {
        String query = "MATCH (x:OntologyClass)-[r:relatedTo]->(n:OntologyClass) WHERE n.id= $id RETURN x";
        String countQuery = "MATCH (x:OntologyClass)-[r:relatedTo]->(n:OntologyClass) WHERE n.id= $id RETURN count(x)";

        return neo4jClient.queryPaginated(query, "x", countQuery, parameters("id", entityId), pageable).map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getSuperClassPaginated(String entityId, String lang, Pageable pageable) {
        String query =
                "MATCH (a:OntologyClass)-[r:`http://www.w3.org/2000/01/rdf-schema#subClassOf`]->(b:OntologyClass) " +
                        "WHERE a.id = $id RETURN b";
        String countQuery =
                "MATCH (a:OntologyClass)-[r:`http://www.w3.org/2000/01/rdf-schema#subClassOf`]->(b:OntologyClass) " +
                        "WHERE a.id = $id RETURN count(b)";

        return neo4jClient.queryPaginated(query, "b", countQuery, parameters("id", entityId), pageable).map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Term> getEquivalentClassPaginated(String entityId, String lang, Pageable pageable) {
        String query =
                "MATCH (a:OntologyClass)-[r:`http://www.w3.org/2002/07/owl#equivalentClass`]-(b:OntologyClass) " +
                        "WHERE a.id = $id RETURN DISTINCT b";
        String countQuery =
                "MATCH (a:OntologyClass)-[r:`http://www.w3.org/2002/07/owl#equivalentClass`]-(b:OntologyClass) " +
                        "WHERE a.id = $id RETURN count(DISTINCT b)";

        return neo4jClient.queryPaginated(query, "b", countQuery, parameters("id", entityId), pageable).map(record -> V1TermMapper.mapTerm(record, lang));
    }

    public Page<V1Individual> getTermInstancesPaginated(String entityId, String lang, Pageable pageable) {
        String query =
                "MATCH (a:OntologyClass)<-[r:`http://www.w3.org/1999/02/22-rdf-syntax-ns#type`]-(b:OntologyIndividual) " +
                        "WHERE a.id = $id RETURN b";
        String countQuery =
                "MATCH (a:OntologyClass)<-[r:`http://www.w3.org/1999/02/22-rdf-syntax-ns#type`]-(b:OntologyIndividual) " +
                        "WHERE a.id = $id RETURN count(b)";

        return neo4jClient.queryPaginated(query, "b", countQuery, parameters("id", entityId), pageable).map(record -> V1IndividualMapper.mapIndividual(record, lang));
    }

    public String getTermJson(String entityId) {
        String query = "MATCH (a:OntologyClass) WHERE a.id = '"+entityId+"' RETURN a._json AS result";
        List<Map<String,Object>> results = neo4jClient.rawQuery(query);
        return results.get(0).get("result").toString();
    }

    JsonObject getOntologyNodeJson(Node node, String lang) {
        JsonElement ontologyNodeObject = new JsonObject();
        if(node.asMap().get("_json") != null && node.asMap().get("_json") instanceof String)
            ontologyNodeObject = JsonParser.parseString((String) node.asMap().get("_json"));

        return RemoveLiteralDatatypesTransform.transform(
                LocalizationTransform.transform(ontologyNodeObject, lang)
        ).getAsJsonObject();
    }

    JsonObject getOntologyEdgeJson(Relationship r, String lang) {
        JsonElement ontologyEdgeObject = new JsonObject();
        if(r.asMap().get("_json") != null && r.asMap().get("_json") instanceof String)
            ontologyEdgeObject = JsonParser.parseString((String) r.asMap().get("_json"));

        return RemoveLiteralDatatypesTransform.transform(
                LocalizationTransform.transform(ontologyEdgeObject, lang)
        ).getAsJsonObject();
    }

}

