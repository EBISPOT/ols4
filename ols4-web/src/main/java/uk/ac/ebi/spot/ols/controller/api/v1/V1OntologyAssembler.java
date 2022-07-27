package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1OntologyAssembler implements ResourceAssembler<V1Ontology, Resource<V1Ontology>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V1Ontology> toResource(V1Ontology document) {
        Resource<V1Ontology> resource = new Resource<V1Ontology>(document);
        final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                ControllerLinkBuilder.methodOn(V1OntologyController.class).getOntology(document.lang, document.ontologyId));

        resource.add(lb.withSelfRel());

        resource.add(lb.slash("terms").withRel("terms"));
        resource.add(lb.slash("properties").withRel("properties"));
        resource.add(lb.slash("individuals").withRel("individuals"));
        return resource;
    }
}