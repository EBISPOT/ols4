import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Link {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option manifest = new Option(null, "manifest", true, "input manifest JSON file (from create-manifest)");
        manifest.setRequired(true);
        options.addOption(manifest);

        Option input = new Option(null, "input", true, "unlinked ontology JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "output", true, "linked ontology JSON output filename");
        output.setRequired(true);
        options.addOption(output);

        Option leveldbPath = new Option(null, "leveldbPath", true, "optional path of leveldb containing extra mappings (for ORCID etc.)");
        leveldbPath.setRequired(false);
        options.addOption(leveldbPath);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("link", options);
            System.exit(1);
            return;
        }

        String manifestFilePath = cmd.getOptionValue("manifest");
        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("output");
        String leveldb_path = cmd.getOptionValue("leveldbPath");

        LevelDB leveldb = leveldb_path != null ? new LevelDB(leveldb_path) : null;

        try {
            System.out.println("Loading manifest from: " + manifestFilePath);
            LinkerPass1Result pass1Result = gson.fromJson(
                new InputStreamReader(new FileInputStream(manifestFilePath)), 
                LinkerPass1Result.class
            );

            System.out.println("Linking ontology from: " + inputFilePath);
            LinkerPass2.run(inputFilePath, outputFilePath, leveldb, pass1Result);

            System.out.println("Linking complete. Output written to: " + outputFilePath);
        } finally {
            if(leveldb != null)
                leveldb.close();
        }
    }
}
