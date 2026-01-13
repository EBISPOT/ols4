import com.google.gson.Gson;

import uk.ac.ebi.ols.shared.Embeddings;

import org.apache.commons.cli.*;

import java.io.*;
import java.util.*;

public class JSON2CSV {

    static Gson gson = new Gson();


    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option ontologyIdOpt = new Option(null, "ontologyId", true, "ontology ID to process");
        ontologyIdOpt.setRequired(false);
        options.addOption(ontologyIdOpt);

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output CSV folder path");
        output.setRequired(true);
        options.addOption(output);
        
        Option manifest = new Option(null, "manifest", true, "manifest JSON file from create-manifest");
        manifest.setRequired(true);
        options.addOption(manifest);

        Option embeddingsDbsPath = new Option(null, "embeddingDbsPath", true, "optional folder containing embeddings Parquet files");
        embeddingsDbsPath.setRequired(false);
        options.addOption(embeddingsDbsPath);

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

        String ontologyId = cmd.getOptionValue("ontologyId");
        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("outDir");
        String manifestFilePath = cmd.getOptionValue("manifest");
        String embeddingsDbs = cmd.getOptionValue("embeddingDbsPath");

        Map<String, Embeddings> embeddings = new HashMap<>();

        if (embeddingsDbs != null) {
            File embeddingsDbsDir = new File(embeddingsDbs);
            for (File f : embeddingsDbsDir.listFiles()) {
                if (f.getName().endsWith(".parquet")) {
                    System.err.println("Loading embeddings from " + f.getAbsolutePath());
                    String modelName = f.getName().substring(0, f.getName().length() - ".parquet".length());
                    Embeddings e = new Embeddings();
                    e.loadEmbeddingsFromFile(f.getAbsolutePath(), ontologyId);

                    System.out.println("Loaded embeddings model " + modelName + " with " + e.embeddingsCache.size() + " entries for ontology id " + ontologyId);

                    embeddings.put(modelName, e);
                }
            }
            System.err.println("Loaded " + embeddings.size() + " embeddings databases from " + embeddingsDbsDir.listFiles().length + " files");
        } else {
            System.err.println("No embeddings path provided, skipping embeddings load.");
        }

        try {
            new NeoConverter(ontologyId, inputFilePath, outputFilePath, manifestFilePath, embeddings).convert();
        } catch (Exception e) {
            System.err.println("ERROR: Failed to convert JSON to CSV");
            e.printStackTrace();
            System.exit(1);
        }
    }

}


