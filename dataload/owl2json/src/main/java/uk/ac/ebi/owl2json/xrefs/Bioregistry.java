
package uk.ac.ebi.owl2json.xrefs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.annotation.RegEx;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * The Bioregistry (https://bioregistry.io) is an open source, community curated registry
 * of prefixes for biomedical ontologies/vocabularies and their associated metadata. It
 * can be used to look generate links for prefixes found in xrefs and other components of
 * ontologies.
 *
 * Source code and data is availale under CC0/MIT licenses at https://github.com/biopragmatics/bioregistry
 */
public class Bioregistry {

    Gson gson = new Gson();

    public String registryUrl;

    JsonObject theRegistry;
    Map<String, JsonObject> prefixToDatabase = new HashMap<>();
    Map<String, Pattern> patterns = new HashMap<>();

    public Bioregistry() {
        this("https://raw.githubusercontent.com/biopragmatics/bioregistry/main/exports/registry/registry.json");
    }

    public Bioregistry(String jsonUrl){

        this.registryUrl = jsonUrl;

        try {
            theRegistry = urlToJson(jsonUrl).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for(var entry : theRegistry.entrySet()) {
            
            JsonObject db = entry.getValue().getAsJsonObject();

            // The key is the canonical Bioregistry prefix, always lowercase
            prefixToDatabase.put(norm(entry.getKey()), db);

            // The preferred prefix can have various capitalization,
            // usually is same as canonical
            prefixToDatabase.put(norm(db.get("preferred_prefix").getAsString()), db);

            JsonElement synonyms = db.get("synonyms");

            if(synonyms != null) {
                for(JsonElement synonym : synonyms.getAsJsonArray()) {
                    prefixToDatabase.put(norm(synonym.getAsString()), db);
                }
            }
        }

    }

    public String getRegistryUrl() {
        return  registryUrl;
    }

    public String getUrlForId(String databaseId, String id) {

        JsonObject db = prefixToDatabase.get(norm(databaseId));

        if(db == null)
            return null;

        if(id == null)
            return null;

        JsonElement patternObj = db.get("pattern");

        if(patternObj == null)
            return null;

        Pattern pattern = getPattern(patternObj.getAsString());

        if(!pattern.matcher(id).matches()) {
            return null;
        }

        JsonElement uriFormat = db.get("uri_format");

        if(uriFormat == null) {
            return null;
        }
            
        return uriFormat.getAsString().replace("$1", id);
    }
    
    private static String norm(String s) {
        // see https://github.com/biopragmatics/bioregistry/blob/a7424ef4a0d22eaca61d3a86c6175e2059e9c855/src/bioregistry/utils.py#L128-L133
        s = s.toLowerCase(Locale.ROOT);
        s = s.replace(".", "");
        s = s.replace("-", "");
        s = s.replace("_", "");
        s = s.replace("/", "");
        return s;
    }

    private Pattern getPattern(String patternStr) {

        Pattern found = patterns.get(patternStr);

        if(found != null) {
            return found;
        }

        Pattern pattern = Pattern.compile(patternStr);
        patterns.put(patternStr, pattern);
        return pattern;
    }

    private JsonElement urlToJson(String url) throws IOException {

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setSocketTimeout(5000).build();

        CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();

        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            return new JsonParser().parse(new InputStreamReader(entity.getContent()));
        } else {
            throw new RuntimeException("bioregistry response was null");
        }
    }

}
