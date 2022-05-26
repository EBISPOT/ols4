
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodesAndPropertiesExtractor {


    public static void extractNodesAndProperties(
        JsonOntology ontology,
        Set<String> allClassProperties,
        Set<String> allEdgeProperties,
        Set<String> allNodes) {

        for(Map<String, Object> _class : ontology.classes) {

            String uri = (String) _class.get("uri");
            allNodes.add(uri);

            for(String predicate : _class.keySet()) {

                if(predicate.equals("uri"))
                    continue;

                allClassProperties.add(predicate);

                Object value = _class.get(predicate);

                visitValue(predicate, value, allClassProperties, allEdgeProperties, allNodes);
            }

        }
    }

    private static void visitValue(
        String predicate,
        Object value,
        Set<String> allClassProperties,
        Set<String> allEdgeProperties,
        Set<String> allNodes) {

        if(value instanceof String) {

        } else if(value instanceof List) {

            List<Object> listValue = (List<Object>) value;

            for(Object entry : listValue)
                visitValue(predicate, entry, allClassProperties, allEdgeProperties, allNodes);

        } else if(value instanceof Map) {

            // either reification, or a bnode (anon. class or restriction)

            Map<String, Object> mapValue = (Map<String, Object>) value;

            if(mapValue.containsKey("value")) {

                // reification (an owl axiom)

                // for predicates used in reification we store it twice, one with the value and no axiom metadata
                // so that it can be queried directly, and then again with the metadata as json in an axiom+ field
                //
                allClassProperties.add("axiom+" + predicate);

                // predicates used to describe the edge itself
                for(String edgePredicate : mapValue.keySet()) {

                    if(edgePredicate.equals("value"))
                        continue;

                    allEdgeProperties.add(edgePredicate);
                }

                visitValue(predicate, mapValue.get("value"), allClassProperties, allEdgeProperties, allNodes);

            } else {

                // bnode (anon. class or restriction)

            }

        }

    }


}
