import React from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import About from "./pages/About";
import Help from "./pages/help/HelpPage";
import Home from "./pages/home/Home";
import Search from "./pages/search/Search";
import EntityPage from "./pages/ontologies/EntityPage";
import OntologiesPage from "./pages/ontologies/OntologiesPage";
import OntologyPage from "./pages/ontologies/OntologyPage";

class App extends React.Component {
  render() {
    return (
      <BrowserRouter basename={process.env.PUBLIC_URL}>
        <Routes>
          <Route path={`/`} element={<Home />} />
          <Route path={`/home`} element={<Home />} />
          <Route
            path={`/search/:search?`}
            element={<Search />}
          />

          <Route path={`/ontologies`} element={<OntologiesPage />} />
          <Route
            path={`/ontologies/:ontologyId`}
            element={<OntologyPage tab="classes" />}
          />
          <Route
            path={`/ontologies/:ontologyId/classes`}
            element={<OntologyPage tab='classes' />}
          />
          <Route
            path={`/ontologies/:ontologyId/classes/:entityIri`}
            element={<EntityPage entityType="classes" />}
          />
          <Route
            path={`/ontologies/:ontologyId/properties`}
            element={<OntologyPage tab='properties' />}
          />
          <Route
            path={`/ontologies/:ontologyId/properties/:entityIri`}
            element={<EntityPage entityType="properties" />}
          />
          <Route
            path={`/ontologies/:ontologyId/individuals`}
            element={<OntologyPage tab='individuals' />}
          />
          <Route
            path={`/ontologies/:ontologyId/individuals/:entityIri`}
            element={<EntityPage entityType="individuals" />}
          />

          <Route path={`/help`} element={<Help />} />
          <Route path={`/about`} element={<About />} />
        </Routes>
      </BrowserRouter>
    );
  }
}

export default App;
