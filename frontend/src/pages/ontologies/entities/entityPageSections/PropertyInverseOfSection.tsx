import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function PropertyInverseOfSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  let inverseOfs = entity.getInverseOf();

  if (!inverseOfs || inverseOfs.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Inverse of</div>
      {inverseOfs.length === 1 ? (
        <p>
          {typeof inverseOfs[0] === "object" &&
          !Array.isArray(inverseOfs[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
                currentEntity={entity}
              expr={inverseOfs[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"properties"}
              iri={inverseOfs[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {inverseOfs.map((inverseOf) => {
            return (
              <li key={randomString()}>
                {typeof inverseOf === "object" &&
                !Array.isArray(inverseOf) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                currentEntity={entity}
                    expr={inverseOf}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"properties"}
                    iri={inverseOf}
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