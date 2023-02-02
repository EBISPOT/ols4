package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.xrefs.Bioregistry;
import uk.ac.ebi.owl2json.xrefs.OboDatabaseUrlService;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.helpers.RdfListEvaluator;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.properties.PropertySet;
import uk.ac.ebi.owl2json.properties.PropertyValue;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;
import uk.ac.ebi.owl2json.properties.PropertyValueReferencedEntities;
import uk.ac.ebi.owl2json.properties.PropertyValueRelated;
import uk.ac.ebi.owl2json.properties.PropertyValueURI;
import uk.ac.ebi.owl2json.properties.PropertyValue.Type;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReferencedEntitiesAnnotator {

	public static void annotateReferencedEntities(OwlGraph graph) {

        long startTime3 = System.nanoTime();

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

			// skip bnodes
			if(c.uri == null)
				continue;

			c.properties.addProperty("referencedEntities", new PropertyValueReferencedEntities(c));
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate referenced entities: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }


}


