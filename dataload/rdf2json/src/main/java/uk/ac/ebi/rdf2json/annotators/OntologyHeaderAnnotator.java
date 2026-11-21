package uk.ac.ebi.rdf2json.annotators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.List;

import static uk.ac.ebi.ols.shared.DefinedFields.MAILING_LIST;

/**
 * OLS 3 read an ontology's homepage, issue tracker, mailing list and logo from the
 * ontology header annotations. The config record still wins: a field is only taken
 * from the header when the config does not set it (which also keeps the config value
 * from being written twice, as config keys are written alongside the ontology node's
 * properties).
 */
public class OntologyHeaderAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(OntologyHeaderAnnotator.class);

    private static final List<HeaderField> HEADER_FIELDS = List.of(
            new HeaderField("homepage", "http://xmlns.com/foaf/0.1/homepage", List.of("homepage")),
            new HeaderField("tracker", "http://usefulinc.com/ns/doap#bug-database", List.of("tracker")),
            new HeaderField(MAILING_LIST.getText(), "http://usefulinc.com/ns/doap#mailing-list",
                    List.of("mailing_list", MAILING_LIST.getText())),
            // OBO Foundry configs give the logo as depicted_by
            new HeaderField("logo", "https://schema.org/logo", List.of("logo", "depicted_by"))
    );

    public static void annotateOntologyHeader(OntologyGraph graph) {

        if(graph.ontologyNode == null)
            return;

        for(HeaderField field : HEADER_FIELDS) {

            if(field.configKeys.stream().anyMatch(key -> graph.config.get(key) != null))
                continue;

            PropertyValue value = getHeaderValue(graph, field.headerPredicate);

            if(value != null) {
                graph.ontologyNode.properties.addProperty(field.property, value);
                logger.debug("{} taken from ontology header annotation {}", field.property, field.headerPredicate);
            }
        }
    }

    // The first IRI or non-blank literal value, in document order. Both were accepted by OLS 3.
    private static PropertyValue getHeaderValue(OntologyGraph graph, String predicate) {

        List<PropertyValue> values = graph.ontologyNode.properties.getPropertyValues(predicate);

        if(values == null)
            return null;

        for(PropertyValue value : values) {
            if(value.getType() == PropertyValue.Type.URI) {
                return PropertyValueURI.fromUri(((PropertyValueURI) value).getUri());
            }
            if(value.getType() == PropertyValue.Type.LITERAL) {
                String literal = ((PropertyValueLiteral) value).getValue().trim();
                if(!literal.isEmpty()) {
                    return PropertyValueLiteral.fromString(literal);
                }
            }
        }

        return null;
    }

    private record HeaderField(String property, String headerPredicate, List<String> configKeys) {}
}
