package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Property;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1PropertyAssembler implements RepresentationModelAssembler<V1Property, EntityModel<V1Property>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V1Property> toModel(V1Property term) {
        EntityModel<V1Property> resource = EntityModel.of(term);
        String id = UriUtils.encode(term.iri, "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V1OntologyPropertyController.class).getProperty(term.ontologyName, id, term.lang));

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

        return resource;
    }
}