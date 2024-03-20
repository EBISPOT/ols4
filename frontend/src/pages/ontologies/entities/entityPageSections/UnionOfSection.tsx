import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function UnionOfSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property) && !(entity instanceof Class)) {
    return <Fragment />;
  }

  let unionOfs = entity.getUnionOf();

  if (!unionOfs || unionOfs.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Union of</div>
      {unionOfs.length === 1 ? (
        <p>
          {typeof unionOfs[0] === "object" &&
          !Array.isArray(unionOfs[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={unionOfs[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	          currentEntity={entity}
              entityType={
                entity.getType() === "property" ? "properties" : "classes"
              }
              iri={unionOfs[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {unionOfs.map((disjointWith) => {
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