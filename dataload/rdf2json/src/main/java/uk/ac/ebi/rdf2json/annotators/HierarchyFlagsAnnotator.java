package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HierarchyFlagsAnnotator {

    public static void annotateHierarchyFlags(OntologyGraph graph) {

        long startTime3 = System.nanoTime();

        // Set of IRIs that have children
        Set<String> hasChildren = new HashSet<>();
        Set<String> hasHierarchicalChildren = new HashSet<>();

        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);

            if (c.types.contains(OntologyNode.NodeType.CLASS) ||
                    c.types.contains(OntologyNode.NodeType.PROPERTY) ||
                    c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

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
            OntologyNode c = graph.nodes.get(id);

            if (c.types.contains(OntologyNode.NodeType.CLASS) ||
                    c.types.contains(OntologyNode.NodeType.PROPERTY) ||
                    c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

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


