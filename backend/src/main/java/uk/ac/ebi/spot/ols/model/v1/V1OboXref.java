package uk.ac.ebi.spot.ols.model.v1;

import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;

import java.util.Objects;

public class V1OboXref {

    public String database;
    public String id;
    public String description;
    public String url;


    // e.g. Orphanet:1037
    public static V1OboXref fromString(String oboXref, OboDatabaseUrlService oboDbUrls) {

        if(oboXref.startsWith("http:") || oboXref.startsWith("https:")) {
            V1OboXref xref = new V1OboXref();
            xref.id = oboXref;
            xref.url = oboXref;
            return xref;
        }

        if(oboXref.contains("://")) {
            if(oboXref.matches("^[A-Z]+:.+")) {
                    // e.g. DOI:https://doi.org/10.1378/chest.12-2762
                    V1OboXref xref = new V1OboXref();
                    xref.id = oboXref;
    //                xref.database = oboXref.split(":")[0];
    //                xref.id = oboXref.substring(oboXref.indexOf(':') + 1);
    //                xref.url = oboXref.substring(oboXref.indexOf(':') + 1);
                    return xref;
            }
        }

        String[] tokens = oboXref.split(":");

        if(tokens.length < 2) {
            V1OboXref xref = new V1OboXref();
            xref.id = tokens[0];
            return xref;
        }

        V1OboXref xref = new V1OboXref();
        xref.database = tokens[0];
        xref.id = tokens[1];
        xref.url = oboDbUrls.getUrlForId(xref.database, xref.id);
        return xref;
    }


    @Override
    public boolean equals(Object other) {

        if(! (other instanceof V1OboXref)) {
            return false;
        }

        return Objects.equals(((V1OboXref) other).id, this.id) &&
                Objects.equals(((V1OboXref) other).description, this.description)  &&
                Objects.equals(((V1OboXref) other).database, this.database)  &&
                Objects.equals(((V1OboXref) other).url, this.url);
    }
}
