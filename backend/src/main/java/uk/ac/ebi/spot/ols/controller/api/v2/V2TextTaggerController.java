package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.service.TextTaggerService;
import uk.ac.ebi.spot.ols.service.TextTaggerService.TaggedEntity;

import java.util.*;

@Tag(
        name = "V2 Text Tagger Controller",
        description = "Annotate free text with ontology terms using dictionary based matching over labels, synonyms, and previous curations"
)
@RestController
@RequestMapping("/api/v2")
public class V2TextTaggerController {

    @Autowired
    TextTaggerService textTaggerService;

    @Autowired
    OlsSolrClient solrClient;

    @RequestMapping(path = "/tag_text", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.POST)
    @Parameter(name = "tag_text", description = "Annotate free text with matching ontology terms")
    public HttpEntity<Map<String, Object>> tagText(
            @RequestBody Map<String, Object> requestBody,
            @RequestParam(value = "ontologyId", required = false) List<String> ontologyIds,
            @RequestParam(value = "source", required = false) List<String> sources,
            @RequestParam(value = "delimiters", required = false) String delimiters,
            @RequestParam(value = "minLength", required = false, defaultValue = "3") int minLength,
            @RequestParam(value = "includeSubstrings", required = false, defaultValue = "true") boolean includeSubstrings
    ) {

        if (!textTaggerService.isAvailable()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Text tagger service is not available");
            error.put("message", "The text tagger database has not been configured or the binary is not on the PATH");
            return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
        }

        Object textObj = requestBody.get("text");
        if (textObj == null || textObj.toString().isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Missing required field: text");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        String text = textObj.toString();

        List<TaggedEntity> entities = textTaggerService.tagText(text, ontologyIds, sources, delimiters, minLength, includeSubstrings);

        List<Map<String, Object>> entityMaps = new ArrayList<>(entities.size());
        for (TaggedEntity e : entities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start", e.start);
            m.put("end", e.end);
            m.put("term_label", e.termLabel);
            m.put("term_iri", e.termIri);
            m.put("ontology_id", e.ontologyId);
            if (e.stringType != null) m.put("string_type", e.stringType);
            if (e.source != null) m.put("source", e.source);
            if (e.subjectCategories != null) m.put("subject_categories", e.subjectCategories);
            entityMaps.add(m);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("text", text);
        response.put("entities", entityMaps);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(path = "/tag_text", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @Parameter(name = "tag_text_status", description = "Check whether the text tagger service is available")
    public HttpEntity<Map<String, Object>> tagTextStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", textTaggerService.isAvailable());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(path = "/curation_sources", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @Parameter(name = "curation_sources", description = "List available curation source names (from SSSOM curated mappings)")
    public HttpEntity<List<String>> getCurationSources() {
        SolrQuery query = new SolrQuery();
        query.setQuery("curatedFromSources:[* TO *]");
        query.setFacet(true);
        query.addFacetField("curatedFromSources");
        query.setFacetMinCount(1);
        query.setFacetLimit(-1);
        query.setRows(0);

        QueryResponse qr = solrClient.runSolrQuery(query, null);

        List<String> sources = new ArrayList<>();
        FacetField facet = qr.getFacetField("curatedFromSources");
        if (facet != null) {
            for (FacetField.Count count : facet.getValues()) {
                sources.add(count.getName());
            }
        }
        Collections.sort(sources);
        return new ResponseEntity<>(sources, HttpStatus.OK);
    }
}
