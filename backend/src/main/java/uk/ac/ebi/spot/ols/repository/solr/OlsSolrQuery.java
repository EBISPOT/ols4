package uk.ac.ebi.spot.ols.repository.solr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;

public class OlsSolrQuery {

	String searchText = null;
	List<SearchField> searchFields = new ArrayList<>();
	List<SearchField> boostFields = new ArrayList<>();
	List<Filter> filters = new ArrayList<>();

	public OlsSolrQuery() {
	}

	public void setSearchText(String searchText) {
		this.searchText = searchText;
	}

	public void addSearchField(String propertyName, int weight, Fuzziness fuzziness) {
		this.searchFields.add(new SearchField(propertyName, weight, fuzziness));
	}

	public void addBoostField(String propertyName, int weight, Fuzziness fuzziness) {
		this.boostFields.add(new SearchField(propertyName, weight, fuzziness));
	}

	public void addFilter(String propertyName, String propertyValue, Fuzziness fuzziness) {
		this.filters.add(new Filter(propertyName, propertyValue, fuzziness));
	}

	public SolrQuery constructQuery() {

		SolrQuery query = new SolrQuery();
		query.set("defType", "edismax");

		if(searchText != null) {

			query.setQuery(searchText);

			StringBuilder qf = new StringBuilder();

			for(SearchField searchField : searchFields) {
				if(qf.length() > 0) {
					qf.append(" ");
				}
				qf.append(getSolrPropertyName(searchField.propertyName, searchField.fuzziness));
				qf.append("^");
				qf.append(searchField.weight);
			}

			query.set("qf", qf.toString());

		} else {
			query.setQuery("*:*");
		}

		if(boostFields.size() > 0) {

			StringBuilder bf = new StringBuilder();

			for(SearchField boostField : boostFields) {
				if(bf.length() > 0) {
					bf.append(" ");
				}
				bf.append(getSolrPropertyName(boostField.propertyName, boostField.fuzziness));
				bf.append("^");
				bf.append(boostField.weight);
			}

			query.set("bq", bf.toString());
		}

		for(Filter f : filters) {
			query.addFilterQuery(
				ClientUtils.escapeQueryChars(getSolrPropertyName(f.propertyName, f.fuzziness))
					+ ":\"" + ClientUtils.escapeQueryChars(getSolrPropertyValue(f.propertyValue, f.fuzziness)) + "\"");
		}

		return query;
	}

	private class Filter {

		String propertyName;
		String propertyValue;
		Fuzziness fuzziness;

		public Filter(String propertyName, String propertyValue, Fuzziness fuzziness) {
			this.propertyName = propertyName;
			this.propertyValue = propertyValue;
			this.fuzziness = fuzziness;
		}
	}

	private class SearchField {

		String propertyName;
		int weight;
		Fuzziness fuzziness;

		public SearchField(String propertyName, int weight, Fuzziness fuzziness) {
			this.propertyName = propertyName;
			this.weight = weight;
			this.fuzziness = fuzziness;
		}

	}

	private String getSolrPropertyName(String propertyName, Fuzziness fuzziness) {
		switch(fuzziness) {
			case CASE_INSENSITIVE_SUBSTRING:
				return "lowercase_" + propertyName;
			case CASE_SENSITIVE_SUBSTRING:
				return propertyName;
			case EXACT:
				return "str_" + propertyName;
			default:
				throw new RuntimeException("unknown filter accuracy");
		}
	}

	private String getSolrPropertyValue(String propertyValue, Fuzziness fuzziness) {
		switch(fuzziness) {
			case CASE_INSENSITIVE_SUBSTRING:
				return propertyValue.toLowerCase();
			case CASE_SENSITIVE_SUBSTRING:
			case EXACT:
				return propertyValue;
			default:
				throw new RuntimeException("unknown filter accuracy");
		}
	}

}
