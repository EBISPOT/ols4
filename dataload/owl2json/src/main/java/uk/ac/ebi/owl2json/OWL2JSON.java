package uk.ac.ebi.owl2json;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.cli.*;

import java.io.*;
import java.net.URL;

public class OWL2JSON {

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option optConfig = new Option(null, "config", true, "config JSON filename");
        optConfig.setRequired(true);
        options.addOption(optConfig);

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

        String configFilePath = cmd.getOptionValue("config");
        String outputFilePath = cmd.getOptionValue("output");
        boolean bLoadLocalFiles = cmd.hasOption("loadLocalFiles");
        boolean bNoDates = cmd.hasOption("noDates");


        System.out.println("Config: " + configFilePath);
        System.out.println("Output: " + outputFilePath);

        Gson gson = new Gson();

	InputStream inputStream;

	if(configFilePath.contains("://")) {
		inputStream = new URL(configFilePath).openStream();
	} else {
		inputStream = new FileInputStream(configFilePath);
	}

        JsonReader reader = new JsonReader(
                new InputStreamReader(inputStream)
        );

        InputJson config = gson.fromJson(reader, InputJson.class);

        JsonWriter writer = new JsonWriter(new FileWriter(outputFilePath));
        writer.setIndent("  ");

        writer.beginObject();
        writer.name("ontologies");
        writer.beginArray();

        for(var ontoConfig : config.ontologies) {
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
