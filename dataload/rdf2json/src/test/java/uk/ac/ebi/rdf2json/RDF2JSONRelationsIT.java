package uk.ac.ebi.rdf2json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Contract of RDF2JSON's packaged executable for named relations and hierarchy fields. */
public class RDF2JSONRelationsIT {
    private static final String BASE = "https://example.org/relations#";
    private static final String EQUIVALENT_CLASS = "http://www.w3.org/2002/07/owl#equivalentClass";
    private static final String EQUIVALENT_PROPERTY = "http://www.w3.org/2002/07/owl#equivalentProperty";
    private static final String DISJOINT_WITH = "http://www.w3.org/2002/07/owl#disjointWith";
    private static final String PROPERTY_DISJOINT_WITH = "http://www.w3.org/2002/07/owl#propertyDisjointWith";
    private static final String DIFFERENT_FROM = "http://www.w3.org/2002/07/owl#differentFrom";
    private static final String INVERSE_OF = "http://www.w3.org/2002/07/owl#inverseOf";
    private static final String SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void namedSubclassChainHasImmediateAndTransitiveHierarchyFields() throws Exception {
        JsonObject ontology = run("hierarchy");
        JsonObject leaf = node(ontology.getAsJsonArray("classes"), "Leaf");
        JsonObject middle = node(ontology.getAsJsonArray("classes"), "Middle");
        JsonObject root = node(ontology.getAsJsonArray("classes"), "Root");

        assertEquals(Set.of(iri("Middle")), strings(leaf.getAsJsonArray("directParent")));
        assertEquals(Set.of(iri("Middle"), iri("Root")), strings(leaf.getAsJsonArray("directAncestor")));
        assertEquals(Set.of(iri("Middle")), strings(leaf.getAsJsonArray("hierarchicalParent")));
        assertEquals(Set.of(iri("Middle"), iri("Root")), strings(leaf.getAsJsonArray("hierarchicalAncestor")));
        assertTrue(leaf.get("hasDirectParents").getAsBoolean());
        assertFalse(leaf.get("hasDirectChildren").getAsBoolean());
        assertEquals(1, middle.get("numDescendants").getAsInt());
        assertEquals(2, root.get("numDescendants").getAsInt());
        assertTrue(root.get("hasDirectChildren").getAsBoolean());
    }

    @Test
    public void configuredRestrictionAddsHierarchicalButNotDirectParent() throws Exception {
        JsonObject ontology = run("hierarchy");
        JsonObject part = node(ontology.getAsJsonArray("classes"), "Part");
        JsonObject whole = node(ontology.getAsJsonArray("classes"), "Whole");

        assertEquals(Set.of(), strings(part.getAsJsonArray("directAncestor")));
        assertEquals(Set.of(iri("Whole")), strings(part.getAsJsonArray("hierarchicalAncestor")));
        assertFalse(part.get("hasDirectParents").getAsBoolean());
        assertTrue(part.get("hasHierarchicalParents").getAsBoolean());
        assertEquals(0, whole.get("numDescendants").getAsInt());
        assertEquals(1, whole.get("numHierarchicalDescendants").getAsInt());

        JsonObject relation = part.getAsJsonArray("relatedTo").get(0).getAsJsonObject();
        assertEquals(iri("partOf"), relation.get("property").getAsString());
        assertEquals(iri("Whole"), relation.get("value").getAsString());
        JsonObject parent = part.getAsJsonArray("hierarchicalParent").get(0).getAsJsonObject();
        assertEquals(iri("Whole"), parent.get("value").getAsString());
        assertEquals(iri("partOf"), parent.getAsJsonArray("axioms").get(0).getAsJsonObject()
                .get("childRelationToParent").getAsString());
    }

    @Test
    public void multiParentClassAndInstanceHaveCompleteDirectAncestryWithoutDuplicateDescendants() throws Exception {
        JsonObject ontology = run("mixed-hierarchy");
        JsonObject leaf = node(ontology.getAsJsonArray("classes"), "Leaf");
        JsonObject top = node(ontology.getAsJsonArray("classes"), "Top");
        JsonObject rootA = node(ontology.getAsJsonArray("classes"), "RootA");
        JsonObject rootB = node(ontology.getAsJsonArray("classes"), "RootB");
        JsonObject instance = node(ontology.getAsJsonArray("individuals"), "Instance");
        JsonObject subProperty = node(ontology.getAsJsonArray("properties"), "SubProperty");
        JsonObject topProperty = node(ontology.getAsJsonArray("properties"), "TopProperty");

        assertEquals(Set.of(iri("Middle")), strings(leaf.getAsJsonArray("directParent")));
        assertEquals(Set.of(iri("Middle"), iri("RootA"), iri("RootB"), iri("Top")),
                strings(leaf.getAsJsonArray("directAncestor")));
        assertEquals(Set.of(iri("Leaf")), strings(instance.getAsJsonArray("directParent")));
        assertEquals(Set.of(iri("Leaf"), iri("Middle"), iri("RootA"), iri("RootB"), iri("Top")),
                strings(instance.getAsJsonArray("directAncestor")));
        assertEquals(5, top.get("numDescendants").getAsInt());
        assertEquals(3, rootA.get("numDescendants").getAsInt());
        assertEquals(3, rootB.get("numDescendants").getAsInt());

        assertEquals(Set.of(iri("TopProperty")), strings(subProperty.getAsJsonArray("directParent")));
        assertEquals(Set.of(iri("TopProperty")), strings(subProperty.getAsJsonArray("directAncestor")));
        assertTrue(subProperty.get("hasDirectParents").getAsBoolean());
        assertTrue(topProperty.get("hasDirectChildren").getAsBoolean());
        assertEquals(1, topProperty.get("numDescendants").getAsInt());
    }

    @Test
    public void configuredIndividualHierarchyPreservesInverseEdgeMetadata() throws Exception {
        JsonObject ontology = run("mixed-hierarchy");
        JsonObject child = node(ontology.getAsJsonArray("individuals"), "ChildCohort");
        JsonObject parent = node(ontology.getAsJsonArray("individuals"), "ParentCohort");

        assertEquals(Set.of(iri("Cohort")), strings(child.getAsJsonArray("directAncestor")));
        assertEquals(Set.of(iri("ParentCohort")), strings(child.getAsJsonArray("hierarchicalAncestor")));
        assertEquals(iri("isPartOf"), child.getAsJsonObject("hierarchicalProperty").get("value").getAsString());
        assertTrue(child.get("hasHierarchicalParents").getAsBoolean());
        assertTrue(parent.get("hasHierarchicalChildren").getAsBoolean());
        assertEquals(1, parent.get("numHierarchicalDescendants").getAsInt());

        JsonObject edge = child.getAsJsonArray("hierarchicalParent").get(0).getAsJsonObject();
        assertEquals(iri("ParentCohort"), edge.get("value").getAsString());
        JsonObject axiom = edge.getAsJsonArray("axioms").get(0).getAsJsonObject();
        assertEquals(iri("isPartOf"), axiom.get("childRelationToParent").getAsString());
        assertEquals(iri("hasPart"), axiom.get("parentRelationToChild").getAsString());
    }

    @Test
    public void oneWayEquivalentClassAssertionIsAvailableFromBothClasses() throws Exception {
        JsonObject ontology = run("owl-relations");
        JsonObject alpha = node(ontology.getAsJsonArray("classes"), "Alpha");
        JsonObject beta = node(ontology.getAsJsonArray("classes"), "Beta");

        assertEquals(iri("Beta"), alpha.get(EQUIVALENT_CLASS).getAsString());
        assertEquals(iri("Alpha"), beta.get(EQUIVALENT_CLASS).getAsString());
    }

    @Test
    public void anonymousIntersectionProducesNamedRelationsInPackagedOutput() throws Exception {
        JsonObject ontology = run("owl-relations");
        JsonObject child = node(ontology.getAsJsonArray("classes"), "IntersectChild");
        Set<String> targets = new HashSet<>();
        for (JsonElement item : child.getAsJsonArray("relatedTo")) {
            JsonObject relation = item.getAsJsonObject();
            assertEquals(SUBCLASS_OF, relation.get("property").getAsString());
            targets.add(relation.get("value").getAsString());
        }

        assertEquals(Set.of(iri("IntersectA"), iri("IntersectB")), targets);
        assertEquals(Set.of(iri("IntersectA"), iri("IntersectB")),
                strings(child.getAsJsonArray("hierarchicalAncestor")));
    }

    @Test
    public void oneWayEquivalentPropertyAssertionIsAvailableFromBothProperties() throws Exception {
        JsonObject ontology = run("owl-relations");
        JsonObject first = node(ontology.getAsJsonArray("properties"), "EquivalentPropertyA");
        JsonObject second = node(ontology.getAsJsonArray("properties"), "EquivalentPropertyB");

        assertEquals(iri("EquivalentPropertyB"), first.get(EQUIVALENT_PROPERTY).getAsString());
        assertEquals(iri("EquivalentPropertyA"), second.get(EQUIVALENT_PROPERTY).getAsString());
    }

    @Test
    public void allDisjointClassMembersArePairwiseDisjoint() throws Exception {
        JsonObject ontology = run("owl-relations");
        JsonObject beta = node(ontology.getAsJsonArray("classes"), "Beta");
        JsonObject gamma = node(ontology.getAsJsonArray("classes"), "Gamma");
        JsonObject delta = node(ontology.getAsJsonArray("classes"), "Delta");

        assertEquals(Set.of(iri("Gamma"), iri("Delta")), strings(beta.getAsJsonArray(DISJOINT_WITH)));
        assertEquals(Set.of(iri("Beta"), iri("Delta")), strings(gamma.getAsJsonArray(DISJOINT_WITH)));
        assertEquals(Set.of(iri("Beta"), iri("Gamma")), strings(delta.getAsJsonArray(DISJOINT_WITH)));
    }

    @Test
    public void allDisjointPropertyMembersArePairwiseDisjoint() throws Exception {
        JsonObject ontology = run("owl-relations");
        JsonObject first = node(ontology.getAsJsonArray("properties"), "DisjointPropertyA");
        JsonObject second = node(ontology.getAsJsonArray("properties"), "DisjointPropertyB");
        JsonObject third = node(ontology.getAsJsonArray("properties"), "DisjointPropertyC");

        assertEquals(Set.of(iri("DisjointPropertyB"), iri("DisjointPropertyC")),
                strings(first.getAsJsonArray(PROPERTY_DISJOINT_WITH)));
        assertEquals(Set.of(iri("DisjointPropertyA"), iri("DisjointPropertyC")),
                strings(second.getAsJsonArray(PROPERTY_DISJOINT_WITH)));
        assertEquals(Set.of(iri("DisjointPropertyA"), iri("DisjointPropertyB")),
                strings(third.getAsJsonArray(PROPERTY_DISJOINT_WITH)));
    }

    @Test
    public void allDifferentIndividualsArePairwiseDifferent() throws Exception {
        JsonObject ontology = run("owl-relations");
        JsonObject first = node(ontology.getAsJsonArray("individuals"), "PersonA");
        JsonObject second = node(ontology.getAsJsonArray("individuals"), "PersonB");
        JsonObject third = node(ontology.getAsJsonArray("individuals"), "PersonC");

        assertEquals(Set.of(iri("PersonB"), iri("PersonC")), strings(first.getAsJsonArray(DIFFERENT_FROM)));
        assertEquals(Set.of(iri("PersonA"), iri("PersonC")), strings(second.getAsJsonArray(DIFFERENT_FROM)));
        assertEquals(Set.of(iri("PersonA"), iri("PersonB")), strings(third.getAsJsonArray(DIFFERENT_FROM)));
    }

    @Test
    public void oneWayInversePropertyAssertionIsAvailableFromBothProperties() throws Exception {
        JsonObject ontology = run("owl-relations");
        JsonObject hasPart = node(ontology.getAsJsonArray("properties"), "hasPart");
        JsonObject partOf = node(ontology.getAsJsonArray("properties"), "partOf");

        assertEquals(iri("partOf"), hasPart.get(INVERSE_OF).getAsString());
        assertEquals(iri("hasPart"), partOf.get(INVERSE_OF).getAsString());
    }

    private JsonObject run(String fixture) throws Exception {
        Path resources = Path.of(getClass().getResource("/relations/" + fixture + ".ttl").toURI()).getParent();
        Path output = temporaryFolder.newFile(fixture + ".json").toPath();
        Files.delete(output);
        Path log = temporaryFolder.newFile(fixture + ".log").toPath();

        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("rdf2json.jar"),
                "--config", resources.resolve(fixture + ".json").toString(),
                "--ontologyIds", "relations",
                "--output", output.toString(),
                "--basePath", resources.toString(),
                "--loadLocalFiles", "--noDates"));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertTrue("RDF2JSON timed out", completed);
        String logs = Files.readString(log);
        assertEquals("RDF2JSON failed:\n" + logs, 0, process.exitValue());

        try (Reader reader = Files.newBufferedReader(output)) {
            JsonArray ontologies = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("ontologies");
            assertEquals(1, ontologies.size());
            JsonObject ontology = ontologies.get(0).getAsJsonObject();
            assertEquals("relations", ontology.get("ontologyId").getAsString());
            return ontology;
        }
    }

    private static JsonObject node(JsonArray nodes, String shortName) {
        for (JsonElement item : nodes) {
            JsonObject node = item.getAsJsonObject();
            if (iri(shortName).equals(node.get("iri").getAsString())) return node;
        }
        throw new AssertionError("Missing node " + iri(shortName));
    }

    private static Set<String> strings(JsonArray values) {
        assertNotNull(values);
        Set<String> result = new HashSet<>();
        for (JsonElement value : values) result.add(value.getAsString());
        return result;
    }

    private static String iri(String shortName) {
        return BASE + shortName;
    }
}
