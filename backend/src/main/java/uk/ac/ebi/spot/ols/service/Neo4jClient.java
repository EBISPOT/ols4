
package uk.ac.ebi.spot.ols.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;

import javax.validation.constraints.NotNull;

@Component
public class Neo4jClient {


	@NotNull
	@org.springframework.beans.factory.annotation.Value("${ols.neo4j.host:bolt://localhost:7687}")
	private String host;



	private Gson gson = new Gson();

	private Driver driver;

	public Driver getDriver() {

		if(driver == null) {
			driver = GraphDatabase.driver(host);
		}

		return driver;

	}

	public Session getSession() {

		return getDriver().session(SessionConfig.forDatabase("neo4j"));

	}



	public List<Map<String, Object>> query(String query, String resVar) {

		Session session = getSession();

		Result result = session.run(query);

		return result.list().stream()
					.map(r -> r.get(resVar).get("_json").asString())
					.map(r -> (Map<String,Object>) gson.fromJson(r, Map.class))
					.collect(Collectors.toList());
	}

	public Page<Map<String, Object>> queryPaginated(String query, String resVar, String countQuery, Value parameters, Pageable pageable) {

		Session session = getSession();


		String sort = "";

		if(pageable.getSort() != null) {
			for (Sort.Order order : pageable.getSort()) {
				if (sort.length() > 0) {
					sort += ", ";
				}
				sort += order.getProperty();
				sort += " ";
				sort += order.getDirection() == Sort.Direction.ASC ? "ASC" : " DESC";
			}
		} else {
			sort = "ORDER BY " + resVar + ".iri ASC";
		}

		String queryToRun = query
				+ " " + sort
		+ " SKIP " + pageable.getOffset()
						+ " LIMIT " + pageable.getPageSize();

		System.out.println(queryToRun);
		System.out.println(gson.toJson(parameters.asMap()));

		Stopwatch timer = Stopwatch.createStarted();
		Result result = session.run(
				queryToRun,

		    parameters
		);
		System.out.println("Neo4j run paginted query: " + timer.stop());

		Stopwatch timer2 = Stopwatch.createStarted();
		Result countResult = session.run(countQuery, parameters);
		System.out.println("Neo4j run paginated count: " + timer2.stop());

		Record countRecord = countResult.single();
		int count = countRecord.get(0).asInt();

		return new PageImpl<Map<String,Object>>(
				result.list().stream()
						.map(r -> (Map<String,Object>) gson.fromJson(r.get(resVar).get("_json").asString(), Map.class))
						.collect(Collectors.toList()),
				pageable, count);
	}

	public Map<String,Object> queryOne(String query, String resVar, Value parameters) {

		Session session = getSession();

		System.out.println(query);

		Stopwatch timer = Stopwatch.createStarted();
		Result result = session.run(query, parameters);
		System.out.println("Neo4j run query " + query + ": " + timer.stop());

		Value v = null;

		try {
			v = result.single().get(resVar).get("_json");
		} catch(NoSuchRecordException e) {
			throw new ResourceNotFoundException();
		}

		return (Map<String,Object>)gson.fromJson(v.asString(), Map.class);
	}



}

