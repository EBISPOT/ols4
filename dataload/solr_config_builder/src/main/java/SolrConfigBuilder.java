
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import uk.ac.ebi.ols.shared.DefinedFields;

import java.io.*;
import java.util.*;

public class SolrConfigBuilder {

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option manifestPath = new Option(null, "manifestPath", true, "path to manifest JSON from create-manifest");
        manifestPath.setRequired(true);
        options.addOption(manifestPath);

        Option solrConfigTemplatePath = new Option(null, "solrConfigTemplatePath", true, "path to solr_config_template folder");
        solrConfigTemplatePath.setRequired(true);
        options.addOption(solrConfigTemplatePath);

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

        var manifestFilePath = cmd.getOptionValue("manifestPath");

        // props already in the template config xml
        // or that we don't want in solr
        final var skipProps = Set.of("iri");

        Set<String> allProps = FindAllProperties.findAllPropertiesFromManifest(
            manifestFilePath
        );

        allProps.removeAll(skipProps);
        
        // Add defined fields that are added by LinkerPass2 and won't be in the manifest
        // These fields are programmatically added during the linking phase
        for (DefinedFields field : DefinedFields.values()) {
            String fieldName = field.getText();
            if (!skipProps.contains(fieldName)) {
                allProps.add(fieldName);
            }
        }

        FileUtils.copyDirectory(new File(solrConfigTemplatePathValue), new File(outPath));

        var schemaXml = FileUtils.readFileToString(
            new File(solrConfigTemplatePathValue + "/ols4_entities/conf/schema.xml"),
            "UTF-8"
        );

        schemaXml = schemaXml.replace(
            "[[OLS_FIELDS]]",
            makeFieldDefinitions(allProps)
        );

        FileUtils.writeStringToFile(
            new File(outPath + "/ols4_entities/conf/schema.xml"),
            schemaXml,
            "UTF-8"
        );
    }

    static String makeFieldDefinitions(Set<String> allProps) {
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

        return sb.toString();
    }

}



