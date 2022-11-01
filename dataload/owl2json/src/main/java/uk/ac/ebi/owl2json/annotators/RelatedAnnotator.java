package uk.ac.ebi.owl2json.annotators;

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Property;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;
import uk.ac.ebi.owl2json.annotators.helpers.ShortFormExtractor;
import uk.ac.ebi.owl2json.properties.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class RelatedAnnotator {

    public static void annotateRelated(OwlGraph graph) {

		Set<String> hierarchicalProperties = HierarchicalParentsAnnotator.getHierarchicalProperties(graph);
		Set<String> ontologyBaseUris = OntologyBaseUris.getOntologyBaseUris(graph);
		String preferredPrefix = (String)graph.config.get("preferredPrefix");

		long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);
            if (c.types.contains(OwlNode.NodeType.CLASS)) {

                // skip bnodes
                if(c.uri == null)
                    continue;


                List<PropertyValue> parents = c.properties.getPropertyValues("http://www.w3.org/2000/01/rdf-schema#subClassOf");

                if(parents != null) {

					///
					/// **** TODO: Logic copied from OLS3 with no explanation!
					/// We need to formalise what "related" actually means in the API documentation and/or refine it for OLS4.
					///
                    for(PropertyValue parent : parents) {

						// Only want existential with named class as filler
						if(parent.getType() != PropertyValue.Type.BNODE) {
							continue;
						}

						OwlNode restrictionNode = graph.nodes.get( ((PropertyValueBNode) parent).getId() );

						PropertyValue someValuesFrom = restrictionNode.properties.getPropertyValue("http://www.w3.org/2002/07/owl#someValuesFrom");
						PropertyValue hasValue = restrictionNode.properties.getPropertyValue("http://www.w3.org/2002/07/owl#hasValue");
						PropertyValue property = restrictionNode.properties.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");
						PropertyValue filler = restrictionNode.properties.getPropertyValue("http://www.w3.org/2002/07/owl#hasValue");

						if(someValuesFrom != null)  {
							// This is a someValuesFrom restriction

							// someValuesFrom, if neither filler nor property of the someValuesFrom are anonymous
							if(property.getType() == PropertyValue.Type.URI &&
									filler.getType() == PropertyValue.Type.URI) {

								String propertyUri = ((PropertyValueURI) property).getUri();

								// If the property is hierarchical or partOf
								if(hierarchicalProperties.contains(propertyUri) || isPartOf(graph, ontologyBaseUris, preferredPrefix, propertyUri)) {

									String fillerUri = ((PropertyValueURI) filler).getUri();

									// Is the filler different from the entity we are annotating?
									if(!fillerUri.equals(c.uri)) {

										OwlNode fillerNode = graph.nodes.get(fillerUri);

										// = addRelatedChildTerm in OLS3
										// TODO: check this is the right way around
										//
										fillerNode.properties.addProperty("relatedTo", PropertyValueURI.fromUri(c.uri));
										c.properties.addProperty("relatedFrom", PropertyValueURI.fromUri(fillerUri));
									}
								}

							// someValuesFrom, if filler is anonymous but property is named
							} else if(property.getType() == PropertyValue.Type.URI && filler.getType() == PropertyValue.Type.BNODE) {
								indexTermToIndividualRelations(restrictionNode, graph, c);
							}

							continue;
						}

						if(hasValue != null) {
							// This is a hasValue restriction

							/*                    OWLObjectSomeValuesFrom someValuesFrom = (OWLObjectSomeValuesFrom) ((OWLObjectHasValue) expression).asSomeValuesFrom();
                    indexTermToIndividualRelations(someValuesFrom, relatedIndividualsToClasses);*/

							continue;
						}
					}
				}
			}


        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate related: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }


	//
	// below methods adapted from OLS3
	//

	private static boolean isPartOf(OwlGraph graph, Set<String> ontologyBaseUris, String preferredPrefix, String uri) {
		String shortForm = ShortFormExtractor.extractShortForm(graph, ontologyBaseUris, preferredPrefix, uri);
		return shortForm.toLowerCase().replaceAll("_", "").equals("partof");
	}

	private static void indexTermToIndividualRelations(OwlNode restrictionSomeValuesFrom, OwlGraph graph, OwlNode node) {

		PropertyValue fillerClassExpression = restrictionSomeValuesFrom.properties.getPropertyValue("http://www.w3.org/2002/07/owl#hasValue");
		PropertyValue property = restrictionSomeValuesFrom.properties.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");

        if (fillerClassExpression.getType() != PropertyValue.Type.URI) {
			// Property cannot be anonymous
            return;
        }

		OwlNode fillerClassExpressionNode = graph.nodes.get( ((PropertyValueURI) fillerClassExpression).getUri() );
		String propertyUri = ((PropertyValueURI) property).getUri();

		PropertyValue oneOf = fillerClassExpressionNode.properties.getPropertyValue("http://www.w3.org/2002/07/owl#oneOf");

		if(oneOf != null) {

			OwlNode oneOfNode = graph.nodes.get( ((PropertyValueBNode) oneOf).getId() );

			indexRelationsFromExistentialRestrictionsToNominals(oneOfNode, propertyUri, graph, node);
		}


	}

	private static void indexRelationsFromExistentialRestrictionsToNominals(OwlNode oneOfExpression, String propertyUri, OwlGraph graph, OwlNode node) {

		if(! (oneOfExpression.types.contains(OwlNode.NodeType.RDF_LIST))) {
			throw new RuntimeException("expected list for oneOf");
		}

		List<PropertyValue> values = new ArrayList<>();

		// TODO factor out RDF list iteration
		for(OwlNode cur = oneOfExpression;;) {

			PropertyValue first = cur.properties.getPropertyValue("http://www.w3.org/1999/02/22-rdf-syntax-ns#first");
			values.add(first);

			PropertyValue rest = cur.properties.getPropertyValue("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest");

			if(rest.getType() == PropertyValue.Type.URI &&
					((PropertyValueURI) rest).getUri().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil")) {
				break;
			}

			cur = graph.nodes.get(graph.nodeIdFromPropertyValue(rest));
		}

		if (values.size() != 1) {
			// (from OLS3) If there are more than one, we cannot assume a relationship.
			return;
		}

		PropertyValue individual = values.get(0);

		if(individual.getType() == PropertyValue.Type.URI) {
			OwlNode individualNode = graph.getNodeForPropertyValue(individual);
			individualNode.properties.addProperty("related__"+propertyUri, PropertyValueURI.fromUri(node.uri));
		}
	}



}
