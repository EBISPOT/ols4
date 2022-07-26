import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

public class OntologyScanner {

    static Gson gson = new Gson();

    public static class Result {
        String ontologyId;
        public Set<String> allOntologyProperties = new HashSet<>();
        public Set<String> allClassProperties = new HashSet<>();
        public Set<String> allPropertyProperties = new HashSet<>();
        public Set<String> allIndividualProperties = new HashSet<>();
        public Set<String> allEdgeProperties = new HashSet<>();
        public Set<String> allNodes = new HashSet<>();
    }

    public static Result scanOntology(JsonReader reader) throws IOException {

        Result res = new Result();

        reader.beginObject();

        while(reader.peek() != JsonToken.END_OBJECT) {

            String name = reader.nextName();

            if (name.equals("classes")) {
                reader.beginArray();
                while (reader.peek() != JsonToken.END_ARRAY) {
                    reader.beginObject(); // class
                    while(reader.peek() != JsonToken.END_OBJECT) {

                        String property = reader.nextName();
                        res.allClassProperties.add(property);

                        if(property.equals("uri")) {
                            res.allNodes.add(reader.nextString());
                        } else {
                            Object value = gson.fromJson(reader, Object.class);
                            visitValue(property, value, res.allClassProperties, res.allEdgeProperties);
                        }
                    }
                    reader.endObject();
                }
                reader.endArray();
                continue;
            }

            if (name.equals("properties")) {
                reader.beginArray();
                while (reader.peek() != JsonToken.END_ARRAY) {
                    reader.beginObject(); // class
                    while(reader.peek() != JsonToken.END_OBJECT) {

                        String property = reader.nextName();
                        res.allPropertyProperties.add(property);

                        if(property.equals("uri")) {
                            res.allNodes.add(reader.nextString());
                        } else {
                            Object value = gson.fromJson(reader, Object.class);
                            visitValue(property, value, res.allPropertyProperties, res.allEdgeProperties);
                        }
                    }
                    reader.endObject();
                }
                reader.endArray();
                continue;
            }

            if (name.equals("individuals")) {
                reader.beginArray();
                while (reader.peek() != JsonToken.END_ARRAY) {
                    reader.beginObject(); // class
                    while(reader.peek() != JsonToken.END_OBJECT) {

                        String property = reader.nextName();
                        res.allIndividualProperties.add(property);

                        if(property.equals("uri")) {
                            res.allNodes.add(reader.nextString());
                        } else {
                            Object value = gson.fromJson(reader, Object.class);
                            visitValue(property, value, res.allIndividualProperties, res.allEdgeProperties);
                        }
                    }
                    reader.endObject();
                }
                reader.endArray();
                continue;
            }

            res.allOntologyProperties.add(name);

            if(name.equals("uri")) {
                res.allNodes.add(reader.nextString());
            } else if(name.equals("ontologyId")) {
                res.ontologyId = reader.nextString();
            } else {
                Object value = gson.fromJson(reader, Object.class);
                visitValue(name, value, res.allOntologyProperties, res.allEdgeProperties);
            }
        }

        reader.endObject();

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

                        if(edgePredicate.equals("value"))
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
