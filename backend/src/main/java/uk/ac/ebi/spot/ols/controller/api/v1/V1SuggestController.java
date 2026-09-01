package uk.ac.ebi.spot.ols.controller.api.v1;

import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.ols.repository.Validation;
import uk.ac.ebi.spot.ols.repository.search.OlsSearchClient;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

@Tag(name = "Suggest Controller")
@RestController
public class V1SuggestController {

    Gson gson = new Gson();

    @Autowired
    private OlsSearchClient searchClient;

    @RequestMapping(path = "/api/suggest", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    public void suggest(
            @RequestParam("q") String query,
            @RequestParam(value = "ontology", required = false) Collection<String> ontologies,
            @RequestParam(value = "rows", defaultValue = "10") Integer rows,
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            HttpServletResponse response
    ) throws IOException {

        if (ontologies != null) {
            for (String ontologyId : ontologies)
                Validation.validateOntologyId(ontologyId);
        }

        List<String> ontologyIds = ontologies != null ? new ArrayList<>(ontologies) : null;
        List<String> labels = searchClient.suggestLabels(query.toLowerCase(), ontologyIds, start, rows);

        List<Object> docs = new ArrayList<>();
        for (String label : labels) {
            Map<String,Object> outDoc = new HashMap<>();
            outDoc.put("autosuggest", label);
            docs.add(outDoc);
        }

        Map<String, Object> responseHeader = new HashMap<>();
        responseHeader.put("status", 0);
        responseHeader.put("QTime", 0);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("numFound", labels.size());
        responseBody.put("start", start);
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
