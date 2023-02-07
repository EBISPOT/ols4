
package uk.ac.ebi.owl2json.properties;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.helpers.RdfListEvaluator;
import uk.ac.ebi.owl2json.xrefs.Bioregistry;
import uk.ac.ebi.owl2json.xrefs.OboDatabaseUrlService;

import java.util.*;

// Like PropertyValueAncestors, we don't actually want to construct the referenced entities object for every
// entity in the ontology in RAM because this would make the RAM requirements huge for ontologies like ncbitaxon.
// So we store this facade property value that actually constructs the referenced entities map at write time.
// Therefore the RAM requirements are only ever those of one entity at a time.
//
public class PropertyValueReferencedEntities extends PropertyValue {

	public static final OboDatabaseUrlService dbUrls = new OboDatabaseUrlService();
	public static final Bioregistry bioregistry = new Bioregistry();

	OwlNode node;

	public PropertyValueReferencedEntities(OwlNode node) {
		this.node = node;
	}

	@Override
	public Type getType() {
		return Type.REFERENCED_ENTITIES;
	}

	@Override
	public boolean equals(PropertyValue other) {
		throw new RuntimeException("unimplemented");
	}

	public Map<String,PropertySet> getEntityIriToProperties(OwlGraph graph) {

		Set<String> possibleIds = new TreeSet<>() ;

		possibleIds.addAll(node.properties.getPropertyPredicates()); // need labels for all the predicates

		for(String predicate : node.properties.getPropertyPredicates()) {
			for(PropertyValue val : node.properties.getPropertyValues(predicate)) {
				// Get anything that looks like it might be an IRI or a CURIE
				possibleIds.addAll(getPossibleIds(val, graph));
			}
		}

		Map<String,PropertySet> referencedIriToProperties = new TreeMap<>();

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

				referencedIriToProperties.put(referencedEntity.uri, properties);
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
					referencedIriToProperties.put(possibleId, properties);

					continue;
				}

				// check bioregistry
				url = bioregistry.getUrlForId(databaseId, entryId);

				if(url != null) {
					PropertySet properties = new PropertySet();
					properties.addProperty("url", PropertyValueURI.fromUri(url));
					properties.addProperty("source", PropertyValueURI.fromUri(bioregistry.getRegistryUrl()));
					referencedIriToProperties.put(possibleId, properties);

					continue;
				}
			}
		}

		return referencedIriToProperties;
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
