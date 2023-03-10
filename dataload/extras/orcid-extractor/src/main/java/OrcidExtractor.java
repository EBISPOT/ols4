
import org.apache.commons.cli.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.CloseShieldFilterInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class OrcidExtractor {

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "orcid data dump zip path");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "output", true, "orcid mappings json output filename");
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



        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();


        String inputFilePath = cmd.getOptionValue("input");
        String outputFilePath = cmd.getOptionValue("output");

        ZipFile zipFile = new ZipFile(inputFilePath);

        var entries = zipFile.entries();

        while(entries.hasMoreElements()) {

            ZipEntry zipEntry = entries.nextElement();

            String name = zipEntry.getName();

            if(name.endsWith("_summaries.tar.gz")) {

                System.out.println(name);

                TarArchiveInputStream tarInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(zipFile.getInputStream(zipEntry)));

                for(TarArchiveEntry tarEntry = tarInputStream.getNextTarEntry(); tarEntry != null; tarEntry = tarInputStream.getNextTarEntry()) {

                    String tarEntryName = tarEntry.getName();

                    if(tarEntryName.endsWith(".xml")) {
                        System.out.println(tarEntryName);

                        Document doc = builder.parse(new InputSource(new CloseShieldFilterInputStream(tarInputStream)));

                        try{
                            String givenName = xpath.evaluate("/*[local-name()='record']/*[local-name()='person']/*[local-name()='name']/*[local-name()='given-names']/text()", doc);
                            String familyName = xpath.evaluate("/*[local-name()='record']/*[local-name()='person']/*[local-name()='name']/*[local-name()='family-name']/text()", doc);
                            
                        } catch (XPathExpressionException e) {
                            continue;
                        }


//                        IOUtils.copyRange(tarInputStream, tarEntry.getSize(), new FileOutputStream(outputFilePath));
//                        System.exit(0);
                    }
                }

                break;
            }
        }

    }
}


