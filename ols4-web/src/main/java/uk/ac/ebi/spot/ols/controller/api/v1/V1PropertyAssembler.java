package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Property;

import java.io.UnsupportedEncodingException;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1PropertyAssembler implements ResourceAssembler<V1Property, Resource<V1Property>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V1Property> toResource(V1Property term) {
        Resource<V1Property> resource = new Resource<V1Property>(term);
        try {
            String id = UriUtils.encode(term.iri, "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V1OntologyPropertyController.class).getProperty(term.ontologyName, id, term.lang));

            resource.add(lb.withSelfRel());

            if (!term.isRoot) {
                resource.add(lb.slash("parents").withRel("parents"));
                resource.add(lb.slash("ancestors").withRel("ancestors"));
                resource.add(lb.slash("jstree").withRel("jstree"));
            }

            if (term.hasChildren) {
                resource.add(lb.slash("children").withRel("children"));
                resource.add(lb.slash("descendants").withRel("descendants"));
            }

            // other links
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}