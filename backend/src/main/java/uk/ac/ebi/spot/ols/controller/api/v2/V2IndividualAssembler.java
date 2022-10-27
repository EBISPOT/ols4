package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.V2IndividualController;
import uk.ac.ebi.spot.ols.model.v2.V2Individual;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;

@Component
public class V2IndividualAssembler implements ResourceAssembler<V2Individual, Resource<V2Individual>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V2Individual> toResource(V2Individual _individual) {
        Resource<V2Individual> resource = new Resource<V2Individual>(_individual);
        try {
            String id = UriUtils.encode(_individual.get("iri"), "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V2IndividualController.class).getIndividual(_individual.get("ontologyId"), id, _individual.get("lang")));

            String isRoot = _individual.get("isRoot");

            if (isRoot != null && !isRoot.equals("true")) {
                resource.add(lb.slash("ancestors").withRel("ancestors"));
            }

            resource.add(lb.withSelfRel());

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}


