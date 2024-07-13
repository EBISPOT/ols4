
package uk.ac.ebi.spot.ols.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import uk.ac.ebi.spot.ols.repository.neo4j.OlsNeo4jClient;

import javax.validation.constraints.NotNull;

@Component
public class Neo4jClient {


	@NotNull
	@org.springframework.beans.factory.annotation.Value("${ols.neo4j.host:bolt://localhost:7687}")
	private String host;

	private static final Logger logger = LoggerFactory.getLogger(Neo4jClient.class);

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


	// only used by OLS3 graph repo, remove at some point
	public List<Map<String,Object>> rawQuery(String query) {

		Session session = getSession();

		Result result = session.run(query);

		List<Map<String,Object>> list = result.stream().map(r -> r.asMap()).collect(Collectors.toList());

		session.close();
		return list;
	}

	public List<JsonElement> query(String query, String resVar) {

		Session session = getSession();

		Result result = session.run(query);


		List<JsonElement> list =  result.list().stream()
				.map(r -> r.get(resVar).get("_json").asString())
				.map(JsonParser::parseString)
				.collect(Collectors.toList());
		session.close();

		return list;
	}

	public Page<JsonElement> queryPaginated(String query, String resVar, String countQuery, Value parameters, Pageable pageable) {

		Session session = getSession();

		String sort = "";
		String queryToRun;

		if(pageable != null) {
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
			queryToRun = query + " " + sort + " SKIP " + pageable.getOffset() + " LIMIT " + pageable.getPageSize();
		} else {
			queryToRun = query;
		}

		logger.trace("queryToRun = {}", queryToRun);
		logger.info(" gson.toJson(parameters.asMap()) = {}", gson.toJson(parameters.asMap()));

		Stopwatch timer = Stopwatch.createStarted();
		Result result = session.run(
				queryToRun,

		    parameters
		);
		logger.info("Neo4j run paginated query duration = {}", timer.stop());

		Stopwatch timer2 = Stopwatch.createStarted();
		Result countResult = session.run(countQuery, parameters);
		logger.info("Neo4j run paginated count duration = {}", timer2.stop());

		Record countRecord = countResult.single();
		int count = countRecord.get(0).asInt();

		if(!result.hasNext() || result.peek().values().get(0).isNull()) {
			return new PageImpl<>(List.of(), pageable, count);
		}

		Page<JsonElement> page = new PageImpl<>(
				result.list().stream()
						.map(r -> JsonParser.parseString(r.get(resVar).get("_json").asString()))
						.collect(Collectors.toList()),
				pageable, count);

		session.close();
		return page;
	}

	public JsonElement queryOne(String query, String resVar, Value parameters) {

		Session session = getSession();

		logger.debug("query = {}", query);

		Stopwatch timer = Stopwatch.createStarted();
		Result result = session.run(query, parameters);
		logger.info("Neo4j run query {} has duration {}", query, timer.stop());

		Value v = null;

		try {
			v = result.single().get(resVar).get("_json");
		} catch(NoSuchRecordException e) {
			throw new ResourceNotFoundException();
		}

		session.close();
		return JsonParser.parseString(v.asString());
	}
}

