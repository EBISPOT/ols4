import { Tooltip } from "@mui/material";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function MetadataTooltip({
  metadata,
  linkedEntities,
}: {
  metadata: any;
  linkedEntities: LinkedEntities;
}) {
  return (
    <Tooltip
      title={Object.keys(metadata)
        .map((key) => {
          let label = linkedEntities.getLabelForIri(key) || key;
          if (label) {
            return (
              "*" + metadata[key] + " (" + label + ")"
            );
          }
          return "";
        })
        .join("\n")}
      placement="top"
      arrow
    >
      <i className="icon icon-common icon-info text-neutral-default text-sm ml-1" />
    </Tooltip>
  );
}