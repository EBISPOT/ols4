package uk.ac.ebi.spot.ols.model.v1;

import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

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
                                .collect(Collectors.toList());

        synonyms.addAll(
                related.stream().map(synonym -> fromSynonymObject(synonym, "hasRelatedSynonym", oboDbUrls)).collect(Collectors.toList())
        );

        return synonyms;
    }

    private static V1OboSynonym fromSynonymObject(Object synonymObj, String scope, OboDatabaseUrlService oboDbUrls) {

        if(synonymObj instanceof String) {
            V1OboSynonym synonym = new V1OboSynonym();
            synonym.name = (String)synonymObj;
            synonym.scope = scope;
            return synonym;
        }

        Map<String,Object> synonymMap = (Map<String,Object>) synonymObj;
        V1OboSynonym synonym = new V1OboSynonym();

        synonym.name = (String)synonymMap.get("value");
        synonym.scope = scope;
        synonym.type = (String)synonymMap.get("oboSynonymTypeName");

        Object xrefs = synonymMap.get("http://www.geneontology.org/formats/oboInOwl#hasDbXref");

        if(xrefs != null) {
            if(! (xrefs instanceof List)) {
                xrefs = List.of(xrefs);
            }

            synonym.xrefs =
                    ((List<Object>) xrefs).stream().map(xref -> V1OboXref.fromString((String) xref, oboDbUrls))
                            .collect(Collectors.toList());
        }

        return synonym;
    }

}

