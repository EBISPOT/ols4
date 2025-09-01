import { Source } from "@mui/icons-material";
import { Fragment } from "react";
import { Link } from "react-router-dom";
import Header from "../components/Header";

export default function MCP() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="mcp" />
      <main className="container mx-auto px-4 my-8">
          <div className="text-2xl font-bold my-6">MCP Server</div>
          <p>
            OLS provides a hosted <a href="https://modelcontextprotocol.io/docs/getting-started/intro">Model Context Protocol (MCP)</a> server which can be used with LLMs to provide access to ontology terms and hierarchies.
            </p>
            <p>
            The MCP server for this instance of OLS is available at:
            </p>
            <ul>
                <li>
                    <code>{process.env.REACT_APP_APIURL}sse</code>
                </li>
            </ul>
      </main>
    </Fragment>
  );
}

