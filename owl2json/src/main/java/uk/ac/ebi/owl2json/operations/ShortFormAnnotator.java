package uk.ac.ebi.owl2json.operations;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.util.NodeUtils;
import uk.ac.ebi.owl2json.OwlTranslator;

public class ShortFormAnnotator {

	public static void annotateShortForms(OwlTranslator translator) {

		long startTime3 = System.nanoTime();


		Set<String> ontologyBaseUris = new HashSet<String>();



		Object configBaseUris = translator.config.get("baseUris");

		if(configBaseUris instanceof Collection<?>) {
			ontologyBaseUris.addAll((Collection<String>) configBaseUris);
		}



		String preferredPrefix = (String)translator.config.get("preferredPrefix");

		if(preferredPrefix != null) {
			ontologyBaseUris.add("http://purl.obolibrary.org/obo/" + preferredPrefix + "_");
		}

		for(ResIterator it = translator.model.listSubjects(); it.hasNext();) {

			Resource res = it.next();

			if (!res.isURIResource())
				continue;

			translator.graph.add(Triple.create(
					res.asNode(),
					NodeUtils.asNode("shortForm"),
					NodeFactory.createLiteral(
							getShortForm(translator, ontologyBaseUris, preferredPrefix, res.getURI())
					)
			));
		}
		long endTime3 = System.nanoTime();
		System.out.println("annotate short forms: " + ((endTime3 - startTime3) / 1000 / 1000 / 1000));


	}
	

	private static String getShortForm(OwlTranslator translator, Set<String> ontologyBaseUris, String preferredPrefix, String uri) {

		if(uri.startsWith("urn:")) {
			return uri.substring(4);
		}

		if(uri.startsWith("http://purl.obolibrary.org/obo/")) {
			return uri.substring("http://purl.obolibrary.org/obo/".length());
		}

		for (String baseUri :ontologyBaseUris) {
			if (uri.startsWith(baseUri) && preferredPrefix != null) {
			    return preferredPrefix + "_" + uri.substring(baseUri.length());
			}
		    }
	
		int lastHash = uri.lastIndexOf('#');
		if(lastHash != -1) {
			return uri.substring(lastHash + 1);
		}
	
		int lastSlash = uri.lastIndexOf('/');
		if(lastSlash != -1) {
			return uri.substring(lastSlash + 1);
		}

		return uri;
	}
}
