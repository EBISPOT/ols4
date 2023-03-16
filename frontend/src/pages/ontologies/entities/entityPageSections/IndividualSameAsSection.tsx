import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Individual from "../../../../model/Individual";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function IndividualSameAsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let sameAses = entity.getSameAs();

  if (!sameAses || sameAses.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Same as</div>
      {sameAses.length === 1 ? (
        <p>
          {typeof sameAses[0] === "object" && !Array.isArray(sameAses[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={sameAses[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"individuals"}
              iri={sameAses[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {sameAses.map((sameAs) => {
            return (
              <li key={randomString()}>
                {typeof sameAs === "object" && !Array.isArray(sameAs) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    expr={sameAs}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"individuals"}
                    iri={sameAs}
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