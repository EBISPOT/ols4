import { Fragment, useEffect, useState } from "react";
import ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { useAppDispatch, useAppSelector } from "../app/hooks";
import { createAppStore } from "../app/store";
import Entity from "../model/Entity";
import Ontology from "../model/Ontology";
import EntityTree from "../pages/ontologies/entities/EntityTree";
import { getOntology } from "../pages/ontologies/ontologiesSlice";

export interface EntityTreeWidgetProps {
  iri?: string;
  ontologyId: string;
  apiUrl: string;
  specifiedRootIri?: string;
  entityType?: "entities" | "classes" | "properties" | "individuals";
  lang?: string;
  onNavigateToEntity?: (ontology: Ontology, entity: Entity) => void;
  onNavigateToOntology?: (ontologyId: string, entity: Entity) => void;
}

export function createEntityTree(
  props: EntityTreeWidgetProps,
  container: any,
  callback?: () => void
) {
  // A new API/ontology/language scope owns a fresh store. Pending responses from
  // the previous mount can only reach its discarded store, never this instance.
  const scope = JSON.stringify([props.apiUrl, props.ontologyId, props.lang || "en"]);
  ReactDOM.render(<EntityTreeWidget key={scope} {...props} />, container, callback);
}

function EntityTreeWidget(props: EntityTreeWidgetProps) {
  const [store] = useState(createAppStore);
  return (
    <Provider store={store}>
      <EntityTreeWidgetInner {...props} />
    </Provider>
  );
}

function EntityTreeWidgetInner(props: EntityTreeWidgetProps) {
  const {
    entityType = "classes",
    lang = "en",
    onNavigateToEntity = () => {},
    onNavigateToOntology = () => {},
  } = props;

  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const dispatch = useAppDispatch();
  useEffect(() => {
    const promise = dispatch(
      getOntology({
        ontologyId: props.ontologyId,
        lang,
        apiUrl: props.apiUrl,
      })
    );
    return () => promise.abort();
  }, [dispatch, props.ontologyId, props.apiUrl, lang]);

  return (
    <Fragment>
      {ontology && (
        <EntityTree
          ontology={ontology}
          entityType={entityType}
          specifiedRootIri={props.specifiedRootIri}
          lang={lang}
          onNavigateToEntity={onNavigateToEntity}
          onNavigateToOntology={onNavigateToOntology}
          apiUrl={props.apiUrl}
        />
      )}
    </Fragment>
  );
}
