package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Individual;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1IndividualAssembler implements RepresentationModelAssembler<V1Individual, EntityModel<V1Individual>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V1Individual> toModel(V1Individual term) {
        EntityModel<V1Individual> resource = EntityModel.of(term);
        String id = UriUtils.encode(term.iri, "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V1OntologyIndividualController.class).getIndividual(term.ontologyName, id, term.lang));

        resource.add(lb.withSelfRel());

        resource.add(lb.slash("types").withRel("types"));
        resource.add(lb.slash("alltypes").withRel("alltypes"));
        resource.add(lb.slash("jstree").withRel("jstree"));


        return resource;
    }
}