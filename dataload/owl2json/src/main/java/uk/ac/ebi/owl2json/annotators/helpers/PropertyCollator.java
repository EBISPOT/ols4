package uk.ac.ebi.owl2json.annotators.helpers;

import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.PropertyValue;

import java.util.Collection;
import java.util.List;

public class PropertyCollator {

    public static void collateProperties(OwlGraph graph, String destProp, Collection<String> sourceProps, Collection<String> fallbackProps) {

        long startTime3 = System.nanoTime();

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

		boolean annotated = false;

                for(String prop : sourceProps) {
                    List<PropertyValue> values = c.properties.getPropertyValues(prop);
                    if(values != null) {
                        for(PropertyValue value : values) {
                            c.properties.addProperty(destProp, value);
			    annotated = true;
                        }
                    }
                }

		if(!annotated) {
			for(String prop : fallbackProps) {
				List<PropertyValue> values = c.properties.getPropertyValues(prop);
				if (values != null) {
					for (PropertyValue value : values) {
						c.properties.addProperty(destProp, value);
					}
				}
			}
		}
            }
        }

        long endTime3 = System.nanoTime();
        System.out.println("collate properties from " + sourceProps + " and fallback " + fallbackProps + " into " + destProp + ": " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}


