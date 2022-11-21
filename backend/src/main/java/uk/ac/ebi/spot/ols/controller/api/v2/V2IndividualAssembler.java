package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.V2IndividualController;
import uk.ac.ebi.spot.ols.model.v2.V2Individual;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;

@Component
public class V2IndividualAssembler implements RepresentationModelAssembler<V2Individual, EntityModel<V2Individual>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V2Individual> toModel(V2Individual _individual) {
        EntityModel<V2Individual> resource = EntityModel.of(_individual);
        String id = UriUtils.encode(_individual.get("iri"), "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V2IndividualController.class).getIndividual(_individual.get("ontologyId"), id, _individual.get("lang")));

        String isRoot = _individual.get("isRoot");

        if (isRoot != null && !isRoot.equals("true")) {
            resource.add(lb.slash("ancestors").withRel("ancestors"));
        }

        resource.add(lb.withSelfRel());

        return resource;
    }
}


