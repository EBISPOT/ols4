package uk.ac.ebi.spot.ols.repository.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class V1GraphRepository {


    // Note this is the raw Neo4jClient, NOT the OlsNeo4jClient abstraction we use everywhere else.
    // This is because the OLS3 graph API has a very specific Neo4j query we need to run, and it's
    // not worth abstracting over (at least until OLS4 needs something similar.)
    //
    @Autowired
    Neo4jClient neo4jClient;

    public Map<String,Object> getGraphForClass(String iri, String ontologyId, String lang) {
        return getGraphForEntity(iri, "class", "OntologyClass", ontologyId, lang);
    }

    public Map<String,Object> getGraphForProperty(String iri, String ontologyId, String lang) {
        return getGraphForEntity(iri, "property", "OntologyProperty", ontologyId, lang);
    }

    public Map<String,Object> getGraphForIndividual(String iri, String ontologyId, String lang) {
        return getGraphForEntity(iri, "individual", "OntologyIndividual", ontologyId, lang);
    }

    private Map<String,Object> getGraphForEntity(String iri, String type, String neo4jType, String ontologyId, String lang) {

        String thisEntityId = ontologyId + "+" + type + "+" + iri;

        // adapted from OLS3 code, needs revising for the OLS4 API (particularly the 200 limits should be replaced by a distance)
        //
        String query =
                "MATCH path = (n:OntologyClass)-[r:directParent]-(parent)\n" // TODO Related relation
                + "WHERE n.id=\"" + thisEntityId + "\"\n"
                + "UNWIND nodes(path) as p\n"
                + "UNWIND relationships(path) as r1\n"
                + "RETURN {nodes: collect( distinct {iri: p.iri, label: head(p.label)})[0..200],\n"
                        // TODO put labels on edges in OLS4 database
                + "edges: collect (distinct {source: startNode(r1).iri, target: endNode(r1).iri, label: type(r1), uri: type(r1)}  )[0..200]} as result\n";

        List<Map<String,Object>> res = neo4jClient.rawQuery(query, "result");

        if(res.size() != 1) {
            throw new RuntimeException("expected exactly 1 result");
        }

        Map<String,Object> firstResult = res.get(0);

        Map<String,Object> theActualResult = (Map<String,Object>) firstResult.get("result");

        if(theActualResult == null) {
            throw new RuntimeException("no result object");
        }

        return theActualResult;
    }
}

