package uk.ac.ebi.spot.ols.model.v1;

import uk.ac.ebi.spot.ols.service.OntologyEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class V1OboXref {

    public String database;
    public String id;
    public String description;
    public String url;


    // e.g. Orphanet:1037
    public static V1OboXref fromString(String oboXref) {

        String[] tokens = oboXref.split(":");

        V1OboXref xref = new V1OboXref();
        xref.database = tokens[0];
        xref.id = tokens[1];

        return xref;
    }

    public static List<V1OboXref> extractFromEntity(OntologyEntity entity) {

        List<Object> xrefs = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasDbXref");

        return xrefs.stream().map(xref -> {

            Map<String,Object> xrefMap = (Map<String,Object>) xref;

            V1OboXref xrefObj = V1OboXref.fromString((String) xrefMap.get("value"));

            Object source = xrefMap.get("http://www.geneontology.org/formats/oboInOwl#source");

            if(source instanceof List) {
                // TODO I don't even think OLS3 handles this; how can we be backwards compatible?
                xrefObj.description = ((List<Object>) source).stream().map(src -> src.toString()).collect(Collectors.joining());
            } else {
                xrefObj.description = (String) source;
            }

            // TODO url?

            return xrefObj;

        }).collect(Collectors.toList());
    }
}
