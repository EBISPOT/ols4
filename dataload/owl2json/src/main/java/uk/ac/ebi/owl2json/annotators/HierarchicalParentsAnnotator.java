package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;
import uk.ac.ebi.owl2json.properties.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class HierarchicalParentsAnnotator {

    public static Set<String> getHierarchicalProperties(OwlGraph graph) {

        Set<String> hierarchicalProperties = new TreeSet<>(
                List.of(
                        "http://www.w3.org/2000/01/rdf-schema#subClassOf"
                )
        );

        Object configHierarchicalProperties = graph.config.get("hierarchical_property");

        if(configHierarchicalProperties instanceof Collection<?>) {
            hierarchicalProperties.addAll((Collection<String>) configHierarchicalProperties);
        }

        return hierarchicalProperties;
    }

    public static void annotateHierarchicalParents(OwlGraph graph) {

	Set<String> hierarchicalProperties = getHierarchicalProperties(graph);

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);
            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

                if(parents != null) {
                    for(PropertyValue parent : parents) {

			if(parent.getType() == PropertyValue.Type.URI) {

				// Direct parent; these are also considered hierarchical parents

				c.properties.addProperty("hierarchicalParent", parent);

			} else if(parent.getType() == PropertyValue.Type.BNODE) {

				OwlNode parentNode = graph.nodes.get( ((PropertyValueBNode) parent).getId() );

				visitBNodeParent(graph, c, parentNode, hierarchicalProperties);
                        }
                    }
                }
            }
        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate hierarchical parents: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

    private static void visitBNodeParent(OwlGraph graph, OwlNode node, OwlNode parent, Set<String> hierarchicalProperties) {

	if(parent.types.contains(OwlNode.NodeType.RESTRICTION)) {

		PropertyValue onProperty = parent.properties.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");
		if(onProperty != null) {

			String predicate = ((PropertyValueURI) onProperty).getUri();

			if(!hierarchicalProperties.contains(predicate)) {
				// Not one of the specified hierarchical properties
				return;
			}
		}

		PropertyValue hasValue = parent.properties.getPropertyValue("http://www.w3.org/2002/07/owl#hasValue");
		if(hasValue != null) {
			node.properties.addProperty("hierarchicalParent", hasValue);
			return;
		}

		PropertyValue someValuesFrom = parent.properties.getPropertyValue("http://www.w3.org/2002/07/owl#someValuesFrom");
		if(someValuesFrom != null) {
			node.properties.addProperty("hierarchicalParent", someValuesFrom);
			return;
		}

		PropertyValue allValuesFrom = parent.properties.getPropertyValue("http://www.w3.org/2002/07/owl#allValuesFrom");
		if(allValuesFrom != null) {
			node.properties.addProperty("hierarchicalParent", allValuesFrom);
			return;
		}

	}  


    }


}
