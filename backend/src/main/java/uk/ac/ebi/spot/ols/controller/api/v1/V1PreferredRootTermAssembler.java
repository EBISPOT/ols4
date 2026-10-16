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
import static uk.ac.ebi.ols.shared.DefinedFields.*;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Henriette Harmse
 * @date 2019/06/03
 * Samples, Phenotypes and Ontologies Team, EMBL-EBI
 * 
 * Henriette To do: 
 * Add parent TermAssembler class using the Template pattern with template methods dealWithParents()
 * and dealWithChildren. Add DefaultTermAssembler and PreferredRootTermAssembler classes that
 * extend the TermAssembler class.
 */
@Component
public class V1PreferredRootTermAssembler implements RepresentationModelAssembler<V1Term, EntityModel<V1Term>> {

//    @Autowired
//    EntityLinks entityLinks;

    @Override
    public EntityModel<V1Term> toModel(V1Term term) {
        EntityModel<V1Term> resource = EntityModel.of(term);
        String id = UriUtils.encode(term.iri, "UTF-8");
        final WebMvcLinkBuilder lb = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(V1OntologyTermController.class).getTerm(term.ontologyName, id, term.lang));

        resource.add(lb.withSelfRel());

        if (!term.isPreferredRoot) {
            resource.add(V1Links.child(lb, "parents").withRel("parents"));
            resource.add(V1Links.child(lb, "ancestors").withRel("ancestors"));
            resource.add(V1Links.child(lb, "hierarchicalParents").withRel("hierarchicalParents"));
            resource.add(V1Links.child(lb, "hierarchicalAncestors").withRel("hierarchicalAncestors"));
            resource.add(V1Links.child(lb, "jstree").withRel("jstree"));
        }

        if (term.hasChildren) {
            resource.add(V1Links.child(lb, "children").withRel("children"));
            resource.add(V1Links.child(lb, "descendants").withRel("descendants"));
            resource.add(V1Links.child(lb, "hierarchicalChildren").withRel("hierarchicalChildren"));
            resource.add(V1Links.child(lb, "hierarchicalDescendants").withRel("hierarchicalDescendants"));
        }

        resource.add(V1Links.child(lb, "graph").withRel("graph"));

        Collection<String> relation = new HashSet<>();
        for (V1Related related : term.related) {
            if (!relation.contains(related.label)) {
                String relationId = UriUtils.encode(related.relatedToIri, "UTF-8");

                resource.add(V1Links.child(lb, relationId).withRel(related.label.replaceAll(" ", "_")));
            }
            relation.add(related.label);
        }

//        resource.add(lb.slash("related").withRel("related"));
        // other links

        return resource;
    }
}