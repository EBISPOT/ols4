package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static uk.ac.ebi.rdf2json.properties.PropertyValue.Type.LITERAL;

public class SearchableAnnotationValuesAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(SearchableAnnotationValuesAnnotator.class);
    private static final Set<String> EXCLUDED_PREDICATES = Set.of(
            "loaded",
            "sourceFileTimestamp",
            "updated");

    // Roughly equivalent to "annotations_trimmed" in OLS3.
    //
    // A field that contains a list of just the values (no predicates) of all of the "annotations" (which is not a well
    // defined term, so we have to make it up) of an entity.
    //
    // This field is used for searching, so that you can search for the value of any property (regardless of how
    // important OLS thinks it is), and still expect a result.
    //
    public static void annotateSearchableAnnotationValues(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);
            annotateSearchableAnnotationValues(c);
        }

        long endTime3 = System.nanoTime();
        logger.info("annotate searchable annotation values: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }

    static void annotateSearchableAnnotationValues(OntologyNode node) {
        if(!(node.types.contains(OntologyNode.NodeType.CLASS) ||
                node.types.contains(OntologyNode.NodeType.PROPERTY) ||
                node.types.contains(OntologyNode.NodeType.INDIVIDUAL) ||
                node.types.contains(OntologyNode.NodeType.ONTOLOGY))) {
            return;
        }

        List<PropertyValue> values = new ArrayList<>();

        for(var predicate : node.properties.getPropertyPredicates()) {
            if(!isSearchableAnnotationPredicate(predicate)) {
                continue;
            }

            for(var value : node.properties.getPropertyValues(predicate)) {
                if(value.getType().equals(LITERAL)) {
                    values.add(value);
                }
            }
        }

        for(var value : values) {
            node.properties.addProperty("searchableAnnotationValues", value);
        }
    }

    static boolean isSearchableAnnotationPredicate(String predicate) {

        // Synthetic runtime metadata makes ontology search results nondeterministic and is not useful for users.
        if(EXCLUDED_PREDICATES.contains(predicate)) {
            return false;
        }

        // namespaces that are NOT considered annotations for this exercise...
        //
        return !predicate.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
                && !predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#")
                && !predicate.startsWith("http://www.w3.org/2002/07/owl#");
    }
}
