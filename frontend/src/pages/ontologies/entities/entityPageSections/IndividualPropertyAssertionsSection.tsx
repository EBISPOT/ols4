import { Tooltip } from "@mui/material";
import { Fragment } from "react";
import { asArray, randomString, sortByKeys } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function IndividualPropertyAssertionsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (entity.getType() !== "individual") return <Fragment />;

  let propertyIris = Object.keys(entity.properties);

  let negativeProperties = propertyIris.filter((k) =>
    k.startsWith("negativePropertyAssertion+")
  );

  // let annotationProperties = propertyIris.filter(
  //   (k) =>
  //     linkedEntities.get(k) &&
  //     linkedEntities.get(k)!.type.indexOf("annotationProperty") !== -1
  // );
  let objectProperties = propertyIris.filter(
    (k) =>
      linkedEntities.get(k) &&
      linkedEntities.get(k)!.type.indexOf("objectProperty") !== -1
  );

  let propertyAssertions: JSX.Element[] = [];

  for (let iri of objectProperties) {
    const values = asArray(entity.properties[iri]);
    for (let v of values) {
      propertyAssertions.push(
        <span>
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType="properties"
            expr={iri}
            linkedEntities={linkedEntities}
          />
          &thinsp;
          {typeof v === "string" && v.includes("http") ? (
            <span>
              <span className="pr-1 text-sm" style={{ color: "gray" }}>
                &#9656;
              </span>
              <EntityLink
                ontologyId={entity.getOntologyId()}
                currentEntity={entity}
                entityType="individuals"
                iri={v}
                linkedEntities={linkedEntities}
              />
            </span>
          ) : (
            <Tooltip
              title={
                typeof v === "string"
                  ? v
                  : typeof v === "object" && !Array.isArray(v) && v.value
                  ? JSON.stringify(v.value)
                  : JSON.stringify(v)
              }
              placement="top"
              arrow
            >
              <i className="icon icon-common icon-info text-neutral-default text-sm ml-1" />
            </Tooltip>
          )}
        </span>
      );
    }
  }

  for (let k of negativeProperties) {
    let iri = k.slice("negativePropertyAssertion+".length);
    const values = asArray(entity.properties[k]);
    for (let v of values) {
      propertyAssertions.push(
        <span>
          <span className="px-1 text-embl-purple-default italic">not</span>{" "}
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType="properties"
            expr={iri}
            linkedEntities={linkedEntities}
          />
          &thinsp;
          {typeof v === "string" && v.includes("http") ? (
            <span>
              <span className="pr-1 text-sm" style={{ color: "gray" }}>
                &#9656;
              </span>
              <EntityLink
                ontologyId={entity.getOntologyId()}
                currentEntity={entity}
                entityType="individuals"
                iri={v}
                linkedEntities={linkedEntities}
              />
            </span>
          ) : (
            <Tooltip
              title={
                typeof v === "string"
                  ? v
                  : typeof v === "object" && !Array.isArray(v) && v.value
                  ? JSON.stringify(v.value)
                  : JSON.stringify(v)
              }
              placement="top"
              arrow
            >
              <i className="icon icon-common icon-info text-neutral-default text-sm ml-1" />
            </Tooltip>
          )}
        </span>
      );
    }
  }

  return (
    <div>
      <div className="font-bold">Property assertions</div>
      {propertyAssertions.length === 1 ? (
        <p>{propertyAssertions[0]}</p>
      ) : (
        <ul className="list-disc list-inside">
          {propertyAssertions
            .map((pa) => {
              return <li key={randomString()}>{pa}</li>;
            })
            .sort((a, b) => sortByKeys(a, b))}
        </ul>
      )}
    </div>
  );
}
