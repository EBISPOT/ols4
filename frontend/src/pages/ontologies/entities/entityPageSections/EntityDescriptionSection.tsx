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
  const propertyDescriptions = entity.getDescriptionsFromProperties();

  return (
      <div className="mb-2">
        {propertyDescriptions.length > 0 ? (
            propertyDescriptions.map((propertyGroup, groupIndex) => (
                <div key={`group-${groupIndex}`} className="mb-4">
                  <h4 className="font-semibold text-gray-700 mb-2">
                    {propertyGroup.property}:
                  </h4>
                  {propertyGroup.descriptions.map((definition: Reified<any>, i: number) => (
                      <p
                          key={definition.value.toString().substring(0, 10) + randomString()}
                          className="pb-3 pl-4"
                      >
                <span className="whitespace-pre-line">
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
                  ))}
                </div>
            ))
        ) : null }
      </div>
  );
}
