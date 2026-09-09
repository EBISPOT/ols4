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

    private static final String CLASSES_ONLY = "OntologyClass";
    private static final String CLASSES_AND_INDIVIDUALS = "OntologyClass,OntologyIndividual";

    @Test
    void downwardHierarchyRoutesAllowClassesAndIndividualsAndExcludeObsoleteEntities() {
        RecordingPostgresClient postgresClient = new RecordingPostgresClient();
        ClassRepository repository = repository(postgresClient);

        invokeEveryHierarchyRoute(repository, false);

        assertThat(propertiesForMethods(
                postgresClient,
                "getDirectChildren",
                "getDescendants",
                "getHierarchicalDescendants",
                "getHierarchicalChildren"))
                .hasSize(4)
                .allSatisfy(properties -> assertThat(properties).containsExactlyInAnyOrderEntriesOf(
                        Map.of("type", CLASSES_AND_INDIVIDUALS, "isObsolete", "false")));
    }

    @Test
    void upwardHierarchyRoutesRestrictResultsToClassesAndExcludeObsoleteEntities() {
        RecordingPostgresClient postgresClient = new RecordingPostgresClient();
        ClassRepository repository = repository(postgresClient);

        invokeEveryHierarchyRoute(repository, false);

        assertThat(propertiesForMethods(
                postgresClient,
                "getAncestors",
                "getHierarchicalAncestors"))
                .hasSize(3)
                .allSatisfy(properties -> assertThat(properties).containsExactlyInAnyOrderEntriesOf(
                        Map.of("type", CLASSES_ONLY, "isObsolete", "false")));
    }

    @Test
    void includingObsoleteEntitiesStillRestrictsHierarchyResultTypes() {
        RecordingPostgresClient postgresClient = new RecordingPostgresClient();
        ClassRepository repository = repository(postgresClient);

        invokeEveryHierarchyRoute(repository, true);

        assertThat(propertiesForMethods(
                postgresClient,
                "getDirectChildren",
                "getDescendants",
                "getHierarchicalDescendants",
                "getHierarchicalChildren"))
                .hasSize(4)
                .allSatisfy(properties -> assertThat(properties)
                        .containsExactlyEntriesOf(Map.of("type", CLASSES_AND_INDIVIDUALS)));

        assertThat(propertiesForMethods(
                postgresClient,
                "getAncestors",
                "getHierarchicalAncestors"))
                .hasSize(3)
                .allSatisfy(properties -> assertThat(properties)
                        .containsExactlyEntriesOf(Map.of("type", CLASSES_ONLY)));
    }

    private static List<Map<String, String>> propertiesForMethods(
            RecordingPostgresClient postgresClient,
            String... methods) {
        List<String> methodNames = List.of(methods);
        return postgresClient.invocations.stream()
                .filter(invocation -> methodNames.contains(invocation.method()))
                .map(Invocation::properties)
                .toList();
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
                "efo", pageable, iri, includeObsolete, null, "en", options);
        repository.getAncestorsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getDescendantsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getHierarchicalDescendantsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getHierarchicalChildrenByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getHierarchicalAncestorsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
        repository.getIndividualAncestorsByOntologyId(
                "efo", pageable, iri, includeObsolete, "en", options);
    }

    private record Invocation(String method, Map<String, String> properties) {
    }

    private static class RecordingPostgresClient extends OlsPostgresClient {
        private final List<Invocation> invocations = new ArrayList<>();

        @Override
        public Page<JsonElement> getDirectChildren(
                String id, Map<String, String> properties, Pageable pageable) {
            return record("getDirectChildren", properties, pageable);
        }

        @Override
        public Page<JsonElement> getAncestors(
                String id, Map<String, String> properties, Pageable pageable) {
            return record("getAncestors", properties, pageable);
        }

        @Override
        public Page<JsonElement> getDescendants(
                String id, Map<String, String> properties, Pageable pageable) {
            return record("getDescendants", properties, pageable);
        }

        @Override
        public Page<JsonElement> getHierarchicalDescendants(
                String id, Map<String, String> properties, Pageable pageable) {
            return record("getHierarchicalDescendants", properties, pageable);
        }

        @Override
        public Page<JsonElement> getHierarchicalChildren(
                String id, Map<String, String> properties, Pageable pageable) {
            return record("getHierarchicalChildren", properties, pageable);
        }

        @Override
        public Page<JsonElement> getHierarchicalAncestors(
                String id, Map<String, String> properties, Pageable pageable) {
            return record("getHierarchicalAncestors", properties, pageable);
        }

        private Page<JsonElement> record(
                String method, Map<String, String> properties, Pageable pageable) {
            invocations.add(new Invocation(method, properties));
            return Page.empty(pageable);
        }
    }
}
