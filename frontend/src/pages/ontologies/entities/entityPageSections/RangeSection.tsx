import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function RangeSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  let ranges = entity.getRange();

  if (!ranges || ranges.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Range</div>
      {ranges.length === 1 ? (
        <p>
          {typeof ranges[0] === "object" &&
          !Array.isArray(ranges[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={ranges[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	          currentEntity={entity}
              entityType={
                entity.getType() === "property" ? "properties" : "classes"
              }
              iri={ranges[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {ranges.map((ranges) => {
            return (
              <li key={randomString()}>
                {typeof ranges === "object" &&
                !Array.isArray(ranges) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    expr={ranges}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		            currentEntity={entity}
                    entityType={
                      entity.getType() === "property" ? "properties" : "classes"
                    }
                    iri={ranges}
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