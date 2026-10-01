package uk.ac.ebi.rdf2json;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Small, local fixtures through the packaged executable; no downstream transforms. */
public class RDF2JSONVariantsIT {
    private static final String BASE = "https://example.org/variants#";
    private static final String OWL = "http://www.w3.org/2002/07/owl#";
    private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String OIO = "http://www.geneontology.org/formats/oboInOwl#";
    private static final String PART_OF = "http://purl.obolibrary.org/obo/BFO_0000050";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void overlappingHierarchyPathsCountEachDescendantOnce() throws Exception {
        JsonObject ontology = run("hierarchy", Map.of());
        JsonObject leaf = node(ontology, "classes", "Leaf");
        assertValues(leaf, "directAncestor", BASE + "Left", BASE + "Top");
        assertValues(leaf, "hierarchicalAncestor", BASE + "Left", BASE + "Right", BASE + "Top");
        // Includes the individual typed as Leaf. The two paths to Top do not double count it.
        assertEquals(4, node(ontology, "classes", "Top").get("numDescendants").getAsInt());
        assertEquals(3, node(ontology, "classes", "Top").get("numHierarchicalDescendants").getAsInt());
        assertEquals(1, node(ontology, "classes", "Right").get("numHierarchicalDescendants").getAsInt());
        assertValues(leaf, "hierarchicalProperty", RDFS + "subClassOf");
        assertValues(node(ontology, "individuals", "child"), "hierarchicalProperty", PART_OF);
    }

    @Test
    public void cyclesTerminateWithFiniteReachabilityAndMetrics() throws Exception {
        JsonObject ontology = run("hierarchy", Map.of());
        // Closure follows reachable edges, so a cycle reaches its starting node too.
        for (String name : List.of("CycleA", "CycleB", "CycleLeaf")) {
            JsonObject entity = node(ontology, "classes", name);
            assertValues(entity, "directAncestor", BASE + "CycleA", BASE + "CycleB");
            assertValues(entity, "hierarchicalAncestor", BASE + "CycleA", BASE + "CycleB");
            int expected = name.equals("CycleLeaf") ? 0 : 3;
            assertEquals(expected, entity.get("numDescendants").getAsInt());
            assertEquals(expected, entity.get("numHierarchicalDescendants").getAsInt());
        }
    }

    @Test
    public void unresolvedParentsAndSelfOrUnresolvedRestrictionFillersAreIgnored() throws Exception {
        JsonObject ontology = run("hierarchy", Map.of());
        for (String name : List.of("MissingParent", "SelfRestriction", "MissingRestriction")) {
            JsonObject entity = node(ontology, "classes", name);
            assertFalse(entity.has("directParent"));
            assertFalse(entity.has("hierarchicalParent"));
            assertFalse(entity.has("relatedTo"));
            assertValues(entity, "directAncestor");
            assertValues(entity, "hierarchicalAncestor");
            assertFalse(entity.get("hasHierarchicalParents").getAsBoolean());
        }
        JsonObject unlisted = node(ontology, "classes", "UnlistedRestriction");
        assertEquals(1, unlisted.getAsJsonArray("relatedTo").size());
        assertEquals(BASE + "Top", unlisted.getAsJsonArray("relatedTo").get(0).getAsJsonObject().get("value").getAsString());
        assertFalse(unlisted.has("hierarchicalParent"));
    }

    @Test
    public void individualHierarchyFiltersSelfUnresolvedAndLiteralTargets() throws Exception {
        JsonObject child = node(run("hierarchy", Map.of()), "individuals", "child");
        assertValues(child, "directParent", BASE + "Leaf");
        assertValues(child, "hierarchicalParent", BASE + "parent");
        assertValues(child, "hierarchicalAncestor", BASE + "parent");
        JsonObject edge = child.getAsJsonArray("hierarchicalParent").get(0).getAsJsonObject();
        JsonObject evidence = edge.getAsJsonArray("axioms").get(0).getAsJsonObject();
        assertEquals(PART_OF, evidence.get("childRelationToParent").getAsString());
        assertFalse(evidence.has("parentRelationToChild"));
    }

    @Test
    public void emptyHierarchyConfigDisablesDefaultPartOfButKeepsSubclassEdges() throws Exception {
        JsonObject ontology = run("hierarchy", Map.of("hierarchical_property", List.of()));
        assertValues(node(ontology, "classes", "Leaf"), "hierarchicalAncestor", BASE + "Left", BASE + "Top");
        JsonObject child = node(ontology, "individuals", "child");
        assertFalse(child.has("hierarchicalParent"));
        assertFalse(child.has("hierarchicalAncestor"));
        assertFalse(child.has("hierarchicalProperty"));
    }

    @Test
    public void propertiesHaveDirectHierarchyAndTopNodesDoNotSetParentFlags() throws Exception {
        JsonObject ontology = run("hierarchy", Map.of());
        JsonObject property = node(ontology, "properties", "SubProperty");
        assertValues(property, "directParent", BASE + "SuperProperty");
        assertValues(property, "directAncestor", BASE + "SuperProperty");
        assertFalse(property.has("hierarchicalParent"));
        assertFalse(property.has("hierarchicalAncestor"));
        for (String[] kind : List.of(new String[]{"classes", "TopChild", "Thing"},
                new String[]{"properties", "TopPropertyChild", "TopObjectProperty"})) {
            JsonObject child = node(ontology, kind[0], kind[1]);
            assertValues(child, "directParent", OWL + kind[2]);
            assertFalse(child.get("hasDirectParents").getAsBoolean());
            assertFalse(child.get("hasHierarchicalParents").getAsBoolean());
            JsonObject top = find(ontology, kind[0], OWL + kind[2]);
            assertFalse(top.get("hasDirectChildren").getAsBoolean());
            assertFalse(top.get("hasHierarchicalChildren").getAsBoolean());
        }
    }

    @Test
    public void literalNegativeAssertionsRetainNumbersAndLanguagesAndIgnoreIncompleteAssertions() throws Exception {
        JsonObject item = node(run("annotations", Map.of()), "individuals", "item");
        assertEquals(7, item.get("negativePropertyAssertion+" + BASE + "score").getAsInt());
        JsonObject text = item.getAsJsonObject("negativePropertyAssertion+" + BASE + "note");
        assertEquals("Négation", text.get("value").getAsString());
        assertEquals("fr", text.get("lang").getAsString());
        assertFalse(item.has("negativePropertyAssertion+" + BASE + "invalid"));
        assertFalse(item.has("negativePropertyAssertion+not a URI"));
    }

    @Test
    public void defaultSynonymPredicatesNormalizeSingleAndMultipleValues() throws Exception {
        JsonObject root = node(run("annotations", Map.of()), "classes", "Root");
        assertValues(root, "synonym", "Exact one", "Exact two", "Related", "Narrow", "Broad", "Generic");
        for (String scope : List.of("Exact", "Related", "Narrow", "Broad", "")) {
            String predicate = OIO + "has" + scope + "Synonym";
            assertTrue(root.get(predicate).isJsonArray());
            assertEquals(scope.equals("Exact") ? 2 : 1, root.getAsJsonArray(predicate).size());
        }
        assertValues(root, "synonymProperty", OIO + "hasExactSynonym", OIO + "hasRelatedSynonym",
                OIO + "hasNarrowSynonym", OIO + "hasBroadSynonym", OIO + "hasSynonym");
    }

    @Test
    public void defaultLabelsAndDefinitionsCollateMultiplePredicatesAndRetainLiteralEvidence() throws Exception {
        JsonObject root = node(run("annotations", Map.of()), "classes", "Root");
        assertValues(root, "label", "Plain label", "DC title", "DCT title", "Nom");
        assertEquals(4, root.getAsJsonArray("label").size());
        assertValues(root, "definition", "Comment definition", "Définition", "2026-10-01");
        JsonObject date = valueObject(root.getAsJsonArray("definition"), "2026-10-01");
        assertEquals("http://www.w3.org/2001/XMLSchema#date", date.get("datatype").getAsString());
        assertEquals("fr", valueObject(root.getAsJsonArray("definition"), "Définition").get("lang").getAsString());
        assertValues(root, "definitionProperty", RDFS + "comment", "http://purl.obolibrary.org/obo/IAO_0000115",
                "http://purl.org/dc/terms/description");
    }

    @Test
    public void explicitEmptyLabelAndDefinitionListsUseFallbackWhileSynonymDefaultsRemain() throws Exception {
        JsonObject root = node(run("annotations", Map.of("label_property", List.of(),
                "definition_property", List.of(), "synonym_property", List.of())), "classes", "Root");
        assertValues(root, "label", "Root");
        assertFalse(root.has("definition"));
        assertFalse(root.has("definitionProperty"));
        assertValues(root, "synonym", "Exact one", "Exact two", "Related", "Narrow", "Broad", "Generic");
        assertEquals("Plain label", literal(root.get(RDFS + "label")));
    }

    @Test
    public void searchableValuesExcludeStandardNamespacesAndUriAnnotations() throws Exception {
        JsonObject root = node(run("annotations", Map.of()), "classes", "Root");
        // The synthetic imported=false value is searchable; timestamps are separately excluded.
        assertValues(root, "searchableAnnotationValues", "DC title", "DCT title", "Nom", "Définition",
                "2026-10-01", "Exact one", "Exact two", "Related", "Narrow", "Broad", "Generic", "Search marker", "false");
    }

    @Test
    public void absentPrefixMetadataAndInstanceFlagsAreConsistentAcrossEntityKinds() throws Exception {
        JsonObject ontology = run("annotations", Map.of());
        for (String[] entity : List.of(new String[]{"classes", "Root"}, new String[]{"properties", "score"},
                new String[]{"individuals", "item"})) {
            JsonObject node = node(ontology, entity[0], entity[1]);
            assertEquals("variants", literal(node.get("ontologyId")));
            assertEquals("https://example.org/variants", literal(node.get("ontologyIri")));
            assertFalse(node.has("ontologyPreferredPrefix"));
        }
        assertTrue(node(ontology, "classes", "Root").get("hasIndividuals").getAsBoolean());
        assertFalse(node(ontology, "classes", "Other").has("hasIndividuals"));
        assertTrue(node(ontology, "classes", "PunnedRoot").get("hasIndividuals").getAsBoolean());
        assertFalse(node(ontology, "individuals", "externalItem").has("directParent"));
        // Output types reflect each collection; the same punned IRI appears in both.
        assertValues(node(ontology, "classes", "Punned"), "type", "class", "entity");
        assertValues(node(ontology, "individuals", "Punned"), "type", "individual", "entity");
    }

    @Test
    public void obsoleteLexicalFormsAndObsoleteParentAreRecognized() throws Exception {
        JsonObject ontology = run("annotations", Map.of());
        for (String name : List.of("DeprecatedOne", "DeprecatedTrue", "ObsoleteParent"))
            assertTrue(name, node(ontology, "classes", name).get("isObsolete").getAsBoolean());
        for (String name : List.of("DeprecatedFalse", "DeprecatedZero", "Other"))
            assertFalse(name, node(ontology, "classes", name).get("isObsolete").getAsBoolean());
        assertTrue(node(ontology, "properties", "score").get("isObsolete").getAsBoolean());
        assertFalse(node(ontology, "individuals", "item").get("isObsolete").getAsBoolean());
    }

    @Test
    public void preferredRootsUnionConfigAndOntologyDeclarationsWithoutDuplicates() throws Exception {
        JsonObject ontology = run("annotations", Map.of("preferred_root_term", List.of(BASE + "Root", BASE + "Other")));
        assertValues(ontology, "preferredRoot", BASE + "Root", BASE + "Other", BASE + "MissingRoot", BASE + "score");
        assertEquals(4, ontology.getAsJsonArray("preferredRoot").size());
        assertTrue(node(ontology, "classes", "Root").get("isPreferredRoot").getAsBoolean());
        assertTrue(node(ontology, "classes", "Other").get("isPreferredRoot").getAsBoolean());
        assertTrue(node(ontology, "properties", "score").get("isPreferredRoot").getAsBoolean());
        assertFalse(node(ontology, "classes", "DeprecatedFalse").get("isPreferredRoot").getAsBoolean());
        assertFalse(node(ontology, "individuals", "item").has("isPreferredRoot"));
    }

    @Test
    public void unresolvedOrUnlabelledSynonymTypesKeepEvidenceWithoutInventingNames() throws Exception {
        JsonObject ontology = run("annotations", Map.of());
        for (String[] pair : List.of(new String[]{"TypedMissing", "MissingType"},
                new String[]{"TypedUnlabelled", "NoLabelType"})) {
            JsonObject value = node(ontology, "classes", pair[0]).getAsJsonArray(OIO + "hasExactSynonym").get(0).getAsJsonObject();
            JsonObject axiom = value.getAsJsonArray("axioms").get(0).getAsJsonObject();
            assertEquals(BASE + pair[1], axiom.get(OIO + "hasSynonymType").getAsString());
            assertFalse(axiom.has("oboSynonymTypeName"));
        }
    }

    @Test
    public void bidirectionalAndMissingEquivalenceOrInverseTargetsDoNotCreateDuplicateValues() throws Exception {
        JsonObject ontology = run("symmetry", Map.of());
        assertEquals(BASE + "B", node(ontology, "classes", "A").get(OWL + "equivalentClass").getAsString());
        assertEquals(BASE + "A", node(ontology, "classes", "B").get(OWL + "equivalentClass").getAsString());
        assertEquals(BASE + "Absent", node(ontology, "classes", "Missing").get(OWL + "equivalentClass").getAsString());
        for (String predicate : List.of(OWL + "equivalentProperty", OWL + "inverseOf")) {
            assertEquals(BASE + "q", node(ontology, "properties", "p").get(predicate).getAsString());
            assertEquals(BASE + "p", node(ontology, "properties", "q").get(predicate).getAsString());
            assertEquals(BASE + "absentProperty", node(ontology, "properties", "missingProperty").get(predicate).getAsString());
        }
        assertEquals(BASE + "second", node(ontology, "individuals", "first").get(OWL + "differentFrom").getAsString());
        assertEquals(BASE + "first", node(ontology, "individuals", "second").get(OWL + "differentFrom").getAsString());
        assertEquals(3, ontology.getAsJsonArray("classes").size());
        assertEquals(3, ontology.getAsJsonArray("properties").size());
        assertEquals(2, ontology.getAsJsonArray("individuals").size());
    }

    @Test
    public void nestedAndSharedImportsKeepPrimaryProvenanceAndDeriveCrossFileHierarchy() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        for (String name : List.of("main", "child", "grandchild")) {
            String template = Files.readString(Path.of(getClass().getResource("/variants/import-" + name + ".ttl.template").toURI()));
            template = template.replace("__CHILD__", directory.resolve("child.ttl").toUri().toString())
                    .replace("__GRANDCHILD__", directory.resolve("grandchild.ttl").toUri().toString());
            Files.writeString(directory.resolve(name + ".ttl"), template);
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("id", "variants");
        entry.addProperty("ontology_purl", "main.ttl");
        JsonArray configs = new JsonArray();
        configs.add(entry);
        JsonObject config = new JsonObject();
        config.add("ontologies", configs);
        JsonObject ontology = execute(directory, config);
        assertEquals(3, ontology.getAsJsonArray("classes").size());
        JsonObject root = node(ontology, "classes", "Root");
        JsonObject middle = node(ontology, "classes", "Middle");
        JsonObject leaf = node(ontology, "classes", "Leaf");
        assertFalse(root.get("imported").getAsBoolean());
        assertTrue(middle.get("imported").getAsBoolean());
        assertTrue(leaf.get("imported").getAsBoolean());
        assertValues(root, "label", "Imported root label");
        assertValues(leaf, "label", "Grandchild label");
        assertValues(leaf, "directAncestor", BASE + "Middle", BASE + "Root");
        assertValues(leaf, "hierarchicalAncestor", BASE + "Middle", BASE + "Root");
        assertEquals(2, root.get("numDescendants").getAsInt());
        assertEquals(2, root.get("numHierarchicalDescendants").getAsInt());
    }

    @Test
    public void emptyLiteralValuesRemainPresentInLabelsDefinitionsAndSynonyms() throws Exception {
        JsonObject empty = node(run("annotations", Map.of()), "classes", "Empty");
        assertValues(empty, "label", "");
        assertValues(empty, "definition", "");
        assertValues(empty, "synonym", "");
        assertValues(empty, OIO + "hasExactSynonym", "");
    }

    @Test
    public void configuredAndEmptyPrefixMetadataArePreservedOnAllEntityKinds() throws Exception {
        for (String prefix : List.of("V", "")) {
            JsonObject ontology = run("annotations", Map.of("preferredPrefix", prefix));
            for (String[] entity : List.of(new String[]{"classes", "Root"}, new String[]{"properties", "score"},
                    new String[]{"individuals", "item"})) {
                JsonObject node = node(ontology, entity[0], entity[1]);
                assertEquals(prefix, literal(node.get("ontologyPreferredPrefix")));
                assertEquals("variants", literal(node.get("ontologyId")));
                assertEquals("https://example.org/variants", literal(node.get("ontologyIri")));
            }
        }
    }

    private JsonObject run(String fixture, Map<String, Object> settings) throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        Path source = Path.of(getClass().getResource("/variants/" + fixture + ".ttl").toURI());
        Files.copy(source, directory.resolve("ontology.ttl"));
        JsonObject config = new Gson().toJsonTree(settings).getAsJsonObject();
        config.addProperty("id", "variants");
        config.addProperty("ontology_purl", "ontology.ttl");
        JsonArray configs = new JsonArray();
        configs.add(config);
        JsonObject root = new JsonObject();
        root.add("ontologies", configs);
        return execute(directory, root);
    }

    private JsonObject execute(Path directory, JsonObject config) throws Exception {
        Path configPath = directory.resolve("config.json");
        Files.writeString(configPath, config.toString());
        Path output = directory.resolve("output.json");
        Path log = directory.resolve("rdf2json.log");
        Process process = new ProcessBuilder(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("rdf2json.jar"), "--config", configPath.toString(),
                "--ontologyIds", "variants", "--output", output.toString(),
                "--basePath", directory.toString(), "--loadLocalFiles", "--noDates"))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertTrue("RDF2JSON timed out", completed);
        String logs = Files.readString(log);
        assertEquals(logs, 0, process.exitValue());
        JsonObject status = JsonParser.parseString(Files.readString(directory.resolve("output.status.json"))).getAsJsonObject();
        assertEquals(logs, "SUCCESS", status.get("status").getAsString());
        JsonArray ontologies = JsonParser.parseString(Files.readString(output)).getAsJsonObject().getAsJsonArray("ontologies");
        assertEquals(1, ontologies.size());
        return ontologies.get(0).getAsJsonObject();
    }

    private static JsonObject node(JsonObject ontology, String collection, String name) {
        return find(ontology, collection, BASE + name);
    }

    private static JsonObject find(JsonObject ontology, String collection, String iri) {
        for (JsonElement item : ontology.getAsJsonArray(collection))
            if (iri.equals(item.getAsJsonObject().get("iri").getAsString())) return item.getAsJsonObject();
        throw new AssertionError("Missing " + collection + " entity " + iri);
    }

    private static String literal(JsonElement value) {
        return value.isJsonObject() ? value.getAsJsonObject().get("value").getAsString() : value.getAsString();
    }

    private static void assertValues(JsonObject entity, String field, String... expected) {
        assertTrue("Missing " + field + " on " + entity.get("iri"), entity.has(field));
        JsonElement value = entity.get(field);
        Set<String> actual = new HashSet<>();
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) actual.add(literal(item));
            assertEquals("Duplicate " + field, actual.size(), value.getAsJsonArray().size());
        } else actual.add(literal(value));
        assertEquals(field + " on " + entity.get("iri"), Set.of(expected), actual);
    }

    private static JsonObject valueObject(JsonArray values, String expected) {
        for (JsonElement value : values)
            if (expected.equals(literal(value))) return value.getAsJsonObject();
        throw new AssertionError("Missing literal " + expected + " in " + values);
    }
}
