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

    public Page<JsonElement> getSimilar(String type, String iri, Pageable pageable) {

		// Only the defining class has vector embeddings. So instead of searching by
		// ID (where we may get an imported class with no embeddings), search by IRI
		// and isDefiningOntology=true

		String index = type == "OntologyClass" ? "class_embeddings" : "property_embeddings";

		String query = "MATCH (c:" + type + " {iri: $iri}) "
		+ "WHERE \"true\" IN c.isDefiningOntology "
		+ "CALL db.index.vector.queryNodes('" + index + "', $size, c.embeddings) "
		+ "YIELD node AS similar, score "
		+ "RETURN similar as entity, score "
		+ "ORDER BY score DESC ";


		ArrayList<JsonElement> res = new ArrayList<>();

		Session session = neo4jClient.getSession();
		Result result = session.run(query, Map.of("iri", iri, "size", pageable.getPageSize()));

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

    public double getSimilarity(String type, String iri, String iri2) {

		String query = "MATCH (c:" + type + " {iri: $iri, isDefiningOntology:['true']}) " +
		"MATCH (c2:" + type + " {iri: $iri2, isDefiningOntology:['true']}) " +
		"RETURN vector.similarity.cosine(c.embeddings, c2.embeddings) AS score";

		Session session = neo4jClient.getSession();
		Result result = session.run(query, Map.of("iri", iri, "iri2", iri2));

		for(Record r : result.list()) {
			var rmap = r.asMap();
			double score = (Double) rmap.get("score");
			return score;
		}

		throw new ResourceNotFoundException("entity not found");
    }

    public List<Double> getEmbeddingVector(String type, String iri) {

		String query = "MATCH (c:" + type + " {iri: $iri, isDefiningOntology:['true']}) " +
		"RETURN c.embeddings AS embeddings";

		Session session = neo4jClient.getSession();
		Result result = session.run(query, Map.of("iri", iri));

		for(Record r : result.list()) {
			var rmap = r.asMap();
			List<Double> embeddings = (List<Double>) rmap.get("embeddings");
			return embeddings;
		}

		throw new ResourceNotFoundException("entity not found");
    }

    public Page<JsonElement> searchByVector(String type, List<Double> vector, Pageable pageable) {

		String index = type == "OntologyClass" ? "class_embeddings" : "property_embeddings";

		String query = "CALL db.index.vector.queryNodes('" + index + "', $size, $vec) "
		+ "YIELD node AS similar, score "
		+ "RETURN similar as entity, score "
		+ "ORDER BY score DESC ";

		ArrayList<JsonElement> res = new ArrayList<>();

		Session session = neo4jClient.getSession();
		Result result = session.run(query, Map.of("vec", vector, "size", pageable.getPageSize()));

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
	
}
