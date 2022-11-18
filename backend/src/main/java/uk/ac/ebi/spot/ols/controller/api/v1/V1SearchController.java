package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.Gson;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.v1.V1OntologyRepository;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Simon Jupp
 * @date 02/07/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Controller
public class V1SearchController {

    Gson gson = new Gson();

    private static String COLON = ":";
    private static String QUOTUE = "\"";
    private static String SPACE = " ";
    private static String OR = "OR";
    private static String AND = "AND";

    @Autowired
    private V1OntologyRepository ontologyRepository;

    @Autowired
    private OlsSolrClient solrClient;

    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(WebMvcLinkBuilder.linkTo(V1SearchController.class).withRel("search"));
        return resource;
    }

    // In OLS3, Solr contained fields such as "synonym_s" which were mapped from their OWL properties at index time.
    // In OLS4, we store the OWL properties directly in Solr by IRI.
    //
    // In order to perform a search we therefore need to map OLS3 field names to the appropriate predicate IRIs.
    //
    // nb. sometimes a field name maps to more than one predicate.
    //
    private String[] mapFieldsList(String[] ols3FieldNames) {

        List<String> newFields = new ArrayList<>();

        for (String legacyFieldName : ols3FieldNames) {

            String prefix = "";
            String suffix = "";

            // OLS3 uses a SUFFIX of "_s" for lowercased versions of fields.
            // In OLS4 we use "lowercase_" as a PREFIX instead.
            //
            if (legacyFieldName.endsWith("_s")) {
                prefix = "lowercase_";
                legacyFieldName = legacyFieldName.substring(2);
            } else if (legacyFieldName.endsWith("_e")) {
                prefix = "edge_";
                legacyFieldName = legacyFieldName.substring(2);
            }

            if (legacyFieldName.indexOf('^') != -1) {
                suffix = legacyFieldName.substring(legacyFieldName.indexOf('^') + 1);
                legacyFieldName = legacyFieldName.substring(0, legacyFieldName.indexOf('^') - 1);
            }

            if (legacyFieldName.equals("iri")) {
                newFields.add(prefix + "iri" + suffix);
                continue;
            }

            if (legacyFieldName.equals("label")) {
                newFields.add(prefix + "http__//www.w3.org/2000/01/rdf-schema#label" + suffix);
                continue;
            }

            if (legacyFieldName.equals("synonym")) {
                newFields.add(prefix + "synonym" + suffix);
                continue;
            }

            if (legacyFieldName.equals("definition")) {
                newFields.add(prefix + "definition" + suffix);
                continue;
            }

            if (legacyFieldName.equals("description")) {
                newFields.add(prefix + "http__//www.w3.org/2000/01/rdf-schema#comment" + suffix);
                continue;
            }

            if (legacyFieldName.equals("short_form")) {
                newFields.add(prefix + "shortForm" + suffix);
                continue;
            }

        }

        // escape special characters in field names for solr query
        //
        newFields = newFields.stream().map(iri -> {
            return iri.replace(":", "__");
        }).collect(Collectors.toList());

        return newFields.toArray(new String[0]);
    }

//    Collection<V1Ontology> getOntologies(Collection<String> ontologyIds) {
//
//        return ontologyIds.stream().map(ontologyId -> {
//            return ontologyRepository.get(ontologyId);
//        }).collect(Collectors.toList());
//
//    }


    @RequestMapping(path = "/api/search", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void search(
            @RequestParam("q") String query,
            @RequestParam(value = "ontology", required = false) Collection<String> ontologyIds,
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
            HttpServletResponse response
    ) throws IOException, SolrServerException {

        final SolrQuery solrQuery = new SolrQuery(); // 1

        if (queryFields == null) {
            // if exact just search the supplied fields for exact matches
            if (exact) {
                String[] fields = {"label_s", "synonym_s", "short_form_s", "obo_id_s", "iri_s", "annotations_trimmed"};
                solrQuery.setQuery(
                        "((" +
                                createUnionQuery(query.toLowerCase(), mapFieldsList(fields), true)
                                + ") AND (imported:\"false\"^100 OR imported:true^0))"
                );

            } else {

                solrQuery.set("defType", "edismax");
                solrQuery.setQuery(query);

                String[] fields = {"label^5", "synonym^3", "description", "short_form^2", "obo_id^2", "annotations", "logical_description", "iri"};

                solrQuery.set("qf", String.join(" ", mapFieldsList(fields)));

                solrQuery.set("bq",
                        "imported:\"false\"^100 " +
                        "lowercase_http__//www.w3.org/2000/01/rdf-schema#label:\"" + query.toLowerCase() + "\"^5" +
                        "lowercase_synonym:\"" + query.toLowerCase() + "\"^3");
            }
        } else {
            if (exact) {
                List<String> fieldS = queryFields.stream()
                        .map(addStringField).collect(Collectors.toList());
                solrQuery.setQuery(createUnionQuery(query, fieldS.toArray(new String[fieldS.size()]), true));
            } else {

                solrQuery.set("defType", "edismax");
                solrQuery.setQuery(query);
                solrQuery.set("qf", String.join(" ", mapFieldsList(queryFields.toArray(new String[0]))));
            }
        }

        if (fieldList == null) {
            fieldList = new HashSet<>();
            fieldList.add("id");
            fieldList.add("iri");
            fieldList.add("label");
            fieldList.add("short_form");
            fieldList.add("obo_id");
            fieldList.add("is_defining_ontology");
            fieldList.add("ontology_name");
            fieldList.add("ontology_prefix");
            fieldList.add("description");
            fieldList.add("type");
        }
//        solrQuery.setFields(mapFieldsList(fieldList.toArray(new String[fieldList.size()])));

//            if (ontologies != null && !ontologies.isEmpty()) {
//                solrQuery.addFilterQuery("ontology_name: (" + String.join(" OR ", ontologyIds) + ")");
//            }

        if (slims != null) {
            solrQuery.addFilterQuery("subset: (" + String.join(" OR ", slims) + ")");
        }

        if (isLocal) {
            solrQuery.addFilterQuery("imported:false");
        }

        if (isLeaf) {
            solrQuery.addFilterQuery("has_children:false");
        }

        if (types != null) {
            solrQuery.addFilterQuery("type: (" + String.join(" OR ", types) + ")");
        }

        if (groupField != null) {
            solrQuery.addFilterQuery("{!collapse field=iri}");
            solrQuery.add("expand=true", "true");
            solrQuery.add("expand.rows", "100");

        }

        if (childrenOf != null) {
            String result = childrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));

            if (inclusive) {
                solrQuery.addFilterQuery("filter( iri: (" + result + ")) filter(ancestor_iri: (" + result + "))");
            } else {
                solrQuery.addFilterQuery("ancestor_iri: (" + result + ")");
            }

        }

        if (allChildrenOf != null) {
            String result = allChildrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));

            if (inclusive) {
                solrQuery.addFilterQuery("filter( iri: (" + result + ")) filter(hierarchical_ancestor_iri: (" + result + "))");
            } else {
                solrQuery.addFilterQuery("hierarchical_ancestor_iri: (" + result + ")");
            }
        }

        // TODO
//        solrQuery.addFilterQuery("is_obsolete:" + queryObsoletes);

        solrQuery.setStart(start);
        solrQuery.setRows(rows);
//        solrQuery.setHighlight(true);
//        solrQuery.add("hl.simple.pre", "<b>");
//        solrQuery.add("hl.simple.post", "</b>");
//        solrQuery.addHighlightField("http://www.w3.org/2000/01/rdf-schema#label");
//        solrQuery.addHighlightField("https://github.com/EBISPOT/owl2neo#synonym");
//        solrQuery.addHighlightField("https://github.com/EBISPOT/owl2neo#definition");

//        solrQuery.addFacetField("ontology_name", "ontology_prefix", "type", "subset", "is_defining_ontology", "is_obsolete");
        solrQuery.add("wt", format);


        System.out.println(solrQuery.jsonStr());

        QueryResponse qr = dispatchSearch(solrQuery, response);

        List<Object> docs = new ArrayList<>();
        for(SolrDocument res : qr.getResults()) {

            Map<String,Object> outDoc = new HashMap<>();

            outDoc.put("id", res.get("id"));
            outDoc.put("iri", ((Collection<String>) res.get("iri")).toArray()[0]);
            outDoc.put("ontology_name", ((Collection<String>) res.get("ontology_id")).toArray()[0]);

            Collection<String> labels = (Collection<String>) res.get("http__//www.w3.org/2000/01/rdf-schema#label");
            if(labels != null && labels.size() > 0) {
                outDoc.put("label", labels.toArray()[0]);
            }

            Collection<String> descriptions = (Collection<String>) res.get("definition");
            if(descriptions != null) {
                outDoc.put("description", descriptions.toArray());
            }

            Collection<String> shortForms = (Collection<String>) res.get("shortForm");
            if(shortForms != null) {
                outDoc.put("short_form", shortForms.toArray()[0]);
            }


            Collection<String> oboIds = (Collection<String>) res.get("oboId");
            if(oboIds != null) {
                outDoc.put("obo_id", oboIds.toArray()[0]);
            }

            Collection<String> imported = (Collection<String>) res.get("imported");
            if(imported != null) {
                outDoc.put("is_defining_ontology", !imported.toArray()[0].equals("true"));
            }

            outDoc.put("type", "class");

            // TODO: ontology_prefix

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
        responseObj.put("docs", docs);

        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    Function<String, String> addQuotes = new Function<String, String>() {
        @Override
        public String apply(String s) {
            return new StringBuilder(s.length() + 2).append('"').append(s).append('"').toString();
        }
    };

    Function<String, String> addStringField = new Function<String, String>() {
        @Override
        public String apply(String s) {

            // todo - need to support shortform_s for time being while https://helpdesk.ebi.ac.uk/Ticket/Display.html?id=75961 is updated
            if (s.equals("short_form")) {
                s = "shortform";
            }

            return new StringBuilder(s.length() + 2).append(s).append("_").append('s').toString();
        }
    };

    private String createUnionQuery(String query, String[] fields, boolean exact) {
        StringBuilder builder = new StringBuilder();
        for (int x = 0; x < fields.length; x++) {
            builder.append(fields[x]);
            builder.append(COLON);
            builder.append(QUOTUE);

            if(!exact)
                builder.append("*");

            builder.append(query);

            if(!exact)
                builder.append("*");

            builder.append(QUOTUE);
            builder.append(SPACE);

            if (x + 1 < fields.length) {
                builder.append(OR);
                builder.append(SPACE);

            }
        }
        return builder.toString();
    }

    private String createIntersectionString(String query) {
        StringBuilder builder = new StringBuilder();
        String[] tokens = query.split(" ");
        for (int x = 0; x < tokens.length; x++) {
            builder.append(tokens[x]);
            if (x + 1 < tokens.length) {
                builder.append(SPACE);
                builder.append(AND);
                builder.append(SPACE);
            }
        }
        return builder.toString();
    }

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
        solrQuery.set("qf", "label synonym label_autosuggest_e label_autosuggest synonym_autosuggest_e synonym_autosuggest shortform_autosuggest iri");
        solrQuery.set("bq", "type:ontology^10.0 is_defining_ontology:true^100.0 label_s:\"" + queryLc + "\"^1000  label_autosuggest_e:\"" + queryLc + "\"^500 synonym_s:\"" + queryLc + "\" synonym_autosuggest_e:\"" + queryLc + "\"^100");
        solrQuery.set("wt", "json");

        if (fieldList == null) {
            fieldList = new HashSet<>();
        }

        if (fieldList.isEmpty()) {
            fieldList.add("label");
            fieldList.add("iri");
            fieldList.add("id");
            fieldList.add("type");
            fieldList.add("short_form");
            fieldList.add("obo_id");
            fieldList.add("ontology_name");
            fieldList.add("ontology_prefix");
        }
        solrQuery.setFields(fieldList.toArray(new String[fieldList.size()]));

        if (ontologies != null && !ontologies.isEmpty()) {
            solrQuery.addFilterQuery("ontology_name: (" + String.join(" OR ", ontologies) + ")");
        }

        if (types != null) {
            solrQuery.addFilterQuery("type: (" + String.join(" OR ", types) + ")");
        }

        if (slims != null) {
            solrQuery.addFilterQuery("subset: (" + String.join(" OR ", slims) + ")");
        }

        if (isLocal) {
            solrQuery.addFilterQuery("is_defining_ontology:true");
        }

        if (childrenOf != null) {
            String result = childrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));
            solrQuery.addFilterQuery("ancestor_iri: (" + result + ")");
        }

        if (allChildrenOf != null) {
            String result = allChildrenOf.stream()
                    .map(addQuotes)
                    .collect(Collectors.joining(" OR "));
            solrQuery.addFilterQuery("hierarchical_ancestor_iri: (" + result + ")");
        }

        solrQuery.addFilterQuery("is_obsolete:" + queryObsoletes);
        solrQuery.setStart(start);
        solrQuery.setRows(rows);
        solrQuery.setHighlight(true);
        solrQuery.add("hl.simple.pre", "<b>");
        solrQuery.add("hl.simple.post", "</b>");
        solrQuery.addHighlightField("label_autosuggest");
        solrQuery.addHighlightField("label");
        solrQuery.addHighlightField("synonym_autosuggest");
        solrQuery.addHighlightField("synonym");

        //dispatchSearch(solrSearchBuilder.toString(), response.getOutputStream());
        dispatchSearch(solrQuery, response);

    }

    @RequestMapping(path = "/api/suggest", produces = {APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void suggest(
            @RequestParam("q") String query,
            @RequestParam(value = "ontology", required = false) Collection<String> ontologies,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            HttpServletResponse response
    ) throws IOException, SolrServerException {


        final SolrQuery solrQuery = new SolrQuery(); // 1

        String queryLc = query.toLowerCase();
        query = new StringBuilder(queryLc.length() + 2).append('"').append(queryLc).append('"').toString();

        String q = "http\\://www.w3.org/2000/01/rdf-schema#label:" + query + "^3 OR " +
                "edge_http\\://www.w3.org/2000/01/rdf-schema#label:" + query + "^2 OR " +
                "whitespace_edge_http\\://www.w3.org/2000/01/rdf-schema#label:" + query + "^1 OR " +
                "synonym:" + query + "^3 OR " +
                "edge_synonym:" + query + "^2 OR " +
                "whitespace_edge_synonym:" + query + "^1";
        solrQuery.setQuery(q);

//        solrQuery.set("qf", "autosuggest^3 autosuggest_e^2 autosuggest_wse^1");
//        solrQuery.set("qf", "http://www.w3.org/2000/01/rdf-schema#label^3 edge_http://www.w3.org/2000/01/rdf-schema#label^2 whitespace_edge_http://www.w3.org/2000/01/rdf-schema#label^1");
        solrQuery.set("wt", "json");
//        solrQuery.setFields("autosuggest");

        if (ontologies != null && !ontologies.isEmpty()) {
            solrQuery.addFilterQuery("ontology_id: (" + String.join(" OR ", ontologies) + ")");
        }

        solrQuery.setStart(start);
        solrQuery.setRows(rows);

        // highlighting seems broken for this endpoint in OLS3, so we won't even try
//        solrQuery.setHighlight(true);
//        solrQuery.add("hl.simple.pre", "<b>");
//        solrQuery.add("hl.simple.post", "</b>");
//        solrQuery.addHighlightField("http://www.w3.org/2000/01/rdf-schema#label");

        // we can't group because all fields in OLS4 have multiple values, and solr can't use this for grouping
        // not sure it actually matters for this endpoint though

//        solrQuery.add("group", "true");
//        solrQuery.add("group.field", "http\\://www.w3.org/2000/01/rdf-schema#label");
//        solrQuery.add("group.main", "true");

        //dispatchSearch(solrSearchBuilder.toString(), response.getOutputStream());

        QueryResponse qr = dispatchSearch(solrQuery, response);


        List<Object> docs = new ArrayList<>();
        for(SolrDocument res : qr.getResults()) {
            Map<String,Object> outDoc = new HashMap<>();

            Collection<String> labels = (Collection<String>) res.get("http__//www.w3.org/2000/01/rdf-schema#label");

            if(labels == null || labels.size() == 0) {
                labels = (Collection<String>) res.get("synonym");
            }

            outDoc.put("autosuggest", labels.toArray()[0]);

            docs.add(outDoc);
        }


        Map<String, Object> responseHeader = new HashMap<>();
        responseHeader.put("status", 0);
        responseHeader.put("QTime", qr.getQTime());

//        Map<String, Object> responseHeaderParams = new HashMap<>();
//        responseHeaderParams.put("hl", "true");
//        responseHeaderParams.put("fl", "autosuggest");
//        responseHeaderParams.put("start", "0");
//        responseHeaderParams.put("rows", "10");
//        responseHeaderParams.put("hl.simple.pre", "<b>");
//        responseHeaderParams.put("q", query);
//        responseHeaderParams.put("group.main", "true");
//        responseHeaderParams.put("hl.simple.post", "</b>");
//        responseHeaderParams.put("qf", "autosuggest^3 autosuggest_e^2 autosuggest_wse^1");
//        responseHeaderParams.put("hl.fl", "autosuggest");
//        responseHeaderParams.put("wt", "json");
//        responseHeaderParams.put("group.field", "autosuggest");
//        responseHeaderParams.put("group", "true");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("numFound", qr.getResults().getNumFound());
        responseBody.put("start", 0);
        responseBody.put("docs", docs);

        Map<String, Object> responseObj = new HashMap<>();
        responseObj.put("responseHeader", responseHeader);
        responseObj.put("docs", docs);

        response.getOutputStream().write(gson.toJson(responseObj).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private QueryResponse dispatchSearch(SolrQuery query, HttpServletResponse httpresponse) throws IOException, SolrServerException {

//        NoOpResponseParser rawJsonResponseParser = new NoOpResponseParser();
//        rawJsonResponseParser.setWriterType("json");
//        req.setResponseParser(rawJsonResponseParser);

        org.apache.solr.client.solrj.SolrClient mySolrClient = new HttpSolrClient.Builder(solrClient.host + "/solr/ols4").build();

        QueryResponse qr = mySolrClient.query(query);

        return qr;
    }



}
