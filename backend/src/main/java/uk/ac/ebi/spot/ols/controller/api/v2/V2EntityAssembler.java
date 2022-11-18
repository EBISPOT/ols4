package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;

@Component
public class V2EntityAssembler implements RepresentationModelAssembler<V2Entity, EntityModel<V2Entity>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V2Entity> toModel(V2Entity entity) {
        EntityModel<V2Entity> resource = EntityModel.of(entity);
        String id = UriUtils.encode(entity.get("iri"), "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V2EntityController.class).getEntity(entity.get("ontologyId"), id, entity.get("lang")));

        resource.add(lb.withSelfRel());

        return resource;
    }
}


