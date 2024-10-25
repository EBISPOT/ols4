package uk.ac.ebi.spot.ols.controller.api.v1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
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
import uk.ac.ebi.spot.ols.model.FilterOption;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;
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
    private OlsSolrClient solrClient;


    private static final Logger logger = LoggerFactory.getLogger(V1SearchController.class);

    @RequestMapping(path = "/api/search", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void search(
            @RequestParam("q") String query,
            @RequestParam(value = "schema", required = false) Collection<String> schemas,
            @RequestParam(value = "classification", required = false) Collection<String> classifications,
            @RequestParam(value = "ontology", required = false) Collection<String> ontologies,
            @Parameter(description = "Set to true (default setting is false) for intersection (default behavior is union) of classifications.")
            @RequestParam(value = "exclusive", required = false, defaultValue = "false") boolean exclusive,
            @Parameter(description = "Use License option to filter based on license.label, license.logo and license.url variables. " +
                    "Use Composite Option to filter based on the objects (i.e. collection, subject) within the classifications variable. " +
                    "Use Linear option to filter based on String and Collection<String> based variables.")
            @RequestParam(value = "option", required = false, defaultValue = "LINEAR") FilterOption filterOption,
            @RequestParam(value = "type", required = false) Collection<String> types,
            @RequestParam(value = "slim", required = false) Collection<String> slims,
            @RequestParam(value = "fieldList", required = false) Collection<String> fieldList,
            @RequestParam(value = "queryFields", required = false) Collection<String> queryFields,
            @RequestParam(value = "exact", required = false) boolean exact,
            @RequestParam(value = "groupField", required = false) String groupField,
            @RequestParam(value = "obsoletes", defaultValue = "false") boolean queryObsoletes,
            @RequestParam(value = "local", defaultValue = "false") boolean isLocal,
            @RequestParam(value = "childrenOf", required = false) Collection<String> childrenOf,
            @RequestParam(value = "allChildrenOf", required = false) Collection<String> allChildrenOf,
            @RequestParam(value = "inclusive", required = false) boolean inclusive,
            @RequestParam(value = "isLeaf", required = false) boolean isLeaf,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam(value = "lang", defaultValue = "en") String lang,
            HttpServletResponse response
    ) throws IOException, SolrServerException {

        ontologies = ontologyRepository.filterOntologyIDs(schemas,classifications,ontologies,exclusive,filterOption,lang);

        final SolrQuery solrQuery = new SolrQuery(); // 1

        if (queryFields == null) {
            // if exact just search the supplied fields for exact matches
            if (exact) {
                String[] fields = {"label_s", "synonym_s", "short_form_s", "obo_id_s", "iri_s", "annotations_trimmed"};
                solrQuery.setQuery(
                        "((" +
                                createUnionQuery(query.toLowerCase(), SolrFieldMapper.mapFieldsList(List.of(fields))
                                        .toArray(new String[0]), true)
                                + ") AND ("+ IS_DEFINING_ONTOLOGY.getText() + ":\"true\"^100 OR " +
                                IS_DEFINING_ONTOLOGY.getText() + ":false^0))"
                );

            } else {

                solrQuery.set("defType", "edismax");
                solrQuery.setQuery(query);

                String[] fields = {"label^5", "synonym^3", "definition", "short_form^2", "obo_id^2", "iri", "annotations_trimmed"};

                solrQuery.set("qf", String.join(" ", SolrFieldMapper.mapFieldsList(List.of(fields))));

                solrQuery.set("bq",
                        IS_DEFINING_ONTOLOGY.getText() + ":\"true\"^100 " +
                        "lowercase_label:\"" + query.toLowerCase() + "\"^5 " +
                        "lowercase_synonym:\"" + query.toLowerCase() + "\"^3");
            }
        } else {
            if (exact) {
                String[] fields = SolrFieldMapper.mapFieldsList(queryFields.stream().map(queryField -> queryField + "_s")
                        .collect(Collectors.toList())).toArray(new String[0]);
                solrQuery.setQuery(createUnionQuery(query.toLowerCase(), fields, true));
            } else {

                solrQuery.set("defType", "edismax");
                solrQuery.setQuery(query.toLowerCase());
                solrQuery.set("qf", String.join(" ", SolrFieldMapper.mapFieldsList(queryFields)));
            }
        }

        if (fieldList != null && fieldList.contains("score"))
           solrQuery.setFields("_json","score");
        else
            solrQuery.setFields("_json");

        if (ontologies != null && !ontologies.isEmpty()) {

            for(String ontologyId : ontologies)
                Validation.validateOntologyId(ontologyId);

            solrQuery.addFilterQuery("ontologyId: (" + String.join(" OR ", ontologies) + ")");
        }

        if (slims != null) {
            solrQuery.addFilterQuery("subset: (" + String.join(" OR ", slims) + ")");
        }

        if (isLocal) {
            solrQuery.addFilterQuery(IMPORTED.getText() + ":false");
        }

        if (isLeaf) {
            solrQuery.addFilterQuery("hasChildren:false");
        }

        if (types != null) {
            solrQuery.addFilterQuery("type: (" + String.join(" OR ", types) + ")");
        }

        if (groupField != null) {
            solrQuery.addFilterQuery("{!collapse field=iri}");
            solrQuery.add("expand", "true");
            solrQuery.add("expand.rows", "100");

        }

        if (childrenOf != null) {
            String result = childrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));

            if (inclusive) {
                solrQuery.addFilterQuery("filter( iri: (" + result + ")) filter(hierarchicalAncestor: (" + result + "))");
            } else {
                solrQuery.addFilterQuery("hierarchicalAncestor: (" + result + ")");
            }

        }

        if (allChildrenOf != null) {
            String result = allChildrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));

            if (inclusive) {
                solrQuery.addFilterQuery("filter( iri: (" + result + ")) filter(hierarchicalAncestor: (" + result + "))");
            } else {
                solrQuery.addFilterQuery("hierarchicalAncestor: (" + result + ")");
            }
        }

        solrQuery.addFilterQuery(IS_OBSOLETE.getText() + ":" + queryObsoletes);

        solrQuery.setStart(start);
        solrQuery.setRows(rows);
//        solrQuery.setHighlight(true);
//        solrQuery.add("hl.simple.pre", "<b>");
//        solrQuery.add("hl.simple.post", "</b>");
//        solrQuery.addHighlightField("http://www.w3.org/2000/01/rdf-schema#label");
//        solrQuery.addHighlightField("https://github.com/EBISPOT/owl2neo#synonym");
//        solrQuery.addHighlightField("https://github.com/EBISPOT/owl2neo#definition");

//        solrQuery.addFacetField("ontology_name", "ontology_prefix", "type", "subset", "is_defining_ontology", "is_obsolete");

        /*
		 * Fix: Start issue -
		 * https://github.com/EBISPOT/ols4/issues/613
		 * Added new OLS4 faceFields
		 *
		 */
		// TODO: Need to check and add additional faceted fields if required
		solrQuery.addFacetField("ontologyId",
                "ontologyIri",
                "ontologyPreferredPrefix",
                "type",
                IS_DEFINING_ONTOLOGY.getText(),
                IS_OBSOLETE.getText());
		/*
		 * Fix: End
		 */
        solrQuery.add("wt", format);

        logger.debug("search: ()", solrQuery.toQueryString());

        QueryResponse qr = solrClient.dispatchSearch(solrQuery, "ols4_entities");

        List<Object> docs = parseSolrDocs(qr.getResults(), fieldList, lang);
        /*List<Object> docs = new ArrayList<>();
        for(SolrDocument res : qr.getResults()) {
            String _json = (String)res.get("_json");
            if(_json == null) {
                throw new RuntimeException("_json was null");
            }

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
                fieldList.add("label");
                fieldList.add("description");
                fieldList.add("short_form");
                fieldList.add("obo_id");
                fieldList.add("type");
                fieldList.add("ontology_prefix");
            }

            if (fieldList.contains("id")) outDoc.put("id", JsonHelper.getString(json, "id"));
            if (fieldList.contains("iri")) outDoc.put("iri", JsonHelper.getString(json, "iri"));
            if (fieldList.contains("ontology_name")) outDoc.put("ontology_name", JsonHelper.getString(json, "ontologyId"));
            if (fieldList.contains("label")) {
                var label = outDoc.put("label", JsonHelper.getString(json, "label"));
                if(label!=null) {
                    outDoc.put("label", label);
                }
            }
            if (fieldList.contains("description")) outDoc.put("description", JsonHelper.getStrings(json, "definition"));
            if (fieldList.contains("short_form")) outDoc.put("short_form", JsonHelper.getString(json, "shortForm"));
            if (fieldList.contains("obo_id")) outDoc.put("obo_id", JsonHelper.getString(json, "curie"));
            if (fieldList.contains(IS_DEFINING_ONTOLOGY.getOls3Text())) outDoc.put(IS_DEFINING_ONTOLOGY.getOls3Text(),
                    JsonHelper.getString(json, IS_DEFINING_ONTOLOGY.getText()) != null &&
                            JsonHelper.getString(json, IS_DEFINING_ONTOLOGY.getText()).equals("true"));
            if (fieldList.contains("type")) {
                outDoc.put("type", JsonHelper.getType(json, "type"));
            }
            if (fieldList.contains("synonym")) outDoc.put("synonym", JsonHelper.getStrings(json, "synonym"));
            if (fieldList.contains("ontology_prefix")) outDoc.put("ontology_prefix", JsonHelper.getString(json, "ontologyPreferredPrefix"));
            if (fieldList.contains("subset")) outDoc.put("subset", JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#inSubset"));
            if (fieldList.contains("ontology_iri")) outDoc.put("ontology_iri", JsonHelper.getStrings(json, "ontologyIri").get(0));
            if (fieldList.contains("score")) outDoc.put("score", res.get("score"));

            // Include annotations that were specified with <field>_annotation
            boolean anyAnnotations = fieldList.stream()
                    .anyMatch(s -> s.endsWith("_annotation"));
            if (anyAnnotations) {
                Stream<String> annotationFields = fieldList.stream().filter(s -> s.endsWith("_annotation"));
                Map<String, Object> termAnnotations = AnnotationExtractor.extractAnnotations(json);

                annotationFields.forEach(annotationName -> {
                    // Remove _annotation suffix to get plain annotation name
                    String fieldName = annotationName.replaceFirst("_annotation$", "");
                    outDoc.put(annotationName, termAnnotations.get(fieldName));
                });
            }

            docs.add(outDoc);
        }*/

        Map<String, Object> responseHeader = new HashMap<>();
        responseHeader.put("status", 0);
        responseHeader.put("QTime", qr.getQTime());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("numFound", qr.getResults().getNumFound());
        responseBody.put("start", start);
        responseBody.put("docs", docs);

        /*
		 * Fix: Start issue -
		 * https://github.com/EBISPOT/ols4/issues/613
		 * Created facetFieldsMap: Start Gson not able to parse FacetField format -
		 * [ontologyId:[efo (17140)] Converting FacetFied to Map format
		 */
		Map<String, List<String>> facetFieldsMap = parseFacetFields(qr.getFacetFields());
		Map<String, Object> facetCounts = new HashMap<>();
		facetCounts.put("facet_fields", facetFieldsMap);
		/*
		 * Fix: End
		 */

        Map<String, Object> responseObj = new HashMap<>();
        responseObj.put("responseHeader", responseHeader);
        responseObj.put("response", responseBody);

        /*
		 * Fix: Start issue -
		 * https://github.com/EBISPOT/ols4/issues/613
		 * Added facet_counts to responseObj
		 */
		responseObj.put("facet_counts", facetCounts);
		/*
		 * Fix: End
		 */

		/**
		 * Fix: Start
		 * issue - https://github.com/TIBHannover/ols4/issues/78
		 * 
		 */
		if(qr.getExpandedResults() != null && qr.getExpandedResults().size() > 0)
			responseObj.put("expanded", parseExpandedSolrResults(qr.getExpandedResults(), fieldList, lang));
		
		/**
		 * Fix: End
		 */
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
    
    private Map<String,Object> parseExpandedSolrResults(Map<String, SolrDocumentList> expandedResults, Collection<String> fieldList,
			String lang) {
    	Map<String, Object> result = new HashMap<>();
    	expandedResults.entrySet().parallelStream().forEach((entry) -> {
    		Map<String, Object> expandedResult = new HashMap<>();
    		expandedResult.put("numFound", entry.getValue().getNumFound());
    		expandedResult.put("start", entry.getValue().getStart());
    		expandedResult.put("docs", parseSolrDocs(entry.getValue(), fieldList, lang));
    		result.put(entry.getKey(), expandedResult);
    	});
    	return result;
	}

	private List<Object> parseSolrDocs(SolrDocumentList results, Collection<String> fieldList, String lang) {
    	List<Object> docs = new ArrayList<>();
    	for(SolrDocument res : results) {
            String _json = (String)res.get("_json");
            if(_json == null) {
                throw new RuntimeException("_json was null");
            }

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
                fieldList.add("label");
                fieldList.add("description");
                fieldList.add("short_form");
                fieldList.add("obo_id");
                fieldList.add("type");
                fieldList.add("ontology_prefix");
            }

            if (fieldList.contains("id")) outDoc.put("id", JsonHelper.getString(json, "id"));
            if (fieldList.contains("iri")) outDoc.put("iri", JsonHelper.getString(json, "iri"));
            if (fieldList.contains("ontology_name")) outDoc.put("ontology_name", JsonHelper.getString(json, "ontologyId"));
            if (fieldList.contains("label")) {
                var label = outDoc.put("label", JsonHelper.getString(json, "label"));
                if(label!=null) {
                    outDoc.put("label", label);
                }
            }
            if (fieldList.contains("description")) outDoc.put("description", JsonHelper.getStrings(json, "definition"));
            if (fieldList.contains("short_form")) outDoc.put("short_form", JsonHelper.getString(json, "shortForm"));
            if (fieldList.contains("obo_id")) outDoc.put("obo_id", JsonHelper.getString(json, "curie"));
            if (fieldList.contains(IS_DEFINING_ONTOLOGY.getOls3Text())) outDoc.put(IS_DEFINING_ONTOLOGY.getOls3Text(),
                    JsonHelper.getString(json, IS_DEFINING_ONTOLOGY.getText()) != null &&
                            JsonHelper.getString(json, IS_DEFINING_ONTOLOGY.getText()).equals("true"));
            if (fieldList.contains("type")) {
                outDoc.put("type", JsonHelper.getType(json, "type"));
            }
            if (fieldList.contains("synonym")) outDoc.put("synonym", JsonHelper.getStrings(json, "synonym"));
            if (fieldList.contains("ontology_prefix")) outDoc.put("ontology_prefix", JsonHelper.getString(json, "ontologyPreferredPrefix"));
            if (fieldList.contains("subset")) outDoc.put("subset", JsonHelper.getStrings(json, "http://www.geneontology.org/formats/oboInOwl#inSubset"));
            if (fieldList.contains("ontology_iri")) outDoc.put("ontology_iri", JsonHelper.getStrings(json, "ontologyIri").get(0));
            if (fieldList.contains("score")) outDoc.put("score", res.get("score"));

            // Include annotations that were specified with <field>_annotation
            boolean anyAnnotations = fieldList.stream()
                    .anyMatch(s -> s.endsWith("_annotation"));
            if (anyAnnotations) {
                Stream<String> annotationFields = fieldList.stream().filter(s -> s.endsWith("_annotation"));
                Map<String, Object> termAnnotations = AnnotationExtractor.extractAnnotations(json);

                annotationFields.forEach(annotationName -> {
                    // Remove _annotation suffix to get plain annotation name
                    String fieldName = annotationName.replaceFirst("_annotation$", "");
                    outDoc.put(annotationName, termAnnotations.get(fieldName));
                });
            }

            docs.add(outDoc);
        }
    	return docs;
	}

    private Map<String, List<String>> parseFacetFields(List<FacetField> facetFields) {
		Map<String, List<String>> facetFieldsMap = new HashMap<>();
		List<String> newFacetFields;
		if (facetFields != null && facetFields.size() > 0) {
			for (FacetField ff : facetFields) {
				List<Count> facetFieldCount = ff.getValues();
				if (facetFieldsMap.containsKey(ff.getName()))
					newFacetFields = facetFieldsMap.get(ff.getName());
				else
					newFacetFields = new ArrayList<>();

				for (Count ffCount : facetFieldCount) {
					newFacetFields.add(ffCount.getName());
					newFacetFields.add("" + ffCount.getCount());
				}
				facetFieldsMap.put(ff.getName(), newFacetFields);
			}
		}
		return facetFieldsMap;
	}

    Function<String, String> addQuotes = new Function<String, String>() {
        @Override
        public String apply(String s) {
            return new StringBuilder(s.length() + 2).append('"').append(s).append('"').toString();
        }
    };

    private String createUnionQuery(String query, String[] fields, boolean exact) {
        StringBuilder builder = new StringBuilder();
        for (int x = 0; x < fields.length; x++) {
            builder.append(fields[x]);
            builder.append(":\"");

            if(!exact)
                builder.append("*");

            builder.append(query);

            if(!exact)
                builder.append("*");

            builder.append("\" ");

            if (x + 1 < fields.length) {
                builder.append("OR ");
            }
        }
        return builder.toString();
    }
}
