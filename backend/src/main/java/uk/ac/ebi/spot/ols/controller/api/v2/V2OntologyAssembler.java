package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.model.v2.V2Ontology;

@Component
public class V2OntologyAssembler implements RepresentationModelAssembler<V2Ontology, EntityModel<V2Ontology>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V2Ontology> toModel(V2Ontology document) {

        EntityModel<V2Ontology> resource = EntityModel.of(document);
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V2OntologyController.class).getOntology(document.get("ontologyId"), document.get("lang")));

        resource.add(lb.withSelfRel());

        resource.add(lb.slash("entities").withRel("entities"));
        resource.add(lb.slash("classes").withRel("classes"));
        resource.add(lb.slash("properties").withRel("properties"));
        resource.add(lb.slash("individuals").withRel("individuals"));
        return resource;
    }
}