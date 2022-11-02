package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.owl2json.annotators.helpers.ShortFormExtractor;
import uk.ac.ebi.owl2json.helpers.RdfListEvaluator;
import uk.ac.ebi.owl2json.properties.*;

import java.util.*;
import java.util.stream.Collectors;

public class RelatedAnnotator {

	/*
	 *  Add shortcut annotations for the form   (a subClassOf (some b))
	 *
	 */

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

                    for(PropertyValue parent : parents) {

						// We are only looking for anonymous parents, which are either class expressions or restrictions.
						//
						if(parent.getType() != PropertyValue.Type.BNODE) {
							continue;
						}

						OwlNode parentClassExprOrRestriction = graph.nodes.get( ((PropertyValueBNode) parent).getId() );

						PropertyValue onProperty = parentClassExprOrRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");

						if(onProperty == null) {
							annotateRelated_Class_subClassOf_ClassExpr(
									c, parentClassExprOrRestriction, hierarchicalProperties, ontologyBaseUris, preferredPrefix, graph);
						} else {
							annotateRelated_Class_subClassOf_Restriction(
									c, onProperty, parentClassExprOrRestriction, hierarchicalProperties, ontologyBaseUris, preferredPrefix, graph);
						}

					}
				}
			}


        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate related: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

	private static void annotateRelated_Class_subClassOf_ClassExpr(
			OwlNode classNode, OwlNode fillerClassExpr, Set<String> hierarchicalProperties, Set<String> ontologyBaseUris, String preferredPrefix, OwlGraph graph) {

		PropertyValue oneOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#oneOf");
		if(oneOf != null)  {
			// This is a oneOf class expression
			annotateRelated_Class_subClassOf_ClassExpr_oneOf(classNode, oneOf, graph);
			return;
		}

		PropertyValue intersectionOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#intersectionOf");
		if(intersectionOf != null)  {
			// This is an intersectionOf class expression (anonymous conjunction)
			annotateRelated_Class_subClassOf_ClassExpr_intersectionOf(classNode, intersectionOf, graph);
			return;
		}
	}

	private static void annotateRelated_Class_subClassOf_ClassExpr_oneOf(OwlNode classNode, PropertyValue filler, OwlGraph graph) {

		// The filler is an RDF list of Individuals

		OwlNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OwlNode> fillerIndividuals =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.nodes.get( ((PropertyValueURI) propertyValue).getUri() ))
						.collect(Collectors.toList());

		for(OwlNode individualNode : fillerIndividuals) {
			classNode.properties.addProperty("relatedFrom", PropertyValueURI.fromUri(individualNode.uri));
			individualNode.properties.addProperty("relatedTo", PropertyValueURI.fromUri(classNode.uri));
		}
	}

	private static void annotateRelated_Class_subClassOf_ClassExpr_intersectionOf(OwlNode classNode, PropertyValue filler, OwlGraph graph) {

		// The filler is an RDF list of Classes

		OwlNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OwlNode> fillerClasses =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.nodes.get( ((PropertyValueURI) propertyValue).getUri() ))
						.collect(Collectors.toList());

		for(OwlNode fillerClassNode : fillerClasses) {
			classNode.properties.addProperty("relatedFrom", PropertyValueURI.fromUri(fillerClassNode.uri));
			fillerClassNode.properties.addProperty("relatedTo", PropertyValueURI.fromUri(classNode.uri));
		}
	}


	private static void annotateRelated_Class_subClassOf_Restriction(
				OwlNode classNode, PropertyValue property, OwlNode fillerRestriction, Set<String> hierarchicalProperties, Set<String> ontologyBaseUris, String preferredPrefix, OwlGraph graph) {

		if(property.getType() != PropertyValue.Type.URI) {
			// We can't do anything with anonymous properties.
			return;
		}

		String propertyUri = ((PropertyValueURI) property).getUri();

		PropertyValue someValuesFrom = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#someValuesFrom");
		if(someValuesFrom != null)  {
			// This is a someValuesFrom restriction
			annotateRelated_Class_subClassOf_Restriction_someValuesFrom(
					classNode, propertyUri, someValuesFrom, hierarchicalProperties, ontologyBaseUris, preferredPrefix, graph);
			return;
		}

		PropertyValue hasValue = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#hasValue");
		if(hasValue != null)  {
			// This is a hasValue restriction
			annotateRelated_Class_subClassOf_Restriction_hasValue(classNode, propertyUri, hasValue, graph);
			return;
		}
	}

	private static void annotateRelated_Class_subClassOf_Restriction_someValuesFrom(
			OwlNode classNode, String propertyUri, PropertyValue filler, Set<String> hierarchicalProperties, Set<String> ontologyBaseUris, String preferredPrefix, OwlGraph graph) {

		if(filler.getType() == PropertyValue.Type.URI) {

			String fillerUri = ((PropertyValueURI) filler).getUri();

			// If the property is hierarchical or partOf
			if(hierarchicalProperties.contains(propertyUri) || isPartOf(graph, ontologyBaseUris, preferredPrefix, propertyUri)) {

				// Is the filler different from the entity we are annotating?
				if(!fillerUri.equals(classNode.uri)) {

					OwlNode fillerNode = graph.nodes.get(fillerUri);

					// = addRelatedChildTerm in OLS3
					// TODO: check this is the right way around
					//
					fillerNode.properties.addProperty("relatedTo", PropertyValueURI.fromUri(classNode.uri));
//					fillerNode.properties.addProperty("related+"+propertyUri, PropertyValueURI.fromUri(c.uri));
					classNode.properties.addProperty("relatedFrom", PropertyValueURI.fromUri(fillerUri));
//					classNode.properties.addProperty("related+"+propertyUri, PropertyValueURI.fromUri(fillerUri));
				}
			}

			return;

		}

		if(filler.getType() == PropertyValue.Type.BNODE) {

			OwlNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

			// Evaluate this e.g. (subClassOf (some (oneOf...)) the same as (subClassOf (oneOf...))
			// This seems to be what OLS3 does.
			// (TODO: it ignores the propertyUri; does OLS3 use the property anywhere in this case?)
			//
			annotateRelated_Class_subClassOf_ClassExpr(classNode, fillerNode, hierarchicalProperties, ontologyBaseUris, preferredPrefix, graph);
		}

	}

	private static void annotateRelated_Class_subClassOf_Restriction_hasValue(OwlNode classNode, String propertyUri, PropertyValue filler, OwlGraph graph) {

		// The filler is an Individual

		OwlNode individualNode = graph.nodes.get( ((PropertyValueURI) filler).getUri() );

		classNode.properties.addProperty("relatedFrom", PropertyValueURI.fromUri(individualNode.uri));
		individualNode.properties.addProperty("relatedTo", PropertyValueURI.fromUri(classNode.uri));
	}


	//
	// below methods adapted from OLS3
	//

	private static boolean isPartOf(OwlGraph graph, Set<String> ontologyBaseUris, String preferredPrefix, String uri) {
		String shortForm = ShortFormExtractor.extractShortForm(graph, ontologyBaseUris, preferredPrefix, uri);
		return shortForm.toLowerCase().replaceAll("_", "").equals("partof");
	}
}
