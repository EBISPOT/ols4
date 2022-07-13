import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

public class NodesAndPropertiesExtractor {

    public static class Result {
        public Set<String> allClassProperties = new HashSet<>();
        public Set<String> allPropertyProperties = new HashSet<>();
        public Set<String> allIndividualProperties = new HashSet<>();
        public Set<String> allEdgeProperties = new HashSet<>();
        public Set<String> allNodes = new HashSet<>();
    }

    public static Result extractNodesAndProperties(Map<String,Object> ontology) {

        Result res = new Result();

        for(Map<String, Object> _class : (Collection<Map<String,Object>>) ontology.get("classes")) {

            String uri = (String) _class.get("uri");
            res.allNodes.add(uri);

            for(String predicate : _class.keySet()) {

                if(predicate.equals("uri") || JSON2CSV.DONT_INDEX_FIELDS.contains(predicate))
                    continue;

                res.allClassProperties.add(predicate);

                Object value = _class.get(predicate);
                visitValue(predicate, value, res.allClassProperties, res.allEdgeProperties);
            }
        }

        for(Map<String, Object> property : (Collection<Map<String,Object>>) ontology.get("properties")) {

            String uri = (String) property.get("uri");
            res.allNodes.add(uri);

            for(String predicate : property.keySet()) {

                if(predicate.equals("uri") || JSON2CSV.DONT_INDEX_FIELDS.contains(predicate))
                    continue;

                res.allPropertyProperties.add(predicate);

                Object value = property.get(predicate);
                visitValue(predicate, value, res.allPropertyProperties, res.allEdgeProperties);
            }
        }

        for(Map<String, Object> individual : (Collection<Map<String,Object>>) ontology.get("individuals")) {

            String uri = (String) individual.get("uri");
            res.allNodes.add(uri);

            for(String predicate : individual.keySet()) {

                if(predicate.equals("uri") || JSON2CSV.DONT_INDEX_FIELDS.contains(predicate))
                    continue;

                res.allIndividualProperties.add(predicate);

                Object value = individual.get(predicate);
                visitValue(predicate, value, res.allIndividualProperties, res.allEdgeProperties);
            }
        }

        return res;

    }

    private static void visitValue(String predicate, Object value, Set<String> outProps, Set<String> outEdgeProps) {

        if(value instanceof String) {

        } else if(value instanceof List) {

            List<Object> listValue = (List<Object>) value;

            for(Object entry : listValue)
                visitValue(predicate, entry, outProps, outEdgeProps);

        } else if(value instanceof Map) {

            // either reification, or a bnode (anon. class or restriction)

            Map<String, Object> mapValue = (Map<String, Object>) value;

            if(mapValue.containsKey("value")) {

                // either reification (an owl axiom) OR a langString

                if(mapValue.containsKey("lang")) {

                    String lang = (String)mapValue.get("lang");
                    assert(lang != null);

                    // add a localized property like e.g. fr+http://some/predicate
                    // (english is the default and doesn't get a prefix)
                    // 
                    if(!lang.equals("en")) {
                        outProps.add(lang + "+" + predicate);
                    }

                } else {

                    // assume reificiation (owl:Axiom); TODO maybe don't assume
                    
                    // predicates used to describe the edge itself
                    for(String edgePredicate : mapValue.keySet()) {

                        if(edgePredicate.equals("value") || JSON2CSV.DONT_INDEX_FIELDS.contains(edgePredicate))
                            continue;

                        outEdgeProps.add(edgePredicate);
                    }
                }

                visitValue(predicate, mapValue.get("value"), outProps, outEdgeProps);

            } else {

                // bnode (anon. class or restriction)

            }

        }

    }


}
