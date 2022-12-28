package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class HierarchyFlagsAnnotator {

    public static void annotateHierarchyFlags(OwlGraph graph) {

        long startTime3 = System.nanoTime();

        // Set of IRIs that have children
        Set<String> hasChildren = new HashSet<>();
        Set<String> hasHierarchicalChildren = new HashSet<>();

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;


		// 1. Direct parents (subClassOf)
		//

                List<PropertyValue> parents = c.properties.getPropertyValues("directParent");

                boolean hasDirectParent = false;

                if(parents != null) {
                    for (PropertyValue parent : parents) {

                        String iri = ((PropertyValueURI) parent).getUri();

                        if (iri.equals("http://www.w3.org/2002/07/owl#Thing") ||
                                iri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                                    continue;
                        }

                        hasDirectParent = true;
                        hasChildren.add(iri);
                    }
                }

                c.properties.addProperty("hasDirectParent", PropertyValueLiteral.fromString(hasDirectParent ? "true" : "false"));

		// 2. Hierarchical parents
		//

                List<PropertyValue> hierarchicalParents = c.properties.getPropertyValues("hierarchicalParent");

                boolean hasHierarchicalParent = false;

                if(hierarchicalParents != null) {
                    for (PropertyValue parent : hierarchicalParents) {

                        String iri = ((PropertyValueURI) parent).getUri();

                        if (iri.equals("http://www.w3.org/2002/07/owl#Thing") ||
                                iri.equals("http://www.w3.org/2002/07/owl#TopObjectProperty")) {
                                    continue;
                        }

                        hasHierarchicalParent = true;
                        hasHierarchicalChildren.add(iri);
                    }
                }

                c.properties.addProperty("hasHierarchicalParent", PropertyValueLiteral.fromString(hasHierarchicalParent ? "true" : "false"));
            }
        }

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                if(hasChildren.contains(c.uri)) {
                    c.properties.addProperty("hasDirectChildren", PropertyValueLiteral.fromString("true"));
                } else {
                    c.properties.addProperty("hasDirectChildren", PropertyValueLiteral.fromString("false"));
                }

                if(hasHierarchicalChildren.contains(c.uri)) {
                    c.properties.addProperty("hasHierarchicalChildren", PropertyValueLiteral.fromString("true"));
                } else {
                    c.properties.addProperty("hasHierarchicalChildren", PropertyValueLiteral.fromString("false"));
                }
            }
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchy flags: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}


