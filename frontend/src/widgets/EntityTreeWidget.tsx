import React, { useEffect, Fragment } from "react";
import { Provider } from "react-redux";
import { store } from "../app/store"
import { getOntology } from "../pages/ontologies/ontologiesSlice";
import { useAppDispatch, useAppSelector } from "../app/hooks";
import EntityTree from "../pages/ontologies/entities/EntityTree"


export interface EntityTreeWidgetProps {
  iri?: string;
  ontologyId: string;
  apiUrl: string;
}

export function EntityTreeWidget(props: EntityTreeWidgetProps) {

  return (
    <Provider store={store}>
      <EntityTreeWidgetInner
        ontologyId={props.ontologyId}
        iri={props.iri}
        apiUrl={props.apiUrl}
      />
    </Provider>
  );
};

function EntityTreeWidgetInner(props: EntityTreeWidgetProps) {
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const dispatch = useAppDispatch();
  useEffect(() => {
    dispatch(getOntology({ ontologyId: props.ontologyId, lang: "en", apiUrl: props.apiUrl }));
  }, [dispatch, props.ontologyId]);


  return (
    <Fragment>
      {ontology &&
       <EntityTree
         ontology={ontology}
         entityType={"classes"}
         lang={"en"}
         onNavigateToEntity={() => {}}
         onNavigateToOntology={() => {}}
         apiUrl={props.apiUrl}
       />}
    </Fragment>
  );
};
