package uk.ac.ebi.rdf2json;

import org.junit.Test;
import uk.ac.ebi.rdf2json.annotators.OntologyHeaderAnnotator;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueBNode;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class OntologyHeaderAnnotatorTest {
    private static final String FOAF_HOMEPAGE = "http://xmlns.com/foaf/0.1/homepage";
    private static final String DOAP_BUG_DATABASE = "http://usefulinc.com/ns/doap#bug-database";
    private static final String DOAP_MAILING_LIST = "http://usefulinc.com/ns/doap#mailing-list";
    private static final String SCHEMA_LOGO = "https://schema.org/logo";
    private static final String ANY_URI = "http://www.w3.org/2001/XMLSchema#anyURI";

    @Test
    public void iriValuedHeaderAnnotationsPopulateMissingConfigFields() throws IOException {
        OntologyGraph graph = graphWithConfig(Map.of());
        header(graph, FOAF_HOMEPAGE, PropertyValueURI.fromUri("https://example.org/home"));
        header(graph, DOAP_BUG_DATABASE, PropertyValueURI.fromUri("https://example.org/issues"));
        header(graph, DOAP_MAILING_LIST, PropertyValueURI.fromUri("mailto:list@example.org"));
        header(graph, SCHEMA_LOGO, PropertyValueURI.fromUri("https://example.org/logo.png"));

        OntologyHeaderAnnotator.annotateOntologyHeader(graph);

        assertEquals(List.of("https://example.org/home"), uris(graph, "homepage"));
        assertEquals(List.of("https://example.org/issues"), uris(graph, "tracker"));
        assertEquals(List.of("mailto:list@example.org"), uris(graph, "mailingList"));
        assertEquals(List.of("https://example.org/logo.png"), uris(graph, "logo"));
    }

    @Test
    public void literalValuedHeaderAnnotationsAreTrimmedPlainStrings() throws IOException {
        OntologyGraph graph = graphWithConfig(Map.of());
        header(graph, FOAF_HOMEPAGE, new PropertyValueLiteral(" https://example.org/home ", ANY_URI, ""));
        header(graph, DOAP_MAILING_LIST, new PropertyValueLiteral("list@example.org", "http://www.w3.org/2001/XMLSchema#string", ""));

        OntologyHeaderAnnotator.annotateOntologyHeader(graph);

        PropertyValueLiteral homepage = (PropertyValueLiteral) graph.ontologyNode.properties.getPropertyValue("homepage");
        assertEquals("https://example.org/home", homepage.getValue());
        assertEquals("http://www.w3.org/2001/XMLSchema#string", homepage.getDatatype());
        assertEquals("list@example.org",
                ((PropertyValueLiteral) graph.ontologyNode.properties.getPropertyValue("mailingList")).getValue());
    }

    @Test
    public void configValuesWinOverHeaderAnnotations() throws IOException {
        OntologyGraph graph = graphWithConfig(Map.of(
                "homepage", "https://config.example.org/home",
                "tracker", "https://config.example.org/issues",
                "mailing_list", "config-list@example.org",
                "depicted_by", "https://config.example.org/logo.png"));
        header(graph, FOAF_HOMEPAGE, PropertyValueURI.fromUri("https://example.org/home"));
        header(graph, DOAP_BUG_DATABASE, PropertyValueURI.fromUri("https://example.org/issues"));
        header(graph, DOAP_MAILING_LIST, PropertyValueURI.fromUri("mailto:list@example.org"));
        header(graph, SCHEMA_LOGO, PropertyValueURI.fromUri("https://example.org/logo.png"));

        OntologyHeaderAnnotator.annotateOntologyHeader(graph);

        for(String property : List.of("homepage", "tracker", "mailingList", "logo")) {
            assertFalse(property, graph.ontologyNode.properties.hasProperty(property));
        }
    }

    @Test
    public void camelCaseMailingListConfigKeyAlsoWins() throws IOException {
        OntologyGraph graph = graphWithConfig(Map.of("mailingList", "config-list@example.org"));
        header(graph, DOAP_MAILING_LIST, PropertyValueURI.fromUri("mailto:list@example.org"));

        OntologyHeaderAnnotator.annotateOntologyHeader(graph);

        assertFalse(graph.ontologyNode.properties.hasProperty("mailingList"));
    }

    @Test
    public void onlyOneValueIsTakenAndBlankOrBNodeValuesAreSkipped() throws IOException {
        OntologyGraph graph = graphWithConfig(Map.of());
        header(graph, FOAF_HOMEPAGE, new PropertyValueBNode("_:b0"));
        header(graph, FOAF_HOMEPAGE, PropertyValueLiteral.fromString("  "));
        header(graph, FOAF_HOMEPAGE, PropertyValueURI.fromUri("https://example.org/first"));
        header(graph, FOAF_HOMEPAGE, PropertyValueURI.fromUri("https://example.org/second"));
        header(graph, DOAP_BUG_DATABASE, PropertyValueLiteral.fromString(""));

        OntologyHeaderAnnotator.annotateOntologyHeader(graph);

        assertEquals(List.of("https://example.org/first"), uris(graph, "homepage"));
        assertNull(graph.ontologyNode.properties.getPropertyValues("tracker"));
    }

    private OntologyGraph graphWithConfig(Map<String, Object> extraConfig) throws IOException {
        Map<String, Object> config = new HashMap<>(extraConfig);
        config.put("id", "test");
        OntologyGraph graph = new OntologyGraph(config, false, null, true, null);
        graph.ontologyNode = new OntologyNode();
        graph.ontologyNode.uri = "http://example.org/test.owl";
        graph.ontologyNode.types.add(OntologyNode.NodeType.ONTOLOGY);
        graph.nodes.put(graph.ontologyNode.uri, graph.ontologyNode);
        return graph;
    }

    private void header(OntologyGraph graph, String predicate, PropertyValue value) {
        graph.ontologyNode.properties.addProperty(predicate, value);
    }

    private List<String> uris(OntologyGraph graph, String predicate) {
        return graph.ontologyNode.properties.getPropertyValues(predicate).stream()
                .map(value -> ((PropertyValueURI) value).getUri())
                .toList();
    }
}
