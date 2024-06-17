import React, { Fragment, useEffect } from "react";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useAppDispatch, useAppSelector } from "./app/hooks";
import Footer from "./components/Footer";
import About from "./pages/About";
import Downloads from "./pages/Downloads";
import Error from "./pages/Error";
import Help from "./pages/Help";
import OLS3Help from "./pages/OLS3Help";
import Home from "./pages/home/Home";
import OntologiesPage from "./pages/ontologies/OntologiesPage";
import OntologyPage from "./pages/ontologies/OntologyPage";
import EntityPage from "./pages/ontologies/entities/EntityPage";
import { getEntity } from "./pages/ontologies/ontologiesSlice";
import Search from "./pages/search/Search";
import {Helmet} from "react-helmet";
import LoadingOverlay from "./components/LoadingOverlay";

class App extends React.Component {
  render() {
    return (
      <Fragment>
        <Helmet>
          <meta charSet="utf-8" />
          <title>Ontology Lookup Service (OLS)</title>
        </Helmet>
      <BrowserRouter basename={process.env.PUBLIC_URL!}>
        <Routes>
          <Route path={`*`} element={<Error />} />
          <Route path={`/error`} element={<Error />} />

          <Route path={`/`} element={<Home />} />
          <Route path={`/home`} element={<Home />} />
          <Route path={`/index`} element={<Home />} />
          <Route path={`/search`} element={<Search />} />

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
            element={<RedirectToClasses />}
          />
          <Route
            path={`/ontologies/:ontologyId/terms/:entityIri`}
            element={<RedirectToType />}
          />
          <Route
            path={`/ontologies/:ontologyId/entities`}
            element={<RedirectToClasses />}
          />
          <Route
            path={`/ontologies/:ontologyId/entities/:entityIri`}
            element={<RedirectToType />}
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
          <Route path={`/ols3help`} element={<OLS3Help />} />
          <Route path={`/about`} element={<About />} />
          <Route path={`/downloads`} element={<Downloads />} />
        </Routes>
        <Footer />
      </BrowserRouter>
      </Fragment>
    );
  }
}

export default App;

function RedirectToClasses() {
  const params = useParams();
  const [search] = useSearchParams();
  const entityIri = search.get("iri")
  if(entityIri) {
    return (
      <Navigate
        to={{
          pathname: `/ontologies/${params.ontologyId}/entities/${encodeURIComponent(encodeURIComponent(entityIri))}`,
          search: search.toString(),
        }}
      />
    );
  }
  return (
    <Navigate
      to={{
        pathname: `/ontologies/${params.ontologyId}/classes`,
        search: search.toString(),
      }}
    />
  );
}

function RedirectToType() {
  const dispatch = useAppDispatch();
  const entity = useAppSelector((state) => state.ontologies.entity);
  const params = useParams();
  const [search] = useSearchParams();

  useEffect(() => {
    if (params.ontologyId && params.entityIri) {
      dispatch(
        getEntity({
          ontologyId: params.ontologyId,
          entityIri: params.entityIri,
        })
      );
    }
  }, [dispatch, params]);

  if (
    entity &&
    entity.getTypePlural() !== "ontologies" &&
    params.ontologyId &&
    params.entityIri
  ) {
    return (
      <Navigate
        to={{
          pathname: `/ontologies/${
            params.ontologyId
          }/${entity.getTypePlural()}/${
            encodeURIComponent(params.entityIri as string)
          }`,
          search: search.toString(),
        }}
      />
    );
  } else {
    return <LoadingOverlay />
  }
}
