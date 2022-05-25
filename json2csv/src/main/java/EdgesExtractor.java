import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EdgesExtractor {

    public static class Result {
        public Set<String> allEdges = new HashSet<>();
    }

    public static Result extractEdges(JsonOntology ontology, NodesAndPropertiesExtractor.Result nodesAndProps) {

        Result res = new Result();

        for(Map<String, Object> _class : ontology.classes) {

            for(String predicate : _class.keySet()) {

                if(predicate.equals("uri"))
                    continue;

                Object value = _class.get(predicate);
                visitValue(predicate, value, nodesAndProps, res);
            }

        }

        return res;

    }

    private static void visitValue(String predicate, Object value, NodesAndPropertiesExtractor.Result nodesAndProps, Result res) {

        if(value instanceof String) {

            // is the value the URI of another node?
            boolean isNode = nodesAndProps.allNodes.contains((String) value);

            if(isNode) {
                // if so, the predicate is an edge
                res.allEdges.add(predicate);
            }

        } else if(value instanceof List) {

            List<Object> listValue = (List<Object>) value;

            for(Object entry : listValue)
                visitValue(predicate, value, nodesAndProps, res);

        } else if(value instanceof Map) {

            // either reification, or a bnode (anon. class or restriction)

            Map<String, Object> mapValue = (Map<String, Object>) value;

            if(mapValue.containsKey("value")) {

                // reification (an owl axiom)

                visitValue(predicate, mapValue.get("value"), nodesAndProps, res);

            } else {

                // bnode (anon. class or restriction)

            }

        }

    }


}
