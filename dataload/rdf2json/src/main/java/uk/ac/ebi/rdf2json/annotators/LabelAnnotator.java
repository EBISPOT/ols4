package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class LabelAnnotator {

    private static final Logger logger = LoggerFactory.getLogger(LabelAnnotator.class);

    public static Set<String> getLabelProperties(OntologyGraph graph) {

        Set<String> labelProperties = new TreeSet<>(
                List.of(
                        "http://www.w3.org/2000/01/rdf-schema#label",
                        "http://purl.org/dc/elements/1.1/title",
                        "http://purl.org/dc/terms/title",
                        "http://www.w3.org/2004/02/skos/core#prefLabel"
                )
        );

        Object configLabelProperties = graph.config.get("label_property");

        if(configLabelProperties instanceof Collection<?>) {
            labelProperties.addAll((Collection<String>) configLabelProperties);
        }

        return labelProperties;
    }

    public static void annotateLabels(OntologyGraph graph) {
        collateProperties(graph, "label", getLabelProperties(graph), List.of("shortForm"));
    }

    private static void collateProperties(OntologyGraph graph, String destProp, Collection<String> sourceProps, Collection<String> fallbackProps) {

        long startTime3 = System.nanoTime();

        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);

            // skip bnodes
            if(c.uri == null)
                continue;

            boolean hasEnglishValue = false;

            for(String prop : sourceProps) {
                List<PropertyValue> values = c.properties.getPropertyValues(prop);
                if(values != null) {
                    for(PropertyValue value : values) {
                        c.properties.addProperty(destProp, value);
                        if(!isNonEnglishValue(graph, value))
                            hasEnglishValue = true;
                    }
                }
            }

            if(!hasEnglishValue) {
                for(String prop : fallbackProps) {
                    List<PropertyValue> values = c.properties.getPropertyValues(prop);
                    if (values != null) {
                        for (PropertyValue value : values) {
                            c.properties.addProperty(destProp, value);
                        }
                    }
                }
            }
        }

        long endTime3 = System.nanoTime();
        logger.info("collate properties from {} and fallback {} into {} : {}",
                sourceProps, fallbackProps, destProp, ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }

    private static boolean isNonEnglishValue(OntologyGraph graph, PropertyValue value) {
        if(value.getType() == PropertyValue.Type.LITERAL) {
            PropertyValueLiteral literal = (PropertyValueLiteral) value;
            if( !literal.getLang().equals("") && !literal.getLang().equals("en")) {
                return true;
            }
        }
        return false;
    }
}
