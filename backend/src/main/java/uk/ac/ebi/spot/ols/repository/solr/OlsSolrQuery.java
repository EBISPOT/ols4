package uk.ac.ebi.spot.ols.repository.solr;

import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;

public class OlsSolrQuery {

	String searchText = null;
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

	public void addFilter(String propertyName, String propertyValue, SearchType searchType) {
		this.filters.add(new Filter(propertyName, propertyValue, searchType));
	}

	public SolrQuery constructQuery() {

		SolrQuery query = new SolrQuery();
		query.set("defType", "edismax");

		if(searchText != null) {

//			if (searchText.contains("*")) {
				query.setQuery(searchText);
//			} else {
//				query.setQuery("*" + searchText + "*");
//			}

			StringBuilder qf = new StringBuilder();

			for(SearchField searchField : searchFields) {
				if(qf.length() > 0) {
					qf.append(" ");
				}
				qf.append(getSolrPropertyName(searchField.propertyName, searchField.searchType));
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
				bf.append(getSolrPropertyName(boostField.propertyName, boostField.searchType));
				bf.append(":");
				bf.append(getSolrPropertyValue(boostField.propertyValue, boostField.searchType));
				bf.append("^");
				bf.append(boostField.weight);
			}

			query.set("bq", bf.toString());
		}

		for(Filter f : filters) {
			query.addFilterQuery(
				ClientUtils.escapeQueryChars(getSolrPropertyName(f.propertyName, f.searchType))
					+ ":\"" + ClientUtils.escapeQueryChars(getSolrPropertyValue(f.propertyValue, f.searchType)) + "\"");
		}

		if(facetFields.size() > 0) {
			query.addFacetField(facetFields.toArray(new String[0]));
		}

		return query;
	}

	private class Filter {

		String propertyName;
		String propertyValue;
		SearchType searchType;

		public Filter(String propertyName, String propertyValue, SearchType searchType) {
			this.propertyName = propertyName;
			this.propertyValue = propertyValue;
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
				return propertyValue;
			default:
				throw new RuntimeException("unknown filter accuracy");
		}
	}

}
