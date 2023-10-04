import { Fragment } from "react";
import { Banner } from "../components/Banner";
import DataTable from "../components/DataTable";
import Header from "../components/Header";

export default function Help() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="help" />
      <main className="container mx-auto px-4 my-8">
        {
          // process.env.REACT_APP_APIURL && (
          //   <Link
          //     to={process.env.REACT_APP_APIURL + "swagger-ui/index.html"}
          //     target="_blank"
          //     rel="noopener noreferrer"
          //   >
          //     <button className="button-secondary font-bold self-center">
          //       <div className="flex gap-2">
          //         <Source />
          //         <div>Swagger Documentation</div>
          //       </div>
          //     </button>
          //   </Link>
          // )
        }
        <div className="text-2xl font-bold my-6">Documentation</div>
        <p className="mb-4">
          The OLS4 API should function identically to the OLS3 API. If you find
          any cases where the OLS4 API does not function the same as the OLS3
          API, please open an issue on our&thinsp;
          <a
            href={`${process.env.REACT_APP_SPOT_OLS4_REPO}/issues`}
            rel="noopener noreferrer"
            target="_blank"
            className="link-default"
          >
            issue tracker.
          </a>
        </p>
        <Banner type="info">
          Migrating to the OLS4 API should be as simple as replacing&thinsp;
          <strong>ols</strong> with <strong>ols4</strong> in the path. For
          example,
          <ul className="list-disc list-inside whitespace-nowrap">
            <li>
              <code className="break-words">
                http://www.ebi.ac.uk/ols/api/ontologies
              </code>
              &nbsp;(
              <i>before</i>)
            </li>
            <li>
              <code className="break-words">
                http://www.ebi.ac.uk/ols4/api/ontologies
              </code>
              &nbsp;(
              <i>after</i>)
            </li>
          </ul>
        </Banner>
        <p className="mb-2">
          We are currently in the process of making shiny new documentation
          pages, but in the meantime you can refer to the old documentation
          below.
        </p>
        <div className="text-2xl font-bold my-6">Overview</div>
        <div className="text-xl text-petrol-600 font-bold my-3">HTTP verbs</div>
        <p className="mb-2">This API supports the following HTTP verbs.</p>
        <DataTable
          columns={[
            {
              name: "Verb",
              sortable: false,
              selector: (data) => <span>{data.verb}</span>,
            },
            {
              name: "Usage",
              sortable: false,
              selector: (data) => <span>{data.usage}</span>,
            },
          ]}
          data={[
            {
              verb: <span className="text-code">GET</span>,
              usage: "Used to retrieve a resource",
            },
          ]}
        />
        <div className="text-xl text-petrol-600 font-bold my-3">
          HTTP status codes
        </div>
        <p className="mb-2">
          This API tries to adhere as closely as possible to standard HTTP and
          REST conventions in its use of HTTP status codes.
        </p>
        <DataTable
          columns={[
            {
              name: "Status code",
              sortable: false,
              selector: (data) => <span>{data.status}</span>,
            },
            {
              name: "Usage",
              sortable: false,
              selector: (data) => <span>{data.usage}</span>,
            },
          ]}
          data={[
            {
              status: <span className="text-code">200 OK</span>,
              usage: "The request completed successfully",
            },
            {
              status: <span className="text-code">400 Bad Request</span>,
              usage:
                "The request was malformed. The response body will include an error providing further information.",
            },
            {
              status: <span className="text-code">404 Not Found</span>,
              usage: "The requested resource did not exist",
            },
          ]}
        />
        <div className="text-xl text-petrol-600 font-bold my-3">Errors</div>
        <p className="mb-2">
          Whenever an error response (status code &gt;= 400) is returned, the
          body will contain a JSON object that describes the problem. The error
          object has the following structure:
        </p>
        <DataTable
          columns={columnsPathTypeDesc}
          data={[
            {
              path: "error",
              type: "String",
              description: (
                <>
                  The HTTP error that occurred, e.g.&thinsp;
                  <span className="text-code">Bad Request</span>
                </>
              ),
            },
            {
              path: "message",
              type: "String",
              description: "A description of the cause of the error",
            },
            {
              path: "path",
              type: "String",
              description: "The path to which the request was made",
            },
            {
              path: "status",
              type: "Number",
              description: (
                <>
                  The HTTP status code, e.g.&thinsp;
                  <span className="text-code">400</span>
                </>
              ),
            },
            {
              path: "timestamp",
              type: "Number",
              description:
                "The time, in milliseconds, at which the error occurred",
            },
          ]}
        />
        <p className="mb-2">
          For example, a request that attempts to apply a non-existent tag to a
          resource will produce a&thinsp;
          <span className="text-code">400 Bad Request</span> response:
        </p>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 404 Not Found
Content-Type: application/json;charset=UTF-8
Content-Length: 153
{
  "timestamp" : 1554117059039,
  "status" : 404,
  "error" : "Not Found",
  "message" : "Resource not found",
  "path" : "/ols4/api/ontologies/foobar"
}`}
          </pre>
        </Banner>
        <div className="text-xl text-petrol-600 font-bold my-3">Hypermedia</div>
        <p className="mb-2">
          This API uses hypermedia and resources include links to other
          resources in their responses. Responses are in&thinsp;
          <a
            className="link-default"
            target="_blank"
            rel="noopener noreferrer"
            href="http://stateless.co/hal_specification.html"
          >
            Hypertext Application Language (HAL)
          </a>
          &thinsp;format. Links can be found beneath the&thinsp;
          <span className="text-code">_links</span> key. Users of the API should
          not create URIs themselves, instead they should use the
          above-described links to navigate from resource to resource.
        </p>
        <div className="text-xl text-petrol-600 font-bold my-3">
          Listing resources
        </div>
        <p className="mb-2">
          Requests that return multiple resources will be paginated to 20 items
          by default. You can change the number of items returned using
          the&thinsp;<span className="text-code">size</span> parameter up to a
          maximum of 500 for a single request. The API also supports the&thinsp;
          <span className="text-code">page</span> parameter for accessing a
          specific page of items.
        </p>
        <div className="text-xl font-bold italic my-3">Paging resources</div>
        <p className="mb-2">
          Links will be provided in the response to navigate the resources.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          $ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies?page=1&size=1' -i
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Response structure</div>
        <DataTable
          columns={columnsPathTypeDesc}
          data={[
            {
              path: "_links",
              type: "Object",
              description: "Links to other resources",
            },
            {
              path: "_embedded",
              type: "Object",
              description: "The list of resources",
            },
            {
              path: "page.size",
              type: "Number",
              description: "The number of resources in this page",
            },
            {
              path: "page.totalElements",
              type: "Number",
              description: "The total number of resources",
            },
            {
              path: "page.totalPages",
              type: "Number",
              description: "The total number of pages",
            },
            {
              path: "page.number",
              type: "Number",
              description: "The page number",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Type: application/json
{
  "_links" : {
    "first" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies?page=0&size=1"
    },
    "prev" : {
      "href" :   "http://www.ebi.ac.uk/ols4/api/ontologies?page=0&size=1"
    },
    "self" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies"
    },
    "next" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies?page=2&size=1"
    },
    "last" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies?page=140&size=1"
    }
  },
  "_embedded" : {
    ...
  },
  "page" : {
    "size" : 1,
    "totalElements" : 141,
    "totalPages" : 141,
    "number" : 1
  }
}`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Links</div>
        <DataTable
          columns={columnsRelDesc}
          data={[
            {
              relation: "self",
              description: "This resource list",
            },
            {
              relation: "first",
              description: "The first page in the resource list",
            },
            {
              relation: "next",
              description: "The next page in the resource list",
            },
            {
              relation: "prev",
              description: "The previous page in the resource list",
            },
            {
              relation: "last",
              description: "The last page in the resource list",
            },
          ]}
        />
        <div className="text-2xl font-bold my-6">Resources</div>
        <div className="text-xl text-petrol-600 font-bold my-3">API</div>
        <p className="mb-2">
          The api endpoint provides the entry point into the service.
        </p>
        <div className="text-xl font-bold italic my-3">Accessing the API</div>
        <p className="mb-2">
          A&thinsp;<span className="text-code">GET</span> request is used to
          access the API
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          $ curl -L 'http://www.ebi.ac.uk/ols4/api/' -i -H 'Accept:
          application/json'
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Response structure</div>
        <DataTable
          columns={columnsPathTypeDesc}
          data={[
            {
              path: "_links",
              type: "Object",
              description: "Links to other resources",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Type: application/json
{
  "_links" : {
    "ontologies" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies"
    },
    "individuals" : {
      "href" :   "http://www.ebi.ac.uk/ols4/api/individuals"
    },
    "terms" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/terms"
    },
    "properties" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/properties"
    },
    "profile" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/profile"
    }
  }
}`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Links</div>
        <DataTable
          columns={columnsRelDesc}
          data={[
            {
              relation: "ontologies",
              description: "Link to the ontologies in OLS",
            },
            {
              relation: "terms",
              description: "Link to all the terms in OLS",
            },
            {
              relation: "properties",
              description: "Link to all the properties in OLS",
            },
            {
              relation: "individuals",
              description: "Link to all the individuals in OLS",
            },
            {
              relation: "profile",
              description: "ALPS is not currently supported",
            },
          ]}
        />
        <div className="text-xl text-petrol-600 font-bold my-3">Ontologies</div>
        <p className="mb-2">
          The Ontologies resources is used to list ontologies in OLS
        </p>
        <div className="text-xl font-bold italic my-3">Listing ontologies</div>
        <p className="mb-2">
          A&thinsp;<span className="text-code">GET</span> request will list all
          of the OLS ontologies.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          $ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies' -i -H 'Accept:
          application/json'
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Response structure</div>
        <p className="mb-2">
          The response is paginated where the individual ontology resources are
          in the&thinsp;<span className="text-code">_embedded.ontologies</span>
          &thinsp;field.
        </p>
        <div className="text-xl text-petrol-600 font-bold my-3">Ontology</div>
        <div className="text-xl font-bold italic my-3">
          Retrieve an ontology
        </div>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: "The ontology id in OLS",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          $ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/efo' -i -H
          'Accept: application/json'
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 3386

{
  "ontologyId" : "efo",
  "loaded" : "2019-04-01T10:31:42.870+0000",
  "updated" : "2019-04-01T10:31:42.870+0000",
  "status" : "LOADED",
  "message" : "",
  "version" : null,
  "numberOfTerms" : 22669,
  "numberOfProperties" : 328,
  "numberOfIndividuals" : 0,
  "config" : {
    "id" : "http://www.ebi.ac.uk/efo/efo.owl",
    "versionIri" : "http://www.ebi.ac.uk/efo/releases/2019-03-18/efo.owl",
    "title" : "Experimental Factor Ontology",
    "namespace" : "efo",
    "preferredPrefix" : "EFO",
    "description" : "The Experimental Factor Ontology (EFO) provides a systematic description of many experimental variables available in EBI databases, and for external projects such as the NHGRI GWAS catalogue. It combines parts of several biological ontologies, such as anatomy, disease and chemical compounds. The scope of EFO is to support the annotation, analysis and visualization of data handled by many groups at the EBI and as the core ontology for the Centre for Therapeutic Validation (CTTV)",
    "homepage" : "http://www.ebi.ac.uk/efo",
    "version" : "2.106",
    "mailingList" : "efo-users@lists.sourceforge.net",
    "creators" : [ ],
    "annotations" : {
      "license" : [ "www.apache.org/licenses/LICENSE-2.0" ],
      "creator" : [ "Gautier Koscielny", "Simon Jupp", "Jon Ison", "Laura Huerta Martinez", "Helen Parkinson", "Eleanor Williams", "James Malone", "Zoe May Pendlington", "Trish Whetzel", "Sirarat Sarntivijai", "Catherine Leroy", "Ele Holloway", "Tomasz Adamusiak", "Emma Kate Hastings", "Olamidipupo Ajigboye", "Paola Roncaglia", "Natalja Kurbatova", "Dani Welter", "Drashtti Vasant" ],
      "rights" : [ "Copyright [2014] EMBL - European Bioinformatics Institute Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. " ],
      "format-version" : [ "1.4" ],
      "comment" : [ "2019-03-18" ]
    },
    "fileLocation" : "http://www.ebi.ac.uk/efo/efo.owl",
    "reasonerType" : "OWL2",
    "oboSlims" : false,
    "labelProperty" : "http://www.w3.org/2000/01/rdf-schema#label",
    "definitionProperties" : [ "http://www.ebi.ac.uk/efo/definition" ],
    "synonymProperties" : [ "http://www.ebi.ac.uk/efo/alternative_term" ],
    "hierarchicalProperties" : [ "http://purl.obolibrary.org/obo/RO_0002202", "http://purl.obolibrary.org/obo/BFO_0000050" ],
    "baseUris" : [ "http://www.ebi.ac.uk/efo/EFO_" ],
    "hiddenProperties" : [ "http://www.ebi.ac.uk/efo/has_flag" ],
    "internalMetadataProperties" : [ "http://www.w3.org/2002/07/owl#versionInfo" ],
    "skos" : false
  },
  "_links" : {
    "self" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo"
    },
    "terms" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms"
    },
    "properties" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/properties"
    },
    "individuals" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/individuals"
    }
  }
}`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Links</div>
        <DataTable
          columns={columnsRelDesc}
          data={[
            {
              relation: "self",
              description: "This ontology",
            },
            {
              relation: "terms",
              description: "Paginated list of terms in the ontology",
            },
            {
              relation: "properties",
              description: "Paginated list of properties in the ontology",
            },
            {
              relation: "individuals",
              description: "Paginated list of individuals in the ontology",
            },
          ]}
        />
        <div className="text-xl font-bold italic my-3">Roots terms</div>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology&#125;/terms/roots
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: "The ontology id in OLS",
            },
          ]}
        />
        <div className="text-xl text-petrol-600 font-bold my-3">Terms</div>
        <p className="mb-2">
          The terms resources is used to list terms (or classes) in OLS from a
          particular ontology
        </p>
        <div className="text-xl font-bold italic my-3">
          Listing ontology terms
        </div>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology&#125;/terms
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: "The ontology id in OLS",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">
          Request parameters (optional)
        </div>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "iri",
              description:
                "Filter by IRI, when using IRI the result will always be one",
            },
            {
              parameter: "short_form",
              description:
                "Filter by IRI shortform, these values aren’t guaranteed to be unique e.g. GO_0098743",
            },
            {
              parameter: "obo_id",
              description:
                "Filter by OBO id. This is OBO style id that aren’t guaranteed to be unique within a given ontology e.g. GO:0098743",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          $ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms' -i -H
          'Accept: application/json'
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Response structure</div>
        <p className="mb-2">
          The response is paginated where the individual term resources are in
          the&thinsp;<span className="text-code">_embedded.terms</span> field.
        </p>
        <div className="text-xl text-petrol-600 font-bold my-3">Term</div>
        <div className="text-xl font-bold italic my-3">Retrieve a term</div>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology&#125;/terms/&#123;iri&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: "The ontology id in OLS",
            },
            {
              parameter: "iri",
              description:
                "The IRI of the terms, this value must be double URL encoded",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          $ curl -L
          'http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226'
          -i -H 'Accept: application/json'
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Disposition: inline;filename=f.txt
Content-Type: application/json
Content-Length: 3624

{
  "iri" : "http://purl.obolibrary.org/obo/GO_0043226",
  "label" : "GO_0043226",
  "description" : [ "Organized structure of distinctive morphology and function. Includes the nucleus, mitochondria, plastids, vacuoles, vesicles, ribosomes and the cytoskeleton, and prokaryotic structures such as anammoxosomes and pirellulosomes. Excludes the plasma membrane." ],
  "annotation" : {
    "hasDbXref" : [ "NIF_Subcellular:sao1539965131", "Wikipedia:Organelle" ],
    "hasOBONamespace" : [ "cellular_component" ],
    "id" : [ "GO:0043226" ]
  },
  "synonyms" : null,
  "ontology_name" : "go",
  "ontology_prefix" : "GO",
  "ontology_iri" : "http://purl.obolibrary.org/obo/go.owl",
  "is_obsolete" : false,
  "term_replaced_by" : null,
  "is_defining_ontology" : true,
  "has_children" : true,
  "is_root" : false,
  "short_form" : "GO_0043226",
  "obo_id" : "GO:0043226",
  "in_subset" : [ "goslim_chembl", "goslim_generic", "goslim_pir" ],
  "obo_definition_citation" : [ {
    "definition" : "Organized structure of distinctive morphology and function. Includes the nucleus, mitochondria, plastids, vacuoles, vesicles, ribosomes and the cytoskeleton, and prokaryotic structures such as anammoxosomes and pirellulosomes. Excludes the plasma membrane.",
    "oboXrefs" : [ {
      "database" : "GOC",
      "id" : "go_curators",
      "description" : null,
      "url" : null
    } ]
  } ],
  "obo_xref" : [ {
    "database" : "Wikipedia",
    "id" : "Organelle",
    "description" : null,
    "url" : "http://en.wikipedia.org/wiki/Organelle"
  }, {
    "database" : "NIF_Subcellular",
    "id" : "sao1539965131",
    "description" : null,
    "url" : "http://www.neurolex.org/wiki/sao1539965131"
  } ],
  "obo_synonym" : null,
  "_links" : {
    "self" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226"
    },
    "parents" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/parents"
    },
    "ancestors" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/ancestors"
    },
    "hierarchicalParents" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/hierarchicalParents"
    },
    "hierarchicalAncestors" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/hierarchicalAncestors"
    },
    "jstree" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/jstree"
    },
    "children" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/children"
    },
    "descendants" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/descendants"
    },
    "hierarchicalChildren" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/hierarchicalChildren"
    },
    "hierarchicalDescendants" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/hierarchicalDescendants"
    },
    "graph" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0043226/graph"
    }
  }
}`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Links</div>
        <DataTable
          columns={columnsRelDesc}
          data={[
            {
              relation: "self",
              description: "Link to this resource",
            },
            {
              relation: "parents",
              description: "Link to the direct parent resources for this term",
            },
            {
              relation: "hierarchicalParents",
              description:
                "Link to the direct hierarchical parent resources for this term. Hierarchical parents include is-a and other related parents, such as part-of/develops-from, that imply a hierarchical relationship",
            },
            {
              relation: "hierarchicalAncestors",
              description:
                "Link to all hierarchical ancestors (all parents’s parents) resources for this term. Hierarchical ancestors include is-a and other related parents, such as part-of/develops-from, that imply a hierarchical relationship",
            },
            {
              relation: "ancestors",
              description: "Link to all parent resources for this term",
            },
            {
              relation: "children",
              description:
                "Link to the direct children resources for this term",
            },
            {
              relation: "hierarchicalChildren",
              description:
                "Link to the direct hierarchical children resources for this term. Hierarchical children include is-a and other related children, such as part-of/develops-from, that imply a hierarchical relationship",
            },
            {
              relation: "hierarchicalDescendants",
              description:
                "Link to all hierarchical children resources for this term. Hierarchical children include is-a and other related children, such as part-of/develops-from, that imply a hierarchical relationship",
            },
            {
              relation: "descendants",
              description: "Link to all child resources for this term",
            },
            {
              relation: "jstree",
              description: "A JSON tree structure of the term hierarchy",
            },
            {
              relation: "graph",
              description:
                "A JSON graph structure of the immediately related nodes",
            },
          ]}
        />
        <div className="text-xl font-bold italic my-3">
          Parent/child relationships for terms
        </div>
        <p className="mb-2">
          The RESTful way to retrieve direct parent/child or all parent/child
          (ancestors/descendant) terms is to follow the _links URL on a given
          term. There are cases where it is convenient to request parent/child
          terms for a given term when you only have the URI or short id. For
          these cases we have implemented a convenient endpoint for these that
          takes a term id as a parameter. There are methods for all parent/child
          relationships as documented in the links sections for term resources.
        </p>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology&#125;/parents?id=&#123;id&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: "The ontology id in OLS",
            },
            {
              parameter: "id",
              description:
                "The id of the term, can be URI, short form or obo id",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample requests</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/go/parents?id=GO:0043226' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/go/children?id=GO:0043226' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/go/ancestors?id=GO:0043226' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/go/descendants?id=GO:0043226' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/go/hierarchicalDescendants?id=GO:0043226' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/go/hierarchicalAncestors?id=GO:0043226' -i -H 'Accept: application/json'
`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Type: application/json

{
  "_embedded": {
    "terms": [
      {
        "iri": "http://purl.obolibrary.org/obo/GO_0110165",
        "lang": "en",
        "description": [
          "A part of a cellular organism that is either an immaterial entity or a material entity with granularity above the level of a protein complex but below that of an anatomical system. Or, a substance produced by a cellular organism with granularity above the level of a protein complex."
        ],
        "synonyms": [],
        "annotation": {
          "created_by": [
            "kmv"
          ],
          "creation_date": [
            "2019-08-12T18:01:37Z"
          ],
          "has_obo_namespace": [
            "cellular_component"
          ],
          "id": [
            "GO:0110165"
          ]
        },
        "label": "cellular anatomical entity",
        "ontology_name": "go",
        "ontology_prefix": "GO",
        "ontology_iri": "http://purl.obolibrary.org/obo/go/extensions/go-plus.owl",
        "is_obsolete": false,
        "term_replaced_by": null,
        "is_defining_ontology": true,
        "has_children": true,
        "is_root": false,
        "short_form": "GO_0110165",
        "obo_id": "GO:0110165",
        "in_subset": [
          "goslim_pir"
        ],
        "obo_definition_citation": [
          {
            "definition": "A part of a cellular organism that is either an immaterial entity or a material entity with granularity above the level of a protein complex but below that of an anatomical system. Or, a substance produced by a cellular organism with granularity above the level of a protein complex.",
            "oboXrefs": [
              {
                "database": "GOC",
                "id": "kmv",
                "description": null,
                "url": null
              }
            ]
          }
        ],
        "obo_xref": null,
        "obo_synonym": null,
        "is_preferred_root": false,
        "_links": {
          "self": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165?lang=en"
          },
          "parents": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/parents"
          },
          "ancestors": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/ancestors"
          },
          "hierarchicalParents": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/hierarchicalParents"
          },
          "hierarchicalAncestors": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/hierarchicalAncestors"
          },
          "jstree": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/jstree"
          },
          "children": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/children"
          },
          "descendants": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/descendants"
          },
          "hierarchicalChildren": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/hierarchicalChildren"
          },
          "hierarchicalDescendants": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/hierarchicalDescendants"
          },
          "graph": {
            "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FGO_0110165/graph"
          }
        }
      }
    ]
  },
  "_links": {
    "self": {
      "href": "https://www.ebi.ac.uk/ols4/api/ontologies/go/parents?id=GO:0043226&page=0&size=20"
    }
  },
  "page": {
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "number": 0
  }
}`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">Other related terms</div>
        <p className="mb-2">
          In cases where a term has a direct relation to another term (single
          existential to a named class in OBO), for example a "part of"
          relation, the related terms can be accessed directly with this API.
        </p>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology_id&#125;/terms/&#123;term_iri&#125;/&#123;property_iri&#125;
        </code>
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/uberon/terms/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FUBERON_0000016/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FBFO_0000050' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">
          Term matching across ontologies
        </div>
        <p className="mb-2">
          It is possible to search for terms across ontologies using the
          requests and responses as defined in this section.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample requests</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms?iri=http://www.ebi.ac.uk/efo/EFO_0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms?short_form=EFO_0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms?obo_id=EFO:0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms?id=EFO:0000001' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Disposition: inline;filename=f.txt
Content-Type: application/json
Content-Length: 3022

{
  "_embedded" : {
    "terms" : [ {
      "iri" : "http://www.ebi.ac.uk/efo/EFO_0000001",
      "label" : "EFO_0000001",
      "description" : null,
      "annotation" : {
        "IAO_0000115" : [ "An experimental factor in Array Express which are essentially the variable aspects of an experiment design which can be used to describe an experiment, or set of experiments, in an increasingly detailed manner. This upper level class is really used to give a root class from which applications can rely on and not be tied to upper ontology classses which do change." ],
        "IAO_0000117" : [ "Helen Parkinson", "James Malone", "Tomasz Adamusiak", "Jie Zheng" ],
        "MO_definition_citation" : [ "MO:10" ],
        "bioportal_provenance" : [ "ExperimentalFactor[accessedResource: MO_10][accessDate: 05-04-2011]" ],
        "comment" : [ "Concept naming convention is lower case natural naming with spaces, when necessary captials should be used, for example disease factor, HIV, breast carcinoma, Ewing's sarcoma" ],
        "created_by" : [ "Helen Parkinson", "James Malone", "Tomasz Adamusiak" ],
        "organizational_class" : [ "true" ]
      },
      "synonyms" : [ "ExperimentalFactor" ],
      "ontology_name" : "efo",
      "ontology_prefix" : "EFO",
      "ontology_iri" : "http://www.ebi.ac.uk/efo/efo.owl",
      "is_obsolete" : false,
      "term_replaced_by" : null,
      "is_defining_ontology" : true,
      "has_children" : true,
      "is_root" : true,
      "short_form" : "EFO_0000001",
      "obo_id" : "EFO:0000001",
      "in_subset" : null,
      "obo_definition_citation" : null,
      "obo_xref" : null,
      "obo_synonym" : null,
      "_links" : {
        "self" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001"
        },
        "children" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/children"
        },
        "descendants" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/descendants"
        },
        "hierarchicalChildren" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/hierarchicalChildren"
        },
        "hierarchicalDescendants" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/hierarchicalDescendants"
        },
        "graph" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/graph"
        }
      }
    } ]
  },
  "_links" : {
    "self" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001"
    }
  },
  "page" : {
    "size" : 20,
    "totalElements" : 1,
    "totalPages" : 1,
    "number" : 0
  }
}`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">
          Terms based on defining ontology
        </div>
        <p className="mb-2">
          Where users are interested in a particular ontology, they may also be
          interested in retrieving only terms from that ontology. The requests
          and reponses in this section defines an API for achieving this.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample requests</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms/findByIdAndIsDefiningOntology/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms/findByIdAndIsDefiningOntology?iri=http://www.ebi.ac.uk/efo/EFO_0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms/findByIdAndIsDefiningOntology?short_form=EFO_0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms/findByIdAndIsDefiningOntology?obo_id=EFO:0000001' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/terms/findByIdAndIsDefiningOntology?id=EFO:0000001' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Disposition: inline;filename=f.txt
Content-Type: application/json
Content-Length: 3052

{
  "_embedded" : {
    "terms" : [ {
      "iri" : "http://www.ebi.ac.uk/efo/EFO_0000001",
      "label" : "EFO_0000001",
      "description" : null,
      "annotation" : {
        "IAO_0000115" : [ "An experimental factor in Array Express which are essentially the variable aspects of an experiment design which can be used to describe an experiment, or set of experiments, in an increasingly detailed manner. This upper level class is really used to give a root class from which applications can rely on and not be tied to upper ontology classses which do change." ],
        "IAO_0000117" : [ "Helen Parkinson", "James Malone", "Tomasz Adamusiak", "Jie Zheng" ],
        "MO_definition_citation" : [ "MO:10" ],
        "bioportal_provenance" : [ "ExperimentalFactor[accessedResource: MO_10][accessDate: 05-04-2011]" ],
        "comment" : [ "Concept naming convention is lower case natural naming with spaces, when necessary captials should be used, for example disease factor, HIV, breast carcinoma, Ewing's sarcoma" ],
        "created_by" : [ "Helen Parkinson", "James Malone", "Tomasz Adamusiak" ],
        "organizational_class" : [ "true" ]
      },
      "synonyms" : [ "ExperimentalFactor" ],
      "ontology_name" : "efo",
      "ontology_prefix" : "EFO",
      "ontology_iri" : "http://www.ebi.ac.uk/efo/efo.owl",
      "is_obsolete" : false,
      "term_replaced_by" : null,
      "is_defining_ontology" : true,
      "has_children" : true,
      "is_root" : true,
      "short_form" : "EFO_0000001",
      "obo_id" : "EFO:0000001",
      "in_subset" : null,
      "obo_definition_citation" : null,
      "obo_xref" : null,
      "obo_synonym" : null,
      "_links" : {
        "self" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001"
        },
        "children" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/children"
        },
        "descendants" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/descendants"
        },
        "hierarchicalChildren" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/hierarchicalChildren"
        },
        "hierarchicalDescendants" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/hierarchicalDescendants"
        },
        "graph" : {
          "href" : "http://www.ebi.ac.uk/ols4/api/ontologies/efo/terms/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001/graph"
        }
      }
    } ]
  },
  "_links" : {
    "self" : {
      "href" : "http://www.ebi.ac.uk/ols4/api/terms/findByIdAndIsDefiningOntology/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000001"
    }
  },
  "page" : {
    "size" : 20,
    "totalElements" : 1,
    "totalPages" : 1,
    "number" : 0
  }
}`}
          </pre>
        </Banner>
        <div className="text-xl text-petrol-600 font-bold my-3">
          Properties and individuals
        </div>
        <p className="mb-2">
          You can access property (relationships) and ontology individuals
          (instances) following similar methods to terms.
        </p>
        <div className="text-xl font-bold italic my-3">Retrieve a property</div>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology&#125;/properties/&#123;iri&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: "The ontology id in OLS",
            },
            {
              parameter: "iri",
              description:
                "The IRI of the relation, this value must be double URL encoded",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/efo/properties/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FBFO_0000050' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Type: application/json

{
  "iri": "http://purl.obolibrary.org/obo/BFO_0000050",
  "lang": "en",
  "description": [
    "a core relation that holds between a part and its whole"
  ],
  "synonyms": [
    "part of"
  ],
  "annotation": {
    "EFO_URI": [
      "http://www.ebi.ac.uk/efo/part_of"
    ],
    "alternative label": [
      "part_of"
    ],
    "editor note": [
      "Everything is part of itself. Any part of any part of a thing is itself part of that thing. Two distinct things cannot be part of each other.",
      "Occurrents are not subject to change and so parthood between occurrents holds for all the times that the part exists. Many continuants are subject to change, so parthood between continuants will only hold at certain times, but this is difficult to specify in OWL. See http://purl.obolibrary.org/obo/ro/docs/temporal-semantics/",
      "Occurrents are not subject to change and so parthood between occurrents holds for all the times that the part exists. Many continuants are subject to change, so parthood between continuants will only hold at certain times, but this is difficult to specify in OWL. See https://code.google.com/p/obo-relations/wiki/ROAndTime",
      "Parthood requires the part and the whole to have compatible classes: only an occurrent can be part of an occurrent; only a process can be part of a process; only a continuant can be part of a continuant; only an independent continuant can be part of an independent continuant; only an immaterial entity can be part of an immaterial entity; only a specifically dependent continuant can be part of a specifically dependent continuant; only a generically dependent continuant can be part of a generically dependent continuant. (This list is not exhaustive.)\\n\\nA continuant cannot be part of an occurrent: use 'participates in'. An occurrent cannot be part of a continuant: use 'has participant'. A material entity cannot be part of an immaterial entity: use 'has location'. A specifically dependent continuant cannot be part of an independent continuant: use 'inheres in'. An independent continuant cannot be part of a specifically dependent continuant: use 'bearer of'."
    ],
    "editor preferred term": [
      "is part of" 
    ],
    "example of usage": [
      "my brain is part of my body (continuant parthood, two material entities)",
      "my stomach cavity is part of my stomach (continuant parthood, immaterial entity is part of material entity)",
      "this day is part of this year (occurrent parthood)"
    ],
    "has_dbxref": [
      "BFO:0000050",
      "OBO_REL:part_of"
    ],
    "has_obo_namespace": [
      "external",
      "quality",
      "relationship",
      "spatial",
      "uberon"
    ],
    "id": [
      "part_of"
    ],
    "is homeomorphic for": [
      "http://purl.obolibrary.org/obo/BFO_0000002",
      "http://purl.obolibrary.org/obo/BFO_0000003",
      "http://purl.obolibrary.org/obo/BFO_0000004",
      "http://purl.obolibrary.org/obo/BFO_0000017",
      "http://purl.obolibrary.org/obo/BFO_0000019",
      "http://purl.obolibrary.org/obo/BFO_0000020",
      "http://purl.obolibrary.org/obo/BFO_0000031"
    ],
    "seeAlso": [
      "http://ontologydesignpatterns.org/wiki/Community:Parts_and_Collections",
      "http://ontologydesignpatterns.org/wiki/Submissions:PartOf"
    ],
    "shorthand": [
      "part_of"
    ],
    "temporal interpretation": [
      "http://purl.obolibrary.org/obo/RO_0001901"
    ]
  },
  "label": "part of",
  "ontology_name": "efo",
  "ontology_prefix": "EFO",
  "ontology_iri": "http://www.ebi.ac.uk/efo/efo.owl",
  "is_obsolete": false,
  "is_defining_ontology": false,
  "has_children": false,
  "is_root": true,
  "short_form": "BFO_0000050",
  "obo_id": "BFO:0000050",
  "_links": {
    "self": {
      "href": "https://www.ebi.ac.uk/ols4/api/ontologies/efo/properties/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FBFO_0000050?lang=en"
    }
  }
}`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">
          Property matching across ontologies
        </div>
        <p className="mb-2">
          Similar to terms, properties can be found based on id. See Term
          matching across ontologies. Here we only provide 2 examples: Both
          examples retrieve a property based on its IRI. The first illustrates
          finding a property where the IRI is specified as part of the path and
          the second illustrates finding a property where the IRI is specified
          as a parameter.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample requests</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/properties/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000784' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/properties?iri=http://www.ebi.ac.uk/efo/EFO_0000784' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">
          Properties based on defining ontology
        </div>
        <p className="mb-2">
          Similar to terms, users may want to only access properties that are
          related to a particular ontology. See Terms based on defining
          ontology.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample requests</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/properties/findByIdAndIsDefiningOntology/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0000784' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/properties/findByIdAndIsDefiningOntology?iri=http://www.ebi.ac.uk/efo/EFO_0000784' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">
          Retrieve an individual
        </div>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/ontologies/&#123;ontology&#125;/individuals/&#123;iri&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: "The ontology id in OLS",
            },
            {
              parameter: "iri",
              description:
                "The IRI of the individual, this value must be double URL encoded",
            },
          ]}
        />
        <div className="text-lg text-petrol-600 my-3">Sample request</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/ontologies/iao/individuals/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FIAO_0000002' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-lg text-petrol-600 my-3">Sample response</div>
        <Banner type="code">
          <pre>
            {`HTTP/1.1 200 OK
Content-Type: application/json

{
  "iri": "http://purl.obolibrary.org/obo/IAO_0000002",
  "lang": "en",
  "description": [],
  "synonyms": [],
  "annotation": {
    "editor preferred term": [
      "example to be eventually removed"
    ]
  },
  "type": null,
  "label": "example to be eventually removed",
  "ontology_name": "iao",
  "ontology_prefix": "IAO",
  "ontology_iri": "http://purl.obolibrary.org/obo/iao.owl",
  "is_obsolete": false,
  "is_defining_ontology": false,
  "has_children": false,
  "is_root": false,
  "short_form": "IAO_0000002",
  "obo_id": "IAO:0000002",
  "in_subset": null,
  "_links": {
    "self": {
      "href": "https://www.ebi.ac.uk/ols4/api/ontologies/iao/individuals/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FIAO_0000002?lang=en"
    },
    "types": {
      "href": "https://www.ebi.ac.uk/ols4/api/ontologies/iao/individuals/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FIAO_0000002/types"
    },
    "alltypes": {
      "href": "https://www.ebi.ac.uk/ols4/api/ontologies/iao/individuals/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FIAO_0000002/alltypes"
    },
    "jstree": {
      "href": "https://www.ebi.ac.uk/ols4/api/ontologies/iao/individuals/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FIAO_0000002/jstree"
    }
  }
}`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">
          Individual matching across ontologies
        </div>
        <p className="mb-2">
          Similar to terms, individuals can be found based on id. See Term
          matching across ontologies. Here we only provide 2 examples: Both
          examples retrieve an individual based on its IRI. The first
          illustrates finding an individual where the IRI is specified as part
          of the path and the second illustrates finding an individual where the
          IRI is specified as a parameter.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample requests</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/individuals/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FIAO_0000125' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/individuals?iri=http://purl.obolibrary.org/obo/IAO_0000125' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-xl font-bold italic my-3">
          Individual based on defining ontology
        </div>
        <p className="mb-2">
          Similar to terms, users may want to only access individuals that are
          related to a particular ontology. See Terms based on defining
          ontology.
        </p>
        <div className="text-lg text-petrol-600 my-3">Sample requests</div>
        <Banner type="code">
          <pre>
            {`$ curl -L 'http://www.ebi.ac.uk/ols4/api/individuals/findByIdAndIsDefiningOntology/http%253A%252F%252Fpurl.obolibrary.org%252Fobo%252FRO_0001901' -i -H 'Accept: application/json'
$ curl -L 'http://www.ebi.ac.uk/ols4/api/individuals/findByIdAndIsDefiningOntology?iri=http://purl.obolibrary.org/obo/RO_0001901' -i -H 'Accept: application/json'`}
          </pre>
        </Banner>
        <div className="text-xl text-petrol-600 font-bold my-3">Search</div>
        <div className="text-xl font-bold italic my-3">Search terms</div>
        <p className="mb-2">
          The search API is independent of the REST API and supports free text
          search over the ontologies. The default search is across all textual
          fields in the ontology, but results are ranked towards hits in labels,
          then synonyms, then definitions, then annotations.
        </p>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/search?q=&#123;q&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "q",
              description:
                "The terms to search. By default the search is performed over term labels, synonyms, descriptions, identifiers and annotation properties.",
            },
          ]}
        />
        <p className="mb-2">
          You can override the fields that are searched by supplying a&thinsp;
          <span className="text-code">queryFields</span> argument. For example,
          to query on labels and synonyms use.
        </p>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/search?q=&#123;q&#125;&queryFields=label,synonym
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "queryFields",
              description:
                "Specifcy the fields to query, the defaults are {label, synonym, description, short_form, obo_id, annotations, logical_description, iri}",
            },
            {
              parameter: "ontology",
              description: (
                <>
                  Restrict a search to a set of ontologies e.g.&thinsp;
                  <span className="text-code">ontology=uberon,ma</span>
                </>
              ),
            },
            {
              parameter: "type",
              description:
                "Restrict a search to an entity type, one of {class,property,individual,ontology}",
            },
            {
              parameter: "slim",
              description:
                "Restrict a search to an particular set of slims by name",
            },
            {
              parameter: "fieldList",
              description:
                "Specifcy the fields to return, the defaults are {iri,label,short_form,obo_id,ontology_name,ontology_prefix,description,type}",
            },
            {
              parameter: "exact",
              description: "Set to true for exact matches",
            },
            {
              parameter: "groupField",
              description: "Set to true to group results by unique id (IRI)",
            },
            {
              parameter: "obsoletes",
              description:
                "Set to true to include obsoleted terms in the results",
            },
            {
              parameter: "local",
              description:
                "Set to true to only return terms that are in a defining ontology e.g. Only return matches to gene ontology terms in the gene ontology, and exclude ontologies where those terms are also referenced",
            },
            {
              parameter: "childrenOf",
              description:
                "You can restrict a search to children of a given term. Supply a list of IRI for the terms that you want to search under",
            },
            {
              parameter: "allChildrenOf",
              description:
                "You can restrict a search to all children of a given term. Supply a list of IRI for the terms that you want to search under (subclassOf/is-a plus any hierarchical/transitive properties like 'part of' or 'develops from')",
            },
            {
              parameter: "rows",
              description: "How many results per page",
            },
            {
              parameter: "start",
              description: "The results page number",
            },
          ]}
        />
        <div className="text-xl font-bold italic my-3">Select terms</div>
        <p className="mb-2">
          We provide an additional search endopint that is designed specifically
          for selecting ontology terms. This has been tuned specifically to
          support applications such as autocomplete.
        </p>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/select?ontology=&#123;ontology&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: (
                <>
                  Restrict a search to a set of ontologies e.g.&thinsp;
                  <span className="text-code">ontology=uberon,ma</span>
                </>
              ),
            },
            {
              parameter: "type",
              description:
                "Restrict a search to an entity type, one of {class,property,individual,ontology}",
            },
            {
              parameter: "slim",
              description:
                "Restrict a search to an particular set of slims by name",
            },
            {
              parameter: "fieldList",
              description:
                "Specifcy the fields to return, the defaults are {iri,label,short_form,obo_id,ontology_name,ontology_prefix,description,type}",
            },
            {
              parameter: "obsoletes",
              description:
                "Set to true to include obsoleted terms in the results",
            },
            {
              parameter: "local",
              description:
                "Set to true to only return terms that are in a defining ontology e.g. Only return matches to gene ontology terms in the gene ontology, and exclude ontologies where those terms are also referenced",
            },
            {
              parameter: "childrenOf",
              description:
                "You can restrict a search to all children of a given term. Supply a list of IRI for the terms that you want to search under (subclassOf/is-a relation only)",
            },
            {
              parameter: "allChildrenOf",
              description:
                "You can restrict a search to all children of a given term. Supply a list of IRI for the terms that you want to search under (subclassOf/is-a plus any hierarchical/transitive properties like 'part of' or 'develops from')",
            },
            {
              parameter: "rows",
              description: "How many results per page",
            },
            {
              parameter: "start",
              description: "The results page number",
            },
          ]}
        />
        <div className="text-xl font-bold italic my-3">Suggest terms</div>
        <p className="mb-2">
          We also provide a generic suggester endpoint. This endpoint aims to
          provide traditional autosuggest based on all the vocabulary in OLS
          (all class labels or synonyms). All results from this endpoint are
          unique and are not coupled to any particular ontology, however,
          searches can be restricted by ontology.
        </p>
        <div className="text-lg text-petrol-600 my-3">Request structure</div>
        <code className="break-words">
          GET&nbsp;/api/suggest?ontology=&#123;ontology&#125;
        </code>
        <DataTable
          columns={columnsParamDesc}
          data={[
            {
              parameter: "ontology",
              description: (
                <>
                  Restrict a search to a set of ontologies e.g.&thinsp;
                  <span className="text-code">ontology=uberon,ma</span>
                </>
              ),
            },
            {
              parameter: "rows",
              description: "How many results per page",
            },
            {
              parameter: "start",
              description: "The results page number",
            },
          ]}
        />
      </main>
    </Fragment>
  );
}

const columnsPathTypeDesc = [
  {
    name: "Path",
    sortable: false,
    selector: (data: any) => <span>{data.path}</span>,
  },
  {
    name: "Type",
    sortable: false,
    selector: (data: any) => <span>{data.type}</span>,
  },
  {
    name: "Description",
    sortable: false,
    selector: (data: any) => <span>{data.description}</span>,
  },
];

const columnsRelDesc = [
  {
    name: "Relation",
    sortable: false,
    selector: (data: any) => <span>{data.relation}</span>,
  },
  {
    name: "Description",
    sortable: false,
    selector: (data: any) => <span>{data.description}</span>,
  },
];

const columnsParamDesc = [
  {
    name: "Parameter",
    sortable: false,
    selector: (data: any) => <span>{data.parameter}</span>,
  },
  {
    name: "Description",
    sortable: false,
    selector: (data: any) => <span>{data.description}</span>,
  },
];
