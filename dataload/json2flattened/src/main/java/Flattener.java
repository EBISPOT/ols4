
import com.google.gson.Gson;

import java.util.*;
import java.util.stream.Collectors;

public class Flattener {

    // Fields that we never want to query, so shouldn't be added to the flattened
    // objects. We can still access them via the API because they will be stored
    // in the "_json" string field.
    //
    public static final Set<String> DONT_INDEX_FIELDS = Set.of(
            "iriToLabel", "classes", "properties", "individuals", "annotationPredicate"
    );

    public static Object flatten(Object obj) {

        JsonNodeType nodeType = classifyJsonNode(obj);

        switch(nodeType) {
            case LITERAL:
                return obj;
            case TYPED_LITERAL:
                // discard type information and recurse to the value
                return flatten(((Map<String,Object>) obj).get("value"));
            case LIST:
                return flattenList((Collection<Object>) obj);
            case NAMED_ENTITY:
                return flattenNamedEntity((Map<String,Object>) obj);
            case CLASS_EXPRESSION:
                return flattenClassExpression((Map<String,Object>) obj);
            case LOCALIZATION:
                return flattenLocalizedLiteral((Map<String,Object>) obj);
            case REIFICATION:
                return flattenReifiedValue((Map<String,Object>) obj);
            case RESTRICTION:
                return null; // Exclusively processed by addFlattenedClassPropertiesFromParentRestrictions
            case JSON_GARBAGE:
                return null; // things in the Ontology object that come from the config
        }

        return null;
    }

    private static List<Object> flattenList(Collection<Object> list) {
        List<Object> flattenedList = new ArrayList<>();
        for (Object entry : list) {
            Object flattened = flatten(entry);
            if (flattened != null)
                flattenedList.add(flattened);
        }
        return flattenedList;
    }

    private static Object flattenNamedEntity(Map<String, Object> entity) {

        Map<String,Object> flattenedEntity = new TreeMap<>();
        Collection<Object> types = asList(entity.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));

        if(types.contains("http://www.w3.org/2002/07/owl#Class")) {
            addFlattenedRestrictionPropertiesFromClassParents(flattenedEntity, entity);
        }

        for(String propertyUri : entity.keySet()) {

            if(DONT_INDEX_FIELDS.contains(propertyUri))
                continue;

            flattenedEntity.put(propertyUri, flatten(entity.get(propertyUri)));
        }

        return flattenedEntity;
    }

    private static void addFlattenedRestrictionPropertiesFromClassParents(Map<String,Object> flattenedClass, Map<String, Object> _class) {

        Collection<Object> parents = asList(_class.get("http://www.w3.org/2000/01/rdf-schema#subClassOf"));

        for(Object parent : parents) {

            JsonNodeType parentType = classifyJsonNode(parent);

            if(parentType == JsonNodeType.RESTRICTION) {
                flattenRestrictionIntoEntity(flattenedClass, (Map<String,Object>) parent);
                continue;
            }

            if(parentType == JsonNodeType.REIFICATION) {

                Map<String,Object> reificationNode = (Map<String,Object>) parent;
                Object value = reificationNode.get("value");
                JsonNodeType valueType = classifyJsonNode(value);

                if(valueType == JsonNodeType.RESTRICTION) {
                    flattenRestrictionIntoEntity(flattenedClass, (Map<String,Object>) parent);
                    continue;
                }
            }
        }
    }

    private static void flattenRestrictionIntoEntity(Map<String,Object> flattenedEntity, Map<String,Object> restriction) {

        String restrictionProperty = (String) restriction.get("http://www.w3.org/2002/07/owl#onProperty");


        Object allValuesFrom = restriction.get("http://www.w3.org/2002/07/owl#allValuesFrom");

        if(allValuesFrom != null) {
            Map<String,Object> flattenedRestrictionNode = new TreeMap<>();
            flattenedRestrictionNode.put("value", allValuesFrom);
            flattenedRestrictionNode.putAll(restriction);
            flattenedEntity.put(restrictionProperty, flattenedRestrictionNode);
            return;
        }

        Object someValuesFrom = restriction.get("http://www.w3.org/2002/07/owl#someValuesFrom");

        if(someValuesFrom != null) {
            Map<String,Object> flattenedRestrictionNode = new TreeMap<>();
            flattenedRestrictionNode.put("value", someValuesFrom);
            flattenedRestrictionNode.putAll(restriction);
            flattenedEntity.put(restrictionProperty, flattenedRestrictionNode);
            return;
        }

        Object hasValue = restriction.get("http://www.w3.org/2002/07/owl#hasValue");

        if(hasValue != null) {
            Map<String,Object> flattenedRestrictionNode = new TreeMap<>();
            flattenedRestrictionNode.put("value", hasValue);
            flattenedRestrictionNode.putAll(restriction);
            flattenedEntity.put(restrictionProperty, flattenedRestrictionNode);
            return;
        }
    }

    private static Object flattenClassExpression(Map<String, Object> _class) {

        Collection<Object> types = asList(_class.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));

        if(!types.contains("http://www.w3.org/2002/07/owl#Class")) {
            throw new RuntimeException("flattenClassExpression called with something that isn't an owl:Class: " + ((new Gson()).toJson(_class)));
        }

        Collection<Object> oneOf = asList(_class.get("http://www.w3.org/2002/07/owl#oneOf"));

        if(oneOf != null && oneOf.size() > 0) {
            return oneOf.stream()
                    .map(obj -> flatten(obj))
                    .filter(obj -> obj != null)
                    .collect(Collectors.toList());
        }

        Collection<Object> intersectionOf = asList(_class.get("http://www.w3.org/2002/07/owl#intersectionOf"));

        if(intersectionOf != null && intersectionOf.size() > 0) {
            return intersectionOf.stream()
                    .map(obj -> flatten(obj))
                    .filter(obj -> obj != null)
                    .collect(Collectors.toList());
        }

        Collection<Object> unionOf = asList(_class.get("http://www.w3.org/2002/07/owl#unionOf"));

        if(unionOf != null && unionOf.size() > 0) {
            return unionOf.stream()
                    .map(obj -> flatten(obj))
                    .filter(obj -> obj != null)
                    .collect(Collectors.toList());
        }

        // Could be: cardinality, complementOf, any others we don't deal with yet...
        //
        return null;

        //throw new RuntimeException("Unknown class expression: " + gson.toJson(expr, Map.class));
    }

    private static Object flattenLocalizedLiteral(Map<String, Object> localizedLiteral) {
        Map<String,Object> flattened = new TreeMap<>();
        for(String p : localizedLiteral.keySet()) {
            if(p.equals("value")) {
                flattened.put("value", flatten(localizedLiteral.get("value")));
            } else {
                flattened.put(p, localizedLiteral.get(p));
            }
        }
        return flattened;
    }

    private static Object flattenReifiedValue(Map<String, Object> reification) {
        Map<String,Object> flattened = new TreeMap<>();
        for(String p : reification.keySet()) {
            if(p.equals("value")) {
                flattened.put("value", flatten(reification.get("value")));
            } else {
                flattened.put(p, reification.get(p));
            }
        }
        return flattened;
    }

    private static Collection<Object> asList(Object val) {

        if(val == null)
            return List.of();

        if(val instanceof Collection)
            return (Collection<Object>) val;
        else
            return List.of(val);
    }


    enum JsonNodeType {
        LITERAL,
        TYPED_LITERAL,
        LOCALIZATION,
        REIFICATION,
        CLASS_EXPRESSION,
        RESTRICTION,
        NAMED_ENTITY,
        LIST,
        JSON_GARBAGE
    }


    static JsonNodeType classifyJsonNode(Object node) {

        if(node instanceof List) {
            return JsonNodeType.LIST;
        }

        if(node instanceof Map) {

            Map<String,Object> map = (Map<String,Object>) node;

            if(map.containsKey("value")) {

                if(map.containsKey("lang")) {
                    return JsonNodeType.LOCALIZATION;
                }

                if(map.containsKey("datatype")) {
                    return JsonNodeType.TYPED_LITERAL;
                }

                return JsonNodeType.REIFICATION;
            }

            if(map.containsKey("iri")) {
                return JsonNodeType.NAMED_ENTITY;
            }

            Collection<Object> types = asList(map.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));

            if(types.contains("http://www.w3.org/2002/07/owl#Restriction")) {
                return JsonNodeType.RESTRICTION;
            }

            if(types.contains("http://www.w3.org/2002/07/owl#Class")) {
                return JsonNodeType.CLASS_EXPRESSION;
            }

            return JsonNodeType.JSON_GARBAGE;
        }

        return JsonNodeType.LITERAL;
    }
}
