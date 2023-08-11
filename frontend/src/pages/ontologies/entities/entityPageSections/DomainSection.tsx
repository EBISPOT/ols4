import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function DomainSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  let domains = entity.getDomain();

  if (!domains || domains.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Domain</div>
      {domains.length === 1 ? (
        <p>
          {typeof domains[0] === "object" &&
          !Array.isArray(domains[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={domains[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	          currentEntity={entity}
              entityType={
                entity.getType() === "property" ? "properties" : "classes"
              }
              iri={domains[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {domains.map((domains) => {
            return (
              <li key={randomString()}>
                {typeof domains === "object" &&
                !Array.isArray(domains) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    expr={domains}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		            currentEntity={entity}
                    entityType={
                      entity.getType() === "property" ? "properties" : "classes"
                    }
                    iri={domains}
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