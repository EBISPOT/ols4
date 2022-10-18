import { Fragment } from "react";
import Header from "../../components/Header";
import HelpSection from "./HelpSection";

export default function Help() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="help" />
      <main className="container mx-auto">
        <HelpSection title="Using the API"></HelpSection>
      </main>
    </Fragment>
  );
}
