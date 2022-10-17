package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.annotators.helpers.PropertyCollator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class HierarchicalParentsAnnotator {

    public static Set<String> getHierarchicalProperties(OwlGraph graph) {

        Set<String> hierarchicalProperties = new TreeSet<>(
                List.of(
                        "http://www.w3.org/2000/01/rdf-schema#subClassOf"
                )
        );

        Object configHierarchicalProperties = graph.config.get("hierarchical_property");

        if(configHierarchicalProperties instanceof Collection<?>) {
            hierarchicalProperties.addAll((Collection<String>) configHierarchicalProperties);
        }

        return hierarchicalProperties;
    }

    public static void annotateHierarchicalParents(OwlGraph graph) {
        PropertyCollator.collateProperties(graph, "hierarchicalParent", getHierarchicalProperties(graph));
    }


}
