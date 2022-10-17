
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

                    if(!predicate.contains("://"))
                        continue;

                    if(predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#") ||
                            predicate.startsWith("http://www.w3.org/2002/07/owl#")) {
                        continue;
                    }

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
