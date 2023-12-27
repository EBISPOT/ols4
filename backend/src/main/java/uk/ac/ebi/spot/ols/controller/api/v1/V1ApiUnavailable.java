package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

/**
 * @author Simon Jupp
 * @date 27/09/2016
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@RestController
public class V1ApiUnavailable {

    @RequestMapping(path = "/api/unavailable", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    HttpEntity<String> getApiReponse(HttpServletResponse response) throws ResourceNotFoundException {

        String body = "OLS service is currently unavailable ";
        return new ResponseEntity<String>( body, HttpStatus.SERVICE_UNAVAILABLE);

    }


}
