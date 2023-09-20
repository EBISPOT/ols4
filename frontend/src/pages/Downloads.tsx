import { Fragment } from "react";
import { Banner } from "../components/Banner";
import DataTable, { Column } from "../components/DataTable";
import Header from "../components/Header";

export default function Downloads() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="downloads" />
      <main className="container mx-auto px-4 my-8">
        <div className="text-2xl font-bold my-6">
          Downloading OLS Data Releases
        </div>
        <div>
          <Banner type="warning">
            The OLS internal database format is&thinsp;
            <b>undocumented and subject to change at any time</b>. We therefore
            strongly recommend that the&thinsp;
            <b>OLS API and/or upstream OWL files from ontology vendors</b> are
            used to access ontology information rather than the OLS data dump,
            with the exception of the SSSOM mappings file which is provided with
            a standardised representation.
          </Banner>
          <p className="px-1 mb-2 text-justify">
            We recognise that the EBI OLS dataset can be useful in some cases to
            e.g. set up a local instance of OLS for development purposes, or to
            perform one-off analyses of the ontology corpus. To this end,
            snapshots of OLS datareleases can be downloaded from&thinsp;
            <a
              className="link-default"
              href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/"
              rel="noopener noreferrer"
              target="_blank"
            >
              https://ftp.ebi.ac.uk/pub/databases/spot/ols
            </a>
            . And the latest snapshot can be found at&thinsp;
            <a
              className="link-default"
              href="https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/"
              rel="noopener noreferrer"
              target="_blank"
            >
              https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest
            </a>
          </p>
          <DataTable columns={columns} data={data} />
        </div>
      </main>
    </Fragment>
  );
}

const columns: readonly Column[] = [
  {
    name: "Description",
    sortable: false,
    selector: (data) => <span>{data.description}</span>,
  },
  {
    name: "File",
    sortable: false,
    selector: (data) => (
      <a
        className="link-default"
        target="_blank"
        rel="noopener noreferrer"
        href={data.downloadLink}
      >
        {data.downloadLabel}
      </a>
    ),
  },
  {
    name: "Format",
    sortable: false,
    selector: (data) => <span>{data.format}</span>,
  },
];

const data: any[] = [
  {
    description:
      "OLS internal data representation of all loaded ontologies (~50 GB uncompressed)",
    downloadLabel: "ontologies.json.gz",
    downloadLink:
      "https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/ontologies.json.gz",
    format: "GZIP JSON",
  },
  {
    description:
      "As ontologies.json.gz but after running through the OLS linker to add references between ontologies and to external databases (~150 GB uncompressed)",
    downloadLabel: "ontologies_linked.json.gz",
    downloadLink:
      "https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/ontologies_linked.json.gz",
    format: "GZIP JSON",
  },
  {
    description:
      "The neo4j database generated from ontologies_linked.json by the OLS json2neo tool, after creating db indexes (Requires Neo4j community 4.4.9)",
    downloadLabel: "neo4j.tgz",
    downloadLink:
      "https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/neo4j.tgz",
    format: "Neo4j database",
  },
  {
    description:
      "The solr database generated from ontologies_linked.json by the OLS json2solr tool (Requires Solr 9.0.0)",
    downloadLabel: "solr.tgz",
    downloadLink:
      "https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/solr.tgz",
    format: "Solr core",
  },
  {
    description: "Mappings extracted from all ontologies in SSSOM TSV format",
    downloadLabel: "mappings_sssom.tsv.gz",
    downloadLink:
      "https://ftp.ebi.ac.uk/pub/databases/spot/ols/latest/mappings_sssom.tgz",
    format: "tab separated file",
  },
];
