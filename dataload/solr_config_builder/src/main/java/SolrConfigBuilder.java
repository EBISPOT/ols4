
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

        Option manifestPath = new Option(null, "manifestPath", true, "path to manifest JSON from create-manifest");
        manifestPath.setRequired(true);
        options.addOption(manifestPath);

        Option solrConfigTemplatePath = new Option(null, "solrConfigTemplatePath", true, "path to solr_config_template folder");
        solrConfigTemplatePath.setRequired(true);
        options.addOption(solrConfigTemplatePath);

        Option embeddingDbsPath = new Option(null, "embeddingDbsPath", true, "optional folder containing embeddings Parquet files");
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

        var manifestFilePath = cmd.getOptionValue("manifestPath");

        // props already in the template config xml
        // or that we don't want in solr
        final var skipProps = Set.of("iri");

        Set<String> allProps = FindAllProperties.findAllPropertiesFromManifest(
            manifestFilePath
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
            sb.append("    <field name=\"embeddings_" + modelName + "\" type=\"knn_vector_" + modelName + "\" indexed=\"true\" stored=\"true\"/>\n");
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
        
        File[] parquetFiles = embeddingsDbsDir.listFiles((dir, name) -> name.endsWith(".parquet"));
        if (parquetFiles == null) {
            System.err.println("No Parquet files found in: " + embeddingDbsPath);
            return models;
        }
        
        for (File parquetFile : parquetFiles) {
            String modelName = parquetFile.getName().substring(0, parquetFile.getName().length() - ".parquet".length());
            
            try {
                int dimension = getEmbeddingDimension(parquetFile.getAbsolutePath());
                if (dimension > 0) {
                    models.put(modelName, dimension);
                    System.err.println("Found embedding model: " + modelName + " with dimension: " + dimension);
                } else {
                    System.err.println("Could not determine dimension for model: " + modelName);
                }
            } catch (Exception e) {
                System.err.println("Error processing Parquet file " + parquetFile.getName() + ": " + e.getMessage());
            }
        }
        
        return models;
    }
    
    static int getEmbeddingDimension(String parquetPath) {
        try {
            org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
            org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(parquetPath);
            
            org.apache.parquet.hadoop.example.GroupReadSupport readSupport = new org.apache.parquet.hadoop.example.GroupReadSupport();
            
            try (org.apache.parquet.hadoop.ParquetReader<org.apache.parquet.example.data.Group> reader = 
                    org.apache.parquet.hadoop.ParquetReader.builder(readSupport, path)
                        .withConf(conf)
                        .build()) {
                
                org.apache.parquet.example.data.Group group = reader.read();
                if (group != null) {
                    // Read the embedding array to determine dimension
                    org.apache.parquet.example.data.Group embeddingGroup = group.getGroup("embedding", 0);
                    int dimension = embeddingGroup.getFieldRepetitionCount("list");
                    return dimension;
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading Parquet file for embedding dimension: " + e.getMessage());
        }
        
        return -1; // Indicate failure
    }
}



