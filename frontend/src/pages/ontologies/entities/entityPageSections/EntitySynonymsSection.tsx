import { Fragment } from "react";
import { randomString, sortByKeys } from "../../../../app/util";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Reified from "../../../../model/Reified";
import MetadataTooltip from "./MetadataTooltip";

export default function EntitySynonymsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let synonyms = entity.getSynonyms();

  if (!synonyms || synonyms.length === 0) {
    return <Fragment />;
  }

  return (
    <div className="mb-2">
      <span className="font-bold mr-2">Synonym</span>
      {synonyms
        .map((synonym: Reified<any>) => {
          const hasMetadata = synonym.hasMetadata();
          return (
            <span
              key={synonym.value.toString().toUpperCase() + randomString()}
              className="flex-none bg-grey-default rounded-sm font-mono py-1 px-3 mr-2 text-sm"
            >
              {synonym.value}
              {hasMetadata && (
                <MetadataTooltip
                  metadata={synonym.getMetadata()}
                  linkedEntities={linkedEntities}
                />
              )}
            </span>
          );
        })
        .sort((a, b) => sortByKeys(a, b))}
    </div>
  );
}