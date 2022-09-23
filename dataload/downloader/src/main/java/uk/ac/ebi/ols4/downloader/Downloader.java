package uk.ac.ebi.ols4.downloader;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Downloader {

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option optConfigs = new Option(null, "config", true, "config JSON filename(s) separated by a comma. subsequent configs are merged with/override previous ones.");
        optConfigs.setRequired(true);
        options.addOption(optConfigs);

        Option optDownloadPath = new Option(null, "downloadPath", true, "Download path to store downloaded ontologies");
        optDownloadPath.setRequired(true);
        options.addOption(optDownloadPath);

        Option loadLocalFiles = new Option(null, "loadLocalFiles", false, "Whether or not to load local files (unsafe, for testing)");
        loadLocalFiles.setRequired(false);
        options.addOption(loadLocalFiles);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("downloader", options);

            System.exit(1);
            return;
        }

        List<String> configFilePaths = Arrays.asList(cmd.getOptionValue("config").split(","));
        boolean bLoadLocalFiles = cmd.hasOption("loadLocalFiles");
        String downloadPath = cmd.getOptionValue("downloadPath");


        System.out.println("Configs: " + configFilePaths);
        System.out.println("Download path: " + downloadPath);

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


        Set<String> ontologyUrls = new LinkedHashSet<>();

        for(Map<String,Object> config : mergedConfigs.values()) {

            String url = (String) config.get("ontology_purl");

            if(url == null) {

                Collection<Map<String,Object>> products =
                    (Collection<Map<String,Object>>) config.get("products");

                if(products != null) {
                    for(Map<String,Object> product : products) {

                        String purl = (String) product.get("ontology_purl");

                        if(purl != null && purl.endsWith(".owl")) {
                            url = purl;
                            break;
                        }

                    }
                }
            }

            if(url != null)
                ontologyUrls.add(url);
        }

            
        BulkOntologyDownloader downloader = new BulkOntologyDownloader(List.copyOf(ontologyUrls), downloadPath, bLoadLocalFiles);

        downloader.downloadAll();

    }

}
