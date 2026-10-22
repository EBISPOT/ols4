package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OLS_POSTGRES_CLIENT_EMBEDDING_MODEL;

/**
 * Ontology-scoped vector search against a database loaded before GitHub issue #1445, whose
 * {@code ols_embedding_nodes} has no {@code ontology_id} or {@code entity_type}. The backend and
 * the database are deployed separately, so a backend that filters on those columns can meet a
 * database that lacks them; it has to notice and reach the ontology through {@code ols_entities}
 * as it did before, not fail with "column does not exist".
 *
 * <p>Same fixture and expectations as {@link OlsPostgresClientEmbeddingIT}'s
 * {@code searchByVectorInOntology} cases, with the two columns dropped.
 */
class OlsPostgresClientLegacyEmbeddingSchemaIT {

    private static final String VEC_ONTO_CLASS_DEFINING_ID = "vectestonto+vecontoclass+http://example.org/VEC_ONTO_CLASS";
    private static final String VEC_ONTO_PROPERTY_DEFINING_ID = "vectestonto+vecontoproperty+http://example.org/VEC_ONTO_PROPERTY";
    private static final String VEC_ONTO_CLASS_TARGET_ID = "vectestonto2+vecontoclass+http://example.org/VEC_ONTO_CLASS";

    private static final List<Double> QUERY = List.of(1.0, 0.0, 0.0, 0.0);
    private static final PageRequest ANY_PAGEABLE = PageRequest.of(0, 10);

    private static PostgreSQLContainer<?> container;
    private static OlsPostgresClientRepositoryHandle handle;
    private static OlsPostgresClient client;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeOlsPostgresClientEmbeddingDatabase(container);
        PostgresIntegrationTestSupport.dropEmbeddingNodeScopeColumns(container);
        handle = PostgresIntegrationTestSupport.createOlsPostgresClientRepositories(container);
        client = handle.olsPostgresClient();
    }

    @AfterAll
    static void tearDownDatabase() {
        if (handle != null) {
            handle.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    private static List<String> ids(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("id").getAsString())
                .toList();
    }

    @Test
    void findsEntitiesDefinedInTheOntologyThroughTheirLabelAndCurationEmbeddings() {
        Page<JsonElement> page = client.searchByVectorInOntology(
                "OntologyEntity", QUERY, ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "vectestonto", true, true);

        assertThat(ids(page)).containsExactlyInAnyOrder(VEC_ONTO_CLASS_DEFINING_ID, VEC_ONTO_PROPERTY_DEFINING_ID);
    }

    @Test
    void appliesTheEntityTypeAndLeavesOutCurationsWhenAsked() {
        Page<JsonElement> page = client.searchByVectorInOntology(
                "VecOntoClass", QUERY, ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "vectestonto", true, false);

        assertThat(ids(page)).containsExactly(VEC_ONTO_CLASS_DEFINING_ID);
    }

    @Test
    void findsImportedEntitiesThroughTheDefiningOntologysEmbeddings() {
        Page<JsonElement> page = client.searchByVectorInOntology(
                "VecOntoClass", QUERY, ANY_PAGEABLE, OLS_POSTGRES_CLIENT_EMBEDDING_MODEL, "vectestonto2", false, false);

        assertThat(ids(page)).containsExactly(VEC_ONTO_CLASS_TARGET_ID);
    }
}
