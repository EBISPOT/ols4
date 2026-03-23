package uk.ac.ebi.spot.ols.repository.neo4j;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import uk.ac.ebi.spot.ols.controller.api.exception.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.neo4j.cypherdsl.core.Cypher.name;
import static org.neo4j.cypherdsl.core.Cypher.parameter;
import static org.neo4j.driver.Values.parameters;

@Component
public class OlsNeo4jClient {

	@Autowired
	Neo4jClient neo4jClient;

	Gson gson = new Gson();
  
	private static final Logger logger = LoggerFactory.getLogger(OlsNeo4jClient.class);
  
  
	public long getDatabaseNodeCount() {
		return neo4jClient.returnNodeCount();
	}

    public Page<JsonElement> getAll(String type, Map<String,String> properties, Pageable pageable) {

		var node = Cypher.node(type).named("a") ;
		var query = Cypher.match(node);

		if(properties.size() > 0) {
			var conditions = properties.entrySet().stream()
				.map((Map.Entry<String, String> entry) -> node.property(entry.getKey()).isEqualTo(Cypher.literalOf(entry.getValue())))
				.collect(Collectors.toList());

			var condition = conditions.stream().reduce((c1, c2) -> c1.and(c2))
				.orElseThrow(() -> new IllegalArgumentException("No properties provided"));

			var queryWithWhere = query.where(condition);

			var getQuery = queryWithWhere.returning(node).build().getCypher();
			var countQuery = queryWithWhere.returning(Cypher.count(node)).build().getCypher();

			return neo4jClient.queryPaginated(getQuery, "a", countQuery, parameters(), pageable);

		} else {

			var getQuery = query.returning(node).build().getCypher();
			var countQuery = query.returning(Cypher.count(node)).build().getCypher();

			return neo4jClient.queryPaginated(getQuery, "a", countQuery, parameters(), pageable);
		}
	}

	public JsonElement getOne(String type, Map<String,String> properties) {

		Page<JsonElement> results = getAll(type, properties, PageRequest.of(0, 10));

		if(results.getTotalElements() != 1) {
			throw new RuntimeException("expected exactly one result for neo4j getOne, but got " + results.getTotalElements());
		}

		return results.getContent().iterator().next();
	}

    public Page<JsonElement> traverseOutgoingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Map<String,String> targetNodeProps, Pageable pageable) {

		var a = Cypher.node(type).named("a");
		var b = Cypher.anyNode().named("b");
		var edgeRel = a.relationshipTo(b, edgeIRIs.toArray(String[]::new)).named("edge");

		var condition = a.property("id").isEqualTo(Cypher.parameter("id"));
		for (var entry : edgeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(edgeRel.property(entry.getKey())));
		}
		for (var entry : targetNodeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(b.property(entry.getKey())));
		}

		var statement = Cypher.match(edgeRel).where(condition);

		var query = statement.returningDistinct(b).build().getCypher();
		var countQuery = statement.returning(Cypher.countDistinct(b)).build().getCypher();

		System.out.println(query);

		return neo4jClient.queryPaginated(query, "b", countQuery, parameters("id", id), pageable);
    }


	public Page<JsonElement> traverseIncomingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Map<String,String> sourceNodeProps, Pageable pageable, String searchQuery) {
		var a = Cypher.node(type).named("a");
		var b = Cypher.anyNode().named("b");
		var edgeRel = b.relationshipTo(a, edgeIRIs.toArray(String[]::new)).named("edge");

		var condition = a.property("id").isEqualTo(Cypher.parameter("id"));
		for (var entry : edgeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(edgeRel.property(entry.getKey())));
		}
		for (var entry : sourceNodeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(b.property(entry.getKey())));
		}

		if (searchQuery != null && !searchQuery.trim().isEmpty()) {
			var labelItem = name("labelItem");
			var searchPredicate = Cypher.toLower(labelItem).contains(Cypher.toLower(parameter("searchQuery")));
			var anyInListCondition = Cypher.any(labelItem).in(b.property("label")).where(searchPredicate);
			condition = condition.and(anyInListCondition);
		}

		var statement = Cypher.match(edgeRel).where(condition);

		var query = statement.returningDistinct(b).build().getCypher();
		var countQuery = statement.returning(Cypher.countDistinct(b)).build().getCypher();

		System.out.println(query);

		return neo4jClient.queryPaginated(
				query,
				"b",
				countQuery,
				parameters("id", id, "searchQuery", searchQuery),
				pageable);
	}

	// Overloaded method for backward compatibility
	public Page<JsonElement> traverseIncomingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Map<String,String> sourceNodeProps, Pageable pageable) {
		return traverseIncomingEdges(type, id, edgeIRIs, edgeProps, sourceNodeProps, pageable, null);
	}

    public Page<JsonElement> recursivelyTraverseOutgoingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Map<String,String> targetNodeProps, Pageable pageable) {

		var a = Cypher.node(type).named("a");
		var b = Cypher.anyNode().named("b");
		var edgeRel = a.relationshipTo(b, edgeIRIs.toArray(String[]::new)).named("edge").length(1, null);

		var condition = a.property("id").isEqualTo(Cypher.parameter("id"));
		for (var entry : edgeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(edgeRel.property(entry.getKey())));
		}
		for (var entry : targetNodeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(b.property(entry.getKey())));
		}

		var statement = Cypher.match(edgeRel).where(condition);

		var query = statement.returningDistinct(b).build().getCypher();
		var countQuery = statement.returning(Cypher.countDistinct(b)).build().getCypher();

		System.out.println(query);

		return neo4jClient.queryPaginated(query, "b", countQuery, parameters("id", id), pageable);
    }

    public Page<JsonElement> recursivelyTraverseIncomingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Map<String,String> sourceNodeProps, Pageable pageable) {

		var a = Cypher.node(type).named("a");
		var b = Cypher.anyNode().named("b");
		var edgeRel = b.relationshipTo(a, edgeIRIs.toArray(String[]::new)).named("edge").length(1, null);

		var condition = a.property("id").isEqualTo(Cypher.parameter("id"));
		for (var entry : edgeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(edgeRel.property(entry.getKey())));
		}
		for (var entry : sourceNodeProps.entrySet()) {
			condition = condition.and(Cypher.literalOf(entry.getValue()).in(b.property(entry.getKey())));
		}

		var statement = Cypher.match(edgeRel).where(condition);

		var query = statement.returningDistinct(b).build().getCypher();
		var countQuery = statement.returning(Cypher.countDistinct(b)).build().getCypher();

		System.out.println(query);

		return neo4jClient.queryPaginated(query, "b", countQuery, parameters("id", id), pageable);
    }




    public static class SimilarResult {
        public JsonElement entity;
        public double score;
    }

    public Page<JsonElement> getSimilar(String type, String iri, Pageable pageable, String modelName) {

		// Only the defining class has vector embeddings. So instead of searching by
		// ID (where we may get an imported class with no embeddings), search by IRI
		// and isDefiningOntology=true

		// Use single OntologyEntity index, then filter by type
		String index = "ontologyentity_" + modelName.replace("-", "_").replace(".", "_") + "_embeddings";
		// Property name preserves the original model name format
		String embeddingProperty = "embeddings_" + modelName;

		// Over-fetch from vector index since we're filtering by type
		int fetchSize = pageable.getPageSize() * 5;

		String query = "MATCH (c:" + type + " {iri: $iri}) "
		+ "WHERE \"true\" IN c.isDefiningOntology "
		+ "AND c.`" + embeddingProperty + "` IS NOT NULL "
		+ "CALL db.index.vector.queryNodes('" + index + "', $fetchSize, c.`" + embeddingProperty + "`) "
		+ "YIELD node AS similar, score "
		+ "WHERE similar:" + type + " "
		+ "RETURN similar as entity, score "
		+ "ORDER BY score DESC "
		+ "LIMIT $size";


		ArrayList<JsonElement> res = new ArrayList<>();

		Session session = neo4jClient.getSession();
		Map<String, Object> params = new HashMap<>();
		params.put("iri", iri);
		params.put("fetchSize", fetchSize);
		params.put("size", pageable.getPageSize());
		Result result = session.run(query, params);

		for(Record r : result.list()) {

			var rmap = r.asMap();

			Map<String,Object> entity = ((Node) rmap.get("entity")).asMap();
			double score = (Double) rmap.get("score");

			var resRow = JsonParser.parseString((String) entity.get("_json"));
			var json = gson.fromJson(resRow, JsonElement.class);
			json.getAsJsonObject().addProperty("score", score);

			res.add(resRow);
		}

		return new PageImpl<JsonElement>(res, pageable, res.size());
    }

    public double getSimilarity(String type, String iri, String iri2, String modelName) {

		// Property name preserves the original model name format
		String embeddingProperty = "embeddings_" + modelName;

		String query = "MATCH (c:" + type + " {iri: $iri, isDefiningOntology:['true']}) " +
		"MATCH (c2:" + type + " {iri: $iri2, isDefiningOntology:['true']}) " +
		"RETURN vector.similarity.cosine(c.`" + embeddingProperty + "`, c2.`" + embeddingProperty + "`) AS score";

		Session session = neo4jClient.getSession();
		Result result = session.run(query, Map.of("iri", iri, "iri2", iri2));

		for(Record r : result.list()) {
			var rmap = r.asMap();
			double score = (Double) rmap.get("score");
			return score;
		}

		throw new ResourceNotFoundException("entity not found");
    }

    public List<Double> getEmbeddingVector(String type, String iri, String modelName) {

		// Property name preserves the original model name format
		String embeddingProperty = "embeddings_" + modelName;

		// Only defining entities have embeddings (enforced by dataload)
		String query = "MATCH (c:" + type + " {iri: $iri}) " +
		"WHERE c.`" + embeddingProperty + "` IS NOT NULL " +
		"RETURN c.`" + embeddingProperty + "` AS embeddings";

		Session session = neo4jClient.getSession();
		Result result = session.run(query, Map.of("iri", iri));

		for(Record r : result.list()) {
			var rmap = r.asMap();
			List<Double> embeddings = (List<Double>) rmap.get("embeddings");
			return embeddings;
		}

		throw new ResourceNotFoundException("entity not found");
    }

    /**
     * Search by vector globally (all ontologies, defining classes only).
     * Queries both LabelEmbedding and CurationEmbedding child node indexes,
     * then traverses back to the parent entity node. Deduplicates by entity IRI.
     */
    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName) {
        return searchByVector(type, vector, pageable, modelName, true);
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable, String modelName, boolean includeCurations) {

		String safeModel = modelName.replace("-", "_").replace(".", "_");

		// Over-fetch from vector index since we deduplicate and filter by type
		int fetchSize = pageable.getPageSize() * 10;

		String matchClause = "MATCH (entity:" + type + ")-[:HAS_EMBEDDING]->(emb) ";
		String entityExpr = "entity";
		String query = buildVectorSearchQuery(safeModel, includeCurations, matchClause, null, entityExpr, "$size");

		ArrayList<JsonElement> res = new ArrayList<>();

		Session session = neo4jClient.getSession();
		Map<String, Object> params = new HashMap<>();
		params.put("vec", vector);
		params.put("fetchSize", fetchSize);
		params.put("size", pageable.getPageSize());
		Result result = session.run(query, params);

		for(Record r : result.list()) {

			var rmap = r.asMap();

			Map<String,Object> entity = ((Node) rmap.get("entity")).asMap();
			double score = (Double) rmap.get("score");

			var json = JsonParser.parseString((String) entity.get("_json")).getAsJsonObject();
			json.addProperty("score", score);

			res.add(json);
		}

		return new PageImpl<JsonElement>(res, pageable, res.size());
    }

    /**
     * Search by vector within a specific ontology.
     * Uses LabelEmbedding and CurationEmbedding child node indexes for matching,
     * then traverses back to the parent entity node.
     * If isDefiningOntology is true, only returns classes defined in this ontology (simple post-filter).
     * If isDefiningOntology is false, includes imported classes by joining on IRI.
     */
    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable, String modelName, String ontologyId, boolean isDefiningOntology) {
        return searchByVectorInOntology(type, vector, pageable, modelName, ontologyId, isDefiningOntology, true);
    }

    public Page<JsonElement> searchByVectorInOntology(String type, List<Double> vector, Pageable pageable, String modelName, String ontologyId, boolean isDefiningOntology, boolean includeCurations) {

		String safeModel = modelName.replace("-", "_").replace(".", "_");

		// Over-fetch from vector index since we're filtering/joining to a subset
		int fetchSize = pageable.getPageSize() * 10;

		String matchClause;
		String whereClause;
		String entityExpr;

		if (isDefiningOntology) {
			matchClause = "MATCH (entity:" + type + ")-[:HAS_EMBEDDING]->(emb) ";
			whereClause = "WHERE $ontologyId IN entity.ontologyId ";
			entityExpr = "entity";
		} else {
			matchClause = "MATCH (defining:" + type + ")-[:HAS_EMBEDDING]->(emb) "
				+ "MATCH (target:" + type + " {iri: defining.iri}) ";
			whereClause = "WHERE $ontologyId IN target.ontologyId ";
			entityExpr = "target AS entity";
		}

		String query = buildVectorSearchQuery(safeModel, includeCurations, matchClause, whereClause, entityExpr, "$limit");

		ArrayList<JsonElement> res = new ArrayList<>();

		Session session = neo4jClient.getSession();
		Map<String, Object> params = new HashMap<>();
		params.put("vec", vector);
		params.put("fetchSize", fetchSize);
		params.put("ontologyId", ontologyId.toLowerCase());
		params.put("limit", pageable.getPageSize());
		Result result = session.run(query, params);

		for(Record r : result.list()) {

			var rmap = r.asMap();

			Map<String,Object> entity = ((Node) rmap.get("entity")).asMap();
			double score = (Double) rmap.get("score");

			var json = JsonParser.parseString((String) entity.get("_json")).getAsJsonObject();
			json.addProperty("score", score);

			res.add(json);
		}

		return new PageImpl<JsonElement>(res, pageable, res.size());
    }

    /**
     * Build the Cypher query for vector search.
     *
     * @param safeModel        sanitized model name (hyphens/dots replaced with underscores)
     * @param includeCurations if true, UNION ALL across LabelEmbedding and CurationEmbedding indexes
     * @param matchClause      Cypher MATCH clause(s) that traverse from emb to the entity
     * @param whereClause      optional WHERE clause (null when not filtering by ontology)
     * @param entityExpr       expression to project as "entity" (e.g. "entity" or "target AS entity")
     * @param limitParam       parameter reference for the LIMIT (e.g. "$size" or "$limit")
     */
    private String buildVectorSearchQuery(String safeModel, boolean includeCurations,
            String matchClause, String whereClause, String entityExpr, String limitParam) {

        String labelIndex = "embedding_" + safeModel + "_label";
        String where = whereClause != null ? whereClause : "";

        // Single-index branch (labels only)
        String labelBranch = "CALL db.index.vector.queryNodes('" + labelIndex + "', $fetchSize, $vec) "
                + "YIELD node AS emb, score "
                + matchClause
                + where;

        if (!includeCurations) {
            return labelBranch
                    + "WITH " + entityExpr + ", max(score) AS score "
                    + "RETURN entity, score "
                    + "ORDER BY score DESC "
                    + "LIMIT " + limitParam;
        }

        // Dual-index: UNION ALL label + curated inside CALL {}, then deduplicate
        String curatedIndex = "embedding_" + safeModel + "_curated";
        String curatedBranch = "CALL db.index.vector.queryNodes('" + curatedIndex + "', $fetchSize, $vec) "
                + "YIELD node AS emb, score "
                + matchClause
                + where;

        return "CALL { "
                + labelBranch
                + "WITH " + entityExpr + ", score "
                + "RETURN entity, score "
                + "UNION ALL "
                + curatedBranch
                + "WITH " + entityExpr + ", score "
                + "RETURN entity, score "
                + "} "
                + "WITH entity, max(score) AS score "
                + "RETURN entity, score "
                + "ORDER BY score DESC "
                + "LIMIT " + limitParam;
    }

    public List<String> getEmbeddingModelsInNeo4j() {
		// Query Neo4j property keys to find embedding properties
		// Average embeddings on OntologyEntity use pattern: embeddings_{model_name}
		// Individual embeddings on Embedding nodes use pattern: embedding_{model_name}
		// We report based on the OntologyEntity properties (which have averages)
		String query = "CALL db.propertyKeys() YIELD propertyKey WHERE propertyKey STARTS WITH 'embeddings_' RETURN propertyKey";

		ArrayList<String> models = new ArrayList<>();

		Session session = neo4jClient.getSession();
		Result result = session.run(query);

		for (Record r : result.list()) {
			String propertyKey = r.get("propertyKey").asString();
			// Extract model name by removing the "embeddings_" prefix
			String modelName = propertyKey.substring("embeddings_".length());
			if (!models.contains(modelName) && !modelName.contains("pca16")) {
				models.add(modelName);
			}
		}

		return models;
    }
	
}
