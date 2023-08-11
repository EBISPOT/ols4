import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function HasKeySection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class)) {
    return <Fragment />;
  }

  let keys = entity.getHasKey();

  if (!keys || keys.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Has Key</div>
      {keys.length === 1 ? (
        <p>
          {typeof keys[0] === "object" &&
          !Array.isArray(keys[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={keys[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	          currentEntity={entity}
              entityType={
                entity.getType() === "property" ? "properties" : "classes"
              }
              iri={keys[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {keys.map((keys) => {
            return (
              <li key={randomString()}>
                {typeof keys === "object" &&
                !Array.isArray(keys) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    expr={keys}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		            currentEntity={entity}
                    entityType={
                      entity.getType() === "property" ? "properties" : "classes"
                    }
                    iri={keys}
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