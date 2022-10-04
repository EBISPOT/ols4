import { Box } from "@mui/material";
import { Fragment } from "react";
import Header from "../../components/Header";
import OntologyList from "./OntologyList";

export default function OntologiesPage() {
  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="ontologies" />
      <main className="container mx-auto">
        <Box>
          <OntologyList />
        </Box>
      </main>
    </Fragment>
  );
}
