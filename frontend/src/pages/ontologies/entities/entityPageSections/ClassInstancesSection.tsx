import { Fragment } from "react";
import { Page } from "../../../../app/api";
import { randomString } from "../../../../app/util";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
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
    <PropertyValuesList
      values={classInstances.elements}
      title="Instances"
      renderValue={(instance: Entity) => (
        <EntityLink
          ontologyId={entity.getOntologyId()}
          currentEntity={entity}
          entityType="individuals"
          iri={instance.getIri()}
          linkedEntities={linkedEntities}
        />
      )}
      searchFilter={(instance: Entity, searchQuery: string) => {
        const name = instance.getName()?.toLowerCase() || '';
        const iri = instance.getIri().toLowerCase();
        return name.includes(searchQuery) || iri.includes(searchQuery);
      }}
    />
  );
}
