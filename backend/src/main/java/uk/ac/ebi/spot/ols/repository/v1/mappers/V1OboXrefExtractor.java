package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.model.v1.V1OboXref;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class V1OboXrefExtractor {

    public static List<V1OboXref> extractFromJson(JsonObject entity) {

        JsonObject linkedEntities = entity.getAsJsonObject("linkedEntities");

        List<JsonElement> xrefs = JsonHelper.getValues(entity, "http://www.geneontology.org/formats/oboInOwl#hasDbXref");

        List<V1OboXref> res = new ArrayList<>();

        for(JsonElement xref : xrefs) {

            if(xref.isJsonPrimitive()) {
                V1OboXref xrefObj = V1OboXref.fromString(JsonHelper.objectToString(xref), linkedEntities);
                res.add(xrefObj);
                continue;
            }

            List<JsonObject> axioms = JsonHelper.getObjects(xref.getAsJsonObject(), "axioms");

            if(axioms.size() > 0) {

                for(JsonObject axiom : axioms) {

                    List<JsonElement> source = JsonHelper.getValues(axiom, "http://www.geneontology.org/formats/oboInOwl#source");
                    String url = JsonHelper.getString(axiom, "url");

                    if(source.size() > 0) {

                        // OLS3 only keeps one of the sources.
                        // Specifically, it keeps the LAST source, alphabetically. This is because this loop in OLS3:
                        // https://github.com/EBISPOT/OLS/blob/6f9a98d564c2759f767d1e01bbe70897cbe9aa82/ontology-tools/src/main/java/uk/ac/ebi/spot/ols/loader/AbstractOWLOntologyLoader.java#L1404-L1406
                        // overwrites the source for each annotation (so the last one in the list wins)
                        //
                        V1OboXref xrefObj = V1OboXref.fromString(JsonHelper.objectToString(xref), linkedEntities);
                        xrefObj.description = JsonHelper.objectToString( Lists.reverse(source).iterator().next() );

                        if(url != null) {
                            xrefObj.url = url;
                        }

                        res.add(xrefObj);

                        continue;

                    }

                    List<JsonElement> label = JsonHelper.getValues(axiom, "http://www.w3.org/2000/01/rdf-schema#label");

                    if(label.size() > 0) {
                        V1OboXref xrefObj = V1OboXref.fromString(JsonHelper.objectToString(xref), linkedEntities);
                        xrefObj.description = JsonHelper.objectToString( Lists.reverse(label).iterator().next() );

                        if(url != null) {
                            xrefObj.url = url;
                        }

                        res.add(xrefObj);

                        continue;
                    }

                    V1OboXref xrefObj = V1OboXref.fromString(JsonHelper.objectToString(xref), linkedEntities);

                    if(url != null) {
                        xrefObj.url = url;
                    }

                    res.add(xrefObj);

                    continue;
                }


            } else {

                V1OboXref xrefObj = V1OboXref.fromString(JsonHelper.objectToString(xref), linkedEntities);
                res.add(xrefObj);

            }
        }

        res = mergeDuplicates(res);

        return res.size() > 0 ? res : null;
    }

    private static List<V1OboXref> mergeDuplicates(List<V1OboXref> xrefs) {

        List<V1OboXref> res = new ArrayList<>();

        for(V1OboXref xref : xrefs) {

            boolean found = false;

            for(V1OboXref existing : res) {
                if(existing.equals(xref)) {
                    found = true;
                }
            }

            if(found)
                continue;

            res.add(xref);
        }

        return res;
    }
}
