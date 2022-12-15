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

    public static void annotateRelated(OwlGraph graph) {

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
									c, parentClassExprOrRestriction, ontologyBaseUris, preferredPrefix, graph);
						} else {
							annotateRelated_Class_subClassOf_Restriction(
									c, onProperty, parentClassExprOrRestriction, ontologyBaseUris, preferredPrefix, graph);
						}

					}
				}
			}


        }
        long endTime3 = System.nanoTime();
        System.out.println("annotate related: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

	private static void annotateRelated_Class_subClassOf_ClassExpr(
			OwlNode classNode, OwlNode fillerClassExpr, Set<String> ontologyBaseUris, String preferredPrefix, OwlGraph graph) {

		PropertyValue oneOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#oneOf");
		if(oneOf != null)  {
			// This is a oneOf class expression
			annotateRelated_Class_subClassOf_ClassExpr_oneOf(classNode, fillerClassExpr, oneOf, graph);
			return;
		}

		PropertyValue intersectionOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#intersectionOf");
		if(intersectionOf != null)  {
			// This is an intersectionOf class expression (anonymous conjunction)
			annotateRelated_Class_subClassOf_ClassExpr_intersectionOf(classNode, fillerClassExpr, intersectionOf, graph);
			return;
		}
	}

	private static void annotateRelated_Class_subClassOf_ClassExpr_oneOf(OwlNode classNode, OwlNode fillerClassExpr, PropertyValue filler, OwlGraph graph) {

		// The filler is an RDF list of Individuals

		OwlNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OwlNode> fillerIndividuals =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.nodes.get( ((PropertyValueURI) propertyValue).getUri() ))
						.collect(Collectors.toList());

		for(OwlNode individualNode : fillerIndividuals) {
			classNode.properties.addProperty("relatedTo",
				new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", individualNode));
			// classNode.properties.addProperty("relatedFrom", new PropertyValueRelated(fillerClassExpr, individualNode));
		}
	}

	private static void annotateRelated_Class_subClassOf_ClassExpr_intersectionOf(OwlNode classNode, OwlNode fillerClassExpr, PropertyValue filler, OwlGraph graph) {

		// The filler is an RDF list of Classes

		OwlNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OwlNode> fillerClasses =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.getNodeForPropertyValue(propertyValue))
						.collect(Collectors.toList());

		for(OwlNode fillerClassNode : fillerClasses) {

			// Named nodes only. TODO what to do about bnodes in this case?
			if(fillerClassNode.uri != null) {

				classNode.properties.addProperty("relatedTo",
					new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", fillerClassNode));

				// classNode.properties.addProperty("relatedFrom", new PropertyValueRelated(fillerClassExpr, fillerClassNode));
			}
		}
	}


	private static void annotateRelated_Class_subClassOf_Restriction(
				OwlNode classNode, PropertyValue property, OwlNode fillerRestriction, Set<String> ontologyBaseUris, String preferredPrefix, OwlGraph graph) {

		if(property.getType() != PropertyValue.Type.URI) {
			// We can't do anything with anonymous properties.
			return;
		}

		String propertyUri = ((PropertyValueURI) property).getUri();

		PropertyValue someValuesFrom = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#someValuesFrom");
		if(someValuesFrom != null)  {
			// This is a someValuesFrom restriction
			annotateRelated_Class_subClassOf_Restriction_someValuesFrom(
					classNode, propertyUri, fillerRestriction, someValuesFrom, ontologyBaseUris, preferredPrefix, graph);
			return;
		}

		PropertyValue hasValue = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#hasValue");
		if(hasValue != null)  {
			// This is a hasValue restriction. The value can be either an individual or a literal data value.
			//
			annotateRelated_Class_subClassOf_Restriction_hasValue(classNode, propertyUri, fillerRestriction, hasValue, graph);
			return;
		}
	}

	private static void annotateRelated_Class_subClassOf_Restriction_someValuesFrom(
			OwlNode classNode, String propertyUri, OwlNode fillerRestriction, PropertyValue filler, Set<String> ontologyBaseUris, String preferredPrefix, OwlGraph graph) {

		if(filler.getType() == PropertyValue.Type.URI) {

			String fillerUri = ((PropertyValueURI) filler).getUri();

				// Is the filler different from the entity we are annotating?
				if(!fillerUri.equals(classNode.uri)) {

					OwlNode fillerNode = graph.nodes.get(fillerUri);

					classNode.properties.addProperty("relatedTo", new PropertyValueRelated(fillerRestriction, propertyUri, fillerNode));
				}

			return;

		}

		if(filler.getType() == PropertyValue.Type.BNODE) {

			OwlNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

			// Evaluate this e.g. (subClassOf (some (oneOf...)) the same as (subClassOf (oneOf...))
			// This seems to be what OLS3 does.
			// (TODO: it ignores the propertyUri; does OLS3 use the property anywhere in this case?)
			//
			annotateRelated_Class_subClassOf_ClassExpr(classNode, fillerNode, ontologyBaseUris, preferredPrefix, graph);
		}

	}

	private static void annotateRelated_Class_subClassOf_Restriction_hasValue(OwlNode classNode, String propertyUri, OwlNode fillerRestriction, PropertyValue filler, OwlGraph graph) {

		// The filler can be either an individual or a literal data value.

		if(filler.getType() == PropertyValue.Type.URI) {

			OwlNode fillerNode = graph.nodes.get( ((PropertyValueURI) filler).getUri() );

			if(fillerNode.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {
				// fillerNode is an individual
				fillerNode.properties.addProperty("relatedTo", new PropertyValueRelated(fillerRestriction, propertyUri, classNode));
			}

			return;
		} 

		// TODO: what to do with data values?
	}

}
