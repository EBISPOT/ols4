package uk.ac.ebi.spot.ols.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;

public class OlsSolrQuery {

	String searchText = null;
	String searchFields = null;
	List<Filter> filter = new ArrayList<>();

	public OlsSolrQuery() {
	}

	public void setSearch(String searchText, String searchFields) {
		this.searchText = searchText;
		this.searchFields = searchFields;
	}

	public void addFilter(String propertyName, String propertyValue, boolean exact) {
		this.filter.add(new Filter(propertyName, propertyValue, exact));
	}

	public void addDynamicFilterProperties(Map<String, String> properties) {
		for (String k : properties.keySet()) {
			String value = properties.get(k);
			k = k.replace(":", "__");
			addFilter(k, value, false);
		}
	}

	public SolrQuery constructQuery() {

		SolrQuery query = new SolrQuery();
		query.set("defType", "edismax");

		if(searchText != null) {
			query.setQuery(searchText);
			query.set("qf", searchFields.replace(":", "__"));
		} else {
			query.setQuery("*:*");
		}

		for(Filter f : filter) {

			String propertyName = f.propertyName;

			if(f.exact) {
				propertyName = "str_" + f.propertyName;
			}

			query.addFilterQuery(
				ClientUtils.escapeQueryChars(propertyName)
					+ ":\"" + ClientUtils.escapeQueryChars(f.propertyValue) + "\"");
		}

		return query;
	}

	private class Filter {

		String propertyName;
		String propertyValue;
		boolean exact;

		public Filter(String propertyName, String propertyValue, boolean exact) {
			this.propertyName = propertyName;
			this.propertyValue = propertyValue;
			this.exact = exact;
		}
	}

	
}
