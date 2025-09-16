
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

export default function EntityEquivalentsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  let equivalents = entity?.getEquivalents();

  if (!equivalents || equivalents.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={equivalents}
      title="Equivalent to"
      renderValue={(eqClass: Reified<any>) => (
        <span>
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity} 
            expr={eqClass.value}
            linkedEntities={linkedEntities}
          />
          {eqClass.hasMetadata() && (
            <MetadataTooltip
              metadata={eqClass.getMetadata()}
              linkedEntities={linkedEntities}
            />
          )}
        </span>
      )}
      searchFilter={(eqClass: Reified<any>, searchQuery) => {
        // Search in the expression
        const exprStr = JSON.stringify(eqClass.value).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}