package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.List;

import static uk.ac.ebi.ols.shared.DefinedFields.IS_OBSOLETE;

public class IsObsoleteAnnotator {
	private static final Logger logger = LoggerFactory.getLogger(IsObsoleteAnnotator.class);

	public static void annotateIsObsolete(OntologyGraph graph) {

		long startTime3 = System.nanoTime();

		for(String id : graph.nodes.keySet()) {
		    OntologyNode c = graph.nodes.get(id);

		    if (c.types.contains(OntologyNode.NodeType.CLASS) ||
				c.types.contains(OntologyNode.NodeType.PROPERTY) ||
				c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

			// skip bnodes
			if(c.uri == null)
				continue;
			}

			c.properties.addProperty(IS_OBSOLETE.getText(),
					PropertyValueLiteral.fromBoolean(isEntityObsolete(c) ? "true" : "false"));
		}

		long endTime3 = System.nanoTime();
		logger.info("annotate isObsolete: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
	}

	private static boolean isEntityObsolete(OntologyNode node) {

		// 1. is owl:deprecated true?

		PropertyValue deprecated = node.properties.getPropertyValue("http://www.w3.org/2002/07/owl#deprecated");

		String deprecatedValue = (deprecated != null) ? ((PropertyValueLiteral) deprecated).getValue() : "false";

		if(deprecated != null &&
				  deprecated.getType() == PropertyValue.Type.LITERAL &&
				(deprecatedValue.equalsIgnoreCase("true") || deprecatedValue.equals("1")))
		{
			 return true;
		}


		// 2. is the class a direct subClassOf oboInOwl:ObsoleteClass?
		List<PropertyValue> parents = node.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

		if(parents != null) {
			for(PropertyValue parent : parents) {
				if(parent.getType() == PropertyValue.Type.URI &&
						((PropertyValueURI) parent).getUri().equals("http://www.geneontology.org/formats/oboInOwl#ObsoleteClass")) {
					return true;
				}
			}
		}


		return false;
	}
}
