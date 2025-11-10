import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.*;


import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class JSON2Solr {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output JSON folder path");
        output.setRequired(true);
        options.addOption(output);

        Option embeddingsDbsPath = new Option(null, "embeddingDbsPath", true, "optional folder containing embeddings DuckDB databases");
        embeddingsDbsPath.setRequired(false);
        options.addOption(embeddingsDbsPath);

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

        String inputFilePath = cmd.getOptionValue("input");
        String outPath = cmd.getOptionValue("outDir");
        String embeddingsDbs = cmd.getOptionValue("embeddingDbsPath");

        Map<String, Embeddings> embeddings = new HashMap<>();

        if (embeddingsDbs != null) {
            File embeddingsDbsDir = new File(embeddingsDbs);
            for (File f : embeddingsDbsDir.listFiles()) {
                if (f.getName().endsWith(".db")) {
                    String modelName = f.getName().substring(0, f.getName().length() - ".db".length());
                    Embeddings e = new Embeddings();
                    e.loadEmbeddingsFromFile(f.getAbsolutePath());
                    embeddings.put(modelName, e);
                }
            }
        }

        SolrJsonWriter.writeSolrJson( inputFilePath, outPath, embeddings );
    }
}


