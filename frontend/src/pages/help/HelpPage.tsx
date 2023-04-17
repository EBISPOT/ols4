import { Fragment } from "react";
import Header from "../../components/Header";
import HelpSection from "./HelpSection";
import {Link} from "react-router-dom";
import {Source} from "@mui/icons-material";

export default function Help() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="help" />
      <main className="container mx-auto">
        <HelpSection title="Using the API"></HelpSection>
          {process.env.REACT_APP_APIURL && (
              <Link to={process.env.REACT_APP_APIURL + "swagger-ui/index.html"} target="_blank">
                  <button className="button-secondary font-bold self-center">
                      <div className="flex gap-2">
                          <Source />
                          <div>Swagger Documentation</div>
                      </div>
                  </button>
              </Link>
          )}
      </main>
    </Fragment>
  );
}
