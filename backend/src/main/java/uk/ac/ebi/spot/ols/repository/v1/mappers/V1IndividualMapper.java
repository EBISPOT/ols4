package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.util.Objects;

public class V1IndividualMapper {

    public static V1Individual mapIndividual(JsonElement json, String lang) {

        V1Individual individual = new V1Individual();

        JsonObject localizedJson = Objects.requireNonNull(LocalizationTransform.transform(json, lang))
                .getAsJsonObject();

        individual.lang = lang;

        individual.iri = JsonHelper.getString(localizedJson, "iri");

        individual.ontologyName = JsonHelper.getString(localizedJson, "ontologyId");
        individual.ontologyPrefix = JsonHelper.getString(localizedJson, "ontologyPreferredPrefix");
        individual.ontologyIri = JsonHelper.getString(localizedJson, "ontologyIri");

        individual.label = JsonHelper.getString(localizedJson, LABEL.getText());
        individual.description = JsonHelper.getStrings(localizedJson, DEFINITION.getText()).toArray(new String[0]);
        individual.synonyms = JsonHelper.getStrings(localizedJson, SYNONYM.getText()).toArray(new String[0]);
        individual.annotation = AnnotationExtractor.extractAnnotations(localizedJson);
        individual.inSubsets = AnnotationExtractor.extractSubsets(localizedJson);

        individual.shortForm = JsonHelper.getString(localizedJson, "shortForm");
        individual.oboId = individual.shortForm.replace("_", ":");

        return individual;
    }

}
