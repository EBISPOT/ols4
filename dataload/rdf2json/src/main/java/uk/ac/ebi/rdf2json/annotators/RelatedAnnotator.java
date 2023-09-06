package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.rdf2json.helpers.RdfListEvaluator;
import uk.ac.ebi.rdf2json.properties.*;

import java.util.*;
import java.util.stream.Collectors;

public class RelatedAnnotator {

    public static void annotateRelated(OntologyGraph graph) {

		Set<String> ontologyBaseUris = OntologyBaseUris.getOntologyBaseUris(graph);
		String preferredPrefix = (String)graph.config.get("preferredPrefix");

		long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);
            if (c.types.contains(OntologyNode.NodeType.CLASS)) {

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

						OntologyNode parentClassExprOrRestriction = graph.nodes.get( ((PropertyValueBNode) parent).getId() );

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
			OntologyNode classNode, OntologyNode fillerClassExpr, Set<String> ontologyBaseUris, String preferredPrefix, OntologyGraph graph) {

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

	private static void annotateRelated_Class_subClassOf_ClassExpr_oneOf(OntologyNode classNode, OntologyNode fillerClassExpr, PropertyValue filler, OntologyGraph graph) {

		// The filler is an RDF list of Individuals

		OntologyNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OntologyNode> fillerIndividuals =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.nodes.get( ((PropertyValueURI) propertyValue).getUri() ))
						.collect(Collectors.toList());

		for(OntologyNode individualNode : fillerIndividuals) {
			classNode.properties.addProperty("relatedTo",
				new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", individualNode));
			individualNode.properties.addProperty("relatedFrom",
				new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", classNode));
		}
	}

	private static void annotateRelated_Class_subClassOf_ClassExpr_intersectionOf(OntologyNode classNode, OntologyNode fillerClassExpr, PropertyValue filler, OntologyGraph graph) {

		// The filler is an RDF list of Classes

		OntologyNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OntologyNode> fillerClasses =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.getNodeForPropertyValue(propertyValue))
						.collect(Collectors.toList());

		for(OntologyNode fillerClassNode : fillerClasses) {

			// Named nodes only. TODO what to do about bnodes in this case?
			if(fillerClassNode.uri != null) {

				classNode.properties.addProperty("relatedTo",
					new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", fillerClassNode));

				fillerClassNode.properties.addProperty("relatedFrom",
					new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", classNode));
			}
		}
	}


	private static void annotateRelated_Class_subClassOf_Restriction(
				OntologyNode classNode, PropertyValue property, OntologyNode fillerRestriction, Set<String> ontologyBaseUris, String preferredPrefix, OntologyGraph graph) {

		if(property.getType() != PropertyValue.Type.URI) {
			// We can't do anything with anonymous properties.
			return;
		}

		PropertyValue onProperty = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");

		if(onProperty == null || onProperty.getType() != PropertyValue.Type.URI)
			return;

		String propertyUri = ((PropertyValueURI) onProperty).getUri();

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
			OntologyNode classNode, String propertyUri, OntologyNode fillerRestriction, PropertyValue filler, Set<String> ontologyBaseUris, String preferredPrefix, OntologyGraph graph) {

		if(filler.getType() == PropertyValue.Type.URI) {

			String fillerUri = ((PropertyValueURI) filler).getUri();

				// Is the filler different from the entity we are annotating?
				if(!fillerUri.equals(classNode.uri)) {

					OntologyNode fillerNode = graph.nodes.get(fillerUri);

					if(fillerNode != null) { // sometimes filler not included in ontology, e.g. "subClassOf some xsd:float" in cdao

						  classNode.properties.addProperty("relatedTo", new PropertyValueRelated(fillerRestriction, propertyUri, fillerNode));
						  fillerNode.properties.addProperty("relatedFrom", new PropertyValueRelated(fillerRestriction, propertyUri, classNode));

					}
				}

			return;

		}

		if(filler.getType() == PropertyValue.Type.BNODE) {

			OntologyNode fillerClassExpr = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

			PropertyValue oneOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#oneOf");
			if(oneOf != null)  {
				// This is a oneOf class expression
				annotateRelated_Class_subClassOf_Restriction_someValuesFrom_oneOf(classNode, propertyUri, fillerClassExpr, oneOf, ontologyBaseUris, preferredPrefix, graph);
				return;
			}

			PropertyValue intersectionOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#intersectionOf");
			if(intersectionOf != null)  {
				// This is an intersectionOf class expression (anonymous conjunction)
				annotateRelated_Class_subClassOf_Restriction_someValuesFrom_intersectionOf(classNode, propertyUri, fillerClassExpr, intersectionOf, ontologyBaseUris, preferredPrefix, graph);
				return;
			}
		}

	}

	private static void annotateRelated_Class_subClassOf_Restriction_someValuesFrom_oneOf(
			OntologyNode classNode, String propertyUri, OntologyNode fillerRestriction, PropertyValue filler, Set<String> ontologyBaseUris, String preferredPrefix, OntologyGraph graph) {

		// The filler is an RDF list of Individuals

		OntologyNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OntologyNode> fillerIndividuals =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.nodes.get( ((PropertyValueURI) propertyValue).getUri() ))
						.collect(Collectors.toList());

		for(OntologyNode individualNode : fillerIndividuals) {
			classNode.properties.addProperty("relatedTo",
					new PropertyValueRelated(fillerNode, propertyUri, individualNode));
			individualNode.properties.addProperty("relatedFrom",
					new PropertyValueRelated(fillerNode, propertyUri, classNode));
		}
	}

	private static void annotateRelated_Class_subClassOf_Restriction_someValuesFrom_intersectionOf(
			OntologyNode classNode, String propertyUri, OntologyNode fillerRestriction, PropertyValue filler, Set<String> ontologyBaseUris, String preferredPrefix, OntologyGraph graph) {

		// The filler is an RDF list of Classes

		OntologyNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OntologyNode> fillerClasses =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.getNodeForPropertyValue(propertyValue))
						.collect(Collectors.toList());

		for(OntologyNode fillerClassNode : fillerClasses) {

			// Named nodes only. TODO what to do about bnodes in this case?
			if(fillerClassNode.uri != null) {

				classNode.properties.addProperty("relatedTo",
						new PropertyValueRelated(fillerRestriction, propertyUri, fillerClassNode));

				fillerClassNode.properties.addProperty("relatedFrom",
						new PropertyValueRelated(fillerRestriction, propertyUri, classNode));
			}
		}

	}

	private static void annotateRelated_Class_subClassOf_Restriction_hasValue(OntologyNode classNode, String propertyUri, OntologyNode fillerRestriction, PropertyValue filler, OntologyGraph graph) {

		// The filler can be either an individual or a literal data value.

		if(filler.getType() == PropertyValue.Type.URI) {

			OntologyNode fillerNode = graph.nodes.get( ((PropertyValueURI) filler).getUri() );

			if(fillerNode.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {
				// fillerNode is an individual
				fillerNode.properties.addProperty("relatedTo", new PropertyValueRelated(fillerRestriction, propertyUri, classNode));
				classNode.properties.addProperty("relatedFrom", new PropertyValueRelated(fillerRestriction, propertyUri, fillerNode));
			}

			return;
		} 

		// TODO: what to do with data values?
	}

}
