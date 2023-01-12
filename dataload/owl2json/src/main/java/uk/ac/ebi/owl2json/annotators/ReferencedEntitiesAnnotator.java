package uk.ac.ebi.owl2json.annotators;

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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

		Set<String> referencedEntityUris = new TreeSet<>();
		referencedEntityUris.addAll(c.properties.getPropertyPredicates()); // need labels for all the predicates
		
		for(String predicate : c.properties.getPropertyPredicates()) {
			for(PropertyValue val : c.properties.getPropertyValues(predicate)) { // need labels for any IRI values
				if(val.getType() == Type.URI) {
					referencedEntityUris.add( ((PropertyValueURI) val).getUri() );
				} else if(val.getType() == Type.BNODE) {
			OwlNode bnode = graph.getNodeForPropertyValue(val);
			if(bnode != null) { // empty bnode values present in some ontologies, see issue #116
			if(bnode.types.contains(OwlNode.NodeType.RDF_LIST)) {
				for(PropertyValue listEntry : RdfListEvaluator.evaluateRdfList(bnode, graph)) {
				if(listEntry.getType() == Type.URI) {
					referencedEntityUris.add( ((PropertyValueURI) listEntry).getUri() );
				}
				}
			}
			}
		} else if(val.getType() == Type.RELATED) {
				referencedEntityUris.add(((PropertyValueRelated) val).getProperty());
				referencedEntityUris.add(((PropertyValueRelated) val).getFiller().uri);
		}
			}
		}

		PropertyValueReferencedEntities referencedEntitiesObj
			= new PropertyValueReferencedEntities();

		for(String referencedEntityUri : referencedEntityUris) {

			OwlNode referencedEntity = graph.nodes.get(referencedEntityUri);

			if(referencedEntity != null && referencedEntity.properties != null) {

				PropertySet properties = new PropertySet();

				List<PropertyValue> labels = referencedEntity.properties.getPropertyValues("label");

				if(labels != null) {
					 for(PropertyValue label : labels)
						   properties.addProperty("label", label);
				}

				referencedEntitiesObj.addEntity(referencedEntityUri, properties);
			}
		}

		c.properties.addProperty("referencedEntities", referencedEntitiesObj);
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate referenced entities: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}


