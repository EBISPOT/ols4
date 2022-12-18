package uk.ac.ebi.spot.ols.model.v1;

import uk.ac.ebi.spot.ols.service.OboDatabaseUrlService;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class V1OboDefinitionCitation {

    public String definition;
    public List<V1OboXref> oboXrefs;


    public static List<V1OboDefinitionCitation> extractFromEntity(OntologyEntity entity, OboDatabaseUrlService oboDbUrls) {

        List<Object> definitions = entity.getObjects("definition");
        List<V1OboDefinitionCitation> res = new ArrayList<>();

        for(Object def : definitions) {
            if(def instanceof Map) {
                Map<String,Object> defAsMap = (Map<String,Object>) def;

                if(defAsMap.containsKey("http://www.geneontology.org/formats/oboInOwl#hasDbXref")) {

                    Object xrefs = defAsMap.get("http://www.geneontology.org/formats/oboInOwl#hasDbXref");
                    List<Object> xrefList = xrefs instanceof List ? (List<Object>)xrefs : List.of(xrefs);

                    String definition = (String)defAsMap.get("value");

                    V1OboDefinitionCitation citation = new V1OboDefinitionCitation();
                    citation.definition = definition;
                    citation.oboXrefs = xrefList.stream().map(xref -> V1OboXref.fromString((String) xref, oboDbUrls)).collect(Collectors.toList());
                    res.add(citation);
                }
            }
        }

        if(res.size() == 0) {
            return null;
        }

        return res;
    }
}
