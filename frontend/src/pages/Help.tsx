import { Source } from "@mui/icons-material";
import { Fragment } from "react";
import { Link } from "react-router-dom";
import Header from "../components/Header";

export default function Help() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="help" />
      <main className="container mx-auto px-4 my-8">
          <div className="text-2xl font-bold my-6">OLS 4 Documentation</div>
        {

          process.env.REACT_APP_APIURL && (
                <div className="iframe-container">
                    <iframe
                        src={process.env.REACT_APP_APIURL + "swagger-ui/index.html"}
                        title="Swagger Documentation"
                        style={{width: '100%', height: '1000px', border: 'none', overflow: 'visible'}}
                    />
                </div>
            )
        }
      </main>
    </Fragment>
  );
}

