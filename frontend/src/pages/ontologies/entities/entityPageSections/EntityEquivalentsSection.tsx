
import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
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
    <div>
      <div className="font-bold">Equivalent to</div>
      <ul className="list-disc list-inside">
        {equivalents.map((eqClass: Reified<any>) => {
          const hasMetadata = eqClass.hasMetadata();
          return (
            <li key={randomString()}>
              <ClassExpression
                ontologyId={entity.getOntologyId()}
		currentEntity={entity} 
                expr={eqClass.value}
                linkedEntities={linkedEntities}
              />
              {hasMetadata && (
                <MetadataTooltip
                  metadata={eqClass.getMetadata()}
                  linkedEntities={linkedEntities}
                />
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}