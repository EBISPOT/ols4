import React from "react";
import { BrowserRouter, Route, Switch } from "react-router-dom";
import About from "./pages/About";
import Help from "./pages/help/HelpPage";
import Home from "./pages/home/Home";
import EntityPage from "./pages/ontologies/EntityPage";
import OntologiesPage from "./pages/ontologies/OntologiesPage";
import OntologyPage from "./pages/ontologies/OntologyPage";

class App extends React.Component {
  constructor(props: any) {
    super(props);
    this.state = {};
  }

  render() {
    return (
      <BrowserRouter basename={process.env.PUBLIC_URL}>
        <Switch>
          <Route exact path={`/`} component={Home} />

          <Route exact path={`/ontologies`} component={OntologiesPage} />

          <Route
            exact
            path={`/ontologies/:id`}
            component={(props: any) => (
              <OntologyPage ontologyId={props.match.params.id} />
            )}
          ></Route>

          <Route
            exact
            path={`/ontologies/:id/classes/:iri`}
            component={(props: any) => (
              <EntityPage
                ontologyId={props.match.params.id}
                entityType="classes"
                entityUri={decodeURIComponent(
                  decodeURIComponent(props.match.params.iri)
                )}
              />
            )}
          ></Route>

          <Route
            exact
            path={`/ontologies/:id/properties/:iri`}
            component={(props: any) => (
              <EntityPage
                ontologyId={props.match.params.id}
                entityType="properties"
                entityUri={decodeURIComponent(
                  decodeURIComponent(props.match.params.iri)
                )}
              />
            )}
          ></Route>

          <Route
            exact
            path={`/ontologies/:id/individuals/:iri`}
            component={(props: any) => (
              <EntityPage
                ontologyId={props.match.params.id}
                entityType="individuals"
                entityUri={decodeURIComponent(
                  decodeURIComponent(props.match.params.iri)
                )}
              />
            )}
          ></Route>

          <Route
            exact
            path={`/help`}
            component={(props: any) => <Help />}
          ></Route>

          <Route
            exact
            path={`/about`}
            component={(props: any) => <About />}
          ></Route>
        </Switch>
      </BrowserRouter>
    );
  }
}

export default App;
