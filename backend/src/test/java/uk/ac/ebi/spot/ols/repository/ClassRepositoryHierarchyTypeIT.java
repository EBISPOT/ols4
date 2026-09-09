package uk.ac.ebi.spot.ols.repository;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ClassRepositoryHierarchyTypeIT {

    private static final String PARENT_CLASS_IRI = "http://example.org/EFO_0001";
    private static final String CHILD_CLASS_IRI = "http://example.org/EFO_0002";
    private static final String INDIVIDUAL_IRI = "http://example.org/EFO_I100";
    private static final String GRANDCHILD_INDIVIDUAL_IRI = "http://example.org/EFO_I200";
    private static final String OBSOLETE_INDIVIDUAL_IRI = "http://example.org/EFO_I999";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.ClassRepositoryHandle repositoryHandle;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        PostgresIntegrationTestSupport.initializeIndividualDatabase(POSTGRES);
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE ols_entities
                    SET direct_ancestors = ARRAY['http://example.org/EFO_0001']
                    WHERE id = 'efo+property+http://example.org/EFO_0100'
                    """);
            statement.executeUpdate("""
                    UPDATE ols_entities
                    SET hierarchical_parents = ARRAY['http://example.org/EFO_0001'],
                        hierarchical_ancestors = ARRAY['http://example.org/EFO_0001']
                    WHERE id = 'efo+individual+http://example.org/EFO_I100'
                    """);
        }
        repositoryHandle = PostgresIntegrationTestSupport.createClassRepository(POSTGRES);
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void classDescendantsIncludeIndividualsButExcludePropertiesAndObsoleteEntities() {
        Page<JsonElement> descendants = repositoryHandle.repository().getDescendantsByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                PARENT_CLASS_IRI,
                false,
                "en",
                new JsonTransformOptions());

        assertThat(iris(descendants)).containsExactlyInAnyOrder(
                INDIVIDUAL_IRI,
                GRANDCHILD_INDIVIDUAL_IRI);
    }

    @Test
    void classChildrenIncludeIndividualsWhoseTypeIsTheClass() {
        Page<JsonElement> children = repositoryHandle.repository().getChildrenByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                PARENT_CLASS_IRI,
                false,
                null,
                "en",
                new JsonTransformOptions());

        assertThat(iris(children)).containsExactly(INDIVIDUAL_IRI);
    }

    @Test
    void classChildrenIncludeObsoleteIndividualsWhenRequested() {
        Page<JsonElement> children = repositoryHandle.repository().getChildrenByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                PARENT_CLASS_IRI,
                true,
                null,
                "en",
                new JsonTransformOptions());

        assertThat(iris(children)).containsExactlyInAnyOrder(
                INDIVIDUAL_IRI,
                OBSOLETE_INDIVIDUAL_IRI);
    }

    @Test
    void classHierarchicalChildrenIncludeIndividuals() {
        Page<JsonElement> children = repositoryHandle.repository().getHierarchicalChildrenByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                PARENT_CLASS_IRI,
                false,
                "en",
                new JsonTransformOptions());

        assertThat(iris(children)).containsExactly(INDIVIDUAL_IRI);
    }

    @Test
    void individualAncestorsReturnOnlyClasses() {
        Page<JsonElement> ancestors = repositoryHandle.repository().getIndividualAncestorsByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                GRANDCHILD_INDIVIDUAL_IRI,
                false,
                "en",
                new JsonTransformOptions());

        assertThat(iris(ancestors)).containsExactlyInAnyOrder(
                PARENT_CLASS_IRI,
                CHILD_CLASS_IRI);
    }

    private static List<String> iris(Page<JsonElement> page) {
        return page.getContent().stream()
                .map(element -> element.getAsJsonObject().get("iri").getAsString())
                .toList();
    }
}
