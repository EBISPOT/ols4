package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceAssembler;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.controller.api.v2.V2ClassController;
import uk.ac.ebi.spot.ols.model.v2.V2Class;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V2ClassAssembler implements ResourceAssembler<V2Class, Resource<V2Class>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public Resource<V2Class> toResource(V2Class _class) {
        Resource<V2Class> resource = new Resource<V2Class>(_class);
        try {
            String id = UriUtils.encode(_class.get("iri"), "UTF-8");
            final ControllerLinkBuilder lb = ControllerLinkBuilder.linkTo(
                    ControllerLinkBuilder.methodOn(V2ClassController.class).getClass(_class.get("ontologyId"), id, _class.get("lang")));

            resource.add(lb.withSelfRel());

            String isRoot = _class.get("isRoot");
            String hasChildren = _class.get("hasChildren");

            if (isRoot != null && !isRoot.equals("true")) {
                resource.add(lb.slash("parents").withRel("parents"));
                resource.add(lb.slash("ancestors").withRel("ancestors"));
                resource.add(lb.slash("hierarchicalParents").withRel("hierarchicalParents"));
                resource.add(lb.slash("hierarchicalAncestors").withRel("hierarchicalAncestors"));
                resource.add(lb.slash("jstree").withRel("jstree"));
            }

            if (hasChildren != null && hasChildren.equals("true")) {
                resource.add(lb.slash("children").withRel("children"));
                resource.add(lb.slash("descendants").withRel("descendants"));
                resource.add(lb.slash("hierarchicalChildren").withRel("hierarchicalChildren"));
                resource.add(lb.slash("hierarchicalDescendants").withRel("hierarchicalDescendants"));
            }

            resource.add(lb.slash("graph").withRel("graph"));

//            Collection<String> relation = new HashSet<>();
//            for (V2Related related : class.related) {
//                if (!relation.contains(related.getLabel())) {
//                    String relationId = UriUtils.encode(related.getIri(), "UTF-8");
//
//                    resource.add(lb.slash(relationId).withRel(related.getLabel().replaceAll(" ", "_")));
//                }
//                relation.add(related.getLabel());
//            }

//        resource.add(lb.slash("related").withRel("related"));
            // other links
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return resource;
    }
}

