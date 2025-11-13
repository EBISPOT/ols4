
import com.google.gson.Gson;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Properties;

public class SolrConfigBuilder {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option inputPath = new Option(null, "inputPath", true, "path to ontologies json");
        inputPath.setRequired(true);
        options.addOption(inputPath);

        Option solrConfigTemplatePath = new Option(null, "solrConfigTemplatePath", true, "path to solr_config_template folder");
        solrConfigTemplatePath.setRequired(true);
        options.addOption(solrConfigTemplatePath);

        Option embeddingDbsPath = new Option(null, "embeddingDbsPath", true, "optional folder containing embeddings DuckDB databases");
        embeddingDbsPath.setRequired(false);
        options.addOption(embeddingDbsPath);

        Option output = new Option(null, "outDir", true, "output solr config path");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("solr_config_builder", options);

            System.exit(1);
            return;
        }


        String solrConfigTemplatePathValue = cmd.getOptionValue("solrConfigTemplatePath");
        String outPath = cmd.getOptionValue("outDir");
        String embeddingsDbs = cmd.getOptionValue("embeddingDbsPath");

        var inputFilePath = cmd.getOptionValue("inputPath");

        // props already in the template config xml
        // or that we don't want in solr
        final var skipProps = Set.of("iri");

        Set<String> allProps = FindAllProperties.findAllProperties(
            inputFilePath,
            skipProps
        );

        // Process embedding databases to get model names and dimensions
        Map<String, Integer> embeddingModels = new HashMap<>();
        if (embeddingsDbs != null) {
            embeddingModels = discoverEmbeddingModels(embeddingsDbs);
        }

        FileUtils.copyDirectory(new File(solrConfigTemplatePathValue), new File(outPath));

        var schemaXml = FileUtils.readFileToString(
            new File(solrConfigTemplatePathValue + "/ols4_entities/conf/schema.xml"),
            "UTF-8"
        );

        schemaXml = schemaXml.replace(
            "[[OLS_FIELDS]]",
            makeFieldDefinitions(allProps, embeddingModels)
        );

        FileUtils.writeStringToFile(
            new File(outPath + "/ols4_entities/conf/schema.xml"),
            schemaXml,
            "UTF-8"
        );
    }

    static String makeFieldDefinitions(Set<String> allProps, Map<String, Integer> embeddingModels) {
        StringBuilder sb = new StringBuilder();
        for (String prop : allProps) {

            prop = prop.replaceAll(":", "__");

            sb.append("    <field name=\"").append(prop).append("\" type=\"string\" indexed=\"true\" stored=\"true\" multiValued=\"true\" />\n");
            sb.append("    <copyField source=\"").append(prop).append("\" dest=\"str_").append(prop).append("\"/>\n");
            sb.append("    <copyField source=\"").append(prop).append("\" dest=\"lowercase_").append(prop).append("\"/>\n");
            sb.append("    <copyField source=\"").append(prop).append("\" dest=\"edge_").append(prop).append("\"/>\n");
            sb.append("    <copyField source=\"").append(prop).append("\" dest=\"whitespace_").append(prop).append("\"/>\n");
            sb.append("    <copyField source=\"").append(prop).append("\" dest=\"whitespace_edge_").append(prop).append("\"/>\n");
        }

        for(Map.Entry<String, Integer> entry : embeddingModels.entrySet()) {
            String modelName = entry.getKey();
            int embeddingVectorSize = entry.getValue();

            sb.append("    <fieldType name=\"knn_vector_" + modelName + "\" class=\"solr.DenseVectorField\" vectorDimension=\"" + embeddingVectorSize + "\" similarityFunction=\"cosine\"/>\n");
            sb.append("    <field name=\"embeddings_" + modelName + "\" type=\"knn_vector_" + modelName + "\" indexed=\"true\" stored=\"false\"/>\n");
        }

        return sb.toString();
    }

    static Map<String, Integer> discoverEmbeddingModels(String embeddingDbsPath) {
        Map<String, Integer> models = new HashMap<>();
        
        File embeddingsDbsDir = new File(embeddingDbsPath);
        if (!embeddingsDbsDir.exists() || !embeddingsDbsDir.isDirectory()) {
            System.err.println("Embeddings directory does not exist or is not a directory: " + embeddingDbsPath);
            return models;
        }
        
        File[] duckdbFiles = embeddingsDbsDir.listFiles((dir, name) -> name.endsWith(".duckdb"));
        if (duckdbFiles == null) {
            System.err.println("No DuckDB files found in: " + embeddingDbsPath);
            return models;
        }
        
        for (File duckdbFile : duckdbFiles) {
            String modelName = duckdbFile.getName().substring(0, duckdbFile.getName().length() - ".duckdb".length());
            
            try {
                int dimension = getEmbeddingDimension(duckdbFile.getAbsolutePath());
                if (dimension > 0) {
                    models.put(modelName, dimension);
                    System.err.println("Found embedding model: " + modelName + " with dimension: " + dimension);
                } else {
                    System.err.println("Could not determine dimension for model: " + modelName);
                }
            } catch (Exception e) {
                System.err.println("Error processing DuckDB file " + duckdbFile.getName() + ": " + e.getMessage());
            }
        }
        
        return models;
    }
    
    static int getEmbeddingDimension(String duckdbPath) {
        try {
            Properties readOnlyProperty = new Properties();
            readOnlyProperty.setProperty("duckdb.read_only", "true");
            
            try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + duckdbPath, readOnlyProperty)) {
                // Query to get the dimension of the embedding column
                // We'll get the first embedding to determine its length
                String query = "SELECT embedding FROM terms_embedded LIMIT 1";
                try (var stmt = connection.prepareStatement(query)) {
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        java.sql.Array sqlArray = rs.getArray("embedding");
                        if (sqlArray != null) {
                            Object[] objArray = (Object[]) sqlArray.getArray();
                            return objArray.length;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error querying DuckDB for embedding dimension: " + e.getMessage());
        }
        
        return -1; // Indicate failure
    }
}



