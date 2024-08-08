package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.model.v1.V1Property;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;

import java.util.Objects;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class V1PropertyMapper {

    public static V1Property mapProperty(JsonElement json, String lang) {

        V1Property property = new V1Property();

        JsonObject localizedJson = Objects.requireNonNull(LocalizationTransform.transform(json, lang))
                .getAsJsonObject();

        property.lang = lang;

        property.iri = JsonHelper.getString(localizedJson, "iri");

        property.ontologyName = JsonHelper.getString(localizedJson, "ontologyId");
        property.ontologyPrefix = JsonHelper.getString(localizedJson, "ontologyPreferredPrefix");
        property.ontologyIri = JsonHelper.getString(localizedJson, "ontologyIri");

        property.label = JsonHelper.getString(localizedJson, "label");
        property.description = JsonHelper.getStrings(localizedJson, "definition").toArray(new String[0]);
        property.synonyms = JsonHelper.getStrings(localizedJson, "synonym").toArray(new String[0]);
        property.annotation = AnnotationExtractor.extractAnnotations(localizedJson);
        //property.inSubsets = AnnotationExtractor.extractSubsets(localizedJson);

        property.shortForm = JsonHelper.getString(localizedJson, "shortForm");
        property.oboId = property.shortForm.replace("_", ":");

        property.hasChildren = Boolean.parseBoolean(JsonHelper.getString(localizedJson, HAS_DIRECT_CHILDREN.getText()))
                || Boolean.parseBoolean(JsonHelper.getString(localizedJson, HAS_DIRECT_CHILDREN.getText()));

        property.isRoot = !(
                JsonHelper.getBoolean(localizedJson, HAS_DIRECT_PARENTS.getText()) ||
                        JsonHelper.getBoolean(localizedJson, HAS_HIERARCHICAL_PARENTS.getText())
        );

        return property;
    }

}
