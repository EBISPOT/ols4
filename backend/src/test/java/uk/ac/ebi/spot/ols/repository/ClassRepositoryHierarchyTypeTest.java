package uk.ac.ebi.spot.ols.repository;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class ClassRepositoryHierarchyTypeTest {

    @Test
    void everyHierarchyRouteRestrictsResultsToClassesAndActiveEntities() {
        RecordingPostgresClient postgresClient = new RecordingPostgresClient();
        ClassRepository repository = repository(postgresClient);

        invokeEveryHierarchyRoute(repository, false);

        assertThat(postgresClient.nodeProperties).hasSize(7)
                .allSatisfy(properties -> assertThat(properties).containsExactlyInAnyOrderEntriesOf(
                        Map.of("type", "OntologyClass", "isObsolete", "false")));
    }

    @Test
    void includingObsoleteEntitiesStillRestrictsHierarchyResultsToClasses() {
        RecordingPostgresClient postgresClient = new RecordingPostgresClient();
        ClassRepository repository = repository(postgresClient);

        invokeEveryHierarchyRoute(repository, true);

        assertThat(postgresClient.nodeProperties).hasSize(7)
                .allSatisfy(properties -> assertThat(properties)
                        .containsExactlyEntriesOf(Map.of("type", "OntologyClass")));
    }

    @Test
    void childrenRoutesForwardExcludeRedundantEdgesToThePostgresClient() {
        RecordingPostgresClient postgresClient = new RecordingPostgresClient();
        ClassRepository repository = repository(postgresClient);
        Pageable pageable = PageRequest.of(0, 20);
        JsonTransformOptions options = new JsonTransformOptions();
        String iri = "http://example.org/EFO_0001";

        repository.getChildrenByOntologyId(
                "efo", pageable, iri, false, true, null, "en", options);
        repository.getChildrenByOntologyId(
                "efo", pageable, iri, false, false, "clinical", "en", options);
        repository.getHierarchicalChildrenByOntologyId(
                "efo", pageable, iri, false, true, "en", options);

        assertThat(postgresClient.excludeRedundantEdges).containsExactly(true, false, true);
        assertThat(postgresClient.searches).containsExactly(null, "clinical", null);
    }

    private static ClassRepository repository(RecordingPostgresClient postgresClient) {
        ClassRepository repository = new ClassRepository();
        setField(repository, "postgresClient", postgresClient);
        return repository;
    }

    private static void invokeEveryHierarchyRoute(
            ClassRepository repository,
            boolean includeObsolete) {
        Pageable pageable = PageRequest.of(0, 20);
        JsonTransformOptions options = new JsonTransformOptions();
        String iri = "http://example.org/EFO_0001";

        repository.getChildrenByOntologyId(
                "efo", pageable, iri, includeObsolete, false, null, "en", options);
        repository.getAncestorsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getDescendantsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getHierarchicalDescendantsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getHierarchicalChildrenByOntologyId(
                "efo", pageable, iri, includeObsolete, false, "en", options);
        repository.getHierarchicalAncestorsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getIndividualAncestorsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
    }

    private static class RecordingPostgresClient extends OlsPostgresClient {
        private final List<Map<String, String>> nodeProperties = new ArrayList<>();
        private final List<Boolean> excludeRedundantEdges = new ArrayList<>();
        private final List<String> searches = new ArrayList<>();

        @Override
        public Page<JsonElement> getDirectChildren(
                String id, Map<String, String> properties, Pageable pageable, String search,
                boolean excludeRedundantEdges) {
            this.excludeRedundantEdges.add(excludeRedundantEdges);
            this.searches.add(search);
            return record(properties, pageable);
        }

        @Override
        public Page<JsonElement> getAncestors(
                String id, Map<String, String> properties, Pageable pageable) {
            return record(properties, pageable);
        }

        @Override
        public Page<JsonElement> getDescendants(
                String id, Map<String, String> properties, Pageable pageable) {
            return record(properties, pageable);
        }

        @Override
        public Page<JsonElement> getHierarchicalDescendants(
                String id, Map<String, String> properties, Pageable pageable) {
            return record(properties, pageable);
        }

        @Override
        public Page<JsonElement> getHierarchicalChildren(
                String id, Map<String, String> properties, Pageable pageable,
                boolean excludeRedundantEdges) {
            this.excludeRedundantEdges.add(excludeRedundantEdges);
            this.searches.add(null);
            return record(properties, pageable);
        }

        @Override
        public Page<JsonElement> getHierarchicalAncestors(
                String id, Map<String, String> properties, Pageable pageable) {
            return record(properties, pageable);
        }

        private Page<JsonElement> record(Map<String, String> properties, Pageable pageable) {
            nodeProperties.add(properties);
            return Page.empty(pageable);
        }
    }
}
