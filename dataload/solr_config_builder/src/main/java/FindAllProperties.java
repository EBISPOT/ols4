import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;

public class FindAllProperties {

    public static Set<String> findAllPropertiesFromManifest(String manifestJsonPath) throws IOException {

        Gson gson = new Gson();
        LinkerPass1Result manifest = gson.fromJson(
            new InputStreamReader(new FileInputStream(manifestJsonPath)), 
            LinkerPass1Result.class
        );

        Set<String> allProperties = new HashSet<>();

        // Collect all properties from all ontologies in the manifest
        for (Set<String> props : manifest.ontologyIdToOntologyProperties.values()) {
            allProperties.addAll(props);
        }
        for (Set<String> props : manifest.ontologyIdToClassProperties.values()) {
            allProperties.addAll(props);
        }
        for (Set<String> props : manifest.ontologyIdToPropertyProperties.values()) {
            allProperties.addAll(props);
        }
        for (Set<String> props : manifest.ontologyIdToIndividualProperties.values()) {
            allProperties.addAll(props);
        }

        return allProperties;
    }
    
}
