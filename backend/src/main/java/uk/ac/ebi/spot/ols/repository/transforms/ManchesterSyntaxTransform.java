package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.*;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ManchesterSyntaxTransform {

    // Public entry point — mirrors the pattern used by your other transforms.
    public static JsonElement transform(JsonElement element) {
        if (element == null || element.isJsonNull()) return JsonNull.INSTANCE;

        if (element.isJsonArray()) {
            return JsonCollectionHelper.map(element.getAsJsonArray(), ManchesterSyntaxTransform::transform);
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            if (isClassExpressionObject(obj)) {
                // Convert class-expression objects to a Manchester string primitive.
                String ms = toManchester(obj);
                return new JsonPrimitive(ms);
            }

            // Otherwise recurse into the object and transform its values.
            return JsonCollectionHelper.map(obj, ManchesterSyntaxTransform::transform);
        }

        // Primitives and booleans are left as-is (the brief asked for objects → strings).
        return element;
    }

    // ---- Helpers to detect and render class expressions ----

    private static boolean isClassExpressionObject(JsonObject obj) {
        // Heuristic: If it has any of the OWL class-expression keys, treat it as a class expression.
        String[] keys = {
                OWL_INTERSECTION_OF, OWL_UNION_OF, OWL_COMPLEMENT_OF, OWL_ONE_OF,
                OWL_ON_PROPERTY, OWL_SOME_VALUES_FROM, OWL_ALL_VALUES_FROM, OWL_HAS_VALUE,
                OWL_MIN_CARDINALITY, OWL_MAX_CARDINALITY, OWL_CARDINALITY,
                OWL_ON_CLASS, OWL_MIN_QUALIFIED_CARDINALITY, OWL_MAX_QUALIFIED_CARDINALITY, OWL_QUALIFIED_CARDINALITY,
                OWL_ON_DATATYPE, OWL_WITH_RESTRICTIONS, OWL_INVERSE_OF
        };

        for (String k : keys) {
            if (obj.has(k)) return true;
        }

        // Datatype with equivalentClass (special case from the React component).
        if (obj.has("type")) {
            JsonArray types = safeArray(obj.get("type"));
            if (containsString(types, "datatype")) {
                if (obj.has(OWL_EQUIVALENT_CLASS)) return true;
            }
        }

        // If an object has exactly a boolean "value" or such, we don't treat it as class expression.
        return false;
    }

    private static String toManchester(JsonElement el) {
        if (el == null || el.isJsonNull()) return "⊥"; // bottom (arbitrary fallback)

        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return String.valueOf(p.getAsBoolean());
            // Numbers/strings just return their lexical form (IRIs will appear as-is).
            return p.getAsString();
        }

        if (el.isJsonArray()) {
            // Not expected as a top-level class expression; join conservatively.
            List<String> parts = new ArrayList<>();
            for (JsonElement e : el.getAsJsonArray()) parts.add(toManchester(e));
            return String.join(", ", parts);
        }

        JsonObject obj = el.getAsJsonObject();

        // 0) Datatype special case with equivalentClass (mirrors React logic).
        if (hasType(obj, "datatype")) {
            JsonElement eq = obj.get(OWL_EQUIVALENT_CLASS);
            if (eq != null) {
                String label = obj.has("label") && obj.get("label").isJsonPrimitive()
                        ? obj.getAsJsonPrimitive("label").getAsString() + " "
                        : "";
                return label + toManchester(eq);
            }
        }

        // If references are already resolved by the ResolveReferencesTransform and this is
        // a referenced entity, we can display the label.
        if(obj.has("label")) {
            return "'" + toManchester(obj.get("label")) + "'";
        }

        // 1) owl:Class expressions
        List<JsonElement> intersection = asList(obj.get(OWL_INTERSECTION_OF));
        if (!intersection.isEmpty()) {
            return parenthesize(joinWith(intersperse(intersection, "and")));
        }

        List<JsonElement> union = asList(obj.get(OWL_UNION_OF));
        if (!union.isEmpty()) {
            return parenthesize(joinWith(intersperse(union, "or")));
        }

        JsonElement complement = firstOf(obj.get(OWL_COMPLEMENT_OF));
        if (complement != null) {
            return "not " + toManchester(complement);
        }

        List<JsonElement> oneOf = asList(obj.get(OWL_ONE_OF));
        if (!oneOf.isEmpty()) {
            List<String> members = new ArrayList<>();
            for (JsonElement m : oneOf) members.add(toManchester(normalizeNumberToString(m)));
            return "{" + String.join(", ", members) + "}";
        }

        JsonElement inverseOf = obj.get(OWL_INVERSE_OF);
        if (inverseOf != null) {
            return "inverse(" + toManchester(inverseOf) + ")";
        }

        // 2) Datatype restrictions
        JsonElement onDatatype = obj.get(OWL_ON_DATATYPE);
        if (onDatatype != null) {
            String dt = toManchester(onDatatype);
            List<JsonElement> restrictions = asList(obj.get(OWL_WITH_RESTRICTIONS));
            List<String> parts = new ArrayList<>();
            for (JsonElement r : restrictions) {
                if (!r.isJsonObject()) continue;
                JsonObject rr = r.getAsJsonObject();
                if (rr.has(XSD_MIN_EXCLUSIVE)) parts.add("> " + toManchester(rr.get(XSD_MIN_EXCLUSIVE)));
                if (rr.has(XSD_MIN_INCLUSIVE)) parts.add("≥ " + toManchester(rr.get(XSD_MIN_INCLUSIVE)));
                if (rr.has(XSD_MAX_EXCLUSIVE)) parts.add("< " + toManchester(rr.get(XSD_MAX_EXCLUSIVE)));
                if (rr.has(XSD_MAX_INCLUSIVE)) parts.add("≤ " + toManchester(rr.get(XSD_MAX_INCLUSIVE)));
            }
            if (parts.isEmpty()) return dt;
            return dt + " [" + String.join(", ", parts) + "]";
        }

        // 3) Property restrictions (unqualified)
        JsonElement onProperty = obj.get(OWL_ON_PROPERTY);
        if (onProperty == null && !isJsonBoolean(obj)) {
            return "unknown class expression";
        }

        JsonElement someValuesFrom = firstOf(obj.get(OWL_SOME_VALUES_FROM));
        if (someValuesFrom != null) {
            return toManchester(onProperty) + " some " + toManchester(someValuesFrom);
        }

        JsonElement allValuesFrom = firstOf(obj.get(OWL_ALL_VALUES_FROM));
        if (allValuesFrom != null) {
            return toManchester(onProperty) + " only " + toManchester(allValuesFrom);
        }

        JsonElement hasValue = normalizeNumberToString(firstOf(obj.get(OWL_HAS_VALUE)));
        if (hasValue != null) {
            return toManchester(onProperty) + " value " + toManchester(hasValue);
        }

        JsonElement minCard = normalizeNumberToString(firstOf(obj.get(OWL_MIN_CARDINALITY)));
        if (minCard != null) {
            return toManchester(onProperty) + " min " + toManchester(minCard);
        }

        JsonElement maxCard = normalizeNumberToString(firstOf(obj.get(OWL_MAX_CARDINALITY)));
        if (maxCard != null) {
            return toManchester(onProperty) + " max " + toManchester(maxCard);
        }

        JsonElement exactCard = normalizeNumberToString(firstOf(obj.get(OWL_CARDINALITY)));
        if (exactCard != null) {
            return toManchester(onProperty) + " exactly " + toManchester(exactCard);
        }

        JsonElement hasSelf = firstOf(obj.get(OWL_HAS_SELF));
        if (hasSelf != null && isTruthy(hasSelf)) {
            return toManchester(onProperty) + " Self";
        }

        // 4) Qualified cardinalities
        JsonElement onClass = obj.get(OWL_ON_CLASS);
        if (onClass != null) {
            JsonElement minQ = firstOf(obj.get(OWL_MIN_QUALIFIED_CARDINALITY));
            if (minQ != null) {
                return toManchester(onProperty) + " min " + toManchester(minQ) + " " + toManchester(onClass);
            }
            JsonElement maxQ = firstOf(obj.get(OWL_MAX_QUALIFIED_CARDINALITY));
            if (maxQ != null) {
                return toManchester(onProperty) + " max " + toManchester(maxQ) + " " + toManchester(onClass);
            }
            JsonElement exactQ = firstOf(obj.get(OWL_QUALIFIED_CARDINALITY));
            if (exactQ != null) {
                return toManchester(onProperty) + " exactly " + toManchester(exactQ) + " " + toManchester(onClass);
            }
        }

        // Fall-through (mirrors React)
        return "unknown class expression";
    }

    // ---- Utility ----

    private static boolean isJsonBoolean(JsonObject obj) {
        // If the entire object were intended to represent a boolean, it’d be a primitive.
        // This method exists only to mirror the React guard; always return false here.
        return false;
    }

    private static boolean hasType(JsonObject obj, String type) {
        if (!obj.has("type")) return false;
        return containsString(safeArray(obj.get("type")), type);
    }

    private static JsonArray safeArray(JsonElement e) {
        if (e == null || e.isJsonNull()) return new JsonArray();
        if (e.isJsonArray()) return e.getAsJsonArray();
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
        }

    private static boolean containsString(JsonArray arr, String value) {
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()
                    && value.equals(e.getAsJsonPrimitive().getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static List<JsonElement> asList(JsonElement e) {
        List<JsonElement> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) out.add(x);
        } else {
            out.add(e);
        }
        return out;
    }

    private static JsonElement firstOf(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return a.size() == 0 ? null : a.get(0);
        }
        return e;
    }

    private static String parenthesize(String s) {
        return "(" + s + ")";
    }

    private static List<String> intersperse(List<JsonElement> items, String sepWord) {
        List<String> out = new ArrayList<>();
        boolean first = true;
        for (JsonElement it : items) {
            if (!first) out.add(sepWord);
            out.add(toManchester(it));
            first = false;
        }
        return out;
    }

    private static String joinWith(List<String> tokens) {
        // Join with single spaces between tokens like: A, and, B, and, C
        return String.join(" ", tokens);
    }

    private static boolean isTruthy(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static JsonElement normalizeNumberToString(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return new JsonPrimitive(e.getAsJsonPrimitive().getAsNumber().toString());
        }
        return e;
    }

    // ---- OWL / XSD IRIs as constants ----

    private static final String OWL_INTERSECTION_OF = "http://www.w3.org/2002/07/owl#intersectionOf";
    private static final String OWL_UNION_OF = "http://www.w3.org/2002/07/owl#unionOf";
    private static final String OWL_COMPLEMENT_OF = "http://www.w3.org/2002/07/owl#complementOf";
    private static final String OWL_ONE_OF = "http://www.w3.org/2002/07/owl#oneOf";

    private static final String OWL_INVERSE_OF = "http://www.w3.org/2002/07/owl#inverseOf";

    private static final String OWL_ON_PROPERTY = "http://www.w3.org/2002/07/owl#onProperty";
    private static final String OWL_SOME_VALUES_FROM = "http://www.w3.org/2002/07/owl#someValuesFrom";
    private static final String OWL_ALL_VALUES_FROM = "http://www.w3.org/2002/07/owl#allValuesFrom";
    private static final String OWL_HAS_VALUE = "http://www.w3.org/2002/07/owl#hasValue";
    private static final String OWL_MIN_CARDINALITY = "http://www.w3.org/2002/07/owl#minCardinality";
    private static final String OWL_MAX_CARDINALITY = "http://www.w3.org/2002/07/owl#maxCardinality";
    private static final String OWL_CARDINALITY = "http://www.w3.org/2002/07/owl#cardinality";

    private static final String OWL_ON_CLASS = "http://www.w3.org/2002/07/owl#onClass";
    private static final String OWL_MIN_QUALIFIED_CARDINALITY = "http://www.w3.org/2002/07/owl#minQualifiedCardinality";
    private static final String OWL_MAX_QUALIFIED_CARDINALITY = "http://www.w3.org/2002/07/owl#maxQualifiedCardinality";
    private static final String OWL_QUALIFIED_CARDINALITY = "http://www.w3.org/2002/07/owl#qualifiedCardinality";

    private static final String OWL_ON_DATATYPE = "http://www.w3.org/2002/07/owl#onDatatype";
    private static final String OWL_WITH_RESTRICTIONS = "http://www.w3.org/2002/07/owl#withRestrictions";
    private static final String OWL_EQUIVALENT_CLASS = "http://www.w3.org/2002/07/owl#equivalentClass";

    private static final String XSD_MIN_EXCLUSIVE = "http://www.w3.org/2001/XMLSchema#minExclusive";
    private static final String XSD_MIN_INCLUSIVE = "http://www.w3.org/2001/XMLSchema#minInclusive";
    private static final String XSD_MAX_EXCLUSIVE = "http://www.w3.org/2001/XMLSchema#maxExclusive";
    private static final String XSD_MAX_INCLUSIVE = "http://www.w3.org/2001/XMLSchema#maxInclusive";

    private static final String OWL_HAS_SELF = "http://www.w3.org/2002/07/owl#hasSelf";

}
