package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AncestorsAnnotator {

    public static void annotateAncestors(OwlGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OwlNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            if (c.types.contains(OwlNode.NodeType.CLASS)) {
                for(String iri : getAncestors(c, "hierarchicalParent", graph, new LinkedHashSet<String>())) {
                    c.properties.addProperty("hierarchicalAncestor", PropertyValueURI.fromUri(iri));
                }
            }

            for (String iri : getAncestors(c, "directParent", graph, new LinkedHashSet<String>())) {
                c.properties.addProperty("directAncestor", PropertyValueURI.fromUri(iri));
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate ancestors: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));

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
