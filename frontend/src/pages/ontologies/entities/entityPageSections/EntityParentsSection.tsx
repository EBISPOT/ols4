
import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
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
    <div>
      <div className="font-bold">
        Sub{entity.getType().toString().toLowerCase()} of
      </div>
      {parents.length === 1 ? (
        <p>
          <ClassExpression
            ontologyId={entity.getOntologyId()}
	    currentEntity={entity}
            expr={parents[0].value}
            linkedEntities={linkedEntities}
          />
          {parents[0].hasMetadata() && (
            <MetadataTooltip
              metadata={parents[0].getMetadata()}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {parents.map((parent: Reified<any>) => {
            return (
              <li key={randomString()}>
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
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}