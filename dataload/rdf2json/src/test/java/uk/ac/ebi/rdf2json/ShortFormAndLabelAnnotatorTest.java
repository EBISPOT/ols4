package uk.ac.ebi.rdf2json;

import org.junit.Test;
import uk.ac.ebi.rdf2json.annotators.LabelAnnotator;
import uk.ac.ebi.rdf2json.annotators.ShortFormAnnotator;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueList;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShortFormAndLabelAnnotatorTest {
    private static final String RDF_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String CUSTOM_LABEL = "https://example.org/name";

    @Test
    public void customExtractionPreservesUnderscoredPreferredPrefixInCurie() throws IOException {
        OntologyGraph graph = graph(Map.of(
                "preferredPrefix", "EX_TRA",
                "shortFormExtractionPattern", "https://example.org/terms/(.*)"));
        OntologyNode entity = entity(graph, "https://example.org/terms/123");

        ShortFormAnnotator.annotateShortForms(graph);

        assertEquals(List.of("EX_TRA_123"), values(entity, "shortForm"));
        assertEquals(List.of("EX_TRA:123"), values(entity, "curie"));
    }

    @Test
    public void numericSuffixAfterMultipleUnderscoresFormsCurieAtLastSeparator() throws IOException {
        OntologyGraph graph = graph(Map.of());
        OntologyNode entity = entity(graph, "https://example.org/terms/EX_TRA_123");

        ShortFormAnnotator.annotateShortForms(graph);

        assertEquals(List.of("EX_TRA_123"), values(entity, "shortForm"));
        assertEquals(List.of("EX_TRA:123"), values(entity, "curie"));
    }

    @Test
    public void englishLabelSuppressesShortFormFallbackButRetainsOtherLanguages() throws IOException {
        OntologyGraph graph = graph(Map.of());
        OntologyNode entity = entity(graph, "https://example.org/terms/EnglishAndFrench");
        entity.properties.addProperty(RDF_LABEL, new PropertyValueLiteral("English name", "", "en"));
        entity.properties.addProperty(RDF_LABEL, new PropertyValueLiteral("Nom français", "", "fr"));

        ShortFormAnnotator.annotateShortForms(graph);
        LabelAnnotator.annotateLabels(graph);

        assertEquals(List.of("English name", "Nom français"), values(entity, "label"));
    }

    @Test
    public void customLabelPredicateReplacesDefaultsAndFallsBackToShortForm() throws IOException {
        OntologyGraph graph = graph(Map.of("label_property", List.of(CUSTOM_LABEL)));
        OntologyNode custom = entity(graph, "https://example.org/terms/Custom");
        custom.properties.addProperty(RDF_LABEL, PropertyValueLiteral.fromString("Default name"));
        custom.properties.addProperty(CUSTOM_LABEL, PropertyValueLiteral.fromString("Configured name"));
        OntologyNode fallback = entity(graph, "https://example.org/terms/Unlabelled");
        fallback.properties.addProperty(RDF_LABEL, PropertyValueLiteral.fromString("Ignored default"));

        ShortFormAnnotator.annotateShortForms(graph);
        LabelAnnotator.annotateLabels(graph);

        assertEquals(List.of("Configured name"), values(custom, "label"));
        assertEquals(List.of("Unlabelled"), values(fallback, "label"));
        assertNull(fallback.properties.getPropertyValues(CUSTOM_LABEL));
    }

    private static OntologyGraph graph(Map<String, Object> settings) throws IOException {
        Map<String, Object> config = new HashMap<>(settings);
        config.put("id", "test");
        return new OntologyGraph(config, false, null, true, null);
    }

    private static OntologyNode entity(OntologyGraph graph, String uri) {
        OntologyNode entity = new OntologyNode();
        entity.uri = uri;
        entity.types.add(OntologyNode.NodeType.CLASS);
        graph.nodes.put(uri, entity);
        return entity;
    }

    private static List<String> values(OntologyNode entity, String predicate) {
        List<String> result = new ArrayList<>();
        List<PropertyValue> properties = entity.properties.getPropertyValues(predicate);
        if (properties != null) {
            for (PropertyValue property : properties) {
                appendValues(property, result);
            }
        }
        return result;
    }

    private static void appendValues(PropertyValue property, List<String> values) {
        if (property instanceof PropertyValueList) {
            for (PropertyValue nested : ((PropertyValueList) property).getPropertyValues()) {
                appendValues(nested, values);
            }
        } else if (property instanceof PropertyValueLiteral) {
            values.add(((PropertyValueLiteral) property).getValue());
        }
    }
}
