package uk.ac.ebi.spot.ols.repository.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.service.GenericLocalizer;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class V1JsTreeRepository {

    @Autowired
    OlsNeo4jClient neo4jClient;

    public List<Map<String,Object>> getJsTreeForClass(String iri, String ontologyId, String lang) {
        return getJsTreeForEntity(iri, "class", "OntologyClass", ontologyId, lang);
    }

    public List<Map<String,Object>> getJsTreeForProperty(String iri, String ontologyId, String lang) {
        return getJsTreeForEntity(iri, "property", "OntologyProperty", ontologyId, lang);
    }

    public List<Map<String,Object>> getJsTreeForIndividual(String iri, String ontologyId, String lang) {
        return getJsTreeForEntity(iri, "individual", "OntologyIndividual", ontologyId, lang);
    }

    private List<Map<String,Object>> getJsTreeForEntity(String iri, String type, String neo4jType, String ontologyId, String lang) {

        List<String> parentRelationIRIs = List.of("directParent");

        String thisEntityId = ontologyId + "+" + type + "+" + iri;

        Map<String,Object> thisEntity = neo4jClient.getOne(neo4jType, Map.of("id", thisEntityId));
        thisEntity = GenericLocalizer.localize(thisEntity, lang);

        List<Map<String,Object>> ancestors =
                neo4jClient.getAncestors(neo4jType, thisEntityId, parentRelationIRIs, null)
                        .getContent();
        ancestors = ancestors.stream().map(ancestor -> GenericLocalizer.localize(ancestor, lang)).collect(Collectors.toList());

        return (new V1AncestorsJsTreeBuilder(thisEntity, ancestors, parentRelationIRIs)).buildJsTree();
    }

    public List<Map<String,Object>> getJsTreeChildrenForClass(String jstreeId, String ontologyId, String lang) {
        return getJsTreeChildrenForEntity(jstreeId, "class", "OntologyClass", ontologyId, lang);
    }

    public List<Map<String,Object>> getJsTreeChildrenForProperty(String jstreeId, String ontologyId, String lang) {
        return getJsTreeChildrenForEntity(jstreeId, "property", "OntologyProperty", ontologyId, lang);
    }

    public List<Map<String,Object>> getJsTreeChildrenForIndividual(String jstreeId, String ontologyId, String lang) {
        return getJsTreeChildrenForEntity(jstreeId, "individual", "OntologyIndividual", ontologyId, lang);
    }

    private List<Map<String,Object>> getJsTreeChildrenForEntity(String jstreeId, String type, String neo4jType, String ontologyId, String lang) {

        String[] tokens = jstreeId.split(";");
        String iri = tokens[tokens.length - 1];

        List<String> parentRelationIRIs = List.of("directParent");

        String thisEntityId = ontologyId + "+" + type + "+" + iri;

        Map<String,Object> thisEntity = neo4jClient.getOne(neo4jType, Map.of("id", thisEntityId));
        thisEntity = GenericLocalizer.localize(thisEntity, lang);

        List<Map<String,Object>> children =
                neo4jClient.getChildren(neo4jType, thisEntityId, parentRelationIRIs, null)
                        .getContent();
        children = children.stream().map(child -> GenericLocalizer.localize(child, lang)).collect(Collectors.toList());

        return (new V1ChildrenJsTreeBuilder(jstreeId, thisEntity, children)).buildJsTree();
    }
}

