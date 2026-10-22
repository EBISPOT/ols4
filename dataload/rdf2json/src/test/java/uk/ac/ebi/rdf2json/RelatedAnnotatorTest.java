package uk.ac.ebi.rdf2json;

import org.junit.Test;
import uk.ac.ebi.rdf2json.annotators.RelatedAnnotator;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueBNode;
import uk.ac.ebi.rdf2json.properties.PropertyValueList;
import uk.ac.ebi.rdf2json.properties.PropertyValueRelated;
import uk.ac.ebi.rdf2json.properties.PropertyValueURI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Public annotator contract for relationships inferred from anonymous OWL parents. */
public class RelatedAnnotatorTest {
    private static final String BASE = "https://example.org/related#";
    private static final String SUB_CLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String ONE_OF = "http://www.w3.org/2002/07/owl#oneOf";
    private static final String INTERSECTION_OF = "http://www.w3.org/2002/07/owl#intersectionOf";
    private static final String ON_PROPERTY = "http://www.w3.org/2002/07/owl#onProperty";
    private static final String SOME_VALUES_FROM = "http://www.w3.org/2002/07/owl#someValuesFrom";
    private static final String RDF_FIRST = "http://www.w3.org/1999/02/22-rdf-syntax-ns#first";
    private static final String RDF_REST = "http://www.w3.org/1999/02/22-rdf-syntax-ns#rest";
    private static final String RDF_NIL = "http://www.w3.org/1999/02/22-rdf-syntax-ns#nil";
    private static final String RELATED_TO = "relatedTo";

    @Test
    public void oneOfSubclassRelatesClassToEveryNamedIndividual() throws IOException {
        OntologyGraph graph = graph();
        OntologyNode chosenClass = namedNode(graph, "ChosenClass", OntologyNode.NodeType.CLASS);
        namedNode(graph, "FirstIndividual", OntologyNode.NodeType.INDIVIDUAL);
        namedNode(graph, "SecondIndividual", OntologyNode.NodeType.INDIVIDUAL);
        OntologyNode expression = blankNode(graph, "choice");
        chosenClass.properties.addProperty(SUB_CLASS_OF, new PropertyValueBNode("choice"));
        expression.properties.addProperty(ONE_OF, rdfList(graph, "choices",
                new PropertyValueURI(iri("FirstIndividual")),
                new PropertyValueURI(iri("SecondIndividual"))));

        new RelatedAnnotator().annotateRelated(graph);

        assertRelatedTargets(chosenClass, SUB_CLASS_OF,
                Set.of(iri("FirstIndividual"), iri("SecondIndividual")));
        assertNull(graph.nodes.get(iri("FirstIndividual")).properties.getPropertyValues("relatedFrom"));
    }

    @Test
    public void intersectionSubclassRelatesClassToNamedMembersOnly() throws IOException {
        OntologyGraph graph = graph();
        OntologyNode child = namedNode(graph, "IntersectionChild", OntologyNode.NodeType.CLASS);
        namedNode(graph, "FirstParent", OntologyNode.NodeType.CLASS);
        namedNode(graph, "SecondParent", OntologyNode.NodeType.CLASS);
        blankNode(graph, "anonymousMember");
        OntologyNode expression = blankNode(graph, "intersection");
        child.properties.addProperty(SUB_CLASS_OF, new PropertyValueBNode("intersection"));
        expression.properties.addProperty(INTERSECTION_OF, rdfList(graph, "intersectionMembers",
                new PropertyValueURI(iri("FirstParent")), new PropertyValueBNode("anonymousMember"),
                new PropertyValueURI(iri("SecondParent"))));

        new RelatedAnnotator().annotateRelated(graph);

        assertRelatedTargets(child, SUB_CLASS_OF, Set.of(iri("FirstParent"), iri("SecondParent")));
    }

    @Test
    public void someValuesFromOneOfRelatesClassToEachNamedIndividual() throws IOException {
        OntologyGraph graph = graph();
        OntologyNode child = namedNode(graph, "RestrictedChild", OntologyNode.NodeType.CLASS);
        namedNode(graph, "OneFiller", OntologyNode.NodeType.INDIVIDUAL);
        namedNode(graph, "AnotherFiller", OntologyNode.NodeType.INDIVIDUAL);
        OntologyNode restriction = blankNode(graph, "oneOfRestriction");
        OntologyNode expression = blankNode(graph, "oneOfFiller");
        child.properties.addProperty(SUB_CLASS_OF, new PropertyValueBNode("oneOfRestriction"));
        restriction.properties.addProperty(ON_PROPERTY, new PropertyValueURI(iri("hasMember")));
        restriction.properties.addProperty(SOME_VALUES_FROM, new PropertyValueBNode("oneOfFiller"));
        expression.properties.addProperty(ONE_OF, rdfList(graph, "oneOfFillerMembers",
                new PropertyValueURI(iri("OneFiller")), new PropertyValueURI(iri("AnotherFiller"))));

        new RelatedAnnotator().annotateRelated(graph);

        assertRelatedTargets(child, iri("hasMember"), Set.of(iri("OneFiller"), iri("AnotherFiller")));
    }

    @Test
    public void someValuesFromIntersectionRelatesClassToNamedMembersOnly() throws IOException {
        OntologyGraph graph = graph();
        OntologyNode child = namedNode(graph, "ConjunctiveChild", OntologyNode.NodeType.CLASS);
        namedNode(graph, "NamedFiller", OntologyNode.NodeType.CLASS);
        blankNode(graph, "anonymousFiller");
        OntologyNode restriction = blankNode(graph, "intersectionRestriction");
        OntologyNode expression = blankNode(graph, "intersectionFiller");
        child.properties.addProperty(SUB_CLASS_OF, new PropertyValueBNode("intersectionRestriction"));
        restriction.properties.addProperty(ON_PROPERTY, new PropertyValueURI(iri("hasPart")));
        restriction.properties.addProperty(SOME_VALUES_FROM, new PropertyValueBNode("intersectionFiller"));
        expression.properties.addProperty(INTERSECTION_OF, rdfList(graph, "intersectionFillerMembers",
                new PropertyValueURI(iri("NamedFiller")), new PropertyValueBNode("anonymousFiller")));

        new RelatedAnnotator().annotateRelated(graph);

        assertRelatedTargets(child, iri("hasPart"), Set.of(iri("NamedFiller")));
    }

    private static OntologyGraph graph() throws IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("id", "related-test");
        return new OntologyGraph(config, false, null, true, null);
    }

    private static OntologyNode namedNode(OntologyGraph graph, String localName, OntologyNode.NodeType type) {
        OntologyNode node = new OntologyNode();
        node.uri = iri(localName);
        node.types.add(type);
        graph.nodes.put(node.uri, node);
        return node;
    }

    private static OntologyNode blankNode(OntologyGraph graph, String id) {
        OntologyNode node = new OntologyNode();
        graph.nodes.put(id, node);
        return node;
    }

    private static PropertyValueBNode rdfList(OntologyGraph graph, String id, PropertyValue... members) {
        for (int i = 0; i < members.length; i++) {
            String memberId = id + i;
            OntologyNode member = blankNode(graph, memberId);
            member.properties.addProperty(RDF_FIRST, members[i]);
            member.properties.addProperty(RDF_REST,
                    i + 1 == members.length ? new PropertyValueURI(RDF_NIL) : new PropertyValueBNode(id + (i + 1)));
        }
        return new PropertyValueBNode(id + "0");
    }

    private static void assertRelatedTargets(OntologyNode node, String property, Set<String> expectedTargets) {
        List<PropertyValue> values = node.properties.getPropertyValues(RELATED_TO);
        assertNotNull("No relatedTo values on " + node.uri, values);
        Set<String> targets = new HashSet<>();
        List<PropertyValueRelated> related = new ArrayList<>();
        for (PropertyValue value : values) {
            assertTrue(value instanceof PropertyValueList);
            for (PropertyValue nested : ((PropertyValueList) value).getPropertyValues()) {
                assertTrue(nested instanceof PropertyValueRelated);
                related.add((PropertyValueRelated) nested);
            }
        }
        for (PropertyValueRelated relation : related) {
            assertEquals(property, relation.getProperty());
            targets.add(relation.getFiller().uri);
        }
        assertEquals(expectedTargets.size(), related.size());
        assertEquals(expectedTargets, targets);
    }

    private static String iri(String localName) {
        return BASE + localName;
    }
}
