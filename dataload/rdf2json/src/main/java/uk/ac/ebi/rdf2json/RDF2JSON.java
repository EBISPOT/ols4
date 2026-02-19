package uk.ac.ebi.rdf2json;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class RDF2JSON {

    private static final Logger logger = LoggerFactory.getLogger(RDF2JSON.class);

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option optConfigs = new Option(null, "config", true, "config JSON filename(s) separated by a comma. subsequent configs are merged with/override previous ones.");
        optConfigs.setRequired(true);
        options.addOption(optConfigs);

        Option optDownloadedPath = new Option(null, "downloadedPath", true, "Optional path of predownloaded ontologies from downloader jar");
        optDownloadedPath.setRequired(false);
        options.addOption(optDownloadedPath);

        Option optMergeOutputWith = new Option(null, "mergeOutputWith", true, "JSON file to merge our output with. Any existing ontologies not indexed this time will be kept.");
        optMergeOutputWith.setRequired(false);
        options.addOption(optMergeOutputWith);

        Option output = new Option(null, "output", true, "JSON output filename");
        output.setRequired(true);
        options.addOption(output);
	
        Option loadLocalFiles = new Option(null, "loadLocalFiles", false, "Whether or not to load local files (unsafe, for testing)");
        loadLocalFiles.setRequired(false);
        options.addOption(loadLocalFiles);

        Option basePath = new Option(null, "basePath", true, "Base path for resolving relative file paths (used with --loadLocalFiles)");
        basePath.setRequired(false);
        options.addOption(basePath);

        Option noDates = new Option(null, "noDates", false, "Set to leave LOADED dates blank (for testing)");
        noDates.setRequired(false);
        options.addOption(noDates);

        Option ontologyIds = new Option(null, "ontologyIds", true, "Optional comma-separated list of ontology IDs to load. If specified, only these ontologies will be loaded from the config.");
        ontologyIds.setRequired(false);
        options.addOption(ontologyIds);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error(e.getMessage());
            formatter.printHelp("rdf2json", options);

            System.exit(1);
            return;
        }

        List<String> configFilePaths = Arrays.asList(cmd.getOptionValue("config").split(","));
        String outputFilePath = cmd.getOptionValue("output");

	    String downloadedPath = cmd.getOptionValue("downloadedPath");
        boolean bLoadLocalFiles = cmd.hasOption("loadLocalFiles");
        String sBasePath = cmd.getOptionValue("basePath");
        boolean bNoDates = cmd.hasOption("noDates");
        String mergeOutputWith = cmd.getOptionValue("mergeOutputWith");
        
        // Parse optional ontology IDs filter
        Set<String> filterOntologyIds = null;
        if (cmd.hasOption("ontologyIds")) {
            String ontologyIdsValue = cmd.getOptionValue("ontologyIds");
            filterOntologyIds = Arrays.stream(ontologyIdsValue.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            logger.info("Filtering to load only these ontologies: {}", filterOntologyIds);
        }


        logger.debug("Configs: {}", configFilePaths);
        logger.debug("Output: {}", outputFilePath);

        Gson gson = new Gson();

        List<InputJson> configs = configFilePaths.stream().map(configPath -> {

            // If an OWL file was given instead of a config JSON, we make a simple config JSON for it.
            // This enables OLS to be easily run for an ontology without having to make a config.
            // For the ID we use the filename of the OWL file without the extension.
            //
            if(configPath.endsWith(".json")) { 

                InputStream inputStream;

                try {
                    if (configPath.contains("://")) {
                        inputStream = new URL(configPath).openStream();
                    } else {
                        inputStream = new FileInputStream(configPath);
                    }
                } catch(IOException e) {
                    throw new RuntimeException("Error loading config file: " + configPath);
                }

                JsonReader reader = new JsonReader(new InputStreamReader(inputStream));

                return (InputJson) gson.fromJson(reader, InputJson.class);

            } else {

                // for example both .owl and .owl.gz is removed from the end of the path
                String ontologyId = configPath.substring(configPath.lastIndexOf("/") + 1, configPath.indexOf('.', configPath.lastIndexOf("/")));
                InputJson autoConfig = new InputJson();
                autoConfig.ontologies = List.of(Map.of("id", ontologyId, "ontology_purl", configPath));
                return autoConfig;
            }

        }).collect(Collectors.toList());


        LinkedHashMap<String, Map<String,Object>> mergedConfigs = new LinkedHashMap<>();

        for(InputJson config : configs) {

            for(Map<String,Object> ontologyConfig : config.ontologies) {

                String ontologyId = ((String) ontologyConfig.get("id")).toLowerCase();

                Map<String,Object> existingConfig = mergedConfigs.get(ontologyId);

                if(existingConfig == null) {
                    mergedConfigs.put(ontologyId, ontologyConfig);
                    continue;
                }

                // override existing config for this ontology with new config
                for(String key : ontologyConfig.keySet()) {
                    existingConfig.put(key, ontologyConfig.get(key));
                }
            }
        }

        // Filter ontologies if --ontologyIds was specified
        if (filterOntologyIds != null) {
            LinkedHashMap<String, Map<String,Object>> filteredConfigs = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : mergedConfigs.entrySet()) {
                if (filterOntologyIds.contains(entry.getKey())) {
                    filteredConfigs.put(entry.getKey(), entry.getValue());
                } else {
                    logger.info("Skipping ontology {} (not in --ontologyIds filter)", entry.getKey());
                }
            }
            mergedConfigs = filteredConfigs;
            logger.info("After filtering: {} ontologies to load", mergedConfigs.size());
        }

        JsonWriter writer = new JsonWriter(new FileWriter(outputFilePath));
        writer.setIndent("  ");

        writer.beginObject();

	// writer.name("loaded");
	// writer.value(java.time.LocalDateTime.now().toString());

        writer.name("ontologies");
        writer.beginArray();


        Set<String> loadedOntologyIds = new HashSet<>();

        for(var ontoConfig : mergedConfigs.values()) {

            String ontologyId = ((String)ontoConfig.get("id")).toLowerCase();

            // Skip obsolete ontologies unless explicitly overridden
            if(ontoConfig.containsKey("is_obsolete") &&
               Boolean.TRUE.equals(ontoConfig.get("is_obsolete"))) {
                logger.info("Skipping obsolete ontology: {}", ontologyId);
                OntologyStatusWriter.writeSkipped(outputFilePath, ontologyId, "Ontology is marked as obsolete");
                continue;
            }

            logger.info("--- Loading ontology: {}", ontologyId);

            try {

                OntologyGraph graph = new OntologyGraph(ontoConfig, bLoadLocalFiles, sBasePath, bNoDates, downloadedPath);

                if(graph.ontologyNode == null) {
                    logger.error("No Ontology node found; nothing will be written");
                    // Write status file for failed ontology (will check for fallback later)
                    if (mergeOutputWith == null) {
                        OntologyStatusWriter.writeFailedNoFallback(outputFilePath, ontologyId, "No Ontology node found in RDF");
                    }
                    continue;
                }

                long startTime3 = System.nanoTime();
                logger.info("Writing ontology: {}", ontologyId);
                graph.write(writer);
                long endTime3 = System.nanoTime();
                logger.info("Write ontology {} : {}", ontologyId,((endTime3 - startTime3) / 1000 / 1000 / 1000));

                loadedOntologyIds.add(ontologyId);

                // Extract version from the ontology node and write status file
                String version = null;
                if (graph.ontologyNode.properties.getPropertyValue("version") != null) {
                    version = graph.ontologyNode.properties.getPropertyValue("version").toString();
                }
                OntologyStatusWriter.writeSuccess(outputFilePath, ontologyId, version);

            } catch(Throwable t) {
                logger.error("Error processing ontology {}: {}", ontologyId, t.getMessage());
                t.printStackTrace();
                // Write status file for failed ontology - we'll update in merge section if fallback found
                OntologyStatusWriter.writeFailedNoFallback(outputFilePath, ontologyId, t.getMessage());

                if (mergeOutputWith == null) {
                    logger.info("No previous build available for fallback for: {}", ontologyId);
                } else {
                    logger.info("Will attempt to use previous build as fallback for: {}", ontologyId);
                    // Note: We'll update the status file to FALLBACK in the merge section if we find a previous version
                }
            }
        }

        if(mergeOutputWith != null) {

            // Need to look for any ontologies that we didn't load but were loaded last time, and
            // keep the old versions of them from the previous JSON file.
            logger.info("Adding previously loaded ontologies and fallbacks from {} (--mergeOutputWith)", mergeOutputWith);
            long startTime = System.nanoTime();

            JsonReader scanReader = new JsonReader(new InputStreamReader(new FileInputStream(mergeOutputWith)));
            JsonReader actualReader = new JsonReader(new InputStreamReader(new FileInputStream(mergeOutputWith)));

            scanReader.beginObject();
            actualReader.beginObject();

            while (scanReader.peek() != JsonToken.END_OBJECT) {
    
                String name = scanReader.nextName();
                actualReader.nextName();
    
                if (name.equals("ontologies")) {

                    scanReader.beginArray();
                    actualReader.beginArray();

                    while (scanReader.peek() != JsonToken.END_ARRAY) {

                        scanReader.beginObject();

                        String key = scanReader.nextName();

                        if(!key.equals("ontologyId")) {
                            throw new RuntimeException("mergeOutputWith does not look like rdf2json output?");
                        }

                        String ontologyId = scanReader.nextString().toLowerCase();

                        // There are two cases where we want to use the previous ontology data:
                        // 1. We didn't process this ontology at all in the current run
                        // 2. We tried to process this ontology but it failed (not in loadedOntologyIds)
                        if(!loadedOntologyIds.contains(ontologyId)) {
                            // Check if this was actually a failed ontology in the current run
                            boolean wasInConfig = mergedConfigs.containsKey(ontologyId);

                            Map<String,Object> ontology = gson.fromJson(actualReader, Map.class);

                            // Extract version for reporting
                            String fallbackVersion = null;
                            Object versionObj = ontology.get("version");
                            if (versionObj != null) {
                                if (versionObj instanceof String) {
                                    fallbackVersion = (String) versionObj;
                                } else if (versionObj instanceof Map) {
                                    fallbackVersion = (String) ((Map<?,?>) versionObj).get("value");
                                }
                            }

                            if (wasInConfig) {
                                logger.info("Using previous build as fallback for failed ontology: {}", ontologyId);
                                OntologyStatusWriter.writeFallback(outputFilePath, ontologyId, fallbackVersion,
                                    "Latest ontology version is failing to load, using the last successful version instead");
                            } else {
                                logger.info("Keeping output for ontology {} from previous run (--mergeOutputWith)", ontologyId);
                                // This is an ontology that wasn't in the config, so we just keep it
                                // Write success status for kept ontology
                                OntologyStatusWriter.writeSuccess(outputFilePath, ontologyId, fallbackVersion);
                            }

                            // If this is a fallback for a failed ontology, add a note about it
                            ontology.put("is_fallback", true); // this is to use in frontend to show a warning about outdated ontology
                            ontology.put("fallback_reason", "Latest ontology version is failing to load, using the last successful version instead");

                            writeGenericValue(writer, ontology);

                        } else {
                            actualReader.skipValue();
                        }

                        while(scanReader.peek() != JsonToken.END_OBJECT) {
                            scanReader.nextName();
                            scanReader.skipValue();
                        }

                        scanReader.endObject();
                    }

                    scanReader.endArray();
                    actualReader.endArray();
                }
            }

            long endTime = System.nanoTime();
            logger.info("time to merge output with previous run: {} s", ((endTime - startTime) / 1000 / 1000 / 1000));
        }


        writer.endArray();
        writer.endObject();

        writer.close();

        logger.info("RDF2JSON processing complete. Status file written alongside output.");
    }


    private static void writeGenericValue(JsonWriter writer, Object val) throws IOException {

        if(val instanceof Collection) {
            writer.beginArray();
            for(Object entry : ((Collection<Object>) val)) {
                writeGenericValue(writer, entry);
            }
            writer.endArray();
        } else if(val instanceof Map) {
            Map<String,Object> originalMap = (Map<String,Object>) val;
            Map<String,Object> orderedMap = new LinkedHashMap<>();
            
            // First write ontologyId if it exists
            if (originalMap.containsKey("ontologyId")) {
                orderedMap.put("ontologyId", originalMap.get("ontologyId"));
            }
            
            // Then write remaining keys in alphabetical order
            Map<String,Object> sortedRemainder = new TreeMap<>(originalMap);
            sortedRemainder.remove("ontologyId"); // Remove it to avoid duplication
            orderedMap.putAll(sortedRemainder);
            
            writer.beginObject();
            for(String k : orderedMap.keySet()) {
                writer.name(k);
                writeGenericValue(writer, orderedMap.get(k));
            }
            writer.endObject();
        } else if(val instanceof String) {
            writer.value((String) val);
        } else if(val instanceof Long) {
            writer.value((Long) val);
        } else if(val instanceof Integer) {
            writer.value((Integer) val);
        } else if(val instanceof Double) {
            writer.value((Double) val);
        } else if(val instanceof Boolean) {
            writer.value((Boolean) val);
        } else if(val == null) {
            writer.nullValue();
        } else {
            throw new RuntimeException("Unknown value type");
        }

    }


}
