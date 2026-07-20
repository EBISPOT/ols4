import { Source } from "@mui/icons-material";
import { Fragment } from "react";
import { Link } from "react-router-dom";
import Header from "../components/Header";

interface McpToolParam {
  name: string;
  type: string;
  required: boolean;
  description?: string;
}

interface McpTool {
  name: string;
  description: string;
  params: McpToolParam[];
}

interface McpToolGroup {
  title: string;
  tools: McpTool[];
}

const mcpToolGroups: McpToolGroup[] = [
  {
    title: "Ontologies",
    tools: [
      {
        name: "listOntologies",
        description: "Get all ontologies from OLS.",
        params: [
          { name: "lang", type: "string", required: false, description: "Language code. Defaults to \"en\"." },
        ],
      },
    ],
  },
  {
    title: "Search (OpenAI-compatible)",
    tools: [
      {
        name: "search",
        description: "OpenAI compliant tool to search OLS for a query string.",
        params: [
          { name: "query", type: "string", required: true },
          { name: "includeObsoleteEntities", type: "boolean", required: false, description: "Whether to include obsolete entities in search results. Default is false." },
        ],
      },
      {
        name: "fetch",
        description: "OpenAI compliant tool to retrieve an entity from OLS by ID returned from the search tool. The ID must be of the format ontologyid+entityIri, e.g. go+http://purl.obolibrary.org/obo/GO_0008150.",
        params: [
          { name: "id", type: "string", required: true },
        ],
      },
    ],
  },
  {
    title: "Classes",
    tools: [
      {
        name: "searchClasses",
        description: "Search all classes in OLS for a query string.",
        params: [
          { name: "query", type: "string", required: true },
          { name: "ontologyId", type: "string", required: false },
          { name: "pageNum", type: "integer", required: false },
          { name: "pageSize", type: "integer", required: false },
          { name: "lang", type: "string", required: false },
          { name: "includeObsoleteEntities", type: "boolean", required: false, description: "Whether to include obsolete entities in search results. Default is false." },
        ],
      },
      {
        name: "getAncestors",
        description: "Get all ancestors for a class in OLS.",
        params: [
          { name: "ontologyId", type: "string", required: true },
          { name: "classIri", type: "string", required: true },
          { name: "pageNum", type: "integer", required: false },
          { name: "pageSize", type: "integer", required: false },
          { name: "lang", type: "string", required: false },
        ],
      },
      {
        name: "getChildren",
        description: "Get direct children of a class in OLS (one level down in the hierarchy).",
        params: [
          { name: "ontologyId", type: "string", required: true },
          { name: "classIri", type: "string", required: true },
          { name: "pageNum", type: "integer", required: false },
          { name: "pageSize", type: "integer", required: false },
          { name: "lang", type: "string", required: false },
        ],
      },
      {
        name: "getDescendants",
        description: "Get all descendants of a class in OLS.",
        params: [
          { name: "ontologyId", type: "string", required: true },
          { name: "classIri", type: "string", required: true },
          { name: "pageNum", type: "integer", required: false },
          { name: "pageSize", type: "integer", required: false },
          { name: "lang", type: "string", required: false },
        ],
      },
      {
        name: "searchClassesWithEmbeddingModel",
        description: "Search classes using semantic/embedding-based similarity. Uses vector embeddings to find semantically similar classes, which can find related concepts even when exact terms don't match. IMPORTANT: Call listEmbeddingModels first - only models with can_embed=true can be used for text search.",
        params: [
          { name: "query", type: "string", required: true, description: "The natural language query to search for semantically similar classes." },
          { name: "model", type: "string", required: true, description: "The embedding model to use. Must have can_embed=true from listEmbeddingModels." },
          { name: "ontologyId", type: "string", required: false, description: "Optional ontology ID to filter results." },
          { name: "includeCurations", type: "boolean", required: false, description: "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings." },
          { name: "pageNum", type: "integer", required: false },
          { name: "pageSize", type: "integer", required: false },
          { name: "lang", type: "string", required: false },
        ],
      },
      {
        name: "getSimilarClasses",
        description: "Find classes similar to a given class by IRI using pre-computed embeddings. Unlike searchClassesWithEmbeddingModel, this uses stored embeddings so any model from listEmbeddingModels can be used (can_embed not required).",
        params: [
          { name: "classIri", type: "string", required: true, description: "The IRI of the class to find similar classes for." },
          { name: "model", type: "string", required: true, description: "The embedding model to use. Any model from listEmbeddingModels works." },
          { name: "pageNum", type: "integer", required: false },
          { name: "pageSize", type: "integer", required: false },
          { name: "lang", type: "string", required: false },
        ],
      },
      {
        name: "getClassSimilarity",
        description: "Calculate the similarity score between two classes using their embeddings. Returns a value between 0 and 1, where 1 means identical. Any model from listEmbeddingModels can be used (can_embed not required).",
        params: [
          { name: "classIri1", type: "string", required: true, description: "The IRI of the first class." },
          { name: "classIri2", type: "string", required: true, description: "The IRI of the second class." },
          { name: "model", type: "string", required: true, description: "The embedding model to use. Any model from listEmbeddingModels works." },
        ],
      },
    ],
  },
  {
    title: "Embeddings",
    tools: [
      {
        name: "listEmbeddingModels",
        description: "List available embedding models for LLM-based semantic search. Call this first to discover which models can be used with the embedding-based search tools. Returns models with their names and whether they support live embedding.",
        params: [],
      },
      {
        name: "searchWithEmbeddingModel",
        description: "Search OLS entities using semantic/embedding-based similarity. Uses vector embeddings to find semantically similar entities, which can find related concepts even when exact terms don't match. IMPORTANT: Call listEmbeddingModels first - only models with can_embed=true can be used for text search.",
        params: [
          { name: "query", type: "string", required: true, description: "The natural language query to search for semantically similar entities." },
          { name: "model", type: "string", required: true, description: "The embedding model to use. Must have can_embed=true from listEmbeddingModels." },
          { name: "ontologyId", type: "string", required: false, description: "Optional ontology ID to filter results." },
          { name: "includeCurations", type: "boolean", required: false, description: "If true (default), include curated text-to-term mapping embeddings in the search. If false, only search label embeddings." },
          { name: "pageNum", type: "integer", required: false },
          { name: "pageSize", type: "integer", required: false },
        ],
      },
    ],
  },
];

export default function MCP() {
  document.title = "Ontology Lookup Service (OLS)";
  let mcpUrl = process.env.REACT_APP_APIURL+'api/mcp'
  // mcpUrl = mcpUrl.split('www.ebi.ac.uk').join('wwwdev.ebi.ac.uk')
  return (
    <Fragment>
      <Header section="mcp" />
      <main className="container mx-auto px-4 my-8">
          <div className="text-2xl font-bold my-6">MCP Server</div>
          <p className="mb-4">
            OLS provides a hosted <a href="https://modelcontextprotocol.io/docs/getting-started/intro">Model Context Protocol (MCP)</a> server which enables LLMs to access ontology terms and hierarchies.
            </p>
            <p className="mb-4">
            The MCP server for this instance of OLS is available at:
            </p>
            <ul>
                <li>
<span className="inline-flex items-center rounded bg-gray-100 px-2 py-1 font-mono text-sm text-gray-800">
  {mcpUrl}
  <button
    type="button"
    onClick={() =>
      navigator.clipboard.writeText(mcpUrl)
    }
    className="ml-2 text-gray-500 hover:text-gray-700 transition"
  >
                    <i className="icon icon-common icon-copy icon-spacer" />
  </button>
</span>


                </li>
            </ul>
	    <br/>
            <p className="mb-4">
            Please note that the type of this endpoint is <b>Streamable HTTP</b> and not legacy SSE.
            </p>

            <div className="text-2xl font-bold my-6">Available Tools</div>
            <p className="mb-4">
            The OLS MCP server exposes the following tools. Optional parameters may be omitted.
            </p>
            {mcpToolGroups.map((group) => (
              <div key={group.title} className="mb-8">
                <div className="text-lg font-semibold mb-2">{group.title}</div>
                <table className="table-auto w-full border-collapse border border-gray-300 mb-2">
                  <thead>
                    <tr className="bg-gray-100">
                      <th className="border border-gray-300 px-4 py-2 text-left">Tool</th>
                      <th className="border border-gray-300 px-4 py-2 text-left">Parameters</th>
                      <th className="border border-gray-300 px-4 py-2 text-left">Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {group.tools.map((tool) => (
                      <tr key={tool.name}>
                        <td className="border border-gray-300 px-4 py-2 align-top font-mono text-sm whitespace-nowrap">{tool.name}</td>
                        <td className="border border-gray-300 px-4 py-2 align-top">
                          {tool.params.length === 0 ? (
                            <span className="text-gray-500 text-sm">none</span>
                          ) : (
                            <ul className="list-disc list-inside">
                              {tool.params.map((param) => (
                                <li key={param.name} className="text-sm mb-1">
                                  <span className="font-mono">{param.name}</span>
                                  <span className="text-gray-500">
                                    {" "}({param.type}{param.required ? "" : ", optional"})
                                  </span>
                                  {param.description ? `: ${param.description}` : ""}
                                </li>
                              ))}
                            </ul>
                          )}
                        </td>
                        <td className="border border-gray-300 px-4 py-2 align-top text-sm">{tool.description}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}

            <p className="mb-4">
            For general project documentation, see the{" "}
            <a
              className="link-default"
              href="https://github.com/EBISPOT/ols4#readme"
              rel="noopener noreferrer"
              target="_blank"
            >
              OLS4 GitHub repository
            </a>
            .
            </p>
      </main>
    </Fragment>
  );
}
