import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function DisjointWithSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property) && !(entity instanceof Class)) {
    return <Fragment />;
  }

  let disjointWiths = entity.getDisjointWith();

  if (!disjointWiths || disjointWiths.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Disjoint with</div>
      {disjointWiths.length === 1 ? (
        <p>
          {typeof disjointWiths[0] === "object" &&
          !Array.isArray(disjointWiths[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={disjointWiths[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	          currentEntity={entity}
              entityType={
                entity.getType() === "property" ? "properties" : "classes"
              }
              iri={disjointWiths[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {disjointWiths.map((disjointWith) => {
            return (
              <li key={randomString()}>
                {typeof disjointWith === "object" &&
                !Array.isArray(disjointWith) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                currentEntity={entity}
                    expr={disjointWith}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={
                      entity.getType() === "property" ? "properties" : "classes"
                    }
                    iri={disjointWith}
                    linkedEntities={linkedEntities}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}