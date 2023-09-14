import { Search } from "@mui/icons-material";
import { Fragment } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { asArray } from "../app/util";
import Entity from "../model/Entity";
import LinkedEntities from "../model/LinkedEntities";

export default function EntityLink({
  ontologyId,
  currentEntity,
  entityType,
  iri,
  linkedEntities,
}: {
  ontologyId: string;
  currentEntity: Entity | undefined;
  entityType: "classes" | "properties" | "individuals" | "ontologies";
  iri: string;
  linkedEntities: LinkedEntities;
}) {
  const [searchParams] = useSearchParams();
  let lang = searchParams.get("lang") || "en";

  if (typeof iri !== "string") {
    throw new Error("EntityLink iri was not a string: " + JSON.stringify(iri));
  }

  // reference to self; just display label bc we are already on that page
  if (currentEntity && iri === currentEntity.getIri()) {
    return <b>{currentEntity.getName()}</b>;
  }

  const label =
    linkedEntities.getLabelForIri(iri) || iri.split("/").pop() || iri;
  const linkedEntity = linkedEntities.get(iri);

  if (!linkedEntity) {
    // So far only known occurrence of this branch is for owl:Thing
    return iri.includes("http") ? (
      <Link className="link-default" to={iri}>
        {label}
      </Link>
    ) : (
      <span>{label}</span>
    );
  }

  let otherDefinedBy = linkedEntity?.definedBy
    ? linkedEntity.definedBy.filter((db) => db !== ontologyId)
    : [];
  const encodedIri = encodeURIComponent(
    encodeURIComponent(linkedEntity?.iri || iri)
  );

  if (otherDefinedBy.length === 1) {
    // Canonical definition in 1 other ontology
    if (linkedEntity.hasLocalDefinition) {
      // Term is defined in this ontology but has a definition 1 canonical ontology
      // Show <label> <ontologyId> where <label> links to the term in THIS ontology
      // and <ontologyId> links to the term in the DEFINING ontology
      return (
        <Fragment>
          <Link
            className="link-default"
            to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}?lang=${lang}`}
          >
            {label}
          </Link>
          <Link
            to={`/ontologies/${linkedEntity.definedBy![0]}/${
              pluraliseType(linkedEntity.type) || entityType
            }/${encodedIri}`}
          >
            <span
              className="mx-1 link-ontology px-2 py-0.5 rounded-md text-sm text-white uppercase ml-1"
              title={ontologyId.toUpperCase()}
            >
              {linkedEntity.definedBy![0]}
            </span>
          </Link>
        </Fragment>
      );
    } else {
      // Term is not defined in this ontology
      // Show <label> <ontologyId> linking to the term in the DEFINING ontology
      return (
        <Fragment>
          <Link
            className="link-default"
            to={`/ontologies/${linkedEntity.definedBy![0]}/${
              pluraliseType(linkedEntity.type) || entityType
            }/${encodedIri}?lang=${lang}`}
          >
            {label}
          </Link>
          <Link
            to={`/ontologies/${linkedEntity.definedBy![0]}/${
              pluraliseType(linkedEntity.type) || entityType
            }/${encodedIri}`}
          >
            <span
              className="link-ontology px-2 py-0.5 rounded-md text-sm text-white uppercase ml-1"
              title={ontologyId.toUpperCase()}
            >
              {linkedEntity.definedBy![0]}
            </span>
          </Link>
        </Fragment>
      );
    }
  } else if (otherDefinedBy.length > 1) {
    // Canonical definition in multiple ontologies
    if (linkedEntity.hasLocalDefinition) {
      // Term is defined in this ontology but also more than 1 canonical definition
      // Show <label><ONTOLOGY> where each ONTOLOGY button links to the term in that defining ontology
      return (
        <Fragment>
          <Link
            className="link-default"
            to={`/ontologies/${ontologyId}/${pluraliseType(
              linkedEntity.type
            )}/${encodedIri}?lang=${lang}`}
          >
            {label}
          </Link>
          {linkedEntity.definedBy!.map((definedBy) => {
            return (
              <Link to={`/ontologies/${definedBy}/${entityType}/${encodedIri}`}>
                <span
                  className="link-ontology px-2 py-0.5 rounded-md text-sm text-white uppercase ml-1 w-fit whitespace-nowrap"
                  title={definedBy.toUpperCase()}
                >
                  {definedBy}
                </span>
              </Link>
            );
          })}
        </Fragment>
      );
    } else {
      // Term is not defined in this ontology but is defined in other ontologies
      // Show <label><ICON> linking to a disambiguation page
      return (
        <Fragment>
          <Link
            className="link-default"
            to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}?lang=${lang}`}
          >
            {label}
          </Link>
          <Link
            to={`/search?q=${encodeURIComponent(
              label
            )}&exactMatch=true&lang=${lang}`}
          >
            <span className="link-ontology px-2 py-0.5 rounded-md text-sm text-white ml-1 whitespace-nowrap">
              <Search fontSize="small" style={{ verticalAlign: "text-top" }} />
              &nbsp;{otherDefinedBy.length}&nbsp;
              {otherDefinedBy.length > 1 ? "ontologies" : "ontology"}
            </span>
          </Link>
        </Fragment>
      );
    }
  } else {
    // No canonical definition in other ontologies
    if (linkedEntity.hasLocalDefinition) {
      // Term is defined in this ontology
      // Show internal link within the ontology
      return (
        <Link
          className="link-default"
          to={`/ontologies/${ontologyId}/${pluraliseType(
            linkedEntity.type
          )}/${encodedIri}?lang=${lang}`}
        >
          {label}
        </Link>
      );
    } else {
      // Term is not defined in this ontology

      if (parseInt(linkedEntity.numAppearsIn) > 0) {
        // Term appears in other ontologies
        // Show <label><ICON> linking to disambiguation page
        return (
          <Fragment>
            <Link
              className="link-default"
              to={`/ontologies/${ontologyId}/${entityType}/${encodedIri}?lang=${lang}`}
            >
              {label}
            </Link>
            <Link
              to={`/search?q=${encodeURIComponent(
                label
              )}&exactMatch=true&lang=${lang}`}
            >
              <span className="mx-1 link-ontology px-2 py-0.5 rounded-md text-sm text-white whitespace-nowrap">
                <Search
                  fontSize="small"
                  style={{ verticalAlign: "text-top" }}
                />
                &nbsp;{linkedEntity.numAppearsIn}&nbsp;
                {parseInt(linkedEntity.numAppearsIn) > 1
                  ? "ontologies"
                  : "ontology"}
              </span>
            </Link>
          </Fragment>
        );
      } else {
        // Term is not defined in other ontologies
        // Show the raw IRI
        return (
          <Link className="link-default" to={linkedEntity.url || iri}>
            {label}
          </Link>
        );
      }
    }
  }
  // throw new Error("unknown entity link");
}

function pluraliseType(type) {
  for (let t of asArray(type)) {
    let plural = {
      class: "classes",
      individual: "individuals",
      property: "properties",
      ontology: "ontologies",
    }[t];

    if (plural) return plural;
  }
}
