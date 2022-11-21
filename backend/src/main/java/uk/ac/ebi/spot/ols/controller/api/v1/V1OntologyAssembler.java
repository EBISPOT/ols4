package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1OntologyAssembler implements RepresentationModelAssembler<V1Ontology, EntityModel<V1Ontology>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V1Ontology> toModel(V1Ontology document) {
        EntityModel<V1Ontology> resource = EntityModel.of(document);
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V1OntologyController.class).getOntology(document.lang, document.ontologyId));

        resource.add(lb.withSelfRel());

        resource.add(lb.slash("terms").withRel("terms"));
        resource.add(lb.slash("properties").withRel("properties"));
        resource.add(lb.slash("individuals").withRel("individuals"));
        return resource;
    }
}