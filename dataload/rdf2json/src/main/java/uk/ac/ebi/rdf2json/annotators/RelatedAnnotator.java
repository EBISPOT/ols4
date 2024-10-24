package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.annotators.helpers.OntologyBaseUris;
import uk.ac.ebi.rdf2json.helpers.RdfListEvaluator;
import uk.ac.ebi.rdf2json.properties.*;

import java.util.*;
import java.util.stream.Collectors;

import static uk.ac.ebi.ols.shared.DefinedFields.RELATED_FROM;
import static uk.ac.ebi.ols.shared.DefinedFields.RELATED_TO;

public class RelatedAnnotator {

	private static final Logger logger = LoggerFactory.getLogger(RelatedAnnotator.class);

    public void annotateRelated(OntologyGraph graph) {

		Set<String> ontologyBaseUris = OntologyBaseUris.getOntologyBaseUris(graph);
		String preferredPrefix = (String)graph.config.get("preferredPrefix");

		long startTime3 = System.nanoTime();
		RelatedInfo relatedInfo = new RelatedInfo();
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

						PropertyValue onProperty = parentClassExprOrRestriction.properties
								.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");

						if(onProperty == null) {
							relatedInfo = annotateRelated_Class_subClassOf_ClassExpr(relatedInfo,
									c, parentClassExprOrRestriction, ontologyBaseUris, preferredPrefix, graph);
						} else {
							relatedInfo = annotateRelated_Class_subClassOf_Restriction(relatedInfo,
									c, onProperty, parentClassExprOrRestriction, graph);
						}

					}
				}


			}
        }
		relatedInfo.updateOntologyNodesWithRelatedLists();
        long endTime3 = System.nanoTime();
        logger.info("annotate related: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

	private static RelatedInfo annotateRelated_Class_subClassOf_ClassExpr(RelatedInfo relatedInfo,
			OntologyNode classNode, OntologyNode fillerClassExpr, Set<String> ontologyBaseUris,
		   String preferredPrefix, OntologyGraph graph) {

		PropertyValue oneOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#oneOf");
		if(oneOf != null)  {
			// This is a oneOf class expression
			return annotateRelated_Class_subClassOf_ClassExpr_oneOf(relatedInfo, classNode, fillerClassExpr, oneOf, graph);
		}

		PropertyValue intersectionOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#intersectionOf");
		if(intersectionOf != null)  {
			// This is an intersectionOf class expression (anonymous conjunction)
			return annotateRelated_Class_subClassOf_ClassExpr_intersectionOf(relatedInfo, classNode, fillerClassExpr,
					intersectionOf, graph);
		}
		return relatedInfo;
	}

	private static RelatedInfo annotateRelated_Class_subClassOf_ClassExpr_oneOf(RelatedInfo relatedInfo, OntologyNode classNode,
										 	OntologyNode fillerClassExpr, PropertyValue filler, OntologyGraph graph) {

		// The filler is an RDF list of Individuals

		OntologyNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OntologyNode> fillerIndividuals =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.nodes.get( ((PropertyValueURI) propertyValue).getUri() ))
						.collect(Collectors.toList());

		for(OntologyNode individualNode : fillerIndividuals) {
			relatedInfo.addRelatedTo(classNode,
					new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", individualNode));

			relatedInfo.addRelatedFrom(individualNode,
					new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", classNode));
		}
		return relatedInfo;
	}

	private static RelatedInfo annotateRelated_Class_subClassOf_ClassExpr_intersectionOf(RelatedInfo relatedInfo,
			  OntologyNode classNode, OntologyNode fillerClassExpr, PropertyValue filler, OntologyGraph graph) {

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

				relatedInfo.addRelatedTo(classNode,
						new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", fillerClassNode));

				relatedInfo.addRelatedFrom(fillerClassNode,
						new PropertyValueRelated(fillerClassExpr, "http://www.w3.org/2000/01/rdf-schema#subClassOf", classNode));
			}
		}
		return relatedInfo;
	}


	private static RelatedInfo annotateRelated_Class_subClassOf_Restriction(RelatedInfo relatedInfo,
				OntologyNode classNode, PropertyValue property, OntologyNode fillerRestriction,
				OntologyGraph graph) {

		if(property.getType() != PropertyValue.Type.URI) {
			// We can't do anything with anonymous properties.
			return relatedInfo;
		}

		PropertyValue onProperty = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#onProperty");

		if(onProperty == null || onProperty.getType() != PropertyValue.Type.URI)
			return relatedInfo;

		String propertyUri = ((PropertyValueURI) onProperty).getUri();

		PropertyValue someValuesFrom = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#someValuesFrom");
		if(someValuesFrom != null)  {
			// This is a someValuesFrom restriction
			return annotateRelated_Class_subClassOf_Restriction_someValuesFrom(relatedInfo,
					classNode, propertyUri, fillerRestriction, someValuesFrom, graph);
		}

		PropertyValue hasValue = fillerRestriction.properties.getPropertyValue("http://www.w3.org/2002/07/owl#hasValue");
		if(hasValue != null)  {
			// This is a hasValue restriction. The value can be either an individual or a literal data value.
			//
			return annotateRelated_Class_subClassOf_Restriction_hasValue(relatedInfo, classNode, propertyUri,
					fillerRestriction, hasValue, graph);

		}
		return relatedInfo;
	}

	private static RelatedInfo annotateRelated_Class_subClassOf_Restriction_someValuesFrom(RelatedInfo relatedInfo,
			OntologyNode classNode, String propertyUri, OntologyNode fillerRestriction, PropertyValue filler,
			OntologyGraph graph) {

		if(filler.getType() == PropertyValue.Type.URI) {

			String fillerUri = ((PropertyValueURI) filler).getUri();

				// Is the filler different from the entity we are annotating?
				if(!fillerUri.equals(classNode.uri)) {

					OntologyNode fillerNode = graph.nodes.get(fillerUri);

					if(fillerNode != null) { // sometimes filler not included in ontology, e.g. "subClassOf some xsd:float" in cdao

						relatedInfo.addRelatedTo(classNode, new PropertyValueRelated(fillerRestriction, propertyUri, fillerNode));
						relatedInfo.addRelatedFrom(fillerNode, new PropertyValueRelated(fillerRestriction, propertyUri, classNode));

					}
				}

			return relatedInfo;

		}

		if(filler.getType() == PropertyValue.Type.BNODE) {

			OntologyNode fillerClassExpr = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

			PropertyValue oneOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#oneOf");
			if(oneOf != null)  {
				// This is a oneOf class expression
				return annotateRelated_Class_subClassOf_Restriction_someValuesFrom_oneOf(relatedInfo, classNode, propertyUri, oneOf, graph);
			}

			PropertyValue intersectionOf = fillerClassExpr.properties.getPropertyValue("http://www.w3.org/2002/07/owl#intersectionOf");
			if(intersectionOf != null)  {
				// This is an intersectionOf class expression (anonymous conjunction)
				return annotateRelated_Class_subClassOf_Restriction_someValuesFrom_intersectionOf(relatedInfo, classNode,
						propertyUri, fillerClassExpr, intersectionOf, graph);
			}
		}
		return relatedInfo;
	}

	private static RelatedInfo annotateRelated_Class_subClassOf_Restriction_someValuesFrom_oneOf(RelatedInfo relatedInfo,
			OntologyNode classNode, String propertyUri, PropertyValue filler, OntologyGraph graph) {

		// The filler is an RDF list of Individuals

		OntologyNode fillerNode = graph.nodes.get( ((PropertyValueBNode) filler).getId() );

		List<OntologyNode> fillerIndividuals =
				RdfListEvaluator.evaluateRdfList(fillerNode, graph)
						.stream()
						.map(propertyValue -> graph.nodes.get( ((PropertyValueURI) propertyValue).getUri() ))
						.collect(Collectors.toList());

		for(OntologyNode individualNode : fillerIndividuals) {
			relatedInfo.addRelatedTo(classNode, new PropertyValueRelated(fillerNode, propertyUri, individualNode));
			relatedInfo.addRelatedFrom(individualNode, new PropertyValueRelated(fillerNode, propertyUri, classNode));
		}
		return relatedInfo;
	}

	private static RelatedInfo annotateRelated_Class_subClassOf_Restriction_someValuesFrom_intersectionOf(RelatedInfo relatedInfo,
			OntologyNode classNode, String propertyUri, OntologyNode fillerRestriction, PropertyValue filler, OntologyGraph graph) {

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
				relatedInfo.addRelatedTo(classNode, new PropertyValueRelated(fillerRestriction, propertyUri, fillerClassNode));
				relatedInfo.addRelatedFrom(fillerClassNode, new PropertyValueRelated(fillerRestriction, propertyUri, classNode));
			}
		}
		return relatedInfo;
	}

	private static RelatedInfo annotateRelated_Class_subClassOf_Restriction_hasValue(RelatedInfo relatedInfo, OntologyNode classNode,
			  String propertyUri, OntologyNode fillerRestriction, PropertyValue filler, OntologyGraph graph) {

		// The filler can be either an individual or a literal data value.

		if(filler.getType() == PropertyValue.Type.URI) {

			OntologyNode fillerNode = graph.nodes.get( ((PropertyValueURI) filler).getUri() );

			if(fillerNode.types.contains(OntologyNode.NodeType.INDIVIDUAL)) {
				// fillerNode is an individual
				relatedInfo.addRelatedTo(fillerNode, new PropertyValueRelated(fillerRestriction, propertyUri, classNode));
				relatedInfo.addRelatedFrom(classNode, new PropertyValueRelated(fillerRestriction, propertyUri, fillerNode));
			}


		}

		// TODO: what to do with data values?
		return relatedInfo;
	}

	private class RelatedInfo {
		private Map<OntologyNode, Set<PropertyValueRelated>> relatedFromMap = new HashMap<>();
		private Map<OntologyNode, Set<PropertyValueRelated>> relatedToMap = new HashMap<>();

		void addRelatedFrom(OntologyNode ontologyNode, PropertyValueRelated relatedFrom) {
			Set<PropertyValueRelated> relatedFromSetToUpdate;
			if (relatedFromMap.containsKey(ontologyNode)) {
				relatedFromSetToUpdate = relatedFromMap.get(ontologyNode);
			} else
				relatedFromSetToUpdate = new HashSet<>();

			relatedFromSetToUpdate.add(relatedFrom);
			relatedFromMap.put(ontologyNode, relatedFromSetToUpdate);
		}

		void addRelatedTo(OntologyNode ontologyNode, PropertyValueRelated relatedTo) {
			Set<PropertyValueRelated> relatedToSetToUpdate;
			if (relatedToMap.containsKey(ontologyNode)) {
				relatedToSetToUpdate = relatedToMap.get(ontologyNode);
			} else
				relatedToSetToUpdate = new HashSet<>();

			relatedToSetToUpdate.add(relatedTo);
			relatedToMap.put(ontologyNode, relatedToSetToUpdate);
		}
		void updateOntologyNodesWithRelatedLists() {
			for(OntologyNode ontologyNode: relatedFromMap.keySet()) {
				ontologyNode.properties.addProperty(RELATED_FROM.getText(),
						new PropertyValueList(Arrays.asList(relatedFromMap.get(ontologyNode).toArray())));
			}
			for(OntologyNode ontologyNode: relatedToMap.keySet()) {
				ontologyNode.properties.addProperty(RELATED_TO.getText(),
						new PropertyValueList(Arrays.asList(relatedToMap.get(ontologyNode).toArray())));
			}
		}

	}

}
