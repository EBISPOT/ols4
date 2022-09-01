package uk.ac.ebi.owl2json;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.cli.*;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OWL2JSON {

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option optConfigs = new Option(null, "config", true, "config JSON filename(s) separated by a comma. subsequent configs are merged with/override previous ones.");
        optConfigs.setRequired(true);
        options.addOption(optConfigs);

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
            formatter.printHelp("owl2json", options);

            System.exit(1);
            return;
        }

        List<String> configFilePaths = Arrays.asList(cmd.getOptionValue("config").split(","));
        String outputFilePath = cmd.getOptionValue("output");
        boolean bLoadLocalFiles = cmd.hasOption("loadLocalFiles");
        boolean bNoDates = cmd.hasOption("noDates");


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

            JsonReader reader = new JsonReader(
                    new InputStreamReader(inputStream));

            return (InputJson) gson.fromJson(reader, InputJson.class);

        }).collect(Collectors.toList());


        LinkedHashMap<String, Map<String,Object>> mergedConfigs = new LinkedHashMap<>();

        for(InputJson config : configs) {

            for(Map<String,Object> ontologyConfig : config.ontologies) {

                String ontologyId = (String) ontologyConfig.get("id");

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
        writer.name("ontologies");
        writer.beginArray();

        for(var ontoConfig : mergedConfigs.values()) {
            System.out.println("--- Loading ontology: " + (String)ontoConfig.get("id"));
            try {
                OwlTranslator translator = new OwlTranslator(ontoConfig, bLoadLocalFiles, bNoDates);
		translator.write(writer);
            } catch(Throwable t) {
                 t.printStackTrace();
            }
        }

        writer.endArray();
        writer.endObject();

        writer.close();
    }

}
