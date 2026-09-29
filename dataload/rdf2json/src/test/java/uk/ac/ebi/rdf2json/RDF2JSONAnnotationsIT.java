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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Semantic contract of a packaged RDF2JSON run, independent of downstream transforms. */
public class RDF2JSONAnnotationsIT {
    private static final String BASE = "https://example.org/contract#";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void serializesEntityKindsAndOntologyIdentity() throws Exception {
        JsonObject ontology = run();

        assertEquals("contract", ontology.get("ontologyId").getAsString());
        assertEquals("https://example.org/contract", ontology.get("iri").getAsString());
        assertEquals("CT", ontology.get("preferredPrefix").getAsString());
        assertEquals("2026-09", literal(ontology.get("http://www.w3.org/2002/07/owl#versionInfo")));
        assertEquals("4", literal(ontology.get("numberOfClasses")));
        assertEquals("8", literal(ontology.get("numberOfProperties")));
        assertEquals("2", literal(ontology.get("numberOfIndividuals")));
        assertContainsLiteral(ontology.getAsJsonArray("language"), "en", null);
        assertContainsLiteral(ontology.getAsJsonArray("language"), "fr", null);

        JsonObject root = entity(ontology, "classes", "Root");
        assertContains(root.getAsJsonArray("type"), "class");
        assertEquals("contract", literal(root.get("ontologyId")));
        assertEquals("https://example.org/contract", literal(root.get("ontologyIri")));
        assertEquals("CT", literal(root.get("ontologyPreferredPrefix")));
        assertEquals("CT_Root", literal(root.get("shortForm")));
        assertEquals("CT:Root", literal(root.get("curie")));

        assertContains(entity(ontology, "properties", "partOf").getAsJsonArray("type"), "objectProperty");
        assertContains(entity(ontology, "properties", "score").getAsJsonArray("type"), "dataProperty");
        assertContains(entity(ontology, "properties", "tag").getAsJsonArray("type"), "annotationProperty");
        JsonObject individual = entity(ontology, "individuals", "item");
        assertContains(individual.getAsJsonArray("type"), "individual");
        assertEquals(7, individual.get(BASE + "score").getAsInt());
        assertTrue(root.get("hasIndividuals").getAsBoolean());
    }

    @Test
    public void annotatesLabelsDefinitionsSynonymsAndSearchValues() throws Exception {
        JsonObject ontology = run();
        JsonObject root = entity(ontology, "classes", "Root");

        assertContainsLiteral(root.getAsJsonArray("label"), "A root", "en");
        assertContainsLiteral(root.getAsJsonArray("label"), "Une racine", "fr");
        assertContainsLiteral(root.getAsJsonArray("definition"), "The configured definition", "en");
        assertEquals(BASE + "definition", literal(root.get("definitionProperty")));
        assertFalse(root.getAsJsonArray("definition").toString().contains("A comment is not"));
        assertContainsLiteral(root.getAsJsonArray("synonym"), "Custom alias", "en");
        assertContainsLiteral(root.getAsJsonArray("synonym"), "Exact alias", "en");
        assertContainsLiteral(root.getAsJsonArray("synonymProperty"), BASE + "alias", null);
        assertContainsLiteral(root.getAsJsonArray("synonymProperty"),
                "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym", null);
        assertContainsLiteral(root.getAsJsonArray("searchableAnnotationValues"), "Findable marker", null);

        JsonObject frenchOnly = entity(ontology, "classes", "FrenchOnly");
        assertContainsLiteral(frenchOnly.getAsJsonArray("label"), "Seulement français", "fr");
        assertContainsLiteral(frenchOnly.getAsJsonArray("label"), "CT_FrenchOnly", null);
    }

    @Test
    public void configuredLabelPredicateOverridesTheDefaultLabelPredicates() throws Exception {
        JsonObject ontology = run("annotations-custom-label.json");
        JsonObject root = entity(ontology, "classes", "Root");
        assertContainsLiteral(root.getAsJsonArray("label"), "Findable marker", null);
        assertFalse(root.getAsJsonArray("label").toString().contains("A root"));
        assertContainsLiteral(entity(ontology, "classes", "FrenchOnly").getAsJsonArray("label"),
                "CT_FrenchOnly", null);
    }

    @Test
    public void marksObsoleteEntitiesAndPreferredRoots() throws Exception {
        JsonObject ontology = run();
        JsonObject root = entity(ontology, "classes", "Root");
        JsonObject obsolete = entity(ontology, "classes", "Obsolete");

        assertTrue(root.get("isPreferredRoot").getAsBoolean());
        assertFalse(root.get("isObsolete").getAsBoolean());
        assertFalse(obsolete.get("isPreferredRoot").getAsBoolean());
        assertTrue(obsolete.get("isObsolete").getAsBoolean());
        assertContains(ontology.getAsJsonArray("preferredRoot"), BASE + "Root");
    }

    @Test
    public void retainsAxiomEvidenceAndNegativeAssertions() throws Exception {
        JsonObject ontology = run();
        JsonObject root = entity(ontology, "classes", "Root");
        JsonObject item = entity(ontology, "individuals", "item");

        JsonObject reifiedNote = root.get(BASE + "note").getAsJsonObject();
        assertContains(reifiedNote.getAsJsonArray("type"), "reification");
        assertEquals("A note with an axiom", literal(reifiedNote.get("value")));
        assertContainsAxiomLiteral(reifiedNote.getAsJsonArray("axioms"),
                "http://www.w3.org/2000/01/rdf-schema#comment", "Evidence for the note");

        assertEquals(BASE + "other", item.get("negativePropertyAssertion+" + BASE + "partOf").getAsString());
    }

    @Test
    public void multipleAxiomsOnOneAssertionRetainBothEvidenceRecords() throws Exception {
        JsonObject root = entity(run(), "classes", "Root");
        JsonArray axioms = root.get(BASE + "note").getAsJsonObject().getAsJsonArray("axioms");
        Set<String> comments = new HashSet<>();
        for (JsonElement axiom : axioms) {
            comments.add(literal(axiom.getAsJsonObject().get("http://www.w3.org/2000/01/rdf-schema#comment")));
        }

        assertEquals(Set.of("Evidence for the note", "Additional evidence"), comments);
        assertEquals(2, axioms.size());
    }

    @Test
    public void uriValuedAssertionRetainsItsAxiomEvidence() throws Exception {
        JsonObject root = entity(run(), "classes", "Root");
        JsonObject reference = root.get(BASE + "seeAlso").getAsJsonObject();

        assertContains(reference.getAsJsonArray("type"), "reification");
        assertEquals(BASE + "other", reference.get("value").getAsString());
        assertContainsAxiomLiteral(reference.getAsJsonArray("axioms"),
                "http://www.w3.org/2000/01/rdf-schema#comment", "URI target evidence");
    }

    @Test
    public void includesTheLabelOfAnOboSynonymTypeInAxiomEvidence() throws Exception {
        JsonObject typed = entity(run(), "classes", "TypedSynonym");
        JsonObject synonym = typed.getAsJsonArray("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")
                .get(0).getAsJsonObject();
        assertContains(synonym.getAsJsonArray("type"), "reification");
        assertEquals("Typed alias", literal(synonym.get("value")));
        JsonObject evidence = synonym.getAsJsonArray("axioms").get(0).getAsJsonObject();
        assertEquals("abbreviation", literal(evidence.get("oboSynonymTypeName")));
    }

    private JsonObject run() throws Exception {
        return run("annotations.json");
    }

    private JsonObject run(String configName) throws Exception {
        Path fixtures = Path.of(getClass().getResource("/contracts/annotations.ttl").toURI()).getParent();
        Path output = temporaryFolder.getRoot().toPath().resolve("ontologies.json");
        Path log = temporaryFolder.getRoot().toPath().resolve("rdf2json.log");
        List<String> command = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("rdf2json.jar"),
                "--config", fixtures.resolve(configName).toString(),
                "--ontologyIds", "contract", "--output", output.toString(),
                "--basePath", fixtures.toString(), "--loadLocalFiles", "--noDates");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertTrue("RDF2JSON timed out", completed);
        assertEquals(Files.readString(log), 0, process.exitValue());
        assertTrue("Missing ontology output:\n" + Files.readString(log), Files.exists(output));
        Path status = output.resolveSibling("ontologies.status.json");
        assertTrue("Missing ontology status:\n" + Files.readString(log), Files.exists(status));
        try (Reader reader = Files.newBufferedReader(status)) {
            assertEquals("SUCCESS", JsonParser.parseReader(reader).getAsJsonObject().get("status").getAsString());
        }
        try (Reader reader = Files.newBufferedReader(output)) {
            JsonArray ontologies = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("ontologies");
            assertEquals(1, ontologies.size());
            return ontologies.get(0).getAsJsonObject();
        }
    }

    private static JsonObject entity(JsonObject ontology, String collection, String localName) {
        for (JsonElement candidate : ontology.getAsJsonArray(collection)) {
            JsonObject entity = candidate.getAsJsonObject();
            if ((BASE + localName).equals(entity.get("iri").getAsString())) return entity;
        }
        throw new AssertionError("Missing " + collection + " entity " + localName);
    }

    private static String literal(JsonElement element) {
        return element.getAsJsonObject().get("value").getAsString();
    }

    private static void assertContains(JsonArray values, String expected) {
        for (JsonElement value : values) {
            if (expected.equals(value.getAsString())) return;
        }
        throw new AssertionError("Expected " + expected + " in " + values);
    }

    private static void assertContainsLiteral(JsonArray values, String expected, String language) {
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject literal = value.getAsJsonObject();
            if (!literal.has("value") || !literal.get("value").isJsonPrimitive()) continue;
            if (!expected.equals(literal.get("value").getAsString())) continue;
            if (language == null && !literal.has("lang")) return;
            if (language != null && literal.has("lang") && language.equals(literal.get("lang").getAsString())) return;
        }
        throw new AssertionError("Expected literal " + expected + " (" + language + ") in " + values);
    }

    private static void assertContainsAxiomLiteral(JsonArray axioms, String predicate, String expected) {
        for (JsonElement axiom : axioms) {
            JsonElement value = axiom.getAsJsonObject().get(predicate);
            if (value != null && expected.equals(literal(value))) return;
        }
        throw new AssertionError("Expected axiom literal " + expected + " in " + axioms);
    }
}
