import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Linker {

    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "unlinked ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "output", true, "linked ontologies JSON output filename");
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
            formatter.printHelp("linker", options);

            System.exit(1);
            return;
        }

        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("output");
        String leveldb_path = cmd.getOptionValue("leveldbPath");

        LevelDB leveldb = leveldb_path != null ? new LevelDB(leveldb_path) : null;

        try {

    //        LinkerPass1.LinkerPass1Result pass1Result = gson.fromJson(new InputStreamReader(new FileInputStream("/Users/james/ols4/linked.json")), LinkerPass1.LinkerPass1Result.class);
            LinkerPass1.LinkerPass1Result pass1Result = LinkerPass1.run(inputFilePath);

    //        gson.toJson(pass1Result, new FileWriter(outputFilePath));
    //        Files.write(Path.of(outputFilePath), gson.toJson(pass1Result).getBytes(StandardCharsets.UTF_8));

            LinkerPass2.run(inputFilePath, outputFilePath, leveldb, pass1Result);

        } finally {
            if(leveldb != null)
                leveldb.close();
        }
    }
}


