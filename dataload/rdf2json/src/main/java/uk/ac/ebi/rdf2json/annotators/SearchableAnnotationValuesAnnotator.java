package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;

import java.util.ArrayList;
import java.util.List;

import static uk.ac.ebi.rdf2json.properties.PropertyValue.Type.LITERAL;

public class SearchableAnnotationValuesAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(SearchableAnnotationValuesAnnotator.class);

    // Roughly equivalent to "annotations_trimmed" in OLS3.
    //
    // A field that contains a list of just the values (no predicates) of all of the "annotations" (which is not a well
    // defined term, so we have to make it up) of an entity.
    //
    // This field is used for solr searching, so that you can search for the value of any property (regardless of how
    // important OLS thinks it is), and still expect a result.
    //
    public static void annotateSearchableAnnotationValues(OntologyGraph graph) {

        long startTime3 = System.nanoTime();
        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);
            if(c.types.contains(OntologyNode.NodeType.CLASS) ||
                    c.types.contains(OntologyNode.NodeType.PROPERTY) ||
                    c.types.contains(OntologyNode.NodeType.INDIVIDUAL) ||
                    c.types.contains(OntologyNode.NodeType.ONTOLOGY)) {

                List<PropertyValue> values = new ArrayList<>();

                for(var predicate : c.properties.getPropertyPredicates()) {

                    // namespaces that are NOT considered annotations for this exercise...
                    //
                    if(predicate.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
                            || predicate.startsWith("http://www.w3.org/2000/01/rdf-schema#")
                            || predicate.startsWith("http://www.w3.org/2002/07/owl#")) {

                        continue;
                    }

                    for(var value : c.properties.getPropertyValues(predicate)) {
                        if(value.getType().equals(LITERAL)) {
                            values.add(value);
                        }
                    }
                }

                for(var value : values) {
                    c.properties.addProperty("searchableAnnotationValues", value);
                }
            }
        }

        long endTime3 = System.nanoTime();
        logger.info("annotate searchable annotation values: {}", ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
