
import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";
import Reified from "../../../../model/Reified";
import MetadataTooltip from "./MetadataTooltip";

export default function EntityParentsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  let parents = entity?.getSuperEntities();

  if (!parents || parents.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={parents}
      title={`Sub${entity.getType().toString().toLowerCase()} of`}
      renderValue={(parent: Reified<any>) => (
        <span>
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={parent.value}
            linkedEntities={linkedEntities}
          />
          {parent.hasMetadata() && (
            <MetadataTooltip
              metadata={parent.getMetadata()}
              linkedEntities={linkedEntities}
            />
          )}
        </span>
      )}
      searchFilter={(parent: Reified<any>, searchQuery) => {
        // Search in the expression
        const exprStr = JSON.stringify(parent.value).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}