package uk.ac.ebi.spot.ols.repository.neo4j;

import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.repository.solr.OlsSolrClient;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import java.util.List;
import java.util.Map;

import static org.neo4j.driver.Values.parameters;

@Component
public class OlsNeo4jClient {

	@Autowired
	Neo4jClient neo4jClient;
  
	private static final Logger logger = LoggerFactory.getLogger(OlsNeo4jClient.class);
  
  
	public long getDatabaseNodeCount() {
		return neo4jClient.returnNodeCount();
	}

    public Page<JsonElement> getAll(String type, Map<String,String> properties, Pageable pageable) {

		String query = "MATCH (a:" + type + ")";

		if(properties.size() > 0) {
			query += " WHERE ";
			boolean isFirst = true;
			for(String property : properties.keySet()) {
				if(isFirst)
					isFirst = false;
				else
					query += " AND ";

				// TODO escape value
				query += "a." + property + " = \"" + properties.get(property) + "\"";
			}
		}
	
		String getQuery = query + " RETURN a";
		String countQuery = query + " RETURN count(a)";

		// TODO: can we just return _json ?
		// seems to break the neo4j client to return a string
		//
		return neo4jClient.queryPaginated(getQuery, "a", countQuery, parameters("type", type), pageable);
	}

	public JsonElement getOne(String type, Map<String,String> properties) {

		Page<JsonElement> results = getAll(type, properties, PageRequest.of(0, 10));

		if(results.getTotalElements() != 1) {
			throw new RuntimeException("expected exactly one result for neo4j getOne, but got " + results.getTotalElements());
		}

		return results.getContent().iterator().next();
	}

	private String makeEdgePropsClause(Map<String,String> edgeProps) {

		String where = "";

		for(String prop : edgeProps.keySet()) {
			String value = edgeProps.get(prop);
			where += " AND \"" + value + "\" IN edge.`" + prop + "` ";
		}

		return where;
	}

    public Page<JsonElement> traverseOutgoingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Pageable pageable) {

		String edge = makeEdgesList(edgeIRIs, edgeProps);

		// TODO fix injection

		String query =
		  "MATCH (a:" + type+ ")-[edge:" + edge + "]->(b) "
		+ "WHERE a.id = $id " + makeEdgePropsClause(edgeProps)
		+ "RETURN distinct b";

		String countQuery =
		  "MATCH (a:" + type + ")-[edge:" + edge + "]->(b) "
		+ "WHERE a.id = $id " + makeEdgePropsClause(edgeProps)
		+ "RETURN count(distinct b)";

		logger.trace(query);

		return neo4jClient.queryPaginated(query, "b", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<JsonElement> traverseIncomingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Pageable pageable) {

		String edge = makeEdgesList(edgeIRIs, Map.of());

		String query =
		  "MATCH (a:" + type + ")<-[edge:" + edge + "]-(b) "
		+ "WHERE a.id = $id "
		+ "RETURN distinct b";

		String countQuery =
		  "MATCH (a:" + type + ")<-[edge:" + edge + "]-(b) "
		+ "WHERE a.id = $id "
		+ "RETURN count(distinct b)";

		return neo4jClient.queryPaginated(query, "b", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<JsonElement> recursivelyTraverseOutgoingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Pageable pageable) {

		String edge = makeEdgesList(edgeIRIs, Map.of());

		String query =
				"MATCH (c:" + type + ") WHERE c.id = $id "
						+ "WITH c "
						+ "OPTIONAL MATCH (c)-[edge:" + edge + " *]->(ancestor) "
						+ "RETURN DISTINCT ancestor AS a";

		String countQuery =
				"MATCH (a:" + type + ") WHERE a.id = $id "
						+ "WITH a "
						+ "OPTIONAL MATCH (a)-[edge:" + edge + " *]->(ancestor) "
						+ "RETURN count(DISTINCT ancestor)";

		return neo4jClient.queryPaginated(query, "a", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<JsonElement> recursivelyTraverseIncomingEdges(String type, String id, List<String> edgeIRIs, Map<String,String> edgeProps, Pageable pageable) {

		String edge = makeEdgesList(edgeIRIs, Map.of());

		String query =
		  "MATCH (a:" + type + ") WHERE a.id = $id "
		+ "WITH a "
		+ "OPTIONAL MATCH (a)<-[edge:" + edge + " *]-(descendant) "
		+ "RETURN DISTINCT descendant AS c";

		String countQuery =
		  "MATCH (a:" + type + ") WHERE a.id = $id "
		+ "WITH a "
		+ "OPTIONAL MATCH (a)<-[edge:" + edge + " *]-(descendant) "
		+ "RETURN count(DISTINCT descendant)";

		return neo4jClient.queryPaginated(query, "c", countQuery, parameters("id", id), pageable);
    }



	private static String makeEdgesList(List<String> edgeIRIs, Map<String,String> edgeProperties) {

		String edge = "";

		for(String iri : edgeIRIs) {
			if(edge != "") {
				edge += "|";
			}

			edge += "`" + iri + "`";
		}

		return edge;
	}

	
}
