package uk.ac.ebi.rdf2json;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.cli.*;

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

        Option noDates = new Option(null, "noDates", false, "Set to leave LOADED dates blank (for testing)");
        noDates.setRequired(false);
        options.addOption(noDates);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("rdf2json", options);

            System.exit(1);
            return;
        }

        List<String> configFilePaths = Arrays.asList(cmd.getOptionValue("config").split(","));
        String outputFilePath = cmd.getOptionValue("output");

	    String downloadedPath = cmd.getOptionValue("downloadedPath");
        boolean bLoadLocalFiles = cmd.hasOption("loadLocalFiles");
        boolean bNoDates = cmd.hasOption("noDates");
        String mergeOutputWith = cmd.getOptionValue("mergeOutputWith");


        System.out.println("Configs: " + configFilePaths);
        System.out.println("Output: " + outputFilePath);

        Gson gson = new Gson();

        List<InputJson> configs = configFilePaths.stream().map(configPath -> {

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
            System.out.println("--- Loading ontology: " + ontologyId);

            try {

                OntologyGraph graph = new OntologyGraph(ontoConfig, bLoadLocalFiles, bNoDates, downloadedPath);

                if(graph.ontologyNode == null) {
                    System.out.println("No Ontology node found; nothing will be written");
                    continue;
                }

                long startTime3 = System.nanoTime();
                System.out.println("Writing ontology: " + ontologyId);
                graph.write(writer);
                long endTime3 = System.nanoTime();
                System.out.println("Write ontology " + ontologyId + ": " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));

                loadedOntologyIds.add(ontologyId);

            } catch(Throwable t) {
                 t.printStackTrace();
            }
        }

        if(mergeOutputWith != null) {

            // Need to look for any ontologies that we didn't load but were loaded last time, and
            // keep the old versions of them from the previous JSON file.

            System.out.println("Adding previously loaded ontologies from " + mergeOutputWith + " (--mergeOutputWith)");
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

                        if(!loadedOntologyIds.contains(ontologyId)) {

                            System.out.println("Keeping output for ontology " + ontologyId + " from previous run (--mergeOutputWith)");

                            Map<String,Object> ontology = gson.fromJson(actualReader, Map.class);
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
            System.out.println("time to merge output with previous run: " + ((endTime - startTime) / 1000 / 1000 / 1000) + "s");
        }


        writer.endArray();
        writer.endObject();

        writer.close();
    }


    private static void writeGenericValue(JsonWriter writer, Object val) throws IOException {

        if(val instanceof Collection) {
            writer.beginArray();
            for(Object entry : ((Collection<Object>) val)) {
                writeGenericValue(writer, entry);
            }
            writer.endArray();
        } else if(val instanceof Map) {
            Map<String,Object> map = new TreeMap<String,Object> ( (Map<String,Object>) val );
            writer.beginObject();
            for(String k : map.keySet()) {
                writer.name(k);
                writeGenericValue(writer, map.get(k));
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
