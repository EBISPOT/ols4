package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v2.V2Property;

import java.io.UnsupportedEncodingException;

@Component
public class V2PropertyAssembler implements RepresentationModelAssembler<V2Property, EntityModel<V2Property>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V2Property> toModel(V2Property _property) {
        EntityModel<V2Property> resource = EntityModel.of(_property);
        String id = UriUtils.encode(_property.get("iri"), "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V2PropertyController.class).getProperty(_property.get("ontologyId"), id, _property.get("lang")));

        resource.add(lb.withSelfRel());

        String isRoot = _property.get("isRoot");
        String hasChildren = _property.get("hasChildren");

        if (isRoot != null && !isRoot.equals("true")) {
            resource.add(lb.slash("parents").withRel("parents"));
            resource.add(lb.slash("ancestors").withRel("ancestors"));
        }

        if (hasChildren != null && hasChildren.equals("true")) {
            resource.add(lb.slash("children").withRel("children"));
            resource.add(lb.slash("descendants").withRel("descendants"));
        }

        return resource;
    }
}



