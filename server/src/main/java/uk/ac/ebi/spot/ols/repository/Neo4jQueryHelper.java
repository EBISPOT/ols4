package uk.ac.ebi.spot.ols.repository;

import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.ols.service.Neo4jClient;

import static org.neo4j.driver.Values.parameters;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.ols.service.OntologyEntity;

@Component
public class Neo4jQueryHelper {

	@Autowired
	Neo4jClient neo4jClient;

    public Page<OntologyEntity> getAll(String type, Pageable pageable) {

		// TODO: can we just return _json ?
		// seems to break the neo4j client to return a string
		//
		String query = "MATCH (a:" + type + ") RETURN a";
		String countQuery = "MATCH (a:" + type + ") RETURN count(a)";

		return neo4jClient.queryPaginated(query, "a", countQuery, parameters("type", type), pageable);
    }


	public Page<OntologyEntity> getAllInOntology(String ontologyId, String type, Pageable pageable) {

		String query = "MATCH (a:" + type + ") WHERE a.ontology_id = $ontologyId RETURN a";
		String countQuery = "MATCH (a:" + type + ") RETURN count(a)";

		return neo4jClient.queryPaginated(query, "a", countQuery, parameters("type", type, "ontologyId", ontologyId), pageable);
	}

	public OntologyEntity getOne(String type, String field, String value) {

	String query = "MATCH (a:" + type + ") WHERE a." + field + "= $val RETURN a";

	return neo4jClient.queryOne(query, "a", parameters("type", type, "val", value));
    }

    public Page<OntologyEntity> getParents(String type, String id, List<String> relationURIs, Pageable pageable) {

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

    public Page<OntologyEntity> getChildren(String type, String id, List<String> relationURIs, Pageable pageable) {

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

    public Page<OntologyEntity> getAncestors(String type, String id, List<String> relationURIs, Pageable pageable) {

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

    public Page<OntologyEntity> getDescendants(String type, String id, List<String> relationURIs, Pageable pageable) {

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
