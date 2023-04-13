import { Fragment } from "react";
import Header from "../components/Header";

export default function Downloads() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="downloads" />
      <main className="container mx-auto my-8">
	<div>
	<p>The OLS internal database format is <b>undocumented and subject to change at any time</b>. We therefore strongly recommend that the <b>OLS API and/or upstream OWL files from ontology vendors</b> are used to access ontology information rather than the OLS data dump, with the exception of the SSSOM mappings file which is provided with a standardised representation.</p>
	<p>However, we recognise that the EBI OLS dataset can be useful in some cases to e.g. set up a local instance of OLS for development purposes, or to perform one-off analyses of the ontology corpus.</p>
	<p>To this end, snapshots of OLS datareleases can be downloaded from <a className="link-default" href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/">https://ftp.ebi.ac.uk/pub/databases/spot/ols</a></p>
	<p>The latest snapshot can be found at <a className="link-default" href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/">https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest</a></p>
	<p>Each snapshot contains the following files (the links below point to the latest snapshot):</p>
	<ul className="list-disc">
		<li><a className="link-default" href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/ontologies.json.gz" target="_blank"><code>ontologies.json.gz</code></a> : OLS internal data representation of all loaded ontologies (~50 GB uncompressed)</li>
		<li><a className="link-default" href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/ontologies_linked.json.gz" target="_blank"><code>ontologies_linked.json.gz</code></a> : As <code>ontologies.json.gz</code> but after running through the OLS linker to add references between ontologies and to external databases (~150 GB uncompressed)</li>
		<li><a className="link-default" href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/neo4j.tgz" target="_blank"><code>neo4j.tgz</code></a> : The neo4j database generated from <code>ontologies_linked.json</code> by the OLS json2neo tool, after creating db indexes (Requires Neo4j community 4.4.9)</li>
		<li><a className="link-default" href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/solr.tgz" target="_blank"><code>solr.tgz</code></a> : The solr database generated from <code>ontologies_linked.json</code> by the OLS json2solr tool (Requires Solr 9.0.0)</li>
		<li><a className="link-default" href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/mappings_sssom.tsv.gz" target="_blank"><code>mappings_sssom.tsv.gz</code></a> : Mappings extracted from all ontologies in <a className="link-default" href="https://github.com/mapping-commons/sssom" target="_blank">SSSOM TSV</a> format</li>
	</ul>
	</div>
      </main>
    </Fragment>
  );
}
