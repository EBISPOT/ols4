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

	public static final OboDatabaseUrlService dbUrls = new OboDatabaseUrlService();
	public static final Bioregistry bioregistry = new Bioregistry();

	public static void annotateReferencedEntities(OwlGraph graph) {

        long startTime3 = System.nanoTime();

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);

			// skip bnodes
			if(c.uri == null)
				continue;

			Set<String> possibleIds = new TreeSet<>();
			possibleIds.addAll(c.properties.getPropertyPredicates()); // need labels for all the predicates

			for(String predicate : c.properties.getPropertyPredicates()) {
				 for(PropertyValue val : c.properties.getPropertyValues(predicate)) {
					   // Get anything that looks like it might be an IRI or a CURIE
						possibleIds.addAll(getPossibleIds(val, graph));
				 }
			}

			PropertyValueReferencedEntities referencedEntitiesObj
				= new PropertyValueReferencedEntities();

			for(String possibleId : possibleIds) {

				// 1. Is this possible ID the IRI of something in the graph?
				//
				OwlNode referencedEntity = graph.nodes.get(possibleId);

				if(referencedEntity != null && referencedEntity.properties != null) {

					 PropertySet properties = new PropertySet();

					 List<PropertyValue> labels = referencedEntity.properties.getPropertyValues("label");

					 if(labels != null) {
						   for(PropertyValue label : labels)
								 properties.addProperty("label", label);
					 }

					 referencedEntitiesObj.addEntity(referencedEntity.uri, properties);

					 continue;
				}

				// 2. Is this possible ID a CURIE with a known prefix?
				//
				if(possibleId.matches("^[A-z0-9]+:[A-z0-9]+$")) {
					 String databaseId = possibleId.substring(0, possibleId.indexOf(':'));
					 String entryId = possibleId.substring(possibleId.indexOf(':') + 1);

					 // check GO db-xrefs
					 String url = dbUrls.getUrlForId(databaseId, entryId);

					 if(url != null) {
						  PropertySet properties = new PropertySet();
						  properties.addProperty("url", PropertyValueURI.fromUri(url));
						 properties.addProperty("source", PropertyValueURI.fromUri(dbUrls.getXrefUrls()));
						 referencedEntitiesObj.addEntity(possibleId, properties);

						  continue;
					 }

					 // check bioregistry
					 url = bioregistry.getUrlForId(databaseId, entryId);

					 if(url != null) {
						 PropertySet properties = new PropertySet();
						 properties.addProperty("url", PropertyValueURI.fromUri(url));
						 properties.addProperty("source", PropertyValueURI.fromUri(bioregistry.getRegistryUrl()));
						 referencedEntitiesObj.addEntity(possibleId, properties);

						 continue;
					 }
				}
			}

			c.properties.addProperty("referencedEntities", referencedEntitiesObj);
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate referenced entities: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }



	// Returns things that might be a referenced entity IRI or CURIE
	// Some of them will be other strings though.
	//
	private static Set<String> getPossibleIds(PropertyValue val, OwlGraph graph) {

		Set<String> potentiallyReferencedEntityIds = new TreeSet<>();

		if(val.getType() == Type.URI) {
			potentiallyReferencedEntityIds.add( ((PropertyValueURI) val).getUri() );
		} else if(val.getType() == Type.LITERAL) {
			// CURIEs are string literals and IRIs are often stored as string literals too
			//
			String literalValue =  ((PropertyValueLiteral) val).getValue();
			if(literalValue.contains("://") // maybe IRI?
					|| literalValue.matches("^[A-z0-9]+:[A-z0-9]+$")) { // maybe CURIE?
				potentiallyReferencedEntityIds.add(literalValue);
			}
		} else if(val.getType() == Type.BNODE) {
			OwlNode bnode = graph.getNodeForPropertyValue(val);
			if(bnode != null) { // empty bnode values present in some ontologies, see issue #116
				if(bnode.types.contains(OwlNode.NodeType.RDF_LIST)) {
					for(PropertyValue listEntry : RdfListEvaluator.evaluateRdfList(bnode, graph)) {
						potentiallyReferencedEntityIds.addAll(getPossibleIds(listEntry, graph));
					}
				} else {
					for(String predicate2 : bnode.properties.getPropertyPredicates()) {
						for(PropertyValue val2 : bnode.properties.getPropertyValues(predicate2)) {
							potentiallyReferencedEntityIds.addAll(getPossibleIds(val2, graph));
						}
					}
				}
			}
		} else if(val.getType() == Type.RELATED) {
			potentiallyReferencedEntityIds.add(((PropertyValueRelated) val).getProperty());
			potentiallyReferencedEntityIds.add(((PropertyValueRelated) val).getFiller().uri);
		}

		return potentiallyReferencedEntityIds;
	}
}


