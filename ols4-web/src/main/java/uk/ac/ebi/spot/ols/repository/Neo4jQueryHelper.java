package uk.ac.ebi.spot.ols.repository;

import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import static org.neo4j.driver.Values.parameters;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.service.OwlGraphNode;

@Component
public class Neo4jQueryHelper {

    public Page<OwlGraphNode> getAll(String type, Pageable pageable) {

		// TODO: can we just return _json ?
		// seems to break the neo4j client to return a string
		//
		String query = "MATCH (a:" + type + ") RETURN a";
		String countQuery = "MATCH (a:" + type + ") RETURN count(a)";

		return Neo4jClient.queryPaginated(query, "a", countQuery, parameters("type", type), pageable);
    }


	public Page<OwlGraphNode> getAllInOntology(String ontologyId, String type, Pageable pageable) {

		String query = "MATCH (a:" + type + ") WHERE a.ontology_id = $ontologyId RETURN a";
		String countQuery = "MATCH (a:" + type + ") RETURN count(a)";

		return Neo4jClient.queryPaginated(query, "a", countQuery, parameters("type", type, "ontologyId", ontologyId), pageable);
	}

	public OwlGraphNode getOne(String type, String id) {

	String query = "MATCH (a:" + type + ") WHERE a.id = $id RETURN a";

	return Neo4jClient.queryOne(query, "a", parameters("type", type, "id", id));
    }

    public static Page<OwlGraphNode> getParents(String type, String id, List<String> relationURIs, Pageable pageable) {

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

		return Neo4jClient.queryPaginated(query, "b", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<OwlGraphNode> getChildren(String type, String id, List<String> relationURIs, Pageable pageable) {

	String edge = makeEdge(relationURIs);

	String query =
	  "MATCH (a:" + type + ")<-[:" + edge + "]-(b) "
	+ "WHERE a.id = $id "
	+ "RETURN distinct b";

	String countQuery =
	  "MATCH (a:" + type + ")<-[:" + edge + "]-(b) "
	+ "WHERE a.id = $id "
	+ "RETURN count(distinct b)";

	return Neo4jClient.queryPaginated(query, "b", countQuery, parameters("type", type, "id", id), pageable);
    }

    public static Page<OwlGraphNode> getAncestors(String type, String id, List<String> relationURIs, Pageable pageable) {

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

	return Neo4jClient.queryPaginated(query, "a", countQuery, parameters("type", type, "id", id), pageable);
    }

    public Page<OwlGraphNode> getDescendants(String type, String id, List<String> relationURIs, Pageable pageable) {

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

	return Neo4jClient.queryPaginated(query, "c", countQuery, parameters("id", id), pageable);
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
