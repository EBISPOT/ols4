import Cytoscape from "cytoscape";
import ColaLayout from "cytoscape-cola";
import { useEffect, useRef, useState } from "react";
import CytoscapeComponent from "react-cytoscapejs";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import Spinner from "../../components/Spinner";
import Entity from "../../model/Entity";
import extractEntityHierarchy from "./extractEntityHierarchy";
import { getAncestors, getRootEntities } from "./ontologiesSlice";
Cytoscape.use(ColaLayout);

export default function EntityGraph(props: {
  ontologyId: string;
  selectedEntity?: Entity;
  entityType: "entities" | "classes" | "properties" | "individuals";
}) {
  const dispatch = useAppDispatch();
  const ancestors = useAppSelector((state) => state.ontologies.ancestors);
  // const rootEntities = useAppSelector((state) => state.ontologies.rootEntities);

  let { ontologyId, selectedEntity, entityType } = props;
  let [elements, setElements] = useState<any[]>([]);
  let cyRef = useRef<Cytoscape.Core>();

  useEffect(() => {
    if (selectedEntity) {
      let entityUri = selectedEntity.getUri();
      dispatch(getAncestors({ ontologyId, entityType, entityUri }));
    } else {
      dispatch(getRootEntities({ ontologyId, entityType }));

      // setRootNodes(rootEntities.map(entity => {
      // 	return {
      // 		uri: entity.getUri(),
      // 		absoluteIdentity: entity.getUri(),
      // 		title: entity.getName(),
      // 		expandable: entity.hasChildren(),
      // 		entity: entity
      // 	}
      // }))
    }
  }, [entityType]);

  useEffect(() => {
    if (selectedEntity) {
      populateGraphFromEntities([selectedEntity, ...ancestors]);
    }
  }, [ancestors]);

  function populateGraphFromEntities(entities: Entity[]) {
    let { uriToChildNodes } = extractEntityHierarchy(entities);

    let nodes: any[] = entities.map((entity) => {
      return {
        data: {
          id: entity.getUri(),
          label: entity.getName(),
          position: {
            x: 50,
            y: 50,
          },
        },
      };
    });

    let edges: any[] = [];

    for (let parentUri of Array.from(uriToChildNodes.keys())) {
      for (let childEntity of uriToChildNodes.get(parentUri)) {
        edges.push({
          data: {
            source: parentUri,
            target: childEntity.getUri(),
            label: "parent",
          },
        });
      }
    }

    setElements([...nodes, ...edges]);
  }

  if (!elements) {
    return <Spinner />;
  }

  return (
    <CytoscapeComponent
      layout={{ name: "cola" }}
      cy={(cy): void => {
        cy.on("add", "node", (_evt) => {
          cy.layout({ name: "cola" }).run();
          cy.fit();
        });

        cyRef.current = cy;
      }}
      elements={elements}
      style={{ width: "600px", height: "600px" }}
    />
  );
}
