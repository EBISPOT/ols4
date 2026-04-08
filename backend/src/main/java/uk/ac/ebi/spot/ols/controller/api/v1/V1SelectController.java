package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.ac.ebi.spot.ols.JsonHelper;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchQuery;
import uk.ac.ebi.spot.ols.repository.search.SearchType;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Tag(name = "Select Controller")
@RestController
public class V1SelectController {

    Gson gson = new Gson();

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    private OlsSearchClient searchClient;

    private static final Logger logger = LoggerFactory.getLogger(V1SelectController.class);

    @RequestMapping(path = "/api/select", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void select(
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
                    description = "Specifcy the fields to return, the defaults are {iri,label,short_form,obo_id,ontology_name,ontology_prefix,description,type}",
                    example = "[\"iri\",\"label\",\"short_form\",\"obo_id\",\"ontology_name\"]") Collection<String> fieldList,
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
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            @RequestParam(value = "lang", defaultValue = "en") String lang,
            HttpServletResponse response
    ) throws IOException {

        OlsSearchQuery searchQuery = new OlsSearchQuery();
        searchQuery.setSearchText(query);

        if (ontologies != null && !ontologies.isEmpty()) {
            for (String ontologyId : ontologies)
                Validation.validateOntologyId(ontologyId);
            searchQuery.addFilter("ontologyId", ontologies, SearchType.WHOLE_FIELD);
        }

        if (types != null) {
            searchQuery.addFilter("type", types, SearchType.WHOLE_FIELD);
        }

        if (slims != null) {
            searchQuery.addFilter("subset", slims, SearchType.WHOLE_FIELD);
        }

        if (isLocal) {
            searchQuery.addFilter("isDefiningOntology", List.of("true"), SearchType.WHOLE_FIELD);
        }

        if (childrenOf != null) {
            searchQuery.addFilter("directAncestor", childrenOf, SearchType.WHOLE_FIELD);
        }

        if (allChildrenOf != null) {
            searchQuery.addFilter("hierarchicalAncestor", allChildrenOf, SearchType.WHOLE_FIELD);
        }

        searchQuery.addFilter("isObsolete", List.of(Boolean.toString(queryObsoletes)), SearchType.WHOLE_FIELD);

        logger.debug("select: searchText={}", query);

        OlsSearchClient.RawSearchResult result = searchClient.searchRaw(searchQuery, start, rows);

        List<Object> docs = new ArrayList<>();
        for (String _json : result.jsonStrings) {

            JsonObject json = RemoveLiteralDatatypesTransform.transform(
                    LocalizationTransform.transform( JsonParser.parseString( _json ), lang)
            ).getAsJsonObject();

            if (fieldList == null) {
                fieldList = new HashSet<>();
            }
            // default fields
            if (fieldList.isEmpty()) {
                fieldList.add("id");
                fieldList.add("iri");
                fieldList.add("short_form");
                fieldList.add("obo_id");
                fieldList.add(LABEL.getText());
                fieldList.add("ontology_name");
                fieldList.add("ontology_prefix");
                fieldList.add(DEFINITION.getOls3Text());
                fieldList.add("type");
            }

            Map<String, Object> outDoc = new HashMap<>();

            if (fieldList.contains("id")) outDoc.put("id", JsonHelper.getString(json, "id"));
            if (fieldList.contains("iri")) outDoc.put("iri", JsonHelper.getString(json, "iri"));
            if (fieldList.contains("ontology_name")) outDoc.put("ontology_name", JsonHelper.getString(json, "ontologyId"));
            if (fieldList.contains(LABEL.getText())) outDoc.put(LABEL.getText(), JsonHelper.getString(json, LABEL.getText()));
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
            if (fieldList.contains("ontology_prefix")) outDoc.put("ontology_prefix", JsonHelper.getString(json, "ontologyPreferredPrefix"));

            docs.add(outDoc);
        }

        Map<String, Object> responseParams = new LinkedHashMap<>();
        responseParams.put("q", query);

        Map<String, Object> responseHeader = new LinkedHashMap<>();
        responseHeader.put("params", responseParams);
        responseHeader.put("status", 0);
        responseHeader.put("QTime", 0);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("numFound", result.numFound);
        responseBody.put("start", 0);
        responseBody.put("docs", docs);

        Map<String, Object> responseObj = new LinkedHashMap<>();
        responseObj.put("responseHeader", responseHeader);
        responseObj.put("response", responseBody);
        // Highlighting not supported in Postgres search — return empty map
        responseObj.put("highlighting", new LinkedHashMap<>());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();

    }

}
