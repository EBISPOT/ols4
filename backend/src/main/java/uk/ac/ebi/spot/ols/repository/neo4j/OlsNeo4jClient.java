package uk.ac.ebi.spot.ols.repository.neo4j;

import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import static org.neo4j.driver.Values.parameters;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import uk.ac.ebi.spot.ols.service.OntologyEntity;

@Component
public class OlsNeo4jClient {

	@Autowired
	Neo4jClient neo4jClient;

    public Page<Map<String, Object>> getAll(String type, Map<String,String> properties, Pageable pageable) {

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

	public Map<String, Object> getOne(String type, Map<String,String> properties) {

		Page<Map<String, Object>> results = getAll(type, properties, new PageRequest(0, 2, Sort.DEFAULT_DIRECTION));

		if(results.getTotalElements() != 1) {
			throw new RuntimeException("expected exactly one result for neo4j getOne");
		}

		return results.getContent().iterator().next();
	}

    public Page<Map<String, Object>> getParents(String type, String id, List<String> relationURIs, Pageable pageable) {

	String edge = makeEdge(relationURIs);

	// TODO fix injection

	String query =
	  "MATCH (a:" + type+ ")-[:" + edge + "]->(b) "
	+ "WHERE a.id = $id "
	+ "RETURN distinct b";

	String countQuery =
	  "MATCH (a:" + type + ")-[:" + edge + "]->(b) "
	+ "WHERE a.id = $id "
	+ "RETURN count(distinct b)";

		System.out.println(query);

		return neo4jClient.queryPaginated(query, "b", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<Map<String, Object>> getChildren(String type, String id, List<String> relationURIs, Pageable pageable) {

	String edge = makeEdge(relationURIs);

	String query =
	  "MATCH (a:" + type + ")<-[:" + edge + "]-(b) "
	+ "WHERE a.id = $id "
	+ "RETURN distinct b";

	String countQuery =
	  "MATCH (a:" + type + ")<-[:" + edge + "]-(b) "
	+ "WHERE a.id = $id "
	+ "RETURN count(distinct b)";

	return neo4jClient.queryPaginated(query, "b", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<Map<String, Object>> getAncestors(String type, String id, List<String> relationURIs, Pageable pageable) {

	String edge = makeEdge(relationURIs);

	String query =
	  "MATCH (c:" + type + ") WHERE c.id = $id "
	+ "WITH c "
	+ "OPTIONAL MATCH (c)-[:" + edge + " *]->(ancestor) "
	+ "RETURN ancestor AS a";

	String countQuery =
	  "MATCH (a:" + type + ") WHERE a.id = $id "
	+ "WITH a "
	+ "OPTIONAL MATCH (a)-[:" + edge + " *]->(ancestor) "
	+ "RETURN count(ancestor)";

	return neo4jClient.queryPaginated(query, "a", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<Map<String, Object>> getDescendants(String type, String id, List<String> relationURIs, Pageable pageable) {

	String edge = makeEdge(relationURIs);

	String query =
	  "MATCH (a:" + type + ") WHERE a.id = $id "
	+ "WITH a "
	+ "OPTIONAL MATCH (a)<-[:" + edge + " *]-(descendant) "
	+ "RETURN descendant AS c";

	String countQuery =
	  "MATCH (a:" + type + ") WHERE a.id = $id "
	+ "WITH a "
	+ "OPTIONAL MATCH (a)<-[:" + edge + " *]-(descendant) "
	+ "RETURN count(descendant)";

	return neo4jClient.queryPaginated(query, "c", countQuery, parameters("id", id), pageable);
    }



	private static String makeEdge(List<String> relationURIs) {

		String edge = "";

		for(String uri : relationURIs) {
			if(edge != "") {
				edge += "|";
			}

			edge += "`" + uri + "`";
		}

		return edge;
	}

	
}
