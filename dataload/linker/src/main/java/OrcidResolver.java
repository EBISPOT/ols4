import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OrcidResolver {

    public static final Set<String> ORCID_PREFIXES = Set.of("https://orcid.org", "http://orcid.org", "ORCID:", "orcid:");

    public static Map<String, String> resolveOrcids(Set<String> orcids) {

        Map<String,String> res = new HashMap<>();

        for(String orcid : orcids) {

            try {
                JsonObject orcidJson = UrlToJson.urlToJson("https://orcid.org/" + orcid).getAsJsonObject();

                JsonObject person = orcidJson.getAsJsonObject("person");
                JsonObject name = person.getAsJsonObject("name");
                JsonObject givenNames = name.getAsJsonObject("given-names");
                JsonObject familyNames = name.getAsJsonObject("family-name");

                String givenName = givenNames.get("value").getAsString();
                String familyName = familyNames.get("value").getAsString();

                res.put(orcid, givenName + " " + familyName);
            } catch(Exception e) {
                System.out.println("Failed to resolve orcid: " + orcid);
                continue;
            }
            System.out.println("Resolved orcid: " + orcid);
        }

        return res;
    }

}
