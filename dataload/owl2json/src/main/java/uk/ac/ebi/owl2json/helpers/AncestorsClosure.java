package uk.ac.ebi.owl2json.helpers;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AncestorsClosure {

    public static Set<String> getAncestors(OwlNode node, String hierarchyPredicate, OwlGraph graph) {
        return getAncestors(node, hierarchyPredicate, graph, new LinkedHashSet<>());
    }

    private static Set<String> getAncestors(OwlNode node, String hierarchyPredicate, OwlGraph graph, Set<String> ancestors) {

        List<PropertyValue> parents = node.properties.getPropertyValues(hierarchyPredicate);
        if(parents != null) {
            for(PropertyValue parent : parents) {
                if(parent.getType() == PropertyValue.Type.URI) {
                    String uri = ((PropertyValueURI) parent).getUri();
                    if(!ancestors.contains(uri)) {
                        ancestors.add( uri );
                        OwlNode parentNode = graph.getNodeForPropertyValue(parent);
                        if(parentNode != null) {
                            getAncestors(parentNode, hierarchyPredicate, graph, ancestors);
                        }
                    }
                }
            }
        }
        return ancestors;
    }
}
