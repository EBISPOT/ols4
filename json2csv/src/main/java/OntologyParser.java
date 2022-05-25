
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public abstract class OntologyParser {

    boolean parseClasses;

    public OntologyParser(boolean parseClasses) {
        this.parseClasses = parseClasses;
    }

    Gson gson = new Gson();

    void beginOntology(Map<String, Object> ontologyConfig, Map<String, Object> ontologyProperties) throws IOException {}
    void endOntology(Map<String, Object> ontologyConfig, Map<String, Object> ontologyProperties) throws IOException {}
    void handleClass(Map<String, Object> ontologyConfig, Map<String, Object> ontologyProperties, Map<String, Object> ontologyClass) throws IOException {}


    protected void parseOntologiesJson(String filename) throws IOException {

        InputStream in = new FileInputStream(filename);
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));

        reader.beginObject();

        while (!reader.peek().equals(JsonToken.END_OBJECT)) {

            String name = reader.nextName();

             if (name.equals("ontologies")) {

                reader.beginArray();

                while (reader.peek().equals(JsonToken.BEGIN_OBJECT)) {

                    parseOntology(reader);
                }

                reader.endArray();

            } else {
                reader.skipValue();
            }

        }

        reader.close();

    }

    private void parseOntology(JsonReader reader) throws IOException {

        Map<String, Object> config = null;
        Map<String, Object> properties = null;

        reader.beginObject();

        while (!reader.peek().equals(JsonToken.END_OBJECT)) {

            String name = reader.nextName();

            if(name.equals("config")) {
                config = gson.fromJson(reader, Map.class);
                continue;
            }

            if(name.equals("properties")) {
                properties = gson.fromJson(reader, Map.class);
                beginOntology(config, properties);

                continue;
            }

            if(name.equals("classes")) {

                if(this.parseClasses) {
                    reader.beginArray();

                    while (!reader.peek().equals(JsonToken.END_ARRAY)) {

                        Map<String, Object> ontologyClass = gson.fromJson(reader, Map.class);

                        handleClass(config, properties, ontologyClass);
                    }

                    reader.endArray();
                } else {
                    reader.skipValue();
                }

                continue;
            }
        }

        endOntology(config, properties);

        reader.endObject();
    }




}
