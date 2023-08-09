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

  console.log("entity.getIri()=" + entity.getIri())
  let propertyIris = Object.keys(entity.properties);

  let negativeProperties = propertyIris.filter((k) =>
      k.startsWith("negativePropertyAssertion+")
  );
  console.log("entity=" + entity.getIri() + " negativeProperties.length = " + negativeProperties.length)

  // let annotationProperties = propertyIris.filter(
  //   (k) =>
  //     linkedEntities.get(k) &&
  //     linkedEntities.get(k)!.type.indexOf("annotationProperty") !== -1
  // );
  // console.log("entity=" + entity.getIri() + " annotationProperties.length = " + annotationProperties.length)

  let objectProperties = propertyIris.filter(
    (k) =>
        linkedEntities.get(k) &&
        linkedEntities.get(k)!.type.indexOf("objectProperty") !== -1
  );

  console.log("entity=" + entity.getIri() + " objectProperties.length = " + objectProperties.length)

  let dataProperties = propertyIris.filter(
    (k) =>
        linkedEntities.get(k) &&
        linkedEntities.get(k)!.type.indexOf("dataProperty") !== -1
  );

  console.log("entity=" + entity.getIri() + " dataProperties.length = " + dataProperties.length)


  let propertyAssertions: JSX.Element[] = [];

  for (let iri of objectProperties) {
    console.log("iri = " + iri)
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

  for (let iri of dataProperties) {
    console.log("iri = " + iri)
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
            {
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
            }
        </span>
      );
    }
  }

  for (let k of negativeProperties) {
    let iri = k.slice("negativePropertyAssertion+".length);
    console.log("k=" + k + " iri = " + iri)
    let linkedEntity = linkedEntities.get(iri)
    let dataProperty = linkedEntity!.type.indexOf("dataProperty") !== -1
    let objectProperty = linkedEntity!.type.indexOf("objectProperty") !== -1
    const values = asArray(entity.properties[k]);
    for (let v of values) {
        console.log("typeof v = " + typeof v)
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
            objectProperty ?
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
            </Tooltip> :
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
