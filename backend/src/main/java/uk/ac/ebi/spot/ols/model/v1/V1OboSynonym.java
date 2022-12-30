package uk.ac.ebi.spot.ols.model.v1;

import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import java.util.*;
import java.util.stream.Collectors;

public class V1OboSynonym {

    public String name;
    public String scope;
    public String type;
    public List<V1OboXref> xrefs;

    public static List<V1OboSynonym> extractFromEntity(OntologyEntity entity, OboDatabaseUrlService oboDbUrls) {

        List<Object> exact = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");
        List<Object> related = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym");
        List<Object> narrow = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym");
        List<Object> broad = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym");

        List<V1OboSynonym> synonyms =
                exact.stream().map(synonym -> fromSynonymObject(synonym, "hasExactSynonym", oboDbUrls))
                                .flatMap(Collection::stream)
                                .filter(synonym -> synonym.type != null || synonym.xrefs != null)
                                .collect(Collectors.toList());

        synonyms.addAll(
                related.stream().map(synonym -> fromSynonymObject(synonym, "hasRelatedSynonym", oboDbUrls))
                        .flatMap(Collection::stream)
                        .filter(synonym -> synonym.type != null || synonym.xrefs != null)
                        .collect(Collectors.toList())
        );

        synonyms.addAll(
                narrow.stream().map(synonym -> fromSynonymObject(synonym, "hasNarrowSynonym", oboDbUrls))
                        .flatMap(Collection::stream)
                        .filter(synonym -> synonym.type != null || synonym.xrefs != null)
                        .collect(Collectors.toList())
        );

        synonyms.addAll(
                broad.stream().map(synonym -> fromSynonymObject(synonym, "hasBroadSynonym", oboDbUrls))
                        .flatMap(Collection::stream)
                        .filter(synonym -> synonym.type != null || synonym.xrefs != null)
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

    private static List<V1OboSynonym> fromSynonymObject(Object synonymObj, String scope, OboDatabaseUrlService oboDbUrls) {

        if(synonymObj instanceof String) {
            V1OboSynonym synonym = new V1OboSynonym();
            synonym.name = (String)synonymObj;
            synonym.scope = scope;
            return new ArrayList<>(List.of(synonym));
        }

        List<V1OboSynonym> synonyms = new ArrayList<>();

        Map<String,Object> synonymMap = (Map<String,Object>) synonymObj;

        List<Object> axioms = (List<Object>) synonymMap.get("axioms");

        if(axioms == null) {
            throw new RuntimeException("axioms were null");
        }

        for(Object axiomObj : axioms) {

            Map<String,Object> axiom = (Map<String,Object>) axiomObj;

            Object xrefs = axiom.get("http://www.geneontology.org/formats/oboInOwl#hasDbXref");

            if(xrefs != null) {
                if(! (xrefs instanceof List)) {
                    xrefs = new ArrayList<>(List.of(xrefs));
                }

                List<V1OboXref> xrefObjs =
                        ((List<Object>) xrefs).stream().map(xref -> V1OboXref.fromString((String) xref, oboDbUrls))
                                .collect(Collectors.toList());

                V1OboSynonym synonym = new V1OboSynonym();

                synonym.name = (String)synonymMap.get("value");
                synonym.scope = scope;
                synonym.type = (String)axiom.get("oboSynonymTypeName");
                synonym.xrefs = xrefObjs;

                synonyms.add(synonym);
            }
        }

        return synonyms;
    }

}

