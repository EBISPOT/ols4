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

        System.out.println("Config: " + configFilePath);
        System.out.println("Output: " + outputFilePath);

        Gson gson = new Gson();

        JsonReader reader = new JsonReader(
                new InputStreamReader(new URL(configFilePath).openStream())
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
                OwlTranslator translator = new OwlTranslator(ontoConfig);
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
