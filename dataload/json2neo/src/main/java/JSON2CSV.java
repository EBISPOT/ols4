import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import uk.ac.ebi.ols.shared.Embeddings;

import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class JSON2CSV {

    static Gson gson = new Gson();


    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output CSV folder path");
        output.setRequired(true);
        options.addOption(output);
        
        Option manifest = new Option(null, "manifest", true, "manifest JSON file from create-manifest");
        manifest.setRequired(true);
        options.addOption(manifest);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("json2csv", options);

            System.exit(1);
            return;
        }

        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("outDir");
        String manifestFilePath = cmd.getOptionValue("manifest");

        try {
            new NeoConverter(inputFilePath, outputFilePath, manifestFilePath).convert();
        } catch (Exception e) {
            System.err.println("ERROR: Failed to convert JSON to CSV");
            e.printStackTrace();
            System.exit(1);
        }
    }

}


