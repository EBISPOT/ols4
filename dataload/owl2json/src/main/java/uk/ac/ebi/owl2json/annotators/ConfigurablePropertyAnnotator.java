
package uk.ac.ebi.owl2json.annotators;

import uk.ac.ebi.owl2json.OwlGraph;
import uk.ac.ebi.owl2json.OwlNode;
import uk.ac.ebi.owl2json.properties.PropertyValueLiteral;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ConfigurablePropertyAnnotator {

    // The OLS3 API doesn't return properties as "annotations" if they have already been parsed as
    // the definition, synonym, hierarchy properties.
    //
    // It would be impossible to work this out in the API server without having access to the ontology
    // config, and in the case of a search that returns terms across multiple ontologies it would be
    // annoying/slow to have to retrieve all the different ontologies. So as a workaround, we store the
    // list of definition/synonym/hierarchy properties on each entity.
    //
    // The API server can then use this to build the list of annotations for the OLS3 backwards compatible API.
    //
    public static void annotateConfigurableProperties(OwlGraph graph) {

	Set<String> hierarchicalProperties = HierarchicalParentsAnnotator.getHierarchicalProperties(graph);
	Set<String> definitionProperties = DefinitionAnnotator.getDefinitionProperties(graph);
	Set<String> synonymProperties = SynonymAnnotator.getSynonymProperties(graph);
	

        long startTime3 = System.nanoTime();

        for(String id : graph.nodes.keySet()) {
            OwlNode c = graph.nodes.get(id);
            if (c.types.contains(OwlNode.NodeType.CLASS) ||
                    c.types.contains(OwlNode.NodeType.PROPERTY) ||
                    c.types.contains(OwlNode.NodeType.NAMED_INDIVIDUAL)) {

                // skip bnodes
                if(c.uri == null)
                    continue;

		for(String p : hierarchicalProperties) {
			c.properties.addProperty("hierarchicalProperty", PropertyValueLiteral.fromString(p));
		}

		for(String p : definitionProperties) {
			c.properties.addProperty("definitionProperty", PropertyValueLiteral.fromString(p));
		}

		for(String p : synonymProperties) {
			c.properties.addProperty("synonymProperty", PropertyValueLiteral.fromString(p));
		}
            }
        }

        long endTime3 = System.nanoTime();
        System.out.println("annotate configurable properties: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


    }
}
