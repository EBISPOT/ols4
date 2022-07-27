package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Term;

import java.io.UnsupportedEncodingException;

/**
 * @author Simon Jupp
 * @date 02/07/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1SearchResultAssembler implements ResourceAssembler<V1Term, Resource<V1Term>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V1Term> toResource(V1Term term) {
        Resource<V1Term> resource = new Resource<V1Term>(term);
        try {
            String id = UriUtils.encode(term.iri, "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V1OntologyTermController.class).getTerm(term.ontologyName, id, term.lang));

            resource.add(lb.withSelfRel());

//            if (!term.isRoot()) {
//                resource.add(lb.slash("parents").withRel("parents"));
//                resource.add(lb.slash("ancestors").withRel("ancestors"));
//                resource.add(lb.slash("jstree").withRel("jstree"));
//            }
//
//            if (!term.hasChildren()) {
//                resource.add(lb.slash("children").withRel("children"));
//                resource.add(lb.slash("descendants").withRel("descendants"));
//            }


//        resource.add(lb.slash("related").withRel("related"));
            // other links
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}