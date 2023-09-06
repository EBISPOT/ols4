import { Fragment, useEffect } from "react";
import ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { useAppDispatch, useAppSelector } from "../app/hooks";
import { store } from "../app/store";
import Entity from "../model/Entity";
import Ontology from "../model/Ontology";
import EntityTree from "../pages/ontologies/entities/EntityTree";
import { getOntology } from "../pages/ontologies/ontologiesSlice";

export interface EntityTreeWidgetProps {
  iri?: string;
  ontologyId: string;
  apiUrl: string;
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
  ReactDOM.render(EntityTreeWidget(props), container, callback);
}

function EntityTreeWidget(props: EntityTreeWidgetProps) {
  return (
    <Provider store={store}>
      <EntityTreeWidgetInner
        ontologyId={props.ontologyId}
        iri={props.iri}
        apiUrl={props.apiUrl}
      />
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
    dispatch(
      getOntology({
        ontologyId: props.ontologyId,
        lang: "en",
        apiUrl: props.apiUrl,
      })
    );
  }, [dispatch, props.ontologyId]);

  return (
    <Fragment>
      {ontology && (
        <EntityTree
          ontology={ontology}
          entityType={entityType}
          lang={lang}
          onNavigateToEntity={onNavigateToEntity}
          onNavigateToOntology={onNavigateToOntology}
          apiUrl={props.apiUrl}
        />
      )}
    </Fragment>
  );
}
