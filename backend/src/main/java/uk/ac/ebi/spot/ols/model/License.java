package uk.ac.ebi.spot.ols.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name="license information of the ontology")
public class License {

	@Schema(name = "URL of the license", example = "http://creativecommons.org/licenses/by/4.0/")
	String url;
	@Schema(name = "Logo of the license", example = "http://mirrors.creativecommons.org/presskit/buttons/80x15/png/by.png")
	String logo;
	@Schema(name = "Label of the license", example = "CC-BY")
	String label;

	public License() {}

	public License(String url, String logo, String label) {
		super();
		this.url = url;
		this.logo = logo;
		this.label = label;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return "License [url=" + url + ", logo=" + logo + ", label=" + label + "]";
	}



}
