import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import uk.ac.ebi.ols.shared.Embeddings;

import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class NeoConverter {

    static Gson gson = new Gson();

    String inputFilePath;
    String outputFilePath;
    String manifestFilePath;
    LinkerPass1Result manifest;

    public NeoConverter(String inputFilePath, String outputFilePath, String manifestFilePath) throws FileNotFoundException, IOException {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
        this.manifestFilePath = manifestFilePath;
        
        // Load the manifest
        System.out.println("Loading manifest from: " + manifestFilePath);
        this.manifest = gson.fromJson(
            new InputStreamReader(new FileInputStream(manifestFilePath)), 
            LinkerPass1Result.class
        );
    }


    JsonReader reader;

    public void convert() throws IOException {

        System.out.println("Reading input file: " + inputFilePath);
        reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject();
        
        boolean foundOntologies = false;

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();
            System.out.println("Found top-level key: " + name);

            if (name.equals("ontologies")) {
                
                foundOntologies = true;

                reader.beginArray();
                
                int ontologyCount = 0;

                while(reader.peek() != JsonToken.END_ARRAY) {
                    
                    ontologyCount++;

                    reader.beginObject(); // ontology
                    
                    // Read the ontologyId to get the scanner result from manifest
                    String ontologyIdKey = reader.nextName();
                    if (!ontologyIdKey.equals("ontologyId")) {
                        throw new RuntimeException("Expected 'ontologyId' as first field in ontology");
                    }
                    String ontologyId = reader.nextString();

                    System.out.println("Processing ontology: " + ontologyId);
                    
                    // Get scanner results from manifest
                    OntologyManifestInfo manifestInfo = new OntologyManifestInfo();
                    manifestInfo.ontologyId = ontologyId;
                    
                    // Apply blacklist to remove properties that shouldn't be in Neo4j
                    manifestInfo.allOntologyProperties = filterBlacklist(manifest.ontologyIdToOntologyProperties.getOrDefault(ontologyId, new HashSet<>()));
                    manifestInfo.allClassProperties = filterBlacklist(manifest.ontologyIdToClassProperties.getOrDefault(ontologyId, new HashSet<>()));
                    manifestInfo.allPropertyProperties = filterBlacklist(manifest.ontologyIdToPropertyProperties.getOrDefault(ontologyId, new HashSet<>()));
                    manifestInfo.allIndividualProperties = filterBlacklist(manifest.ontologyIdToIndividualProperties.getOrDefault(ontologyId, new HashSet<>()));
                    manifestInfo.allEdgeProperties = manifest.ontologyIdToEdgeProperties.getOrDefault(ontologyId, new HashSet<>());
                    
                    // Convert string type sets to NodeType sets for uriToTypes
                    Map<String, Set<String>> uriToTypeStrings = manifest.ontologyIdToUriToTypes.getOrDefault(ontologyId, new HashMap<>());
                    manifestInfo.uriToTypes = new HashMap<>();
                    for (Map.Entry<String, Set<String>> entry : uriToTypeStrings.entrySet()) {
                        Set<OntologyManifestInfo.NodeType> nodeTypes = new HashSet<>();
                        for (String typeStr : entry.getValue()) {
                            nodeTypes.add(OntologyManifestInfo.NodeType.valueOf(typeStr));
                        }
                        manifestInfo.uriToTypes.put(entry.getKey(), nodeTypes);
                    }

                    new OntologyWriter(reader, outputFilePath, manifestInfo).write();
                    
                    reader.endObject(); // close the ontology object

                    System.out.println("OntologyWriter complete for " + ontologyId);
                }

                reader.endArray();
                
                System.out.println("Processed " + ontologyCount + " ontologies");

            } else {

                reader.skipValue();

            }
        }

        reader.endObject();
        reader.close();
        
        if (!foundOntologies) {
            System.err.println("WARNING: No 'ontologies' array found in input JSON");
        }
    }

    /**
     * Filter out blacklisted properties that shouldn't be stored as Neo4j node properties.
     * These properties are still available in the _json field.
     */
    private Set<String> filterBlacklist(Set<String> properties) {
        // Property blacklist from OntologyWriter
        Set<String> blacklist = Set.of(
            // large and doesn't get queried
            APPEARS_IN.getText(),
            // all property values together, this is for solr and not useful in neo4j
            "searchableAnnotationValues"
        );
        
        return properties.stream()
            .filter(prop -> !blacklist.contains(prop))
            .collect(Collectors.toSet());
    }

}
