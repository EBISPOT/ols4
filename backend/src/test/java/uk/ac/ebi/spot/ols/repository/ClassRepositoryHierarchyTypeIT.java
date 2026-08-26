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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ClassRepositoryHierarchyTypeIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.ClassRepositoryHandle repositoryHandle;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE ols_entities
                    SET direct_ancestors = ARRAY['http://example.org/EFO_0001']
                    WHERE id = 'efo+property+http://example.org/EFO_0100'
                    """);
        }
        repositoryHandle = PostgresIntegrationTestSupport.createClassRepository(POSTGRES);
    }

    @AfterAll
    static void closeDatabaseClient() {
        repositoryHandle.close();
    }

    @Test
    void classDescendantsExcludeCrossTypeEntitiesInTheSameHierarchyColumn() {
        Page<JsonElement> descendants = repositoryHandle.repository().getDescendantsByOntologyId(
                "efo",
                PageRequest.of(0, 20),
                "http://example.org/EFO_0001",
                false,
                "en",
                new JsonTransformOptions());

        assertThat(descendants).isEmpty();
        assertThat(descendants.getTotalElements()).isZero();
    }
}
