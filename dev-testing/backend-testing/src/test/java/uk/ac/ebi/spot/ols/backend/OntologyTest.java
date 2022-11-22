package uk.ac.ebi.spot.ols.backend;

import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OntologyTest {
    private static final String ONTOLOGIES_API_REQUEST = "/api/v2/ontologies";
    private static final String BACKEND_URL = "http://localhost:8080";

    /**
     * This should become a parameterized test accepting an ontologyId as parameter. The result should be compared to the expected api
     * output. This should depend on the apitester4 and perhaps move there.
     *
     * Before this can work we need to:
     * 1. split generation of expected output for ontologies for each ontology to be in its own file.
     * 2. docker needs to work on Linux (so that Henriette can actually run this)
     * 3. apitester4 should be extended to allow testing of single ontology. Currently it only tests
     *
     * @param ontologyId
     */
    @Test
    void getOntologyTest(/*String ontologyId*/) {
//        String requestURL = BACKEND_URL + ONTOLOGIES_API_REQUEST + "/" + ontologyId;
        String requestURL = BACKEND_URL + ONTOLOGIES_API_REQUEST + "/owl2primer-minimal";

        try {
            JsonElement jsonResponse = get(requestURL);
            int i = 0;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static JsonElement get(String url) throws IOException {

        System.out.println("GET " + url);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        if (100 <= conn.getResponseCode() && conn.getResponseCode() <= 399) {
            InputStream is = conn.getInputStream();
            Reader reader = new InputStreamReader(is, "UTF-8");
            JsonElement result = JsonParser.parseReader(reader);
            return result;
        } else {
            InputStream is = conn.getErrorStream();
            Reader reader = new InputStreamReader(is, "UTF-8");
            JsonObject error = new JsonObject();
            error.addProperty("error", IOUtils.toString(is, StandardCharsets.UTF_8));
            return error;
        }
    }
}