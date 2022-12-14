import React from "react";
import { BrowserRouter, Route, Switch } from "react-router-dom";
import About from "./pages/About";
import Help from "./pages/help/HelpPage";
import Home from "./pages/home/Home";
import SearchResults from "./pages/home/SearchResults";
import EntityPage from "./pages/ontologies/EntityPage";
import OntologiesPage from "./pages/ontologies/OntologiesPage";
import OntologyPage from "./pages/ontologies/OntologyPage";

class App extends React.Component {
  render() {
    return (
      <BrowserRouter basename={process.env.PUBLIC_URL}>
        <Switch>
          <Route exact path={`/`} component={Home} />
          <Route exact path={`/home`} component={Home} />
          <Route
            exact
            path={`/home/search/:key`}
            component={(props: any) => (
              <SearchResults search={props.match.params.key} />
            )}
          />

          <Route exact path={`/ontologies`} component={OntologiesPage} />
          <Route
            exact
            path={`/ontologies/:id`}
            component={(props: any) => (
              <OntologyPage ontologyId={props.match.params.id} />
            )}
          />
          <Route
            exact
            path={`/ontologies/:id/classes/:iri`}
            component={(props: any) => (
              <EntityPage
                ontologyId={props.match.params.id}
                entityType="classes"
                entityIri={decodeURIComponent(
                  decodeURIComponent(props.match.params.iri)
                )}
              />
            )}
          />
          <Route
            exact
            path={`/ontologies/:id/properties/:iri`}
            component={(props: any) => (
              <EntityPage
                ontologyId={props.match.params.id}
                entityType="properties"
                entityIri={decodeURIComponent(
                  decodeURIComponent(props.match.params.iri)
                )}
              />
            )}
          />
          <Route
            exact
            path={`/ontologies/:id/individuals/:iri`}
            component={(props: any) => (
              <EntityPage
                ontologyId={props.match.params.id}
                entityType="individuals"
                entityIri={decodeURIComponent(
                  decodeURIComponent(props.match.params.iri)
                )}
              />
            )}
          />

          <Route exact path={`/help`} component={(props: any) => <Help />} />

          <Route exact path={`/about`} component={(props: any) => <About />} />
        </Switch>
      </BrowserRouter>
    );
  }
}

export default App;
