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

//        LinkerPass1.LinkerPass1Result pass1Result = gson.fromJson(new InputStreamReader(new FileInputStream("/Users/james/ols4/linked.json")), LinkerPass1.LinkerPass1Result.class);
        LinkerPass1.LinkerPass1Result pass1Result = LinkerPass1.run(inputFilePath);

        Files.write(Path.of(outputFilePath), gson.toJson(pass1Result).getBytes(StandardCharsets.UTF_8));
//
        System.gc();
//
        LinkerPass2.LinkerPass2Result pass2Result = LinkerPass2.run(inputFilePath, pass1Result);

        Files.write(Path.of(outputFilePath+ "pass2.json"), gson.toJson(pass2Result).getBytes(StandardCharsets.UTF_8));

//        LinkerPass2.LinkerPass2Result pass2Result = gson.fromJson(new InputStreamReader(new FileInputStream("/Users/james/ols4/linked.jsonpass2.json")), LinkerPass2.LinkerPass2Result.class);

        LinkerPass3.run(inputFilePath, "/Users/james/ols4/pass3.json", pass1Result, pass2Result);

    }
}


