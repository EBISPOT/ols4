package uk.ac.ebi.rdf2json;

import org.junit.Test;
import uk.ac.ebi.rdf2json.annotators.ShortFormAnnotator;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ShortFormAnnotatorTest {

    private static final String EFO_ID_BASE_URI = "http://www.ebi.ac.uk/efo/EFO_";
    private static final String EFO_NAMESPACE_BASE_URI = "http://www.ebi.ac.uk/efo/";

    @Test
    public void overlappingBaseUrisKeepPrefixedIdsAndPrefixBareNamespaceEntities() throws IOException {
        OntologyGraph graph = graphWithBaseUris("EFO", List.of(EFO_ID_BASE_URI, EFO_NAMESPACE_BASE_URI));
        OntologyNode term = addNode(graph, "http://www.ebi.ac.uk/efo/EFO_0000001", OntologyNode.NodeType.CLASS);
        OntologyNode property = addNode(graph, "http://www.ebi.ac.uk/efo/reason_for_obsolescence", OntologyNode.NodeType.PROPERTY);
        OntologyNode foreign = addNode(graph, "http://purl.obolibrary.org/obo/UBERON_0002107", OntologyNode.NodeType.CLASS);

        ShortFormAnnotator.annotateShortForms(graph);

        assertEquals("EFO_0000001", literalValue(term, "shortForm"));
        assertEquals("EFO:0000001", literalValue(term, "curie"));
        assertEquals("EFO_reason_for_obsolescence", literalValue(property, "shortForm"));
        assertEquals("UBERON_0002107", literalValue(foreign, "shortForm"));
        assertEquals("UBERON:0002107", literalValue(foreign, "curie"));
    }

    @Test
    public void prefixIsNotDuplicatedWhenLocalPartAlreadyCarriesIt() throws IOException {
        OntologyGraph graph = graphWithBaseUris("EFO", List.of(EFO_NAMESPACE_BASE_URI));
        OntologyNode term = addNode(graph, "http://www.ebi.ac.uk/efo/EFO_0000001", OntologyNode.NodeType.CLASS);

        ShortFormAnnotator.annotateShortForms(graph);

        assertEquals("EFO_0000001", literalValue(term, "shortForm"));
        assertEquals("EFO:0000001", literalValue(term, "curie"));
    }

    @Test
    public void mostSpecificBaseUriWinsWhenBaseUrisOverlap() throws IOException {
        OntologyGraph graph = graphWithBaseUris("TEST", List.of("http://example.org/test/", "http://example.org/test/ID_"));
        OntologyNode term = addNode(graph, "http://example.org/test/ID_42", OntologyNode.NodeType.CLASS);
        OntologyNode property = addNode(graph, "http://example.org/test/some_property", OntologyNode.NodeType.PROPERTY);

        ShortFormAnnotator.annotateShortForms(graph);

        assertEquals("TEST_42", literalValue(term, "shortForm"));
        assertEquals("TEST:42", literalValue(term, "curie"));
        assertEquals("TEST_some_property", literalValue(property, "shortForm"));
    }

    @Test
    public void singleBaseUriBehaviourIsUnchanged() throws IOException {
        OntologyGraph graph = graphWithBaseUris("EFO", List.of(EFO_ID_BASE_URI));
        OntologyNode term = addNode(graph, "http://www.ebi.ac.uk/efo/EFO_0000001", OntologyNode.NodeType.CLASS);
        OntologyNode property = addNode(graph, "http://www.ebi.ac.uk/efo/reason_for_obsolescence", OntologyNode.NodeType.PROPERTY);

        ShortFormAnnotator.annotateShortForms(graph);

        assertEquals("EFO_0000001", literalValue(term, "shortForm"));
        assertEquals("EFO:0000001", literalValue(term, "curie"));
        assertEquals("reason_for_obsolescence", literalValue(property, "shortForm"));
    }

    private OntologyGraph graphWithBaseUris(String preferredPrefix, List<String> baseUris) throws IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("id", preferredPrefix.toLowerCase());
        config.put("preferredPrefix", preferredPrefix);
        config.put("base_uri", baseUris);
        return new OntologyGraph(config, false, null, true, null);
    }

    private OntologyNode addNode(OntologyGraph graph, String uri, OntologyNode.NodeType type) {
        OntologyNode node = new OntologyNode();
        node.uri = uri;
        node.types.add(type);
        graph.nodes.put(uri, node);
        return node;
    }

    private String literalValue(OntologyNode node, String predicate) {
        List<PropertyValue> values = node.properties.getPropertyValues(predicate);
        assertEquals(1, values.size());
        return ((PropertyValueLiteral) values.get(0)).getValue();
    }
}
