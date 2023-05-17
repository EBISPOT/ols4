import React from "react";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useParams,
  useSearchParams,
} from "react-router-dom";
import Footer from "./components/Footer";
import About from "./pages/About";
import Downloads from "./pages/Downloads";
import Error from "./pages/Error";
import Help from "./pages/help/HelpPage";
import Home from "./pages/home/Home";
import OntologiesPage from "./pages/ontologies/OntologiesPage";
import OntologyPage from "./pages/ontologies/OntologyPage";
import EntityPage from "./pages/ontologies/entities/EntityPage";
import Search from "./pages/search/Search";

class App extends React.Component {
  render() {
    return (
      <BrowserRouter basename={process.env.PUBLIC_URL}>
        <Routes>
          <Route path={`*`} element={<Error />} />
          <Route path={`/error`} element={<Error />} />

          <Route path={`/`} element={<Home />} />
          <Route path={`/home`} element={<Home />} />
          <Route path={`/search/:search?`} element={<Search />} />

          <Route path={`/ontologies`} element={<OntologiesPage />} />
          <Route path={`/ontologies/:ontologyId`} element={<OntologyPage />} />
          <Route
            path={`/ontologies/:ontologyId/classes`}
            element={<EntityPage entityType="classes" />}
          />
          <Route
            path={`/ontologies/:ontologyId/classes/:entityIri`}
            element={<EntityPage entityType="classes" />}
          />
          <Route
            path={`/ontologies/:ontologyId/terms`}
            element={<RedirectTermsToClasses />}
          />
          <Route
            path={`/ontologies/:ontologyId/terms/:entityIri`}
            element={<RedirectTermToClass />}
          />
          <Route
            path={`/ontologies/:ontologyId/properties`}
            element={<EntityPage entityType="properties" />}
          />
          <Route
            path={`/ontologies/:ontologyId/properties/:entityIri`}
            element={<EntityPage entityType="properties" />}
          />
          <Route
            path={`/ontologies/:ontologyId/individuals`}
            element={<EntityPage entityType="individuals" />}
          />
          <Route
            path={`/ontologies/:ontologyId/individuals/:entityIri`}
            element={<EntityPage entityType="individuals" />}
          />

          <Route path={`/help`} element={<Help />} />
          <Route path={`/about`} element={<About />} />
          <Route path={`/downloads`} element={<Downloads />} />
        </Routes>
        <Footer />
      </BrowserRouter>
    );
  }
}

export default App;

function RedirectTermsToClasses() {
  let params = useParams();
  let [search] = useSearchParams();
  return (
    <Navigate
      to={{
        pathname: `/ontologies/${params.ontologyId}/classes`,
        search: search.toString(),
      }}
    />
  );
}

function RedirectTermToClass() {
  let params = useParams();
  let [search] = useSearchParams();
  return (
    <Navigate
      to={{
        pathname: `/ontologies/${
          params.ontologyId
        }/classes/${encodeURIComponent(
          encodeURIComponent(params.entityIri as string)
        )}`,
        search: search.toString(),
      }}
    />
  );
}
