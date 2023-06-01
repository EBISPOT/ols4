package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Controller
public class V1SelectController {

    Gson gson = new Gson();

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    private OlsSolrClient solrClient;

    @RequestMapping(path = "/api/select", produces = {APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void select(
            @RequestParam("q") String query,
            @RequestParam(value = "ontology", required = false) Collection<String> ontologies,
            @RequestParam(value = "type", required = false) Collection<String> types,
            @RequestParam(value = "slim", required = false) Collection<String> slims,
            @RequestParam(value = "fieldList", required = false) Collection<String> fieldList,
            @RequestParam(value = "obsoletes", defaultValue = "false") boolean queryObsoletes,
            @RequestParam(value = "local", defaultValue = "false") boolean isLocal,
            @RequestParam(value = "childrenOf", required = false) Collection<String> childrenOf,
            @RequestParam(value = "allChildrenOf", required = false) Collection<String> allChildrenOf,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            HttpServletResponse response
    ) throws IOException, SolrServerException {

        final SolrQuery solrQuery = new SolrQuery(); // 1

        String queryLc = query.toLowerCase();
        if (query.contains(" ")) {
            query = "(" + createIntersectionString(query) + ")";
        }
        solrQuery.setQuery(query);
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", "label synonym edge_label edge_synonym shortForm edge_shortForm curie edge_curie iri");
        solrQuery.set("bq", "type:ontology^10.0 isDefiningOntology:true^100.0 str_label:\"" + queryLc + "\"^1000  edge_label:\"" + queryLc + "\"^500 str_synonym:\"" + queryLc + "\" edge_synonym:\"" + queryLc + "\"^100");
        solrQuery.set("wt", "json");

        if (fieldList == null) {
            fieldList = new HashSet<>();
        }

        if (fieldList.isEmpty()) {
            fieldList.add("label");
            fieldList.add("iri");
            fieldList.add("id");
            fieldList.add("type");
            fieldList.add("shortForm");
            fieldList.add("curie");
            fieldList.add("ontologyId");
            fieldList.add("ontologyPreferredPrefix");
        }
        solrQuery.setFields(fieldList.toArray(new String[fieldList.size()]));

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
//        solrQuery.setHighlight(true);
//        solrQuery.add("hl.simple.pre", "<b>");
//        solrQuery.add("hl.simple.post", "</b>");
//        solrQuery.addHighlightField("label_autosuggest");
//        solrQuery.addHighlightField("label");
//        solrQuery.addHighlightField("synonym_autosuggest");
//        solrQuery.addHighlightField("synonym");

        QueryResponse qr = dispatchSearch(solrQuery, "ols4_entities");

        List<Object> docs = new ArrayList<>();
        for (SolrDocument res : qr.getResults()) {
            Map<String, Object> outDoc = new HashMap<>();

            outDoc.put("id", res.get("id").toString().replace("+", ":"));
            outDoc.put("iri", ((Collection<String>) res.get("iri")).iterator().next());
            outDoc.put("short_form", ((Collection<String>) res.get("shortForm")).iterator().next());
            outDoc.put("obo_id", ((Collection<String>) res.get("curie")).iterator().next());
            outDoc.put("label", ((Collection<String>) res.get("label")).iterator().next());
            outDoc.put("ontology_name", ((Collection<String>) res.get("ontologyId")).iterator().next());
            outDoc.put("ontology_prefix", ((Collection<String>) res.get("ontologyPreferredPrefix")).iterator().next());
            outDoc.put("type", ((Collection<String>) res.get("type")).iterator().next());

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

        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();

    }

    private QueryResponse dispatchSearch(SolrQuery query, String core) throws IOException, SolrServerException {
        org.apache.solr.client.solrj.SolrClient mySolrClient = new HttpSolrClient.Builder(solrClient.host + "/solr/" + core).build();
        return mySolrClient.query(query);
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
