package uk.ac.ebi.spot.ols.controller.api.v2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v2.V2Class;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V2ClassAssembler implements RepresentationModelAssembler<V2Class, EntityModel<V2Class>> {

    @Autowired
    EntityLinks entityLinks;

    @Override
    public EntityModel<V2Class> toModel(V2Class _class) {
        EntityModel<V2Class> resource = EntityModel.of(_class);
        String id = UriUtils.encode(_class.get("iri"), "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V2ClassController.class).getClass(_class.get("ontologyId"), id, _class.get("lang")));

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

        return resource;
    }
}

