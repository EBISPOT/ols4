import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Individual from "../../../../model/Individual";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function IndividualTypesSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let types = entity.getIndividualTypes();

  if (!types || types.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Type</div>
      {types.length === 1 ? (
        <p>
          {typeof types[0] === "object" && !Array.isArray(types[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
		     currentEntity={entity}
              expr={types[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"classes"}
              iri={types[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {types.map((type) => {
            return (
              <li key={randomString()}>
                {typeof type === "object" && !Array.isArray(type) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
		     currentEntity={entity}
                    expr={type}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"classes"}
                    iri={type}
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