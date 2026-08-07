package uk.ac.ebi.rdf2json;

import org.junit.Test;
import uk.ac.ebi.rdf2json.annotators.ConfigurablePropertyAnnotator;
import uk.ac.ebi.rdf2json.annotators.DefinitionAnnotator;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueList;
import uk.ac.ebi.rdf2json.properties.PropertyValueLiteral;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DefinitionAnnotatorTest {
    private static final String COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";
    private static final String IAO_DEFINITION = "http://purl.obolibrary.org/obo/IAO_0000115";

    @Test
    public void configuredDefinitionPropertiesOverrideDefaults() throws IOException {
        OntologyGraph graph = graphWithDefinitionProperties(List.of(IAO_DEFINITION));
        OntologyNode entity = addEntityWithDefinitionAndComment(graph);

        DefinitionAnnotator.annotateDefinitions(graph);
        ConfigurablePropertyAnnotator.annotateConfigurableProperties(graph);

        assertEquals(List.of("Configured definition"), literalValues(entity, "definition"));
        assertEquals(List.of(IAO_DEFINITION), literalValues(entity, "definitionProperty"));
        assertTrue(entity.properties.hasProperty(COMMENT));
    }

    @Test
    public void missingDefinitionPropertiesUseDefaults() throws IOException {
        OntologyGraph graph = graphWithDefinitionProperties(null);
        OntologyNode entity = addEntityWithDefinitionAndComment(graph);

        DefinitionAnnotator.annotateDefinitions(graph);
        ConfigurablePropertyAnnotator.annotateConfigurableProperties(graph);

        assertEquals(List.of("Configured definition", "Comment"), literalValues(entity, "definition"));
        assertEquals(List.of(IAO_DEFINITION, COMMENT), literalValues(entity, "definitionProperty"));
    }

    @Test
    public void emptyDefinitionPropertiesDisableDefinitionCollation() throws IOException {
        OntologyGraph graph = graphWithDefinitionProperties(List.of());
        OntologyNode entity = addEntityWithDefinitionAndComment(graph);

        DefinitionAnnotator.annotateDefinitions(graph);
        ConfigurablePropertyAnnotator.annotateConfigurableProperties(graph);

        assertNull(entity.properties.getPropertyValues("definition"));
        assertNull(entity.properties.getPropertyValues("definitionProperty"));
        assertTrue(entity.properties.hasProperty(COMMENT));
        assertTrue(entity.properties.hasProperty(IAO_DEFINITION));
        assertFalse(DefinitionAnnotator.getDefinitionProperties(graph).contains(COMMENT));
    }

    private OntologyGraph graphWithDefinitionProperties(Collection<String> definitionProperties) throws IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("id", "test");
        if(definitionProperties != null) {
            config.put("definition_property", definitionProperties);
        }
        return new OntologyGraph(config, false, null, true, null);
    }

    private OntologyNode addEntityWithDefinitionAndComment(OntologyGraph graph) {
        OntologyNode entity = new OntologyNode();
        entity.uri = "http://example.org/entity";
        entity.types.add(OntologyNode.NodeType.CLASS);
        entity.properties.addProperty(IAO_DEFINITION, PropertyValueLiteral.fromString("Configured definition"));
        entity.properties.addProperty(COMMENT, PropertyValueLiteral.fromString("Comment"));
        graph.nodes.put(entity.uri, entity);
        return entity;
    }

    private List<String> literalValues(OntologyNode entity, String predicate) {
        List<PropertyValue> values = entity.properties.getPropertyValues(predicate);
        List<String> result = new ArrayList<>();
        if(values == null) {
            return result;
        }
        for(PropertyValue value : values) {
            addLiteralValues(value, result);
        }
        return result;
    }

    private void addLiteralValues(PropertyValue value, List<String> result) {
        if(value instanceof PropertyValueList) {
            for(PropertyValue nested : ((PropertyValueList) value).getPropertyValues()) {
                addLiteralValues(nested, result);
            }
        } else if(value instanceof PropertyValueLiteral) {
            result.add(((PropertyValueLiteral) value).getValue());
        }
    }
}
