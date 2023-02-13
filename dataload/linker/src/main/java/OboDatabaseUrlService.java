
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OboDatabaseUrlService {

    private String xrefUrls;

    Map<String, OboDatabase> databases = new HashMap<>();

    public OboDatabaseUrlService() {
        this("https://raw.githubusercontent.com/geneontology/go-site/master/metadata/db-xrefs.yaml");
    }

    public OboDatabaseUrlService(String xrefUrls) {

        this.xrefUrls = xrefUrls;

        for(String xrefUrl : xrefUrls.split(";")) {

            InputStream inputStream;

            try {
                if (xrefUrl.contains("://")) {
                    inputStream = new URL(xrefUrl).openStream();
                } else {
                    inputStream = new FileInputStream(xrefUrl);
                }
            } catch(IOException e) {
                System.out.println("db-xrefs.yaml failed to load. URLs will be missing in OLS3 API OBO xrefs.");
                continue;
            }

            Yaml yaml = new Yaml();

            List<Object> xrefMap = (List<Object>) yaml.load(inputStream);
            for (Object entry : xrefMap) {

                Map<String,Object> entryMap = (Map<String,Object>) entry;
                String databaseId = (String) entryMap.get("database");
                String databaseName = (String) entryMap.get("name");
                if (entryMap.containsKey("entity_types")) {
                    List<Object> entityO = (List<Object>) entryMap.get("entity_types");

                    for (Object type : entityO ) {
                        Map<String,Object> entityMap = (Map<String,Object>) type;
                        if (entityMap.containsKey("url_syntax")) {
                            OboDatabase db = new OboDatabase();
                            db.databaseId = databaseId;
                            db.databaseName = databaseName;
                            db.urlSyntax = (String) entityMap.get("url_syntax");
                            databases.put(databaseId.toLowerCase(Locale.ROOT), db);
                        }
                    }
                }
            }
        }
    }

    public String getXrefUrls() {
        return xrefUrls;
    }

    public String getUrlForId(String databaseId, String id) {

        OboDatabase db = databases.get(databaseId.toLowerCase(Locale.ROOT));

        if(db == null)
            return null;

        if(id == null)
            return null; // or db url?

        return db.getUrlForId(id);
    }



    private class OboDatabase {

        public String databaseId;
        public String databaseName;
        public String urlSyntax;

        public String getUrlForId(String id) {
            return urlSyntax.replace("[example_id]", id);
        }
    }

}