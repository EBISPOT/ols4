import { Fragment, useState } from "react";
import { Link } from "react-router-dom";
import { randomString } from "../../../../app/util";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function DefiningOntologiesSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let definedBy = entity
    .getDefinedBy()
    .filter((ontId) => ontId !== entity.getOntologyId());
  let appearsIn = entity
    .getAppearsIn()
    .filter(
      (ontId) =>
        ontId !== entity.getOntologyId() && definedBy.indexOf(ontId) === -1
    );

  let [appearsInExpanded, setAppearsInExpanded] = useState<boolean>(false);

  const MAX_DISPLAY_APPEARS_IN = 5;

  return (
    <Fragment>
      {definedBy && definedBy.length > 0 && (
        <div className="mb-2">
          <span className="font-bold mr-2">Defined by</span>
          {definedBy.map((definedBy: string) => {
            return (
              <Link
                to={
                  "/ontologies/" +
                  definedBy +
                  `/${entity.getTypePlural()}/` +
                  encodeURIComponent(encodeURIComponent(entity.getIri()))
                }
                key={definedBy + entity.getIri() + randomString()}
              >
                <span
                  className="link-ontology px-2 py-1 rounded-md text-sm text-white uppercase mr-1"
                  title={definedBy.toUpperCase()}
                >
                  {definedBy}
                </span>
              </Link>
            );
          })}
        </div>
      )}
      {appearsIn && appearsIn.length > 0 && (
        <div className="mb-2" style={{ maxWidth: "100%", inlineSize: "100%" }}>
          <span className="font-bold mr-2">Also appears in</span>
          {appearsIn.length <= MAX_DISPLAY_APPEARS_IN || appearsInExpanded ? (
            appearsIn.map(renderAppearsIn)
          ) : (
            <Fragment>
              {appearsIn.slice(0, MAX_DISPLAY_APPEARS_IN).map(renderAppearsIn)}
              &nbsp;
              <span
                className="link-default italic"
                onClick={() => setAppearsInExpanded(true)}
              >
                + {appearsIn.length - MAX_DISPLAY_APPEARS_IN}
              </span>
            </Fragment>
          )}
        </div>
      )}
    </Fragment>
  );

  function renderAppearsIn(appearsIn: string) {
    return (
      <Link
        className="my-1"
        style={{ display: "inline-block" }}
        to={
          "/ontologies/" +
          appearsIn +
          `/${entity.getTypePlural()}/` +
          encodeURIComponent(encodeURIComponent(entity.getIri()))
        }
        key={appearsIn + entity.getIri() + randomString()}
      >
        <span
          className="link-ontology px-2 py-1 rounded-md text-sm text-white uppercase mr-1"
          title={appearsIn.toUpperCase()}
        >
          {appearsIn}
        </span>
      </Link>
    );
  }
}
