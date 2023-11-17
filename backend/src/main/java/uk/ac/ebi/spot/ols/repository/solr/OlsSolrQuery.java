package uk.ac.ebi.spot.ols.repository.solr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;

public class OlsSolrQuery {

	String searchText = null;
	boolean exactMatch = false;
	List<SearchField> searchFields = new ArrayList<>();
	List<BoostField> boostFields = new ArrayList<>();
	List<String> facetFields = new ArrayList<>();
	List<Filter> filters = new ArrayList<>();

	public OlsSolrQuery() {
	}

	public void setSearchText(String searchText) {
		this.searchText = searchText;
	}

	public String getSearchText() {
		return this.searchText;
	}

	public void setExactMatch(boolean exactMatch) {
		this.exactMatch = exactMatch;
	}

	public void addSearchField(String propertyName, int weight, SearchType searchType) {
		this.searchFields.add(new SearchField(propertyName, weight, searchType));
	}

	public void addBoostField(String propertyName, String propertyValue, int weight, SearchType searchType) {
		if(propertyValue != null && propertyValue.length() > 0)
			this.boostFields.add(new BoostField(propertyName, propertyValue, weight, searchType));
	}

	public void addFacetField(String propertyName) {
		this.facetFields.add(propertyName);
	}

	public void addFilter(String propertyName, Collection<String> propertyValues, SearchType searchType) {
		this.filters.add(new Filter(propertyName, propertyValues, searchType));
	}

	public SolrQuery constructQuery() {

		SolrQuery query = new SolrQuery();
		query.set("defType", "edismax");
		query.setFields("_json");

		if(searchText != null) {

			if(exactMatch) {
				query.setQuery("\"" + searchText + "\"");
			} else {
				query.setQuery(searchText);
			}

			StringBuilder qf = new StringBuilder();

			for(SearchField searchField : searchFields) {
				if(qf.length() > 0) {
					qf.append(" ");
				}
				qf.append(ClientUtils.escapeQueryChars( getSolrPropertyName(searchField.propertyName, exactMatch ? SearchType.WHOLE_FIELD : searchField.searchType)) );
				qf.append("^");
				qf.append(searchField.weight);
			}

			query.set("qf", qf.toString());

		} else {
			query.setQuery("*:*");
		}

		if(boostFields.size() > 0) {

			StringBuilder bf = new StringBuilder();

			for(BoostField boostField : boostFields) {
				if(bf.length() > 0) {
					bf.append(" ");
				}
				bf.append(ClientUtils.escapeQueryChars( getSolrPropertyName(boostField.propertyName, exactMatch ? SearchType.WHOLE_FIELD : boostField.searchType)) );
				bf.append(":\"");
				bf.append(ClientUtils.escapeQueryChars( getSolrPropertyValue(boostField.propertyValue, exactMatch ? SearchType.WHOLE_FIELD : boostField.searchType)) );
				bf.append("\"");
				bf.append("^");
				bf.append(boostField.weight);
			}

			query.set("bq", bf.toString());
		}

		for(Filter f : filters) {

			StringBuilder fq = new StringBuilder();

			if(facetFields.contains(f.propertyName)) {
				fq.append("{!tag=olsfacet}");
			}

			fq.append( ClientUtils.escapeQueryChars(getSolrPropertyName(f.propertyName, f.searchType)) );
			fq.append(":(");

			int n = 0;

			for(String value : f.propertyValues) {
				if(n ++ > 0) {
					fq.append(" OR ");
				}
				fq.append("\"");
				fq.append(ClientUtils.escapeQueryChars(getSolrPropertyValue(value, exactMatch ? SearchType.WHOLE_FIELD : f.searchType)));
				fq.append("\"");
			}
			fq.append(")");

			query.addFilterQuery(fq.toString());
		}

		if(facetFields.size() > 0) {
			for(String facetField : facetFields) {
				query.addFacetField("{!ex=olsfacet}" + facetField);
			}
		}

		return query;
	}

	private class Filter {

		String propertyName;
		Collection<String> propertyValues; // all values to search for ("OR")
		SearchType searchType;

		public Filter(String propertyName, Collection<String> propertyValues, SearchType searchType) {
			this.propertyName = propertyName;
			this.propertyValues = propertyValues;
			this.searchType = searchType;
		}
	}

	private class SearchField {

		String propertyName;
		int weight;
		SearchType searchType;

		public SearchField(String propertyName, int weight, SearchType searchType) {
			this.propertyName = propertyName;
			this.weight = weight;
			this.searchType = searchType;
		}

	}

	private class BoostField {

		String propertyName;
		String propertyValue;
		int weight;
		SearchType searchType;

		public BoostField(String propertyName, String propertyValue, int weight, SearchType searchType) {
			this.propertyName = propertyName;
			this.propertyValue = propertyValue;
			this.weight = weight;
			this.searchType = searchType;
		}

	}

	private String getSolrPropertyName(String propertyName, SearchType searchType) {
		switch(searchType) {
			case CASE_INSENSITIVE_TOKENS:
				return "lowercase_" + propertyName;
			case CASE_SENSITIVE_TOKENS:
				return propertyName;
			case WHOLE_FIELD:
				return "str_" + propertyName;
			case EDGES:
				return "edge_" + propertyName;
			case WHITESPACE:
				return "whitespace_" + propertyName;
			case WHITESPACE_EDGES:
				return "whitespace_edge_" + propertyName;
			default:
				throw new RuntimeException("unknown filter accuracy");
		}
	}

	private String getSolrPropertyValue(String propertyValue, SearchType searchType) {
		switch(searchType) {
			case CASE_INSENSITIVE_TOKENS:
				return propertyValue.toLowerCase();
			case CASE_SENSITIVE_TOKENS:
			case WHOLE_FIELD:
			case EDGES:
			case WHITESPACE:
			case WHITESPACE_EDGES:
				return propertyValue;
			default:
				throw new RuntimeException("unknown filter accuracy");
		}
	}

}
