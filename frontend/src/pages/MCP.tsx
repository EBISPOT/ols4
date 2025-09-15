import { Source } from "@mui/icons-material";
import { Fragment } from "react";
import { Link } from "react-router-dom";
import Header from "../components/Header";
import ConfigSnippet from "../components/ConfigSnippet";

export default function MCP() {
  document.title = "Ontology Lookup Service (OLS)";
  let mcpUrl = process.env.REACT_APP_APIURL+'api/mcp/sse'
  mcpUrl = mcpUrl.split('www.ebi.ac.uk').join('wwwdev.ebi.ac.uk')
  
  // Configuration examples for different tools
  const cursorConfig = `{
  "mcpServers": {
    "ols": {
      "command": "npx",
      "args": ["@modelcontextprotocol/server-fetch"],
      "env": {
        "FETCH_BASE_URL": "${process.env.REACT_APP_APIURL}api/mcp/sse"
      }
    }
  }
}`;

  const claudeDesktopConfig = `{
  "mcpServers": {
    "ols": {
      "command": "npx",
      "args": ["@modelcontextprotocol/server-fetch"],
      "env": {
        "FETCH_BASE_URL": "${process.env.REACT_APP_APIURL}api/mcp/sse"
      }
    }
  }
}`;

  const vscodeConfig = `{
  "mcp.servers": {
    "ols": {
      "command": "npx",
      "args": ["@modelcontextprotocol/server-fetch"],
      "env": {
        "FETCH_BASE_URL": "${process.env.REACT_APP_APIURL}api/mcp/sse"
      }
    }
  }
}`;

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
            
            <div className="mt-8">
              <h2 className="text-xl font-bold mb-4">Configuration Examples</h2>
              <p className="mb-6 text-gray-600">
                Below are configuration examples for setting up the OLS MCP server with different tools:
              </p>
              
              <ConfigSnippet
                title="Cursor"
                description="Add this configuration to your Cursor settings (Settings > General > MCP Servers)"
                config={cursorConfig}
              />
              
              <ConfigSnippet
                title="Claude Desktop"
                description="Add this to your Claude Desktop configuration file"
                config={claudeDesktopConfig}
              />
              
              <ConfigSnippet
                title="VS Code"
                description="Add this to your VS Code settings.json file"
                config={vscodeConfig}
              />
            </div>
      </main>
    </Fragment>
  );
}

