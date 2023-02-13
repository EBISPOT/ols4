package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.model.v1.V1OboSynonym;
import uk.ac.ebi.spot.ols.model.v1.V1OboXref;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class V1OboSynonymExtractor {

    public static List<V1OboSynonym> extractFromJson(JsonObject json) {

        JsonObject linkedEntities = json.getAsJsonObject("linkedEntities");

        List<JsonElement> exact = JsonHelper.getValues(json, "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");
        List<JsonElement> related = JsonHelper.getValues(json, "http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym");
        List<JsonElement> narrow = JsonHelper.getValues(json, "http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym");
        List<JsonElement> broad = JsonHelper.getValues(json, "http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym");

        List<V1OboSynonym> synonyms =
                exact.stream().map(synonym -> fromSynonymObject(synonym, "hasExactSynonym", linkedEntities))
                        .flatMap(Collection::stream)
//                                .filter(synonym -> synonym.type != null || synonym.xrefs != null)
                        .collect(Collectors.toList());

        synonyms.addAll(
                related.stream().map(synonym -> fromSynonymObject(synonym, "hasRelatedSynonym", linkedEntities))
                        .flatMap(Collection::stream)
//                        .filter(synonym -> synonym.type != null || synonym.xrefs != null)
                        .collect(Collectors.toList())
        );

        synonyms.addAll(
                narrow.stream().map(synonym -> fromSynonymObject(synonym, "hasNarrowSynonym", linkedEntities))
                        .flatMap(Collection::stream)
//                        .filter(synonym -> synonym.type != null || synonym.xrefs != null)
                        .collect(Collectors.toList())
        );

        synonyms.addAll(
                broad.stream().map(synonym -> fromSynonymObject(synonym, "hasBroadSynonym", linkedEntities))
                        .flatMap(Collection::stream)
//                        .filter(synonym -> synonym.type != null || synonym.xrefs != null)
                        .collect(Collectors.toList())
        );

        return synonyms.size() > 0 ? synonyms : null;
    }

//    private static List<V1OboSynonym> collate(List<V1OboSynonym> synonyms) {
//
//        List<V1OboSynonym> result = new ArrayList<>();
//
//        for(V1OboSynonym synonym : synonyms) {
//            boolean foundExisting = false;
//            for(V1OboSynonym existing : result) {
//                if(Objects.equals(existing.name, synonym.name) &&
//                        Objects.equals(existing.type, synonym.type) &&
//                        Objects.equals(existing.scope, synonym.scope)) {
//
//                    if(synonym.xrefs != null) {
//                        if(existing.xrefs != null) {
//                             existing.xrefs.addAll(synonym.xrefs);
//                        } else {
//                            existing.xrefs = synonym.xrefs;
//                        }
//                    }
//
//                    foundExisting = true;
//                }
//            }
//            if(!foundExisting) {
//                result.add(synonym);
//            }
//        }
//
//        return result;
//    }

    private static List<V1OboSynonym> fromSynonymObject(JsonElement synonymObj, String scope, JsonObject linkedEntities) {

        if(synonymObj.isJsonPrimitive()) {

            // These are ignored in OLS3 for some reason
            // They will still be present in synonyms but not obo synonyms

//            V1OboSynonym synonym = new V1OboSynonym();
//            synonym.name = (String)synonymObj;
//            synonym.scope = scope;
//            return new ArrayList<>(List.of(synonym));
            return new ArrayList<>(List.of());
        }

        List<V1OboSynonym> synonyms = new ArrayList<>();

        List<JsonObject> axioms = JsonHelper.getObjects(synonymObj.getAsJsonObject(), "axioms");

        for(JsonObject axiom : axioms) {

            V1OboSynonym synonym = new V1OboSynonym();
            synonym.name = JsonHelper.getString(synonymObj.getAsJsonObject(), "value");
            synonym.scope = scope;
            synonym.type = JsonHelper.getString(axiom, "oboSynonymTypeName");

            synonym.xrefs = JsonHelper.getValues(axiom, "http://www.geneontology.org/formats/oboInOwl#hasDbXref")
                .stream().map(xref -> V1OboXref.fromString(JsonHelper.objectToString(xref), linkedEntities))
                        .collect(Collectors.toList());

            synonyms.add(synonym);
        }

        synonyms = mergeDuplicates(synonyms);

        return synonyms;
    }

    private static List<V1OboSynonym> mergeDuplicates(List<V1OboSynonym> synonyms) {

        List<V1OboSynonym> res = new ArrayList<>();

        for(V1OboSynonym synonym : synonyms) {

            boolean found = false;

            for(V1OboSynonym existing : res) {
                if(existing.equals(synonym)) {
                    found = true;
                    break;
                }
            }

            if(found)
                continue;

            res.add(synonym);
        }

        return res;
    }
}
