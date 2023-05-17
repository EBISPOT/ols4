import { Fragment } from "react";
import { Page } from "../../../../app/api";
import { randomString } from "../../../../app/util";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function ClassInstancesSection({
  entity,
  classInstances,
  linkedEntities,
}: {
  entity: Entity;
  classInstances: Page<Entity> | null;
  linkedEntities: LinkedEntities;
}) {
  if (entity.getType() !== "class") return <Fragment />;

  if (!classInstances || classInstances.elements.length === 0)
    return <Fragment />;

  return (
    <div>
      <div className="font-bold">Instances</div>
      <ul className="list-disc list-inside">
        {classInstances &&
          classInstances.elements.map((instance: Entity) => {
            return (
              <li key={randomString()}>
                <EntityLink
                  ontologyId={entity.getOntologyId()}
                  currentEntity={entity}
                  entityType="individuals"
                  iri={instance.getIri()}
                  linkedEntities={linkedEntities}
                />
              </li>
            );
          })}
      </ul>
    </div>
  );
}
