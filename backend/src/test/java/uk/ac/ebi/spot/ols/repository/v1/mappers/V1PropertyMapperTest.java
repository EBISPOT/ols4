package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class V1PropertyMapperTest {

    @Test
    void mapsObsoleteAndDefiningOntologyFlags() {
        JsonObject json = new JsonObject();
        json.addProperty("iri", "http://example.org/EFO_0199");
        json.addProperty("ontologyId", "efo");
        json.addProperty("ontologyPreferredPrefix", "EFO");
        json.addProperty("ontologyIri", "http://www.ebi.ac.uk/efo");
        json.addProperty("label", "legacy material relation");
        json.add("definition", new JsonArray());
        json.add("synonym", new JsonArray());
        json.addProperty("shortForm", "EFO_0199");
        json.addProperty("isObsolete", true);
        json.addProperty("isDefiningOntology", true);
        json.addProperty("hasDirectChildren", false);
        json.addProperty("hasDirectParents", false);
        json.addProperty("hasHierarchicalParents", false);
        json.add("linkedEntities", new JsonObject());

        var property = V1PropertyMapper.mapProperty(json, "en");

        assertThat(property.isObsolete).isTrue();
        assertThat(property.isLocal).isTrue();
    }
}
