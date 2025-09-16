import { Source } from "@mui/icons-material";
import { Fragment } from "react";
import { Link } from "react-router-dom";
import Header from "../components/Header";

export default function MCP() {
  document.title = "Ontology Lookup Service (OLS)";
  let mcpUrl = process.env.REACT_APP_APIURL+'api/mcp/sse'
  mcpUrl = mcpUrl.split('www.ebi.ac.uk').join('wwwdev.ebi.ac.uk')
  return (
    <Fragment>
      <Header section="mcp" />
      <main className="container mx-auto px-4 my-8">
          <div className="text-2xl font-bold my-6">MCP Server</div>
          <p className="mb-4">
            OLS provides a hosted <a href="https://modelcontextprotocol.io/docs/getting-started/intro">Model Context Protocol (MCP)</a> server which can be used with LLMs to provide access to ontology terms and hierarchies.
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
      navigator.clipboard.writeText(`${process.env.REACT_APP_APIURL}api/mcp/sse`)
    }
    className="ml-2 text-gray-500 hover:text-gray-700 transition"
  >
                    <i className="icon icon-common icon-copy icon-spacer" />
  </button>
</span>


                </li>
            </ul>
      </main>
    </Fragment>
  );
}

