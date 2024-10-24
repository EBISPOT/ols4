package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Tag(name = "Select Controller")
@RestController
public class V1SelectController {

    Gson gson = new Gson();

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    private OlsSolrClient solrClient;

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
                    example = "efo,bfo") Collection<String> ontologies,
            @RequestParam(value = "type", required = false)
            @Parameter(name = "type",
                    description = "Restrict a search to an entity type, one of {class,property,individual,ontology}",
                    example = "class,property") Collection<String> types,
            @RequestParam(value = "slim", required = false)
            @Parameter(name = "slim",
                    description = "Restrict a search to an particular set of slims by name") Collection<String> slims,
            @RequestParam(value = "fieldList", required = false)
            @Parameter(name = "fieldList",
                    description = "Specifcy the fields to return, the defaults are {iri,label,short_form,obo_id,ontology_name,ontology_prefix,description,type}",
                    example = "iri,label,short_form,obo_id,ontology_name") Collection<String> fieldList,
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
                    example = "http://www.ebi.ac.uk/efo/EFO_0001421, http://www.ebi.ac.uk/efo/EFO_0004228") Collection<String> childrenOf,
            @RequestParam(value = "allChildrenOf", required = false)
            @Parameter(name = "allChildrenOf",
                    description = "You can restrict a search to all children of a given term. Supply a list of IRI for the terms that you want to search under (subclassOf/is-a plus any hierarchical/transitive properties like 'part of' or 'develops from')",
                    example = "http://www.ebi.ac.uk/efo/EFO_0001421, http://www.ebi.ac.uk/efo/EFO_0004228") Collection<String> allChildrenOf,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            @RequestParam(value = "lang", defaultValue = "en") String lang,
            HttpServletResponse response
    ) throws IOException, SolrServerException {

        final SolrQuery solrQuery = new SolrQuery(); // 1

        String queryLc = query.toLowerCase();
        if (query.contains(" ")) {
            query = "(" + createIntersectionString(query) + ")";
        }
        solrQuery.setQuery(query);
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", LABEL.getText()+" whitespace_edge_label synonym whitespace_edge_synonym shortForm whitespace_edge_shortForm curie iri");
        solrQuery.set("bq", "type:ontology^10.0 " +
                IS_DEFINING_ONTOLOGY.getText() +":true^100.0 str_label:\"" + queryLc + "\"^1000  edge_label:\"" +
                queryLc + "\"^500 str_synonym:\"" + queryLc + "\" edge_synonym:\"" + queryLc + "\"^100");
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
            solrQuery.addFilterQuery(IS_DEFINING_ONTOLOGY.getText() + ":true");
        }

        if (childrenOf != null) {
            String result = childrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));
            solrQuery.addFilterQuery(DIRECT_ANCESTOR.getText() + ": (" + result + ")");
        }

        if (allChildrenOf != null) {
            String result = allChildrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));
            solrQuery.addFilterQuery(HIERARCHICAL_ANCESTOR.getText() + ": (" + result + ")");
        }

        solrQuery.addFilterQuery(IS_OBSOLETE.getText() + ":" + queryObsoletes);
        solrQuery.setStart(start);
        solrQuery.setRows(rows);
        solrQuery.setHighlight(true);
        solrQuery.add("hl.simple.pre", "<b>");
        solrQuery.add("hl.simple.post", "</b>");
        solrQuery.addHighlightField("whitespace_edge_label");
        solrQuery.addHighlightField(LABEL.getText());
        solrQuery.addHighlightField("whitespace_edge_synonym");
        solrQuery.addHighlightField(SYNONYM.getText());

       logger.debug("select: ()", solrQuery.toQueryString());

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
                fieldList.add(LABEL.getText());
                fieldList.add("ontology_name");
                fieldList.add("ontology_prefix");
                fieldList.add(DEFINITION.getOls3Text());
                fieldList.add("type");
            }

            Map<String, Object> outDoc = new HashMap<>();

            if (fieldList.contains("id")) outDoc.put("id", res.get("id").toString().replace('+', ':'));
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
                    resHighlight.put(LABEL.getText()+"_autosuggest", highlight.get(fieldName));
                } else if(fieldName.equals("whitespace_edge_synonym")) {
                    resHighlight.put(SYNONYM.getText()+"_autosuggest", highlight.get(fieldName));
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
