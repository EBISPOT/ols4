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

		return getJSFullTreeForClass(iri, "class", "OntologyClass", ontologyId, lang, viewMode, sibling);
	}

	private List<Map<String, Object>> getJSFullTreeForClass(String iri, String type, String neo4jType,
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
					List<JsonElement> roots = getRoots(ontologyId, false, lang, PageRequest.ofSize(100));

					// 3. Add only unique elements from roots to ancestors based on "iri"
					ancestorsWithSiblings.addAll(roots.stream().filter(root -> {
																	String rootIri = root.getAsJsonObject().getAsJsonPrimitive("iri").getAsString();
																	return !ancestorIris.contains(rootIri);
																	})
														   	   .collect(Collectors.toList()));

					return (new V1FullJsTreeBuilder(thisEntity, ancestorsWithSiblings, parentRelationIRIs)).buildJsTree();
				} else {
					return v1JsTreeRepository.getJsTreeForClass(iri, ontologyId, lang);
				}

			default:
				return v1JsTreeRepository.getJsTreeForClass(iri, ontologyId, lang);
		}
	}

	private List<JsonElement> getRoots(String ontologyId, boolean obsolete, String lang, Pageable pageable) {

		OlsSolrQuery query = new OlsSolrQuery();
		query.addFilter("type", List.of("class"), SearchType.WHOLE_FIELD);
		query.addFilter("ontologyId", List.of(ontologyId), SearchType.WHOLE_FIELD);
		query.addFilter(HAS_DIRECT_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);
		query.addFilter(HAS_HIERARCHICAL_PARENTS.getText(), List.of("false"), SearchType.WHOLE_FIELD);

		if (!obsolete)
			query.addFilter(IS_OBSOLETE.getText(), List.of("false"), SearchType.WHOLE_FIELD);

		return solrClient.searchSolrPaginated(query, pageable).stream().collect(Collectors.toList());
	}

	/*
	 * public Object getJsTreeForClassByViewMode(String iri, String ontologyId,
	 * String lang, String viewMode, boolean sibling) {
	 * 
	 * Object res = (sibling) ? getJsTreeParentSiblingQuery(iri, ontologyId, lang,
	 * viewMode) : getJsTreeParentQuery(iri, ontologyId, lang, viewMode);
	 * 
	 * return res;
	 * 
	 * }
	 * 
	 * private Object getJsTreeParentQuery(String iri, String ontologyId, String
	 * lang, String viewMode) { return null; }
	 * 
	 * private Object getJsTreeParentSiblingQuery(String iri, String ontologyId,
	 * String lang, String viewMode) { List<String> parentRelationIRIs =
	 * List.of("directParent"); String thisEntityId = ontologyId + "+class" + iri;
	 * 
	 * JsonElement thisEntity = olsNeo4jClient.getOne("OntologyClass", Map.of("id",
	 * thisEntityId)); thisEntity = LocalizationTransform.transform(thisEntity,
	 * lang); switch(viewMode) { case "all": String query = """ MATCH path =
	 * (n:OntologyClass)-[r:directParent|hierarchicalParent*]
	 * ->(parent)<-[r2:directParent|hierarchicalParent]-(n1:OntologyClass) WHERE
	 * any(ontologyId in n.ontologyId where ontologyId=%s) and n.iri=%s UNWIND
	 * relationships(path) as r1 WITH r1 WHERE any(isObsolete in
	 * startNode(r1).isObsolete where isObsolete="false") RETURN distinct
	 * startNode(r1) as parents """ .formatted(ontologyId, iri); List<JsonElement>
	 * res = neo4jClient.query(query, "parents"); res = res.stream().map(ancestor ->
	 * LocalizationTransform.transform(ancestor,
	 * lang)).collect(Collectors.toList());
	 * 
	 * return (new V1AncestorsJsTreeBuilder(thisEntity, res,
	 * parentRelationIRIs)).buildJsTree();
	 * 
	 * default: return getJsTreeForClass(iri, ontologyId, lang); } }
	 */
}
