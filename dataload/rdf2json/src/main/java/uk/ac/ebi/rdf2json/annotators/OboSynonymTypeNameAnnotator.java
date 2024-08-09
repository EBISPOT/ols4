package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.List;

public class OboSynonymTypeNameAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(OboSynonymTypeNameAnnotator.class);

    public static void annotateOboSynonymTypeNames(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {

            OntologyNode c = graph.nodes.get(id);

            if (c.types.contains(OntologyNode.NodeType.AXIOM)) {

                List<PropertyValue> hasSynonymType = c.properties.getPropertyValues("http://www.geneontology.org/formats/oboInOwl#hasSynonymType");

                if(hasSynonymType != null) {
                    for(PropertyValue synonymType : hasSynonymType) {
                        if(synonymType.getType() == PropertyValue.Type.URI) {

			    String synonymTypeUri = ((PropertyValueURI) synonymType).getUri();
			    OntologyNode synonymTypeNode = graph.nodes.get(synonymTypeUri);

			    if(synonymTypeNode != null) {
				// should be an owl:AnnotationProperty

				PropertyValue labelProp = synonymTypeNode.properties.getPropertyValue("http://www.w3.org/2000/01/rdf-schema#label");

				if(labelProp != null && labelProp.getType() == PropertyValue.Type.LITERAL) {
					String label = ((PropertyValueLiteral) labelProp).getValue();
					c.properties.addProperty("oboSynonymTypeName", PropertyValueLiteral.fromString(label));
				}
			    }

                        }
                    }
                }
            }
        }
        long endTime3 = System.nanoTime();
        logger.info("annotate obo synonym type names: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));

    }


}
