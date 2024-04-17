package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
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
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
public class V1SuggestController {

    Gson gson = new Gson();

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    private OlsSolrClient solrClient;

    @RequestMapping(path = "/api/suggest", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void suggest(
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
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            @RequestParam(value = "lang", defaultValue = "en") String lang,
            HttpServletResponse response
    ) throws IOException, SolrServerException {

        ontologies = ontologyRepository.filterOntologyIDs(schemas,classifications,ontologies,exclusive,filterOption,lang);

        final SolrQuery solrQuery = new SolrQuery();

        String queryLc = query.toLowerCase();
        queryLc = ClientUtils.escapeQueryChars(queryLc);
        query = new StringBuilder(queryLc.length()+2).append('"').append(queryLc).append('"').toString();

        solrQuery.setQuery(query);
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", "label^10 edge_label^2 whitespace_edge_label^1");
        solrQuery.set("wt", "json");
        solrQuery.setFields("label");

        solrQuery.setSort("score", SolrQuery.ORDER.desc);

        if (ontologies != null && !ontologies.isEmpty()) {

            for(String ontologyId : ontologies)
                Validation.validateOntologyId(ontologyId);

            solrQuery.addFilterQuery("ontologyId: (" + String.join(" OR ", ontologies) + ")");
        }


        solrQuery.setStart(start);
        solrQuery.setRows(rows);
        solrQuery.add("group", "true");
        solrQuery.add("group.field", "label");
        solrQuery.add("group.main", "true");


        // broken in OLS3 anyway
//        solrQuery.setHighlight(true);
//        solrQuery.add("hl.simple.pre", "<b>");
//        solrQuery.add("hl.simple.post", "</b>");
//        solrQuery.addHighlightField("label");


        QueryResponse qr = solrClient.dispatchSearch(solrQuery, "ols4_autocomplete");

        List<Object> docs = new ArrayList<>();
        for(SolrDocument res : qr.getResults()) {
            Map<String,Object> outDoc = new HashMap<>();

            outDoc.put("autosuggest", res.get("label"));

            docs.add(outDoc);
        }

        Map<String, Object> responseHeader = new HashMap<>();
        responseHeader.put("status", 0);
        responseHeader.put("QTime", qr.getQTime());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("numFound", qr.getResults().getNumFound());
        responseBody.put("start", 0);
        responseBody.put("docs", docs);


        Map<String, Object> responseObj = new HashMap<>();
        responseObj.put("responseHeader", responseHeader);
        responseObj.put("response", responseBody);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

}
