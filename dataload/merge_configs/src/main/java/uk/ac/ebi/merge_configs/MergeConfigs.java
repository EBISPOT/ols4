package uk.ac.ebi.merge_configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class MergeConfigs {

    private static final Logger logger = LoggerFactory.getLogger(MergeConfigs.class);

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option optConfigs = new Option(null, "config", true, "config JSON filename(s) separated by a comma. subsequent configs are merged with/override previous ones.");
        optConfigs.setRequired(true);
        options.addOption(optConfigs);

        Option output = new Option(null, "output", true, "JSON output filename for merged config");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error(e.getMessage());
            formatter.printHelp("merge_configs", options);
            System.exit(1);
            return;
        }

        List<String> configFilePaths = Arrays.asList(cmd.getOptionValue("config").split(","));
        String outputFilePath = cmd.getOptionValue("output");

        logger.info("Merging configs: {}", configFilePaths);
        logger.info("Output: {}", outputFilePath);

        Gson gson = new Gson();

        // Load all config files
        List<InputJson> configs = new ArrayList<>();
        for (String configPath : configFilePaths) {
            configPath = configPath.trim();
            
            if(configPath.endsWith(".json")) { 
                InputStream inputStream;

                try {
                    if (configPath.contains("://")) {
                        inputStream = new URL(configPath).openStream();
                    } else {
                        inputStream = new FileInputStream(configPath);
                    }
                } catch(IOException e) {
                    throw new RuntimeException("Error loading config file: " + configPath, e);
                }

                JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
                InputJson config = gson.fromJson(reader, InputJson.class);
                configs.add(config);
            } else {
                throw new RuntimeException("Only JSON config files are supported: " + configPath);
            }
        }

        // Merge configs with override logic
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

        // Create output JSON structure
        Map<String, Object> outputJson = new LinkedHashMap<>();
        outputJson.put("ontologies", new ArrayList<>(mergedConfigs.values()));

        // Write merged config to output file
        try (FileWriter writer = new FileWriter(outputFilePath)) {
            Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
            prettyGson.toJson(outputJson, writer);
        }

        logger.info("Merged {} ontologies to {}", mergedConfigs.size(), outputFilePath);
    }
}
