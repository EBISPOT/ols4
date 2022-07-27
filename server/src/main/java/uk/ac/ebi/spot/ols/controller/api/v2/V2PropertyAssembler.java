package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.V2PropertyController;
import uk.ac.ebi.spot.ols.model.v2.V2Property;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;

@Component
public class V2PropertyAssembler implements ResourceAssembler<V2Property, Resource<V2Property>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V2Property> toResource(V2Property _property) {
        Resource<V2Property> resource = new Resource<V2Property>(_property);
        try {
            String id = UriUtils.encode(_property.get("uri"), "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V2PropertyController.class).getProperty(_property.get("ontologyId"), id, _property.get("lang")));

            resource.add(lb.withSelfRel());

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}



