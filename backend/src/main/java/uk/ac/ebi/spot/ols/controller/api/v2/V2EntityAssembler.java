package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v2.V2Entity;

import java.io.UnsupportedEncodingException;

@Component
public class V2EntityAssembler implements ResourceAssembler<V2Entity, Resource<V2Entity>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V2Entity> toResource(V2Entity entity) {
        Resource<V2Entity> resource = new Resource<V2Entity>(entity);
        try {
            String id = UriUtils.encode(entity.get("uri"), "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V2EntityController.class).getEntity(entity.get("ontologyId"), id, entity.get("lang")));

            resource.add(lb.withSelfRel());

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}


