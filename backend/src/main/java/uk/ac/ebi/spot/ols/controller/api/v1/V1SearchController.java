package uk.ac.ebi.spot.ols.controller.api.v1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import uk.ac.ebi.ols.shared.DefinedFields;

import jakarta.servlet.http.HttpServletResponse;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.springframework.web.bind.annotation.RestController;

import uk.ac.ebi.spot.ols.JsonHelper;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;
import uk.ac.ebi.spot.ols.repository.v1.mappers.AnnotationExtractor;

import static uk.ac.ebi.ols.shared.DefinedFields.*;


@Tag(name = "Search Controller")
@RestController
public class V1SearchController {

    Gson gson = new Gson();

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    private OlsSearchClient searchClient;

    private static final Logger logger = LoggerFactory.getLogger(V1SearchController.class);

    @RequestMapping(path = "/api/search", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void search(
            @RequestParam("q")
            @Parameter(name = "q",
                    description = "The terms to search. By default the search is performed over term labels, synonyms, descriptions, identifiers and annotation properties.",
                    example = "disease or liver+disease") String query,
            @RequestParam(value = "ontology", required = false)
            @Parameter(name = "ontology",
                    description = "Restrict a search to a set of ontologies e.g. ontology=efo,bfo",
                    example = "[\"efo\",\"bfo\"]") Collection<String> ontologies,
            @RequestParam(value = "type", required = false)
            @Parameter(name = "type",
                    description = "Restrict a search to an entity type, one of {class,property,individual,ontology}",
                    example = "[\"class\",\"property\"]") Collection<String> types,
            @RequestParam(value = "slim", required = false)
            @Parameter(name = "slim",
                    description = "Restrict a search to an particular set of slims by name") Collection<String> slims,
            @RequestParam(value = "fieldList", required = false)
            @Parameter(name = "fieldList",
                    description = "Specify the fields to return, the defaults are {iri,label,short_form,obo_id,ontology_name,ontology_prefix,description,type,exact_synonyms,related_synonyms,narrow_synonyms,broad_synonyms}. Additional synonym field available: {synonym} which returns all synonyms in one array",
                    example = "[\"iri\",\"label\",\"short_form\",\"obo_id\",\"ontology_name\"]") Collection<String> fieldList,
            @RequestParam(value = "queryFields", required = false)
            @Parameter(name = "queryFields",
                    description = "Specify the fields to query, the defaults are {label, synonym, description, short_form, obo_id, annotations, logical_description, iri}",
                    example = "[\"iri\",\"label\",\"short_form\",\"ontology_name\"]") Collection<String> queryFields,
            @RequestParam(value = "exact", required = false)
            @Parameter(name = "exact",
                    description = "Set to true for exact matches",
                    example = "false") boolean exact,
            @RequestParam(value = "groupField", required = false)
            @Parameter(name = "groupField",
                    description = "Set to true to group results by unique id (IRI), returning only the "
                            + "best match for each IRI instead of one result per ontology containing it. "
                            + "Any value other than false enables grouping.",
                    example = "true") String groupField,
            @RequestParam(value = "obsoletes", defaultValue = "false")
            @Parameter(name = "obsoletes",
                    description = "Set to true to include obsoleted terms in the results",
                    example = "false") boolean queryObsoletes,
            @RequestParam(value = "local", defaultValue = "false")
            @Parameter(name = "local",
                    description = "Set to true to only return terms that are in a defining ontology e.g. Only return matches to gene ontology terms in the gene ontology, and exclude ontologies where those terms are also referenced",
                    example = "false") boolean isLocal,
            @RequestParam(value = "childrenOf", required = false)
            @Parameter(name = "childrenOf",
                    description = "You can restrict a search to children of a given term. Supply a list of IRI for the terms that you want to search under",
                    example = "[\"http://www.ebi.ac.uk/efo/EFO_0001421\",\"http://www.ebi.ac.uk/efo/EFO_0004228\"]") Collection<String> childrenOf,
            @RequestParam(value = "allChildrenOf", required = false)
            @Parameter(name = "allChildrenOf",
                    description = "You can restrict a search to all children of a given term. Supply a list of IRI for the terms that you want to search under (subclassOf/is-a plus any hierarchical/transitive properties like 'part of' or 'develops from')",
                    example = "[\"http://www.ebi.ac.uk/efo/EFO_0001421\",\"http://www.ebi.ac.uk/efo/EFO_0004228\"]") Collection<String> allChildrenOf,
            @RequestParam(value = "inclusive", required = false) boolean inclusive,
            @RequestParam(value = "isLeaf", required = false) boolean isLeaf,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            @RequestParam(value = "format", defaultValue = "json")
            @Parameter(name = "format",
                    description = "The format for the response. Currently only `json` is supported.")
            String format,
            @RequestParam(value = "lang", defaultValue = "en") String lang,
            HttpServletResponse response
    ) throws IOException {

        OlsSearchQuery searchQuery = new OlsSearchQuery();
        searchQuery.setSearchText(query);
        searchQuery.setExactMatch(exact);

        if (queryFields != null && !queryFields.isEmpty()) {
            List<String> parsedFields = queryFields.stream()
                    .flatMap(f -> Arrays.stream(f.split("[,\\s]+")))
                    .map(String::trim)
                    .filter(f -> !f.isEmpty())
                    .map(V1SearchController::translateV1QueryFieldName)
                    .distinct()
                    .collect(Collectors.toList());
            searchQuery.setSearchFields(parsedFields);
        }

        if (ontologies != null && !ontologies.isEmpty()) {
            for (String ontologyId : ontologies)
                Validation.validateOntologyId(ontologyId);
            searchQuery.addFilter("ontologyId", ontologies, SearchType.WHOLE_FIELD);
        }

        if (slims != null) {
            searchQuery.addFilter("subset", slims, SearchType.WHOLE_FIELD);
        }

        if (isLocal) {
            searchQuery.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        }

        if (isLeaf) {
            searchQuery.addFilter("hasChildren", List.of("false"), SearchType.WHOLE_FIELD);
        }

        if (types != null) {
            searchQuery.addFilter("type", types, SearchType.WHOLE_FIELD);
        }

        if (childrenOf != null) {
            if (inclusive) {
                searchQuery.addAnyFilter(Map.of(
                        "iri", childrenOf,
                        "hierarchicalAncestor", childrenOf), SearchType.WHOLE_FIELD);
            } else {
                searchQuery.addFilter("hierarchicalAncestor", childrenOf, SearchType.WHOLE_FIELD);
            }
        }

        if (allChildrenOf != null) {
            if (inclusive) {
                searchQuery.addAnyFilter(Map.of(
                        "iri", allChildrenOf,
                        "hierarchicalAncestor", allChildrenOf), SearchType.WHOLE_FIELD);
            } else {
                searchQuery.addFilter("hierarchicalAncestor", allChildrenOf, SearchType.WHOLE_FIELD);
            }
        }

        searchQuery.addFilter("isObsolete", List.of(Boolean.toString(queryObsoletes)), SearchType.WHOLE_FIELD);

        // Facet fields
        searchQuery.addFacetField("ontologyId");
        searchQuery.addFacetField("ontologyIri");
        searchQuery.addFacetField("ontologyPreferredPrefix");
        searchQuery.addFacetField("type");
        searchQuery.addFacetField(IS_DEFINING_ONTOLOGY.getText());
        searchQuery.addFacetField(IS_OBSOLETE.getText());

        boolean groupByIri = isGroupingEnabled(groupField);

        logger.debug("V1 SEARCH QUERY: searchText={} groupByIri={}", query, groupByIri);

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(searchQuery, start, rows, groupByIri);

        List<Object> docs = new ArrayList<>();
        for (String _json : result.jsonStrings) {

            JsonObject json = RemoveLiteralDatatypesTransform.transform(
                    LocalizationTransform.transform( JsonParser.parseString( _json ), lang)
            ).getAsJsonObject();

            Map<String,Object> outDoc = new HashMap<>();

            if (fieldList == null) {
                fieldList = new HashSet<>();
            }
            // default fields
            if (fieldList.isEmpty()) {
                fieldList.add("id");
                fieldList.add("iri");
                fieldList.add("ontology_name");
                fieldList.add(LABEL.getText());
                fieldList.add(DEFINITION.getOls3Text());
                fieldList.add("short_form");
                fieldList.add("obo_id");
                fieldList.add("type");
                fieldList.add("ontology_prefix");
                fieldList.add("exact_synonyms");
                fieldList.add("related_synonyms");
                fieldList.add("narrow_synonyms");
                fieldList.add("broad_synonyms");
            }

            if (fieldList.contains("id")) outDoc.put("id", JsonHelper.getString(json, "id"));
            if (fieldList.contains("iri")) outDoc.put("iri", JsonHelper.getString(json, "iri"));
            if (fieldList.contains("ontology_name")) outDoc.put("ontology_name", JsonHelper.getString(json, "ontologyId"));
            if (fieldList.contains(LABEL.getText())) {
                var label = outDoc.put(LABEL.getText(), JsonHelper.getString(json, LABEL.getText()));
                if(label!=null) {
                    outDoc.put(LABEL.getText(), label);
                }
            }
            if (fieldList.contains(DEFINITION.getOls3Text())) outDoc.put(DEFINITION.getOls3Text(),
                    JsonHelper.getStrings(json, DEFINITION.getText()));
            if (fieldList.contains("short_form")) outDoc.put("short_form", JsonHelper.getString(json, "shortForm"));
            if (fieldList.contains("obo_id")) outDoc.put("obo_id", JsonHelper.getString(json, "curie"));
            if (fieldList.contains(IS_DEFINING_ONTOLOGY.getOls3Text())) outDoc.put(IS_DEFINING_ONTOLOGY.getOls3Text(),
                    JsonHelper.getString(json, IS_DEFINING_ONTOLOGY.getText()) != null &&
                            JsonHelper.getString(json, IS_DEFINING_ONTOLOGY.getText()).equals("true"));
            if (fieldList.contains("type")) {
                outDoc.put("type", JsonHelper.getType(json, "type"));
            }
            if (fieldList.contains(SYNONYM.getText())) outDoc.put(SYNONYM.getText(), JsonHelper.getStrings(json, SYNONYM.getText()));

            // Add split synonym types (only if non-empty)
            if (fieldList.contains("exact_synonyms")) {
                List<String> exactSynonyms = JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#hasExactSynonym");
                if (!exactSynonyms.isEmpty()) {
                    outDoc.put("exact_synonyms", exactSynonyms);
                }
            }
            if (fieldList.contains("related_synonyms")) {
                List<String> relatedSynonyms = JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym");
                if (!relatedSynonyms.isEmpty()) {
                    outDoc.put("related_synonyms", relatedSynonyms);
                }
            }
            if (fieldList.contains("narrow_synonyms")) {
                List<String> narrowSynonyms = JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym");
                if (!narrowSynonyms.isEmpty()) {
                    outDoc.put("narrow_synonyms", narrowSynonyms);
                }
            }
            if (fieldList.contains("broad_synonyms")) {
                List<String> broadSynonyms = JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym");
                if (!broadSynonyms.isEmpty()) {
                    outDoc.put("broad_synonyms", broadSynonyms);
                }
            }

            if (fieldList.contains("ontology_prefix")) outDoc.put("ontology_prefix", JsonHelper.getString(json, "ontologyPreferredPrefix"));
            if (fieldList.contains("subset")) outDoc.put("subset", JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#inSubset"));
            if (fieldList.contains("ontology_iri")) outDoc.put("ontology_iri", JsonHelper.getStrings(json, "ontologyIri").get(0));

            // Include annotations that were specified with <field>_annotation
            boolean anyAnnotations = fieldList.stream()
                    .anyMatch(s -> s.endsWith("_annotation"));
            if (anyAnnotations) {
                Stream<String> annotationFields = fieldList.stream().filter(s -> s.endsWith("_annotation"));
                Map<String, Object> termAnnotations = AnnotationExtractor.extractAnnotations(json);

                annotationFields.forEach(annotationName -> {
                    String fieldName = annotationName.replaceFirst("_annotation$", "");
                    outDoc.put(annotationName, termAnnotations.get(fieldName));
                });
            }

            docs.add(outDoc);
        }

        Map<String, Object> responseHeader = new HashMap<>();
        responseHeader.put("status", 0);
        responseHeader.put("QTime", 0);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("numFound", result.numFound);
        responseBody.put("start", start);
        responseBody.put("docs", docs);

        Map<String, List<String>> facetFieldsMap = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : result.facets.entrySet()) {
            List<String> facetList = new ArrayList<>();
            for (Map.Entry<String, Long> countEntry : entry.getValue().entrySet()) {
                facetList.add(countEntry.getKey());
                facetList.add(String.valueOf(countEntry.getValue()));
            }
            facetFieldsMap.put(entry.getKey(), facetList);
        }
        Map<String, Object> facetCounts = new HashMap<>();
        facetCounts.put("facet_fields", facetFieldsMap);

        Map<String, Object> responseObj = new HashMap<>();
        responseObj.put("responseHeader", responseHeader);
        responseObj.put("response", responseBody);
        responseObj.put("facet_counts", facetCounts);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    /**
     * The v1 API has always collapsed on IRI regardless of the value passed to groupField: the
     * Solr implementation applied {@code {!collapse field=uri}} whenever the parameter was present,
     * so clients in the wild send both {@code groupField=true} and an IRI (which the old
     * documentation gave as the example). Both are honoured; only an absent, blank or explicitly
     * false value turns grouping off.
     */
    private static boolean isGroupingEnabled(String groupField) {
        return groupField != null
                && !groupField.isBlank()
                && !groupField.equalsIgnoreCase("false");
    }

    // Built from DefinedFields enum (ols3Text → text) plus a few non-enum V1 aliases.
    private static final Map<String, String> V1_QUERY_FIELD_TRANSLATIONS;
    static {
        Map<String, String> map = new HashMap<>();
        for (DefinedFields f : DefinedFields.values()) {
            if (!f.getOls3Text().isEmpty()) {
                map.put(f.getOls3Text(), f.getText());
            }
        }
        map.put("short_form", "shortForm");
        map.put("obo_id", "oboId");
        V1_QUERY_FIELD_TRANSLATIONS = Collections.unmodifiableMap(map);
    }

    private static String translateV1QueryFieldName(String v1Field) {
        return V1_QUERY_FIELD_TRANSLATIONS.getOrDefault(v1Field, v1Field);
    }
}
