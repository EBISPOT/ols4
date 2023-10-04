import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CurieMap {

    Gson gson = new Gson();

    public static class CurieMapping {
        String iriOrUrl = null;
        String curie = null; // FOO:12345
        String curiePrefix = null; // FOO
        String curieLocalPart = null; // 12345
        String curieNamespace = null; // http://foobar/FOO_
    }

    public Map<String, String> curiePrefixToNamespace = new LinkedHashMap<>();
    public Map<String, String> namespaceToCuriePrefix = new LinkedHashMap<>();

    public CurieMap() {
        addMapping("semapv", "https://w3id.org/semapv/vocab/");
        addMapping("owl", "http://www.w3.org/2002/07/owl#");
        addMapping("skos", "http://www.w3.org/2004/02/skos/core#");
        addMapping("oboInOwl", "http://www.geneontology.org/formats/oboInOwl#");
        addMapping("chebi", "http://purl.obolibrary.org/obo/chebi/");
        addMapping("inchi", "https://bioregistry.io/inchi:");
        addMapping("inchikey", "https://bioregistry.io/inchikey:");
        addMapping("smiles", "https://bioregistry.io/smiles:");
    }

    public CurieMapping mapEntity(JsonObject entityOrLinkedEntity) {

        String iriOrUrl = null;

        if(entityOrLinkedEntity.has("iri")) {
            iriOrUrl = entityOrLinkedEntity.get("iri").getAsString();
        } else if(entityOrLinkedEntity.has("url")) {
            iriOrUrl = entityOrLinkedEntity.get("url").getAsString();
        }

        if(!entityOrLinkedEntity.has("curie")) {
            if(iriOrUrl == null) {
                return null;
            }
            CurieMapping mapping = new CurieMapping();
            mapping.iriOrUrl = iriOrUrl;
            return mapping;
        }

        String curie = JsonHelper.getFirstStringValue(entityOrLinkedEntity.get("curie"));

        if(!curie.contains(":")) {
            System.out.println("curie provided by OLS " + curie + " does not look like a curie, in entity/linkedEntity: " + gson.toJson(entityOrLinkedEntity));
            // TODO ???
            return null;
        }

        String curiePrefix = curie.split(":")[0];
        String curieLocalPart = curie.split(":")[1];

        if(iriOrUrl == null || !iriOrUrl.endsWith(curieLocalPart)) {
            System.out.println(iriOrUrl + " does not end with local part of curie " + curie + ". This mapping will be omitted from the results.");

            // We can't print the iri/url in SSSOM and we can't put the CURIE in the prefix map
            // TODO: Currently we just drop the mapping, maybe a better way to approach this.
            //
            return null;
//            CurieMapping mapping = new CurieMapping();
//            mapping.curiePrefix = curiePrefix;
//            mapping.curieLocalPart = curieLocalPart;
//            mapping.curie = curie;
//            return mapping;
        }

        String curieNamespace = iriOrUrl.substring(0, iriOrUrl.length() - curieLocalPart.length());

        if(curiePrefixToNamespace.containsKey(curiePrefix)) {

            String existingNs = curiePrefixToNamespace.get(curiePrefix);

            if(!existingNs.equals(curieNamespace)) {

                String origCurieForDebugLog = curiePrefix;

                // try to find a different curie prefix for this namespace
                String nsToCp = namespaceToCuriePrefix.get(curieNamespace);
                if(nsToCp != null) {
                    curiePrefix = nsToCp;
                    curie = curiePrefix + ":" + curieLocalPart;
                } else {
                    // establish this namespace as a curie prefix
                    int n = 2;
                    while(curiePrefixToNamespace.containsKey(curiePrefix + "_" + n)) {
                        ++ n;
                    }
                    curiePrefix = curiePrefix + "_" + n;
                    curie = curiePrefix + ":" + curieLocalPart;
                    addMapping(curiePrefix, curieNamespace);
                }
//                System.out.println("Namespace " + curieNamespace + " did not match existing namespace " + existingNs + " for curie prefix " + origCurieForDebugLog + ". Using " + curiePrefix + " instead. In entity/linkedEntity: " + gson.toJson(entityOrLinkedEntity));
            }

        } else {
            addMapping(curiePrefix, curieNamespace);
        }

        CurieMapping mapping = new CurieMapping();
        mapping.iriOrUrl = iriOrUrl;
        mapping.curie = curie;
        mapping.curiePrefix = curiePrefix;
        mapping.curieLocalPart = curieLocalPart;
        mapping.curieNamespace = curieNamespace;
        return mapping;
    }

    private void addMapping(String curiePrefix, String curieNamespace) {
        curiePrefixToNamespace.put(curiePrefix, curieNamespace);
        namespaceToCuriePrefix.put(curieNamespace, curiePrefix);
    }


}
