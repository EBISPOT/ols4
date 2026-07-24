import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class JSON2SSSOMTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesMappingSetMetadataAlignedWithSssomSpec() throws Exception {
        File input = temporaryFolder.newFile("apo.json");
        File outputDir = temporaryFolder.newFolder("sssom");

        Files.writeString(input.toPath(), "{\n" +
                "  \"ontologies\": [\n" +
                "    {\n" +
                "      \"ontologyId\": \"apo\",\n" +
                "      \"preferredPrefix\": \"APO\",\n" +
                "      \"title\": \"Ascomycete Phenotype Ontology\",\n" +
                "      \"classes\": [],\n" +
                "      \"properties\": [],\n" +
                "      \"individuals\": [],\n" +
                "      \"linkedEntities\": {}\n" +
                "    }\n" +
                "  ]\n" +
                "}\n", StandardCharsets.UTF_8);

        JSON2SSSOM.main(new String[] {
                "--input", input.getAbsolutePath(),
                "--outDir", outputDir.getAbsolutePath(),
                "--mappingDate", "2026-04-30"
        });

        Path output = outputDir.toPath().resolve("apo.ols.sssom.tsv");
        String sssom = Files.readString(output, StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(sssom.contains("# mapping_set_id: https://w3id.org/commons/ols/mappings/apo.ols.sssom.tsv\n"));
        assertTrue(sssom.contains("# mapping_provider: https://www.ebi.ac.uk/ols4/ontologies/apo\n"));
        assertTrue(sssom.contains("# mapping_tool: https://www.ebi.ac.uk/ols4\n"));
        assertTrue(sssom.contains("# mapping_set_title: OLS extracted APO mappings\n"));
        assertTrue(sssom.contains("# mapping_set_description: These mappings were extracted during the OLS dataload from APO\n"));
        assertTrue(sssom.contains("# mapping_date: '2026-04-30'\n") || sssom.contains("# mapping_date: 2026-04-30\n"));
        assertTrue(sssom.contains("# other:\n#   local_id: apo.ols\n#   prefix: APO\n#   ontology: Ascomycete Phenotype Ontology (APO)\n# local_name: apo.ols.sssom.tsv\n"));
        assertFalse(sssom.contains("mapping_set_source"));
    }

    @Test
    public void fallsBackToPrefixForOntologyFieldWhenTitleMissing() throws Exception {
        File input = temporaryFolder.newFile("notitle.json");
        File outputDir = temporaryFolder.newFolder("sssom-notitle");

        Files.writeString(input.toPath(), "{\n" +
                "  \"ontologies\": [\n" +
                "    {\n" +
                "      \"ontologyId\": \"nt\",\n" +
                "      \"preferredPrefix\": \"NT\",\n" +
                "      \"classes\": [],\n" +
                "      \"properties\": [],\n" +
                "      \"individuals\": [],\n" +
                "      \"linkedEntities\": {}\n" +
                "    }\n" +
                "  ]\n" +
                "}\n", StandardCharsets.UTF_8);

        JSON2SSSOM.main(new String[] {
                "--input", input.getAbsolutePath(),
                "--outDir", outputDir.getAbsolutePath(),
                "--mappingDate", "2026-04-30"
        });

        Path output = outputDir.toPath().resolve("nt.ols.sssom.tsv");
        String sssom = Files.readString(output, StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(sssom.contains("# other:\n#   local_id: nt.ols\n#   prefix: NT\n#   ontology: NT\n# local_name: nt.ols.sssom.tsv\n"));
    }

    @Test
    public void splitsObsoleteAndCurrentTermsIntoSeparateExtracts() throws Exception {
        File input = temporaryFolder.newFile("obs.json");
        File outputDir = temporaryFolder.newFolder("sssom-obs");

        Files.writeString(input.toPath(), "{\n" +
                "  \"ontologies\": [\n" +
                "    {\n" +
                "      \"ontologyId\": \"obs\",\n" +
                "      \"preferredPrefix\": \"OBS\",\n" +
                "      \"classes\": [\n" +
                "        {\n" +
                "          \"iri\": \"http://example.org/OBS_0000001\",\n" +
                "          \"label\": \"obsolete term\",\n" +
                "          \"isObsolete\": true,\n" +
                "          \"isDefiningOntology\": true,\n" +
                "          \"http://www.geneontology.org/formats/oboInOwl#hasDbXref\": \"http://example.org/OTHER_1\",\n" +
                "          \"linkedEntities\": {\n" +
                "            \"http://example.org/OTHER_1\": { \"iri\": \"http://example.org/OTHER_1\", \"label\": \"Other 1\" }\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"iri\": \"http://example.org/OBS_0000002\",\n" +
                "          \"label\": \"current term\",\n" +
                "          \"isObsolete\": false,\n" +
                "          \"isDefiningOntology\": true,\n" +
                "          \"http://www.geneontology.org/formats/oboInOwl#hasDbXref\": \"http://example.org/OTHER_2\",\n" +
                "          \"linkedEntities\": {\n" +
                "            \"http://example.org/OTHER_2\": { \"iri\": \"http://example.org/OTHER_2\", \"label\": \"Other 2\" }\n" +
                "          }\n" +
                "        }\n" +
                "      ],\n" +
                "      \"properties\": [],\n" +
                "      \"individuals\": [],\n" +
                "      \"linkedEntities\": {}\n" +
                "    }\n" +
                "  ]\n" +
                "}\n", StandardCharsets.UTF_8);

        JSON2SSSOM.main(new String[] {
                "--input", input.getAbsolutePath(),
                "--outDir", outputDir.getAbsolutePath(),
                "--mappingDate", "2026-04-30"
        });

        Path currentOutput = outputDir.toPath().resolve("obs.ols.sssom.tsv");
        Path obsoleteOutput = outputDir.toPath().resolve("obs.ols.obsolete.sssom.tsv");

        String current = Files.readString(currentOutput, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String obsolete = Files.readString(obsoleteOutput, StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertFalse(current.contains("OBS_0000001"));
        assertTrue(current.contains("OBS_0000002"));

        assertTrue(obsolete.contains("OBS_0000001"));
        assertFalse(obsolete.contains("OBS_0000002"));

        assertTrue(obsolete.contains("# mapping_set_id: https://w3id.org/commons/ols/mappings/obs.ols.obsolete.sssom.tsv\n"));
        assertTrue(obsolete.contains("# mapping_set_title: OLS extracted OBS mappings (obsolete terms)\n"));
        assertTrue(obsolete.contains("# other:\n#   local_id: obs.ols.obsolete\n#   prefix: OBS\n#   ontology: OBS\n# local_name: obs.ols.obsolete.sssom.tsv\n"));
    }

    @Test
    public void rejectsNonIsoMappingDate() throws Exception {
        Options options = new Options();
        options.addOption(new Option(null, "mappingDate", true, "mapping date"));
        CommandLine cmd = new DefaultParser().parse(options, new String[] {
                "--mappingDate", "30-04-2026"
        });

        assertThrows(IllegalArgumentException.class, () -> JSON2SSSOM.getMappingDate(cmd));
    }
}
