import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import uk.ac.ebi.ols.shared.OntologyScanner;

public class FindAllProperties {

    public static Set<String> findAllProperties(String ontologiesJsonPath, Set<String> ignoreProperties) throws IOException {

        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(ontologiesJsonPath)));

        Set<String> allProperties = new HashSet<>();

        reader.beginObject();

        while (reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("ontologies")) {

                reader.beginArray();

                while (reader.peek() != JsonToken.END_ARRAY) {

                    OntologyScanner.Result ontologyScannerResult =
                            OntologyScanner.scanOntology(reader, ignoreProperties);

                    allProperties.addAll(ontologyScannerResult.allOntologyProperties);
                    allProperties.addAll(ontologyScannerResult.allClassProperties);
                    allProperties.addAll(ontologyScannerResult.allIndividualProperties);
                    allProperties.addAll(ontologyScannerResult.allPropertyProperties);
                }

                reader.endArray();
            }
        }

        reader.endObject();

        return allProperties;

    }
    
}
