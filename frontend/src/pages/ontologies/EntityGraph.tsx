import Cytoscape from "cytoscape";
import ColaLayout from "cytoscape-cola";
import { useEffect, useRef, useState } from "react";
import CytoscapeComponent from "react-cytoscapejs";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
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

  const { ontologyId, selectedEntity, entityType } = props;
  const [elements, setElements] = useState<any[]>([]);
  const cyRef = useRef<Cytoscape.Core>();

  useEffect(() => {
    if (selectedEntity) {
      const entityIri = selectedEntity.getIri();
      dispatch(getAncestors({ ontologyId, entityType, entityIri }));
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
    const { uriToChildNodes } = extractEntityHierarchy(entities);

    const nodes: any[] = entities.map((entity) => {
      return {
        data: {
          id: entity.getIri(),
          label: entity.getName(),
          position: {
            x: 50,
            y: 50,
          },
        },
      };
    });

    const edges: any[] = [];

    for (const parentUri of Array.from(uriToChildNodes.keys())) {
      for (const childEntity of uriToChildNodes.get(parentUri)) {
        edges.push({
          data: {
            source: parentUri,
            target: childEntity.getIri(),
            label: "parent",
          },
        });
      }
    }

    setElements([...nodes, ...edges]);
  }

  return (
    <div>
      {elements ? (
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
      ) : null}
    </div>
  );
}
