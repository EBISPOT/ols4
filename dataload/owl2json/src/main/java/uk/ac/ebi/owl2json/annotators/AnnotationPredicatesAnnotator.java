
package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class AnnotationPredicatesAnnotator {

    // The OLS3 API doesn't return properties as "annotations" if they have already been parsed as
    // the definition, synonym, hierarchy properties.
    //
    // It would be impossible to work this out in the API server without having access to the ontology
    // config, and in the case of a search that returns terms across multiple ontologies it would be
    // annoying/slow to have to retrieve all the different ontologies. So as a workaround, we store
    // a list of which predicates are used as annotations on each entity.
    //
    // The API server can then use this to build the list of annotations for the OLS3 backwards compatible API.
    //
    public static void annotateAnnotationPredicates(OwlGraph graph) {

        Set<String> nonAnnotationPredicates = new HashSet<>();
        nonAnnotationPredicates.addAll(HierarchicalParentsAnnotator.getHierarchicalProperties(graph));
        nonAnnotationPredicates.addAll(DefinitionAnnotator.getDefinitionProperties(graph));
        nonAnnotationPredicates.addAll(SynonymAnnotator.getSynonymProperties(graph));

        long startTime3 = System.nanoTime();

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);
            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                Set<String> annotationPredicates = new TreeSet<>();

                for(String predicate : c.properties.getPropertyPredicates()) {

		    // properties without an IRI are things that were added by owl2json
                    if(!predicate.contains("://"))
                        continue;

		    // anything in the rdf, rdfs, owl namespaces aren't considered annotations
                    if(predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
			predicate.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#") ||
                            predicate.startsWith("http://www.w3.org/2002/07/owl#")) {
                        continue;
                    }

		    // things we already parsed aren't considered annotations
                    if(nonAnnotationPredicates.contains(predicate)) {
                        continue;
                    }

                    annotationPredicates.add(predicate);
                }

                for(String predicate : annotationPredicates) {
                    c.properties.addProperty("annotationPredicate", PropertyValueLiteral.fromString(predicate));
                }
            }
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate annotation predicates: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}
