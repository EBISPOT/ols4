import { randomString } from "../../../../app/util";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Reified from "../../../../model/Reified";
import MetadataTooltip from "./MetadataTooltip";
import addLinksToText from "./addLinksToText";

export default function EntityDescriptionSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let desc = entity.getDescriptionAsArray();
  return (
    <div className="mb-2">
      {desc.map((definition: Reified<any>, i: number) => {
        return (
          <p
            key={definition.value.toString().substring(0, 10) + randomString()}
            className="pb-3"
          >
            <span>
              {addLinksToText(
                definition.value,
                linkedEntities,
                entity.getOntologyId(),
                entity,
                entity.getTypePlural()
              )}
              {definition.hasMetadata() ? (
                <MetadataTooltip
                  metadata={definition.getMetadata()}
                  linkedEntities={linkedEntities}
                />
              ) : null}
            </span>
          </p>
        );
      })}
    </div>
  );
}
