package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class V1IndividualMapperTest {

    @Test
    void mapsObsoleteAndDefiningOntologyFlags() {
        JsonObject json = new JsonObject();
        json.addProperty("iri", "http://example.org/EFO_I999");
        json.addProperty("ontologyId", "efo");
        json.addProperty("ontologyPreferredPrefix", "EFO");
        json.addProperty("ontologyIri", "http://www.ebi.ac.uk/efo");
        json.addProperty("label", "legacy individual");
        json.add("definition", new JsonArray());
        json.add("synonym", new JsonArray());
        json.add("subset", new JsonArray());
        json.addProperty("shortForm", "EFO_I999");
        json.addProperty("isObsolete", true);
        json.addProperty("isDefiningOntology", true);
        json.add("linkedEntities", new JsonObject());

        var individual = V1IndividualMapper.mapIndividual(json, "en");

        assertThat(individual.isObsolete).isTrue();
        assertThat(individual.isLocal).isTrue();
    }
}
