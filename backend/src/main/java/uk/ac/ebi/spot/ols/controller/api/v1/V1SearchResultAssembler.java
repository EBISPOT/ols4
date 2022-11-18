package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Term;

/**
 * @author Simon Jupp
 * @date 02/07/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1SearchResultAssembler implements RepresentationModelAssembler<V1Term, EntityModel<V1Term>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V1Term> toModel(V1Term term) {
        EntityModel<V1Term> resource = EntityModel.of(term);
        String id = UriUtils.encode(term.iri, "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V1OntologyTermController.class).getTerm(term.ontologyName, id, term.lang));

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

        return resource;
    }
}