import { Tooltip } from "@mui/material";
import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Reified from "../../../../model/Reified";
import addLinksToText from "./addLinksToText";

export default function EntityDescriptionSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
	let desc = entity.getDescriptionAsArray()
  return (
    <p>
      {desc.map((definition: Reified<any>, i:number) => {
        const hasMetadata = definition.hasMetadata();
        return (
		<Fragment>
            <p>
          <span key={randomString()}>
            {addLinksToText(definition.value, linkedEntities, entity.getOntologyId(), entity, entity.getTypePlural())}
            {hasMetadata ? (
              <Tooltip
                title={Object.keys(definition.getMetadata())
                  .map((key) => {
                    let label = linkedEntities.getLabelForIri(key);
                    if (label) {
                      return (
                        "*" +
                        definition.getMetadata()[key] +
                        " (" +
                        label.replaceAll("_", " ") +
                        ")"
                      );
                    }
                    return "";
                  })
                  .join("\n")}
                placement="top"
                arrow
              >
                <i className="icon icon-common icon-info text-neutral-default text-sm ml-1 mr-2" />
              </Tooltip>
            ) : null}
          </span>
            </p>
	{i <desc.length -1 ? <div className="py-1"/>:<Fragment/>}
	</Fragment>
        );
      })}
    </p>
  );
}