
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.*;


import static uk.ac.ebi.ols.shared.DefinedFields.*;

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

        Option embeddingVectorFields = new Option(null, "addEmbeddingField", true, "embedding field to add, specified as model name:size e.g. text-embedding-3-small:1536");
        embeddingVectorFields.setRequired(true);
        embeddingVectorFields.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(embeddingVectorFields);

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



        var inputFilePath = cmd.getOptionValue("inputPath");

        // props already in the template config xml
        // or that we don't want in solr
        final var skipProps = Set.of("iri");

        Set<String> allProps = FindAllProperties.findAllProperties(
            inputFilePath,
            skipProps
        );

        FileUtils.copyDirectory(new File(solrConfigTemplatePathValue), new File(outPath));

        var schemaXml = FileUtils.readFileToString(
            new File(solrConfigTemplatePathValue + "/ols4_entities/conf/schema.xml"),
            "UTF-8"
        );

        schemaXml = schemaXml.replace(
            "[[OLS_FIELDS]]",
            makeFieldDefinitions(allProps, cmd.getOptionValues("addEmbeddingField"))
        );

        FileUtils.writeStringToFile(
            new File(outPath + "/ols4_entities/conf/schema.xml"),
            schemaXml,
            "UTF-8"
        );
    }

    static String makeFieldDefinitions(Set<String> allProps, String[] embeddingFields) {
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

        for(String ef : embeddingFields) {
            String[] parts = ef.split(":");
            if(parts.length != 2) {
                System.err.println("Invalid embedding field specification: " + ef);
                continue;
            }
            String modelName = parts[0];
            int embeddingVectorSize = Integer.parseInt(parts[1]);

            sb.append("    <fieldType name=\"knn_vector_" + modelName + "\" class=\"solr.DenseVectorField\" vectorDimension=\"" + embeddingVectorSize + "\" similarityFunction=\"cosine\"/>\n");
            sb.append("    <field name=\"embeddings_" + modelName + "\" type=\"knn_vector_" + modelName + "\" indexed=\"true\" stored=\"true\"/>");
        }

        return sb.toString();
    }
}



