import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CreateManifest {

    static Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "input JSON ontology file(s), comma-separated for multiple files");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "output", true, "output manifest JSON filename");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("create-manifest", options);
            System.exit(1);
            return;
        }

        String inputFilesStr = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("output");

        String[] inputFiles = inputFilesStr.split(",");

        LinkerPass1Result combinedResult = new LinkerPass1Result();

        // Process each input file
        for (String inputFile : inputFiles) {
            inputFile = inputFile.trim();
            System.out.println("Processing input file: " + inputFile);
            
            LinkerPass1Result fileResult = LinkerPass1.run(inputFile);
            
            // Merge results from this file into combined result
            mergeResults(combinedResult, fileResult);
        }

        // Write the combined manifest
        System.out.println("Writing manifest to: " + outputFilePath);
        try (FileWriter writer = new FileWriter(outputFilePath, StandardCharsets.UTF_8)) {
            gson.toJson(combinedResult, writer);
        }
        System.out.println("Manifest creation complete.");
    }

    private static void mergeResults(LinkerPass1Result target, LinkerPass1Result source) {
        // Merge iriToDefinitions
        source.iriToDefinitions.forEach((iri, defSet) -> {
            EntityDefinitionSet targetDefSet = target.iriToDefinitions.get(iri);
            if (targetDefSet == null) {
                target.iriToDefinitions.put(iri, defSet);
            } else {
                targetDefSet.definitions.addAll(defSet.definitions);
                targetDefSet.definingDefinitions.addAll(defSet.definingDefinitions);
                targetDefSet.definingOntologyIris.addAll(defSet.definingOntologyIris);
                targetDefSet.definingOntologyIds.addAll(defSet.definingOntologyIds);
                targetDefSet.ontologyIdToDefinitions.putAll(defSet.ontologyIdToDefinitions);
            }
        });

        // Merge ontologyIriToOntologyIds
        source.ontologyIriToOntologyIds.forEach((iri, ids) -> {
            target.ontologyIriToOntologyIds.computeIfAbsent(iri, k -> new java.util.HashSet<>()).addAll(ids);
        });

        // Merge preferredPrefixToOntologyIds
        source.preferredPrefixToOntologyIds.forEach((prefix, ids) -> {
            target.preferredPrefixToOntologyIds.computeIfAbsent(prefix, k -> new java.util.HashSet<>()).addAll(ids);
        });

        // Merge ontologyIdToBaseUris
        source.ontologyIdToBaseUris.forEach((id, uris) -> {
            target.ontologyIdToBaseUris.computeIfAbsent(id, k -> new java.util.HashSet<>()).addAll(uris);
        });

        // Merge ontologyIdToImportingOntologyIds
        source.ontologyIdToImportingOntologyIds.forEach((id, values) -> {
            target.ontologyIdToImportingOntologyIds.computeIfAbsent(id, k -> new java.util.ArrayList<>()).addAll(values);
        });

        // Merge ontologyIdToImportedOntologyIds
        source.ontologyIdToImportedOntologyIds.forEach((id, values) -> {
            target.ontologyIdToImportedOntologyIds.computeIfAbsent(id, k -> new java.util.ArrayList<>()).addAll(values);
        });
        
        // Merge scanner results - property sets
        source.ontologyIdToOntologyProperties.forEach((id, props) -> {
            target.ontologyIdToOntologyProperties.put(id, props);
        });
        
        source.ontologyIdToClassProperties.forEach((id, props) -> {
            target.ontologyIdToClassProperties.put(id, props);
        });
        
        source.ontologyIdToPropertyProperties.forEach((id, props) -> {
            target.ontologyIdToPropertyProperties.put(id, props);
        });
        
        source.ontologyIdToIndividualProperties.forEach((id, props) -> {
            target.ontologyIdToIndividualProperties.put(id, props);
        });
        
        source.ontologyIdToEdgeProperties.forEach((id, props) -> {
            target.ontologyIdToEdgeProperties.put(id, props);
        });
        
        // Merge scanner results - URI to types mapping
        source.ontologyIdToUriToTypes.forEach((id, uriToTypes) -> {
            target.ontologyIdToUriToTypes.put(id, uriToTypes);
        });
    }
}
