package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.List;

public class DirectParentsAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(DirectParentsAnnotator.class);

    public static void annotateDirectParents(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OntologyNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            if (c.types.contains(OntologyNode.NodeType.CLASS)) {

                List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

                if(parents != null) {
                    for(PropertyValue parent : parents) {
                        if(parent.getType() == PropertyValue.Type.URI && graph.nodes.containsKey(((PropertyValueURI) parent).getUri())) {
                            c.properties.addProperty("directParent", parent);
                        }
                    }
                }

	    } else if( c.types.contains(OntologyNode.NodeType.PROPERTY)) {

                List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subPropertyOf");

                if(parents != null) {
                    for(PropertyValue parent : parents) {
                        if(parent.getType() == PropertyValue.Type.URI && graph.nodes.containsKey(((PropertyValueURI) parent).getUri())) {
                            c.properties.addProperty("directParent", parent);
                        }
                    }
                }

            } else if (c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

                // The type of individuals becomes their parent in OLS
                //
                List<PropertyValue> types = c.properties.getPropertyValues("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

                if(types != null) {
                    for(PropertyValue type : types) {
                        if(type.getType() == PropertyValue.Type.URI) {

                            String typeUri = ((PropertyValueURI) type).getUri();

                            if(typeUri.equals("http://www.w3.org/2002/07/owl#NamedIndividual")) {
                                // the rdf:type field is used both to state that this is a NamedIndividual, and to indicate
                                // which owl:Class it is an instance of. We add the latter to directParents but not the former.
                                continue;
                            }

			    if(graph.nodes.containsKey(typeUri))
				c.properties.addProperty("directParent", type);
                        }
                    }
                }
            }
        }
        long endTime3 = System.nanoTime();
        logger.info("annotate direct parents: {} ", ((endTime3 - startTime3) / 1000 / 1000 / 1000));

    }


}
