package uk.ac.ebi.owl2json.annotators;
import java.util.*;
import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;

public class LabelAnnotator {

    public static Set<String> getLabelProperties(OwlGraph graph) {

        Set<String> labelProperties = new TreeSet<>(
                List.of(
                        "http://www.w3.org/2000/01/rdf-schema#label"
                )
        );

        Object configLabelProperties = graph.config.get("label_property");

        if(configLabelProperties instanceof Collection<?>) {
            labelProperties.addAll((Collection<String>) configLabelProperties);
        }

        return labelProperties;
    }

    public static void annotateLabels(OwlGraph graph) {
        PropertyCollator.collateProperties(graph, "label", getLabelProperties(graph), List.of("shortForm"));
    }
}
