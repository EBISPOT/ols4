package uk.ac.ebi.spot.ols.repository.v1.mappers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage for {@link AnnotationExtractor}. This is a pure static-method utility
 * class with no Spring bean and no Postgres dependency, so a hand-rolled JsonObject fixture per
 * branch is the complete "covered" bar for it -- see docs/backend-testing-strategy.md's
 * "Implemented AnnotationExtractor baseline" section for why no *IT layer applies here.
 */
class AnnotationExtractorTest {

    /**
     * Every real caller (V1TermMapper, V1IndividualMapper, V1PropertyMapper) always supplies a
     * "linkedEntities" object -- extractAnnotations dereferences it unconditionally with no null
     * check, so every fixture must include it too, exactly like production input does.
     */
    private static JsonObject baseJson() {
        JsonObject json = new JsonObject();
        json.add("linkedEntities", new JsonObject());
        return json;
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> annosFor(Map<String, Object> result, String label) {
        return (Set<Object>) result.get(label);
    }

    // --- extractAnnotations: predicate-shape exclusions ------------------------------------

    @Test
    void predicateWithoutIriSchemeIsExcluded() {
        JsonObject json = baseJson();
        json.addProperty("plainField", "not an annotation");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    @Test
    void predicateMatchingNamePatternIsExcluded() {
        JsonObject json = baseJson();
        json.addProperty("relatedTo+http://www.example.org/foo", "value added by rdf2json");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    @Test
    void predicateAlreadyInterpretedAsDefinitionIsExcluded() {
        JsonObject json = baseJson();
        JsonArray definitionProperty = new JsonArray();
        definitionProperty.add("http://example.org/def");
        json.add("definitionProperty", definitionProperty);
        json.addProperty("http://example.org/def", "a definition");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    @Test
    void predicateAlreadyInterpretedAsSynonymIsExcluded() {
        JsonObject json = baseJson();
        JsonArray synonymProperty = new JsonArray();
        synonymProperty.add("http://example.org/syn");
        json.add("synonymProperty", synonymProperty);
        json.addProperty("http://example.org/syn", "a synonym");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    @Test
    void predicateAlreadyInterpretedAsHierarchicalIsExcluded() {
        JsonObject json = baseJson();
        JsonArray hierarchicalProperty = new JsonArray();
        hierarchicalProperty.add("http://example.org/hier");
        json.add("hierarchicalProperty", hierarchicalProperty);
        json.addProperty("http://example.org/hier", "http://example.org/parent");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    // --- extractAnnotations: hardcoded namespace exclusions (with their exceptions) --------

    @Test
    void rdfSchemaNamespacePredicateIsExcluded() {
        JsonObject json = baseJson();
        json.addProperty("http://www.w3.org/2000/01/rdf-schema#label", "a label");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    @Test
    void rdfSyntaxNamespacePredicateIsExcluded() {
        JsonObject json = baseJson();
        json.addProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://example.org/SomeClass");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    @Test
    void owlNamespacePredicateIsExcluded() {
        JsonObject json = baseJson();
        json.addProperty("http://www.w3.org/2002/07/owl#deprecated", "true");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    @Test
    void rdfSchemaCommentExceptionIsIncluded() {
        JsonObject json = baseJson();
        json.addProperty("http://www.w3.org/2000/01/rdf-schema#comment", "A helpful comment");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).containsOnlyKeys("comment");
        assertThat(annosFor(result, "comment")).containsExactly("A helpful comment");
    }

    @Test
    void rdfSchemaSeeAlsoExceptionIsIncluded() {
        JsonObject json = baseJson();
        json.addProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso", "http://example.org/other");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).containsOnlyKeys("seeAlso");
        assertThat(annosFor(result, "seeAlso")).containsExactly("http://example.org/other");
    }

    @Test
    void inSubsetPredicateIsExcludedFromAnnotations() {
        JsonObject json = baseJson();
        json.addProperty("http://www.geneontology.org/formats/oboInOwl#inSubset",
                "http://purl.obolibrary.org/obo/go#GO_slim");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).isEmpty();
    }

    // --- extractAnnotations: value flattening -----------------------------------------------

    @Test
    void singleLevelWrappedValueIsFlattened() {
        JsonObject json = baseJson();
        JsonObject wrapped = new JsonObject();
        wrapped.addProperty("value", "flattened value");
        wrapped.addProperty("type", "literal");
        json.add("http://example.org/onto#prop1", wrapped);

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(annosFor(result, "prop1")).containsExactly("flattened value");
    }

    @Test
    void nestedWrappedValueIsFullyFlattened() {
        JsonObject json = baseJson();
        JsonObject inner = new JsonObject();
        inner.addProperty("value", "double nested value");
        JsonObject outer = new JsonObject();
        outer.add("value", inner);
        json.add("http://example.org/onto#prop2", outer);

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(annosFor(result, "prop2")).containsExactly("double nested value");
    }

    // --- extractAnnotations: label derivation ------------------------------------------------

    @Test
    void labelIsDerivedFromIriFragmentWhenPresent() {
        JsonObject json = baseJson();
        json.addProperty("http://example.org/onto#hasColor", "red");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).containsOnlyKeys("hasColor");
        assertThat(annosFor(result, "hasColor")).containsExactly("red");
    }

    @Test
    void labelIsDerivedFromLastPathSegmentWhenNoFragment() {
        JsonObject json = baseJson();
        json.addProperty("http://example.org/path/lastSegment", "value");

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).containsOnlyKeys("lastSegment");
        assertThat(annosFor(result, "lastSegment")).containsExactly("value");
    }

    @Test
    void labelIsOverriddenByLinkedEntityLabelWhenPresent() {
        JsonObject json = baseJson();
        json.addProperty("http://example.org/onto#rel", "Bob");

        JsonObject linkedEntity = new JsonObject();
        linkedEntity.addProperty("label", "Custom Label");
        JsonObject linkedEntities = new JsonObject();
        linkedEntities.add("http://example.org/onto#rel", linkedEntity);
        json.add("linkedEntities", linkedEntities);

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).containsOnlyKeys("Custom Label");
        assertThat(annosFor(result, "Custom Label")).containsExactly("Bob");
    }

    @Test
    void linkedEntityWithoutLabelFieldFallsBackToIriDerivedLabel() {
        JsonObject json = baseJson();
        json.addProperty("http://example.org/onto#rel", "Bob");

        JsonObject linkedEntity = new JsonObject(); // present in linkedEntities, but no "label" field
        JsonObject linkedEntities = new JsonObject();
        linkedEntities.add("http://example.org/onto#rel", linkedEntity);
        json.add("linkedEntities", linkedEntities);

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).containsOnlyKeys("rel");
        assertThat(annosFor(result, "rel")).containsExactly("Bob");
    }

    // --- extractAnnotations: grouping / dedup -------------------------------------------------

    @Test
    void duplicateValuesForSamePredicateAreDeduplicatedPreservingOrder() {
        JsonObject json = baseJson();
        JsonArray values = new JsonArray();
        values.add("dup");
        values.add("dup");
        values.add("unique");
        json.add("http://example.org/onto#prop4", values);

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(annosFor(result, "prop4")).containsExactly("dup", "unique");
    }

    @Test
    void valuesFromDifferentPredicatesResolvingToSameLabelAreMerged() {
        JsonObject json = baseJson();
        json.addProperty("http://example.org/onto#name", "Alice");
        json.addProperty("http://example.org/other#alias", "Bob");

        JsonObject linkedEntity = new JsonObject();
        linkedEntity.addProperty("label", "name");
        JsonObject linkedEntities = new JsonObject();
        linkedEntities.add("http://example.org/other#alias", linkedEntity);
        json.add("linkedEntities", linkedEntities);

        Map<String, Object> result = AnnotationExtractor.extractAnnotations(json);

        assertThat(result).containsOnlyKeys("name");
        assertThat(annosFor(result, "name")).containsExactly("Alice", "Bob");
    }

    // --- extractSubsets -------------------------------------------------------------------------

    @Test
    void extractSubsetsReturnsNullWhenKeyMissing() {
        JsonObject json = new JsonObject();

        assertThat(AnnotationExtractor.extractSubsets(json)).isNull();
    }

    @Test
    void extractSubsetsReturnsNullWhenArrayIsEmpty() {
        JsonObject json = new JsonObject();
        json.add("http://www.geneontology.org/formats/oboInOwl#inSubset", new JsonArray());

        assertThat(AnnotationExtractor.extractSubsets(json)).isNull();
    }

    @Test
    void extractSubsetsExtractsShortNameFromFragment() {
        JsonObject json = new JsonObject();
        JsonArray subsets = new JsonArray();
        subsets.add("http://purl.obolibrary.org/obo/go#GO_slim");
        json.add("http://www.geneontology.org/formats/oboInOwl#inSubset", subsets);

        List<String> result = AnnotationExtractor.extractSubsets(json);

        assertThat(result).containsExactly("GO_slim");
    }

    @Test
    void extractSubsetsExtractsShortNameFromLastPathSegmentWhenNoFragment() {
        JsonObject json = new JsonObject();
        JsonArray subsets = new JsonArray();
        subsets.add("http://purl.obolibrary.org/obo/go/GO_slim");
        json.add("http://www.geneontology.org/formats/oboInOwl#inSubset", subsets);

        List<String> result = AnnotationExtractor.extractSubsets(json);

        assertThat(result).containsExactly("GO_slim");
    }

    @Test
    void extractSubsetsSortsAndDeduplicatesMultipleUris() {
        JsonObject json = new JsonObject();
        JsonArray subsets = new JsonArray();
        subsets.add("http://x.org/a#Beta");
        subsets.add("http://x.org/a#Beta");
        subsets.add("http://x.org/a#Alpha");
        json.add("http://www.geneontology.org/formats/oboInOwl#inSubset", subsets);

        List<String> result = AnnotationExtractor.extractSubsets(json);

        assertThat(result).containsExactly("Alpha", "Beta");
    }
}
