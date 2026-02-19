package uk.ac.ebi.spot.ols.controller.api.v2;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import uk.ac.ebi.spot.ols.service.TextTaggerService;
import uk.ac.ebi.spot.ols.service.TextTaggerService.TaggedEntity;

import java.util.*;

@Tag(
        name = "V2 Text Tagger Controller",
        description = "Annotate free text with ontology terms using Aho-Corasick matching"
)
@RestController
@RequestMapping("/api/v2")
public class V2TextTaggerController {

    @Autowired
    TextTaggerService textTaggerService;

    @RequestMapping(path = "/tag_text", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.POST)
    @Parameter(name = "tag_text", description = "Annotate free text with matching ontology terms")
    public HttpEntity<Map<String, Object>> tagText(
            @RequestBody Map<String, Object> requestBody,
            @RequestParam(value = "ontologyId", required = false) List<String> ontologyIds,
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

        List<TaggedEntity> entities = textTaggerService.tagText(text, ontologyIds, delimiters, minLength, includeSubstrings);

        List<Map<String, Object>> entityMaps = new ArrayList<>(entities.size());
        for (TaggedEntity e : entities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start", e.start);
            m.put("end", e.end);
            m.put("term_label", e.termLabel);
            m.put("term_iri", e.termIri);
            m.put("ontology_id", e.ontologyId);
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
}
