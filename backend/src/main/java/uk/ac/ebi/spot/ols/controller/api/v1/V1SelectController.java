package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.spot.ols.model.FilterOption;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.repository.transforms.RemoveLiteralDatatypesTransform;
import uk.ac.ebi.spot.ols.repository.v1.JsonHelper;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public class V1SelectController {

    Gson gson = new Gson();

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    private OlsSolrClient solrClient;

    @RequestMapping(path = "/api/select", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void select(
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
            @RequestParam(value = "obsoletes", defaultValue = "false") boolean queryObsoletes,
            @RequestParam(value = "local", defaultValue = "false") boolean isLocal,
            @RequestParam(value = "childrenOf", required = false) Collection<String> childrenOf,
            @RequestParam(value = "allChildrenOf", required = false) Collection<String> allChildrenOf,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            @RequestParam(value = "lang", defaultValue = "en") String lang,
            HttpServletResponse response
    ) throws IOException, SolrServerException {

        ontologies = ontologyRepository.filterOntologyIDs(schemas,classifications,ontologies,exclusive,filterOption,lang);

        final SolrQuery solrQuery = new SolrQuery(); // 1

        String queryLc = query.toLowerCase();
        if (query.contains(" ")) {
            query = "(" + createIntersectionString(query) + ")";
        }
        solrQuery.setQuery(query);
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", "label whitespace_edge_label synonym whitespace_edge_synonym shortForm whitespace_edge_shortForm curie iri");
        solrQuery.set("bq", "type:ontology^10.0 isDefiningOntology:true^100.0 str_label:\"" + queryLc + "\"^1000  edge_label:\"" + queryLc + "\"^500 str_synonym:\"" + queryLc + "\" edge_synonym:\"" + queryLc + "\"^100");
        solrQuery.set("wt", "json");

        solrQuery.setFields("_json", "id");

        if (ontologies != null && !ontologies.isEmpty()) {

            for (String ontologyId : ontologies)
                Validation.validateOntologyId(ontologyId);

            solrQuery.addFilterQuery("ontologyId: (" + String.join(" OR ", ontologies) + ")");
        }

        if (types != null) {
            solrQuery.addFilterQuery("type: (" + String.join(" OR ", types) + ")");
        }

        if (slims != null) {
            solrQuery.addFilterQuery("subset: (" + String.join(" OR ", slims) + ")");
        }

        if (isLocal) {
            solrQuery.addFilterQuery("isDefiningOntology:true");
        }

        if (childrenOf != null) {
            String result = childrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));
            solrQuery.addFilterQuery("directAncestor: (" + result + ")");
        }

        if (allChildrenOf != null) {
            String result = allChildrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));
            solrQuery.addFilterQuery("hierarchicalAncestor: (" + result + ")");
        }

        solrQuery.addFilterQuery("isObsolete:" + queryObsoletes);
        solrQuery.setStart(start);
        solrQuery.setRows(rows);
        solrQuery.setHighlight(true);
        solrQuery.add("hl.simple.pre", "<b>");
        solrQuery.add("hl.simple.post", "</b>");
        solrQuery.addHighlightField("whitespace_edge_label");
        solrQuery.addHighlightField("label");
        solrQuery.addHighlightField("whitespace_edge_synonym");
        solrQuery.addHighlightField("synonym");

        System.out.println("select: " + solrQuery.toQueryString());

        QueryResponse qr = solrClient.dispatchSearch(solrQuery, "ols4_entities");

        List<Object> docs = new ArrayList<>();
        for (SolrDocument res : qr.getResults()) {

            String _json = (String)res.get("_json");
            if(_json == null) {
                throw new RuntimeException("_json was null");
            }

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
                fieldList.add("label");
                fieldList.add("ontology_name");
                fieldList.add("ontology_prefix");
                fieldList.add("description");
                fieldList.add("type");
            }

            Map<String, Object> outDoc = new HashMap<>();

            if (fieldList.contains("id")) outDoc.put("id", res.get("id").toString().replace('+', ':'));
            if (fieldList.contains("iri")) outDoc.put("iri", JsonHelper.getString(json, "iri"));
            if (fieldList.contains("ontology_name")) outDoc.put("ontology_name", JsonHelper.getString(json, "ontologyId"));
            if (fieldList.contains("label")) outDoc.put("label", JsonHelper.getString(json, "label"));
            if (fieldList.contains("description")) outDoc.put("description", JsonHelper.getStrings(json, "definition"));
            if (fieldList.contains("short_form")) outDoc.put("short_form", JsonHelper.getString(json, "shortForm"));
            if (fieldList.contains("obo_id")) outDoc.put("obo_id", JsonHelper.getString(json, "curie"));
            if (fieldList.contains("is_defining_ontology")) outDoc.put("is_defining_ontology",
                    JsonHelper.getString(json, "isDefiningOntology") != null && JsonHelper.getString(json, "isDefiningOntology").equals("true"));
            if (fieldList.contains("type")) outDoc.put("type", "class");
            if (fieldList.contains("synonym")) outDoc.put("synonym", JsonHelper.getStrings(json, "synonym"));
            if (fieldList.contains("ontology_prefix")) outDoc.put("ontology_prefix", JsonHelper.getString(json, "ontologyPreferredPrefix"));

            docs.add(outDoc);
        }

        Map<String, Object> responseParams = new LinkedHashMap<>();
        responseParams.put("q", query);

        Map<String, Object> responseHeader = new LinkedHashMap<>();
        responseHeader.put("params", responseParams);
        responseHeader.put("status", 0);
        responseHeader.put("QTime", qr.getQTime());

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("numFound", qr.getResults().getNumFound());
        responseBody.put("start", 0);
        responseBody.put("docs", docs);


        Map<String, Object> responseObj = new LinkedHashMap<>();
        responseObj.put("responseHeader", responseHeader);
        responseObj.put("response", responseBody);

        Map<String,Object> highlighting = new LinkedHashMap<>();
        for(var hl : qr.getHighlighting().entrySet()) {
            var id = hl.getKey();
            var highlight = hl.getValue();
            Map<String,Object> resHighlight = new LinkedHashMap<>();
            for(var fieldName : highlight.keySet()) {
                if(fieldName.equals("whitespace_edge_label")) {
                    resHighlight.put("label_autosuggest", highlight.get(fieldName));
                } else if(fieldName.equals("whitespace_edge_synonym")) {
                    resHighlight.put("synonym_autosuggest", highlight.get(fieldName));
                } else {
                    resHighlight.put(fieldName, highlight.get(fieldName));
                }
            }
            highlighting.put(id.replace('+', ':'), resHighlight);
        }
        responseObj.put("highlighting", highlighting);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();

    }

    private String createIntersectionString(String query) {
        StringBuilder builder = new StringBuilder();
        String[] tokens = query.split(" ");
        for (int x = 0; x < tokens.length; x++) {
            builder.append(tokens[x]);
            if (x + 1 < tokens.length) {
                builder.append(" AND ");
            }
        }
        return builder.toString();
    }

    Function<String, String> addQuotes = new Function<String, String>() {
        @Override
        public String apply(String s) {
            return new StringBuilder(s.length() + 2).append('"').append(s).append('"').toString();
        }
    };

}
