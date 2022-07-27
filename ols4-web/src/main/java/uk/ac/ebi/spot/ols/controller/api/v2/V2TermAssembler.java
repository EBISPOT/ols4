package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.V2TermController;
import uk.ac.ebi.spot.ols.model.v2.V2Term;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;

@Component
public class V2TermAssembler implements ResourceAssembler<V2Term, Resource<V2Term>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V2Term> toResource(V2Term _term) {
        Resource<V2Term> resource = new Resource<V2Term>(_term);
        try {
            String id = UriUtils.encode(_term.get("uri"), "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V2TermController.class).getTerm(_term.get("ontologyId"), id, _term.get("lang")));

            resource.add(lb.withSelfRel());

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}


