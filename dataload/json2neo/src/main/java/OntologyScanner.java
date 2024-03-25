import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.util.*;

public class OntologyScanner {

    static Gson gson = new Gson();

    public enum NodeType {
        ONTOLOGY, CLASS, PROPERTY, INDIVIDUAL
    }
    public static class Result {
        String ontologyId;
        String ontologyUri;
        public Set<String> allOntologyProperties = new HashSet<>();
        public Set<String> allClassProperties = new HashSet<>();
        public Set<String> allPropertyProperties = new HashSet<>();
        public Set<String> allIndividualProperties = new HashSet<>();
        public Set<String> allEdgeProperties = new HashSet<>();
        public Map<String, Set<NodeType>> uriToTypes = new HashMap<>();
    }

    private static void addType(Result res, String uri, NodeType type) {
        Set<NodeType> types = res.uriToTypes.get(uri);
        if(types == null) {
            types = new TreeSet<NodeType>();
            res.uriToTypes.put(uri, types);
        }
        types.add(type);
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

                        if(!OntologyWriter.PROPERTY_BLACKLIST.contains(property))
                            res.allClassProperties.add(property);

                        if(property.equals("iri")) {
                            addType(res, reader.nextString(), NodeType.CLASS);
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

                        if(!OntologyWriter.PROPERTY_BLACKLIST.contains(property))
                            res.allPropertyProperties.add(property);

                        if(property.equals("iri")) {
                            addType(res, reader.nextString(), NodeType.PROPERTY);
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

                        if(!OntologyWriter.PROPERTY_BLACKLIST.contains(property))
                            res.allIndividualProperties.add(property);

                        if(property.equals("iri")) {
                            addType(res, reader.nextString(), NodeType.INDIVIDUAL);
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

            if(!OntologyWriter.PROPERTY_BLACKLIST.contains(name))
                res.allOntologyProperties.add(name);

            if(name.equals("iri")) {
                res.ontologyUri = reader.nextString();
                addType(res, res.ontologyUri, NodeType.ONTOLOGY);
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

        if(predicate.equals("linkedEntities")) {
            return;
        }

        if(value instanceof String) {

        } else if(value instanceof List) {

            List<Object> listValue = (List<Object>) value;

            for(Object entry : listValue)
                visitValue(predicate, entry, outProps, outEdgeProps);

        } else if(value instanceof Map) {

            // could be a typed literal, a relatedTo object, reification, a bnode,
	    // or some json junk from the ontology config

            Map<String, Object> mapValue = new TreeMap<String,Object>((Map<String, Object>) value);

	    Object type = mapValue.get("type");

	    if(type == null || ! (type instanceof List)) {
		// bnode (anon. class) or json junk
		return;
	    }

	    List<String> types = (List<String>) type;

	    if(types.contains("literal")) {

		// is this a localization?
                if(mapValue.containsKey("lang")) {

                    String lang = (String)mapValue.get("lang");
                    assert(lang != null);

                    // add a localized property like e.g. fr+http://some/predicate
                    // (english is the default and doesn't get a prefix)
                    // 
                    if(!lang.equals("en")) {
                        if(!OntologyWriter.PROPERTY_BLACKLIST.contains(predicate))
                            outProps.add(lang + "+" + predicate);
                    }
		}

	    } else if(types.contains("related")) {

                visitValue(predicate, mapValue.get("value"), outProps, outEdgeProps);

	    } else if(types.contains("reification")) {

		List<Object> axioms = (List<Object>) mapValue.get("axioms");

		for(Object axiomObj : axioms) {

		    Map<String,Object> axiom = (Map<String,Object>) axiomObj;

                    // predicates used to describe the edge itself
                    for(String edgePredicate : axiom.keySet()) {

                        if(edgePredicate.equals("type"))
                            continue;

                        if(!OntologyWriter.PROPERTY_BLACKLIST.contains(edgePredicate))
                            outEdgeProps.add(edgePredicate);
                    }
		}

                visitValue(predicate, mapValue.get("value"), outProps, outEdgeProps);

            } else if(types.contains("datatype")) {

            } else {
		throw new RuntimeException("???");
            }

        }

    }


}
