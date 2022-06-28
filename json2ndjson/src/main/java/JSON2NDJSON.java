import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.jena.vocabulary.RDF;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class JSON2NDJSON {

    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Options options = new Options();

        Option input = new Option(null, "input", true, "ontologies JSON input filename");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option(null, "outDir", true, "output NDJSON folder path");
        output.setRequired(true);
        options.addOption(output);

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
        String outPath = cmd.getOptionValue("outDir");

        PrintStream ontologiesWriter = null;
        PrintStream classesWriter = null;


        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFilePath)));

        reader.beginObject();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while(reader.peek() != JsonToken.END_ARRAY) {

                    reader.beginObject(); // ontology

                    JsonOntology ontology = new JsonOntology();

                    while(reader.peek() != JsonToken.END_OBJECT) {

                        String key = reader.nextName();

                        if(key.equals("ontologyConfig")) {

                            ontology.ontologyConfig = gson.fromJson(reader, Map.class);

                            String ontologiesOutName = outPath + "/" + ((String)ontology.ontologyConfig.get("id")) + "_ontologies.ndjson";
                            String classesOutName = outPath + "/" + ((String)ontology.ontologyConfig.get("id")) + "_classes.ndjson";

                            ontologiesWriter = new PrintStream(ontologiesOutName);

                            classesWriter = new PrintStream(classesOutName);


                        } else if(key.equals("ontologyProperties")) {
                            ontology.ontologyProperties = gson.fromJson(reader, Map.class);
                        } else if(key.equals("classes")) {

                            reader.beginArray();

                            while(reader.peek() != JsonToken.END_ARRAY) {

                                Map<String,Object> _class = gson.fromJson(reader, Map.class);
                                //classesWriter.println("{\"index\": {}}");
                                classesWriter.println(gson.toJson(_class));

                            }

                            reader.endArray();

                        } else {
                            reader.skipValue();
                        }



                    }

                    reader.endObject(); // ontology
                }

                reader.endArray();

            } else {

                reader.skipValue();

            }
        }

        reader.endObject();
        reader.close();
    }


}


