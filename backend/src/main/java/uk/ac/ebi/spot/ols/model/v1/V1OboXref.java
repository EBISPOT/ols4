package uk.ac.ebi.spot.ols.model.v1;

import com.google.common.collect.Lists;
import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

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

    public static List<V1OboXref> extractFromEntity(OntologyEntity entity, OboDatabaseUrlService oboDbUrls) {

        List<Object> xrefs = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasDbXref");

        List<V1OboXref> res = new ArrayList<>();
      
        for(Object xref : xrefs) {

            if(xref instanceof String) {
                V1OboXref xrefObj = V1OboXref.fromString((String) xref, oboDbUrls);
                res.add(xrefObj);
                continue;
            }

            Map<String,Object> xrefMap = (Map<String,Object>) xref;

            List<Map<String,Object>> axioms = (List<Map<String,Object>>) xrefMap.get("axioms");

            for(Map<String,Object> axiom : axioms) {

                Object source = axiom.get("http://www.geneontology.org/formats/oboInOwl#source");

                if(source instanceof List) {

//                    for(String src : (List<String>) source) {
//                        V1OboXref xrefObj = V1OboXref.fromString((String) xrefMap.get("value"), oboDbUrls);
//                        xrefObj.description = src;
//                        res.add(xrefObj);
//                    }

                    // OLS3 only keeps one of the sources.
                    // Specifically, it keeps the LAST source, alphabetically. This is because this loop in OLS3:
                    // https://github.com/EBISPOT/OLS/blob/6f9a98d564c2759f767d1e01bbe70897cbe9aa82/ontology-tools/src/main/java/uk/ac/ebi/spot/ols/loader/AbstractOWLOntologyLoader.java#L1404-L1406
                    // overwrites the source for each annotation (so the last one in the list wins)
                    //
                    V1OboXref xrefObj = V1OboXref.fromString((String) xrefMap.get("value"), oboDbUrls);
                    xrefObj.description = Lists.reverse(((List<String>) source)).iterator().next();
                    res.add(xrefObj);

                } else {
                    V1OboXref xrefObj = V1OboXref.fromString((String) xrefMap.get("value"), oboDbUrls);
                    xrefObj.description = (String) source;
                    res.add(xrefObj);
                }
            }

            // TODO url?
        }

        return res.size() > 0 ? res : null;
    }

}
