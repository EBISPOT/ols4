package uk.ac.ebi.rdf2json.annotators;
import java.util.*;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.annotators.helpers.PropertyCollator;

public class LabelAnnotator {

    public static Set<String> getLabelProperties(OntologyGraph graph) {

        Set<String> labelProperties = new TreeSet<>(
                List.of(
                        "http://www.w3.org/2000/01/rdf-schema#label",
                        "http://purl.org/dc/elements/1.1/title",
                        "http://purl.org/dc/terms/title"
                )
        );

        Object configLabelProperties = graph.config.get("label_property");

        if(configLabelProperties instanceof Collection<?>) {
            labelProperties.addAll((Collection<String>) configLabelProperties);
        }

        return labelProperties;
    }

    public static void annotateLabels(OntologyGraph graph) {
        PropertyCollator.collateProperties(graph, "label", getLabelProperties(graph), List.of("shortForm"));
    }
}
