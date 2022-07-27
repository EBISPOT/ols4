package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;

import java.io.UnsupportedEncodingException;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1IndividualAssembler implements ResourceAssembler<V1Individual, Resource<V1Individual>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V1Individual> toResource(V1Individual term) {
        Resource<V1Individual> resource = new Resource<V1Individual>(term);
        try {
            String id = UriUtils.encode(term.iri, "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V1OntologyIndividualController.class).getIndividual(term.ontologyName, id, term.lang));

            resource.add(lb.withSelfRel());

            resource.add(lb.slash("types").withRel("types"));
            resource.add(lb.slash("alltypes").withRel("alltypes"));
            resource.add(lb.slash("jstree").withRel("jstree"));


            // other links
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}