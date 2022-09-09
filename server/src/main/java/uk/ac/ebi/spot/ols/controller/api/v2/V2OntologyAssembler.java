package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.controller.api.v2.V2OntologyController;
import uk.ac.ebi.spot.ols.model.v1.V1Ontology;
import uk.ac.ebi.spot.ols.model.v2.V2Ontology;

@Component
public class V2OntologyAssembler implements ResourceAssembler<V2Ontology, Resource<V2Ontology>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V2Ontology> toResource(V2Ontology document) {

        Resource<V2Ontology> resource = new Resource<V2Ontology>(document);
        final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                ControllerLinkBuilder.methodOn(V2OntologyController.class).getOntology(document.get("ontologyId"), document.get("lang")));

        resource.add(lb.withSelfRel());

        resource.add(lb.slash("entities").withRel("entities"));
        resource.add(lb.slash("classes").withRel("classes"));
        resource.add(lb.slash("properties").withRel("properties"));
        resource.add(lb.slash("individuals").withRel("individuals"));
        return resource;
    }
}