package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

public class OntologyMetadataAnnotator {

	public static void annotateOntologyMetadata(OntologyGraph graph) {

		long startTime3 = System.nanoTime();


		String ontologyId = ((String) graph.config.get("id")).toLowerCase();
		String ontologyPreferredPrefix = (String) graph.config.get("preferredPrefix");
		String ontologyIri = (String) graph.ontologyNode.uri;


		for(String id : graph.nodes.keySet()) {
		    OntologyNode c = graph.nodes.get(id);
		    if (c.types.contains(OntologyNode.NodeType.CLASS) ||
				c.types.contains(OntologyNode.NodeType.PROPERTY) ||
				c.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {

			// skip bnodes
			if(c.uri == null)
				continue;
	
			c.properties.addProperty("ontologyId", PropertyValueLiteral.fromString(ontologyId));

			if(ontologyPreferredPrefix != null)
				c.properties.addProperty("ontologyPreferredPrefix", PropertyValueLiteral.fromString(ontologyPreferredPrefix));

			c.properties.addProperty("ontologyIri", PropertyValueLiteral.fromString(ontologyIri));
		    }
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate ontology IDs: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
}
