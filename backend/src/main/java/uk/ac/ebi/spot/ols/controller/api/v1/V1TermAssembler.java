package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import uk.ac.ebi.spot.ols.model.v1.V1Related;
import uk.ac.ebi.spot.ols.model.v1.V1Term;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Simon Jupp
 * @date 23/06/2015
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 */
@Component
public class V1TermAssembler implements RepresentationModelAssembler<V1Term, EntityModel<V1Term>> {

//    @Autowired
//    EntityLinks entityLinks;

    @Override
    public EntityModel<V1Term> toModel(V1Term term) {
        EntityModel<V1Term> resource = EntityModel.of(term);
        String id = UriUtils.encode(term.iri, "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V1OntologyTermController.class).getTerm(term.ontologyName, id, term.lang));

        resource.add(lb.withSelfRel());

        if (!term.isRoot) {
            resource.add(lb.slash("parents").withRel("parents"));
            resource.add(lb.slash("ancestors").withRel("ancestors"));
            resource.add(lb.slash("hierarchicalParents").withRel("hierarchicalParents"));
            resource.add(lb.slash("hierarchicalAncestors").withRel("hierarchicalAncestors"));
            resource.add(lb.slash("jstree").withRel("jstree"));
        }

        if (term.hasChildren) {
            resource.add(lb.slash("children").withRel("children"));
            resource.add(lb.slash("descendants").withRel("descendants"));
            resource.add(lb.slash("hierarchicalChildren").withRel("hierarchicalChildren"));
            resource.add(lb.slash("hierarchicalDescendants").withRel("hierarchicalDescendants"));
        }

        resource.add(lb.slash("graph").withRel("graph"));

        Collection<String> relation = new HashSet<>();
        for (V1Related related : term.related) {
            if(!related.iri.equals("http://www.w3.org/2000/01/rdf-schema#subClassOf")
                    && related.label != null) {
                if (!relation.contains(related.label)) {
                    String relationId = UriUtils.encode(related.iri, "UTF-8");

                    resource.add(lb.slash(relationId).withRel(related.label.replaceAll(" ", "_")));
                }
                relation.add(related.label);
            }
        }

//        resource.add(lb.slash("related").withRel("related"));
        // other links

        return resource;
    }
}