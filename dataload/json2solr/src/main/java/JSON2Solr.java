import com.google.gson.Gson;

import uk.ac.ebi.ols.shared.Embeddings;

import org.apache.commons.cli.*;

import java.io.*;
import java.util.*;


public class JSON2Solr {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option ontologyIdOpt = new Option(null, "ontologyId", true, "ontology ID");
        ontologyIdOpt.setRequired(true);
        options.addOption(ontologyIdOpt);

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output JSON folder path");
        output.setRequired(true);
        options.addOption(output);

        Option embeddingsDbsPath = new Option(null, "embeddingDbsPath", true, "optional folder containing embeddings Parquet files");
        embeddingsDbsPath.setRequired(false);
        options.addOption(embeddingsDbsPath);

        Option maxRowsPerFileOpt = new Option(null, "maxRowsPerFile", true, "maximum number of rows per output file");
        maxRowsPerFileOpt.setRequired(true);
        options.addOption(maxRowsPerFileOpt);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("json2solr", options);

            System.exit(1);
            return;
        }

        String ontologyId = cmd.getOptionValue("ontologyId");
        String inputFilePath = cmd.getOptionValue("input");
        String outPath = cmd.getOptionValue("outDir");
        String embeddingsDbs = cmd.getOptionValue("embeddingDbsPath");
        int maxRowsPerFile = Integer.parseInt(cmd.getOptionValue("maxRowsPerFile"));

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
            System.err.println("Loaded " +  embeddings.size() + " embeddings databases from " + embeddingsDbsDir.listFiles().length + " files");
        } else {
            System.err.println("No embeddings path provided, skipping embeddings load.");
        }

        System.out.println("calling writeSolrJson with " + embeddings.size() + " embedding models");

        SolrJsonWriter.writeSolrJson( ontologyId, inputFilePath, outPath, embeddings, maxRowsPerFile );
    }
}


