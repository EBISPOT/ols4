package uk.ac.ebi.rdf2json.annotators;

import org.junit.Test;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SearchableAnnotationValuesAnnotatorTest {
    @Test
    public void runtimeTimestampsStandardNamespacesAndUriValuesAreExcluded() {
        OntologyNode node = new OntologyNode();
        node.uri = "https://example.org/Term";
        node.types.add(OntologyNode.NodeType.CLASS);
        for (String predicate : List.of("loaded", "updated", "sourceFileTimestamp",
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#value",
                "http://www.w3.org/2000/01/rdf-schema#label",
                "http://www.w3.org/2002/07/owl#versionInfo"))
            node.properties.addProperty(predicate, PropertyValueLiteral.fromString("Excluded: " + predicate));
        node.properties.addProperty("https://example.org/uri", PropertyValueURI.fromUri("https://example.org/Target"));
        node.properties.addProperty("https://example.org/note", PropertyValueLiteral.fromString("Find me"));

        SearchableAnnotationValuesAnnotator.annotateSearchableAnnotationValues(node);

        var values = node.properties.getPropertyValues("searchableAnnotationValues");
        assertEquals(1, values.size());
        assertEquals("Find me", ((PropertyValueLiteral) values.get(0)).getValue());
    }

    @Test
    public void anonymousAxiomNodesAreNotSearchableEntities() {
        OntologyNode node = new OntologyNode();
        node.types.add(OntologyNode.NodeType.AXIOM);
        node.properties.addProperty("https://example.org/note", PropertyValueLiteral.fromString("Evidence"));
        SearchableAnnotationValuesAnnotator.annotateSearchableAnnotationValues(node);
        assertNull(node.properties.getPropertyValues("searchableAnnotationValues"));
    }
}
