package uk.ac.ebi.rdf2json.annotators.helpers;

import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.properties.PropertyValue;

import java.util.Collection;
import java.util.List;

public class PropertyCollator {

    public static void collateProperties(OntologyGraph graph, String destProp, Collection<String> sourceProps, Collection<String> fallbackProps) {

        long startTime3 = System.nanoTime();

        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);

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

        long endTime3 = System.nanoTime();
        System.out.println("collate properties from " + sourceProps + " and fallback " + fallbackProps + " into " + destProp + ": " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}


