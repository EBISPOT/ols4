package uk.ac.ebi.rdf2json.annotators;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.*;

import static uk.ac.ebi.ols.shared.DefinedFields.*;

public class PreferredRootsAnnotator {
    
    public static Set<String> getPreferredRoots(OntologyGraph graph) {

        Set<String> preferredRoots = new LinkedHashSet<>();

        Object configPreferredRoots = graph.config.get("preferred_root_term");

        if(configPreferredRoots instanceof Collection<?>) {
            preferredRoots.addAll((Collection<String>) configPreferredRoots);
        }

        
        for(String predicate : List.of("http://purl.obolibrary.org/obo/IAO_0000700", "http://www.ebi.ac.uk/ols/vocabulary/hasPreferredRootTerm")) {
            List<PropertyValue> values = graph.ontologyNode.properties.getPropertyValues(predicate);
            if(values != null) {
                preferredRoots.addAll(values.stream()
                            .filter(prop -> prop.getType() == PropertyValue.Type.URI)
                            .map(prop -> { return ((PropertyValueURI) prop).getUri(); })
                            .collect(Collectors.toList())
                );
            }
        }


        return preferredRoots;
    }

    public static void annotatePreferredRoots(OntologyGraph graph) {

        long startTime3 = System.nanoTime();

        Set<String> preferredRoots = getPreferredRoots(graph);

        for(String root : preferredRoots)
            graph.ontologyNode.properties.addProperty("hasPreferredRoot", PropertyValueURI.fromUri(root));

        for(String id : graph.nodes.keySet()) {
            OntologyNode c = graph.nodes.get(id);

            if (c.types.contains(OntologyNode.NodeType.CLASS) ||
                    c.types.contains(OntologyNode.NodeType.PROPERTY)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

                if(preferredRoots.contains(c.uri)) {
                    c.properties.addProperty(IS_PREFERRED_ROOT.getText(), PropertyValueLiteral.fromBoolean("true"));
                } else {
                    c.properties.addProperty(IS_PREFERRED_ROOT.getText(), PropertyValueLiteral.fromBoolean("false"));
                }
            }
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate preferred roots: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));
    }
}
