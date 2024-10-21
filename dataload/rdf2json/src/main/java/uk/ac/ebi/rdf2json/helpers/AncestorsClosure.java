package uk.ac.ebi.rdf2json.helpers;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueList;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AncestorsClosure {

    public static Set<String> getAncestors(OntologyNode node, String hierarchyPredicate, OntologyGraph graph) {
        return getAncestors(node, hierarchyPredicate, graph, new LinkedHashSet<>());
    }

    private static Set<String> getAncestors(OntologyNode node, String hierarchyPredicate, OntologyGraph graph, Set<String> ancestors) {

        List<PropertyValue> parentsList = node.properties.getPropertyValues(hierarchyPredicate);
        if(parentsList != null) {
            for(PropertyValue parentListElement : parentsList) {
                if (parentListElement.getType() == PropertyValue.Type.LIST) {
                    List<PropertyValue> parents = ((PropertyValueList)parentListElement).getPropertyValues();
                    for (PropertyValue parent: parents) {
                        if (parent.getType() == PropertyValue.Type.URI) {
                            String uri = ((PropertyValueURI) parent).getUri();
                            if (!ancestors.contains(uri)) {
                                ancestors.add(uri);
                                OntologyNode parentNode = graph.getNodeForPropertyValue(parent);
                                if (parentNode != null) {
                                    getAncestors(parentNode, hierarchyPredicate, graph, ancestors);
                                }
                            }
                        }
                    }
                }
            }
        }
        return ancestors;
    }
}
