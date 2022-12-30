package uk.ac.ebi.spot.ols.model.v1;

import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class V1OboSynonym {

    public String name;
    public String scope;
    public String type;
    public List<V1OboXref> xrefs;

    public static List<V1OboSynonym> extractFromEntity(OntologyEntity entity, OboDatabaseUrlService oboDbUrls) {

        List<Object> exact = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");
        List<Object> related = entity.getObjects("http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym");

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

        return synonyms.size() > 0 ? synonyms : null;
    }

    private static List<V1OboSynonym> fromSynonymObject(Object synonymObj, String scope, OboDatabaseUrlService oboDbUrls) {

        if(synonymObj instanceof String) {
            V1OboSynonym synonym = new V1OboSynonym();
            synonym.name = (String)synonymObj;
            synonym.scope = scope;
            return List.of(synonym);
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
                    xrefs = List.of(xrefs);
                }

                List<V1OboXref> xrefObjs =
                        ((List<Object>) xrefs).stream().map(xref -> V1OboXref.fromString((String) xref, oboDbUrls))
                                .collect(Collectors.toList());

                for(V1OboXref xrefObj : xrefObjs) {
                    V1OboSynonym synonym = new V1OboSynonym();

                    synonym.name = (String)synonymMap.get("value");
                    synonym.scope = scope;
                    synonym.type = (String)axiom.get("oboSynonymTypeName");
                    synonym.xrefs = List.of(xrefObj);

                    synonyms.add(synonym);
                }
            }
        }

        return synonyms;
    }

}

