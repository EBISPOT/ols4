import { Tooltip } from "@mui/material";
import { asArray } from "../../../../app/util";
import LinkedEntities from "../../../../model/LinkedEntities";

// Shows the axiom annotations on a reified value (e.g. the source, xref or
// comment asserted alongside it) as a property/value table.
export default function MetadataTooltip({
  metadata,
  linkedEntities,
}: {
  metadata: any;
  linkedEntities: LinkedEntities;
}) {
  // Multi-valued annotations arrive nested (e.g. [["PMID:1", "https://..."]])
  // and values can themselves be reified; show each leaf value on its own line.
  function flatten(v: any): any[] {
    return asArray(v).flatMap((x: any) =>
      Array.isArray(x)
        ? flatten(x)
        : x && typeof x === "object" && "value" in x
        ? flatten(x.value)
        : [x]
    );
  }

  function renderValue(v: any) {
    if (typeof v !== "string") {
      return JSON.stringify(v);
    }
    // CURIEs such as PMID:123 resolve to a URL via linkedEntities
    const url = /^https?:\/\//.test(v) ? v : linkedEntities.get(v)?.url;
    if (url) {
      return (
        <a
          className="link-default break-all"
          href={url}
          target="_blank"
          rel="noopener noreferrer"
        >
          {linkedEntities.getLabelForIri(v) || v}
        </a>
      );
    }
    return <span className="whitespace-pre-line">{v}</span>;
  }

  function renderValues(values: any[]) {
    if (values.length === 1) {
      return renderValue(values[0]);
    }
    return (
      <ul className="list-disc pl-4 m-0">
        {values.map((v: any, i: number) => (
          <li key={i}>{renderValue(v)}</li>
        ))}
      </ul>
    );
  }

  const table = (
    <table className="text-sm text-left border-collapse">
      <tbody>
        {Object.keys(metadata).map((key) => (
          <tr key={key} className="border-b border-neutral-light last:border-0">
            <th className="font-bold align-top pr-3 py-1 whitespace-nowrap">
              {linkedEntities.getLabelForIri(key) || key.split(/[/#]/).pop() || key}
            </th>
            <td className="align-top py-1">
              {renderValues(flatten(metadata[key]))}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );

  return (
    <Tooltip
      title={table}
      placement="top"
      arrow
      componentsProps={{
        tooltip: {
          sx: {
            bgcolor: "white",
            color: "black",
            maxWidth: 640,
            border: "1px solid #ccc",
            boxShadow: 3,
            p: 1.5,
          },
        },
        arrow: { sx: { color: "white", "&::before": { border: "1px solid #ccc" } } },
      }}
    >
      <i className="icon icon-common icon-info text-neutral-default text-sm ml-1 cursor-help" />
    </Tooltip>
  );
}
