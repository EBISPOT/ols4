package uk.ac.ebi.spot.ols.repository.v1;

import static uk.ac.ebi.ols.shared.DefinedFields.HAS_DIRECT_PARENTS;
import static uk.ac.ebi.ols.shared.DefinedFields.HAS_HIERARCHICAL_PARENTS;
import static uk.ac.ebi.ols.shared.DefinedFields.IS_OBSOLETE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;

import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrQuery;
import uk.ac.ebi.spot.ols.repository.solr.SearchType;
import uk.ac.ebi.spot.ols.repository.transforms.LocalizationTransform;
import uk.ac.ebi.spot.ols.service.ViewMode;

/**
 * @author Deepan Anbalagan
 * @email deepan.anbalagan@tib.eu
 * TIB-Leibniz Information Center for Science and Technology
 */
@Component
public class V1JsTreeRepositoryExtn {

	@Autowired
	OlsNeo4jClient neo4jClient;

	@Autowired
    OlsSolrClient solrClient;
	
	@Autowired
	V1JsTreeRepository v1JsTreeRepository;

	public List<Map<String, Object>> getJsTreeForClassByViewMode(String iri, String ontologyId, String lang, ViewMode viewMode,
			boolean sibling) {

		return getJSFullTree(iri, "class", "OntologyClass", ontologyId, lang, viewMode, sibling);
	}
	
	public List<Map<String, Object>> getJsTreeForPropertyByViewMode(String iri, String ontologyId, String lang, ViewMode viewMode,
			boolean sibling) {

		return getJSFullTree(iri, "property", "OntologyProperty", ontologyId, lang, viewMode, sibling);
	}

	private List<Map<String, Object>> getJSFullTree(String iri, String type, String neo4jType,
			String ontologyId, String lang, ViewMode viewMode, boolean sibling) {

		List<String> parentRelationIRIs = List.of("directParent");

		String thisEntityId = ontologyId + "+" + type + "+" + iri;

		JsonElement thisEntity = neo4jClient.getOne(neo4jType, Map.of("id", thisEntityId));
		thisEntity = LocalizationTransform.transform(thisEntity, lang);

		switch (viewMode) {
			case ALL:
				if (sibling) {
					List<JsonElement> ancestorsWithSiblings = neo4jClient
							.recursivelyTraverseOutgoingEdgesWithSiblings(neo4jType, thisEntityId, ontologyId,
									parentRelationIRIs, Map.of(), PageRequest.ofSize(100))
							.getContent();
					
					ancestorsWithSiblings = ancestorsWithSiblings.stream()
																	.map(ancestor -> LocalizationTransform.transform(ancestor, lang)).collect(Collectors.toList());

					// 1. Collect all "iri" values from ancestorsWithSiblings
					Set<String> ancestorIris = ancestorsWithSiblings.parallelStream()
															.map(ancestor -> ancestor.getAsJsonObject().getAsJsonPrimitive("iri").getAsString())
															.collect(Collectors.toSet());
					// 2. Get Root elements by ontologyId
					List<JsonElement> roots = getRoots(ontologyId, type, false, lang, PageRequest.ofSize(100));

					// 3. Add only unique elements from roots to ancestors based on "iri"
					ancestorsWithSiblings.addAll(roots.stream().filter(root -> {
																	String rootIri = root.getAsJsonObject().getAsJsonPrimitive("iri").getAsString();
																	return !ancestorIris.contains(rootIri);
																	})
														   	   .collect(Collectors.toList()));

					return (new V1FullJsTreeBuilder(thisEntity, ancestorsWithSiblings, parentRelationIRIs)).buildJsTree();
				} else {
					return getDefaultJsTreeByType(iri, ontologyId, lang, type);
				}

			default:
				return getDefaultJsTreeByType(iri, ontologyId, lang, type);
		}
	}

	private List<JsonElement> getRoots(String ontologyId, String type, boolean obsolete, String lang, Pageable pageable) {

		OlsSolrQuery query = new OlsSolrQuery();
		query.addFilter("type", List.of(type), SearchType.WHOLE_FIELD);
		query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
		query.addFilter(HAS_DIRECT_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);
		query.addFilter(HAS_HIERARCHICAL_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);

		if (!obsolete)
			query.addFilter(IS_OBSOLETE.getText(), List.of("false"), SearchType.WHOLE_FIELD);

		return solrClient.searchSolrPaginated(query, pageable).stream().collect(Collectors.toList());
	}

	private List<Map<String, Object>> getDefaultJsTreeByType(String iri, String ontologyId, String lang, String type){
		
		switch (type) {
			case "class":
				return v1JsTreeRepository.getJsTreeForClass(iri, ontologyId, lang);
			case "property":
				return v1JsTreeRepository.getJsTreeForProperty(iri, ontologyId, lang);
			default:
				return null;
		}
	}
}
