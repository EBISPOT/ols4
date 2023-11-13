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
            <Link
              to={process.env.REACT_APP_APIURL + "swagger-ui/index.html"}
              target="_blank"
              rel="noopener noreferrer"
            >
              <button className="button-secondary font-bold self-center">
                <div className="flex gap-2">
                  <Source />
                  <div>OLS 4 Swagger Documentation</div>
                </div>
              </button>
            </Link>
          )
        }
        <div className="text-2xl font-bold my-6">OLS 3 Documentation</div>
          {
              <Link
                  to={`/ols3help`}
                  rel="noopener noreferrer"
                  target="_blank"
                  className="link-default"
              >
                  OLS 3 Documentation
              </Link>
          }
      </main>
    </Fragment>
  );
}

