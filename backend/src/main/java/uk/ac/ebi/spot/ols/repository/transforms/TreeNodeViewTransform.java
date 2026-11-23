package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import uk.ac.ebi.spot.ols.repository.transforms.helpers.JsonCollectionHelper;

import java.util.Set;

/**
 * The {@code view=tree} projection of an entity: only the fields a hierarchy tree needs.
 *
 * <p>An entity document carries everything the entity page shows -- every annotation,
 * synonym, definition, the full ancestor closure and a {@code linkedEntities} map with a
 * label for every IRI any of those mention. A tree node needs none of that, but the tree
 * endpoints returned whole documents: a page of 1000 COHO cohorts was 38 MB of JSON. This
 * keeps identity, label, type, obsolescence, the defining ontology, the parent links (with
 * their reification, which carries the relation shown between a node and its parent), the
 * child flags and the descendant counts. {@code linkedEntities} goes too: a tree shows a
 * node's own label, and the full view is there for anything that needs the rest.
 *
 * <p>Anything that is not an entity (arrays are mapped element-wise) is returned as is.
 */
public class TreeNodeViewTransform {

    static final Set<String> TREE_FIELDS = Set.of(
            // identity
            "iri", "curie", "shortForm", "type", "label",
            "ontologyId", "ontologyIri", "ontologyPreferredPrefix",
            "isDefiningOntology", "definedBy", "appearsIn", "imported",
            // obsolescence, as the frontend's Entity model reads it
            "isObsolete",
            "http://www.w3.org/2002/07/owl#deprecated",
            "http://purl.obolibrary.org/obo/IAO_0000231",
            "http://purl.obolibrary.org/obo/IAO_0100001",
            "http://www.ebi.ac.uk/efo/obsoleted_in_version",
            "http://www.ebi.ac.uk/efo/reason_for_obsolescence",
            // position in the hierarchy
            "isPreferredRoot",
            "directParent", "hierarchicalParent",
            "http://www.w3.org/2000/01/rdf-schema#subPropertyOf",
            "hasDirectParents", "hasHierarchicalParents",
            "hasDirectChildren", "hasHierarchicalChildren", "hasIndividuals",
            "numDescendants", "numHierarchicalDescendants"
    );

    public static JsonElement transform(JsonElement object) {
        if (object.isJsonArray()) {
            return JsonCollectionHelper.map(object.getAsJsonArray(), TreeNodeViewTransform::transform);
        }
        if (!object.isJsonObject()) {
            return object;
        }
        JsonObject obj = object.getAsJsonObject();
        if (!isEntity(obj)) {
            return object;
        }
        JsonObject res = new JsonObject();
        for (String k : obj.keySet()) {
            if (TREE_FIELDS.contains(k)) {
                res.add(k, obj.get(k));
            }
        }
        return res;
    }

    private static boolean isEntity(JsonObject obj) {
        if (!obj.has("type") || !obj.get("type").isJsonArray()) {
            return false;
        }
        JsonArray types = obj.getAsJsonArray("type");
        return types.contains(new JsonPrimitive("entity"));
    }
}
