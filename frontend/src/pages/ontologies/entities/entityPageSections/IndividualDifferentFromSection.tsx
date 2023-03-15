import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Individual from "../../../../model/Individual";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function IndividualDifferentFromSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let differentFroms = entity.getDifferentFrom();

  if (!differentFroms || differentFroms.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Different from</div>
      {differentFroms.length === 1 ? (
        <p>
          {typeof differentFroms[0] === "object" &&
          !Array.isArray(differentFroms[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={differentFroms[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"individuals"}
              iri={differentFroms[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {differentFroms.map((differentFrom) => {
            return (
              <li key={randomString()}>
                {typeof differentFrom === "object" &&
                !Array.isArray(differentFrom) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    expr={differentFrom}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"individuals"}
                    iri={differentFrom}
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