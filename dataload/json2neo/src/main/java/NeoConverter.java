import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class NeoConverter {

    static Gson gson = new Gson();

    String inputFilePath;
    String outputFilePath;

    public NeoConverter(String inputFilePath, String outputFilePath) throws FileNotFoundException {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
    }


    JsonReader extractorReader;
    JsonReader reader;

    public void convert() throws IOException {

        extractorReader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));
        reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject(); extractorReader.beginObject();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName(); extractorReader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray(); extractorReader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {


                    System.out.println("Scanning ontology...");

                    OntologyScanner.Result ontologyScannerResult =
                            OntologyScanner.scanOntology(extractorReader);

                    System.out.println("Ontology scan complete for " + ontologyScannerResult.ontologyId);

                    new OntologyWriter(reader, outputFilePath, ontologyScannerResult).write();

                    System.out.println("OntologyWriter complete for " + ontologyScannerResult.ontologyId);
                }

                reader.endArray(); extractorReader.endArray();

            } else {

                reader.skipValue(); extractorReader.skipValue();

            }
        }

        reader.endObject();
        reader.close();
    }

}


