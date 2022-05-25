import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodesAndPropertiesExtractor {

    public static class Result {
        public Set<String> allClassProperties = new HashSet<>();
        public Set<String> allEdgeProperties = new HashSet<>();
        public Set<String> allNodes = new HashSet<>();
    }

    public static Result extractNodesAndProperties(JsonOntology ontology) {

        Result res = new Result();

        for(Map<String, Object> _class : ontology.classes) {

            String uri = (String) _class.get("uri");
            res.allNodes.add(uri);

            for(String predicate : _class.keySet()) {

                if(predicate.equals("uri"))
                    continue;

                res.allClassProperties.add(predicate);

                Object value = _class.get(predicate);

                visitValue(predicate, value, res);
            }

        }

        return res;

    }

    private static void visitValue(String predicate, Object value, Result res) {

        if(value instanceof String) {

        } else if(value instanceof List) {

            List<Object> listValue = (List<Object>) value;

            for(Object entry : listValue)
                visitValue(predicate, entry, res);

        } else if(value instanceof Map) {

            // either reification, or a bnode (anon. class or restriction)

            Map<String, Object> mapValue = (Map<String, Object>) value;

            if(mapValue.containsKey("value")) {

                // reification (an owl axiom)

                // for predicates used in reification we store it twice, one with the value and no axiom metadata
                // so that it can be queried directly, and then again with the metadata as json in an axiom+ field
                //
                res.allClassProperties.add("axiom+" + predicate);

                // predicates used to describe the edge itself
                for(String edgePredicate : mapValue.keySet()) {

                    if(edgePredicate.equals("value"))
                        continue;

                    res.allEdgeProperties.add(edgePredicate);
                }

                visitValue(predicate, mapValue.get("value"), res);

            } else {

                // bnode (anon. class or restriction)

            }

        }

    }


}
