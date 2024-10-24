package uk.ac.ebi.rdf2json.annotators;

import uk.ac.ebi.rdf2json.OntologyGraph;
import uk.ac.ebi.rdf2json.OntologyNode;
import uk.ac.ebi.rdf2json.properties.PropertyValue;
import uk.ac.ebi.rdf2json.properties.PropertyValueList;

import java.util.*;

import static uk.ac.ebi.ols.shared.DefinedFields.SYNONYM;

public class SynonymAnnotator {

	public static Set<String> getSynonymProperties(OntologyGraph graph) {

		Set<String> synonymProperties = new TreeSet<>(
				List.of(
						"http://www.geneontology.org/formats/oboInOwl#hasExactSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasRelatedSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym",
						"http://www.geneontology.org/formats/oboInOwl#hasSynonym"
				)
		);

		Object configSynonymProperties = graph.config.get("synonym_property");

		if(configSynonymProperties instanceof Collection<?>) {
			synonymProperties.addAll((Collection<String>) configSynonymProperties);
		}

		return synonymProperties;
	}

	public static void annotateSynonyms(OntologyGraph graph) {
		collateProperties(graph, SYNONYM.getText(), getSynonymProperties(graph));
	}

	private static void collateProperties(OntologyGraph graph, String destProp, Collection<String> sourceProps) {

		for(String id : graph.nodes.keySet()) {
			OntologyNode c = graph.nodes.get(id);

			// skip bnodes
			if(c.uri == null)
				continue;

			List<PropertyValue> synonyms = new ArrayList<>();
			for(String prop : sourceProps) {
				List<PropertyValue> values = c.properties.getPropertyValues(prop);
				if(values != null) {
					for(PropertyValue value : values) {
						synonyms.add(value);
					}
				}
			}

			if (synonyms.size()>0)
				c.properties.addProperty(destProp, new PropertyValueList(synonyms));
		}

	}
}
