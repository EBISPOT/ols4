import { useState, Fragment } from "react";
import { Link } from "react-router-dom";
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

    let [appearsInExpanded,setAppearsInExpanded] = useState<boolean>(false);

    const MAX_DISPLAY_APPEARS_IN = 5;

  return (
    <Fragment>
      {definedBy && definedBy.length > 0 && (
        <div className="mb-2">
          <span className="font-bold mr-2">Defined by</span>
          {definedBy.map((definedBy: string) => {
            return (
              <Link
                className="link-default"
                to={
                  "/ontologies/" +
                  definedBy +
                  `/${entity.getTypePlural()}/` +
                  encodeURIComponent(encodeURIComponent(entity.getIri()))
                }
              >
                <span
                  className="link-ontology px-3 py-1 rounded-lg text-sm text-white uppercase mr-1"
                  title={definedBy}
                >
                  {definedBy}
                </span>
              </Link>
            );
          })}
        </div>
      )}
      {appearsIn && appearsIn.length > 0 && (
        <div className="mb-2" style={{maxWidth: "100%", inlineSize:"100%" }}>
          <span className="font-bold mr-2">Also appears in</span>
	  { (appearsIn.length <= MAX_DISPLAY_APPEARS_IN || appearsInExpanded) ?
		appearsIn.map(renderAppearsIn)
	    : <Fragment>
		{
		appearsIn.slice(0, MAX_DISPLAY_APPEARS_IN).map(renderAppearsIn)
		}
		&nbsp;
		<a className="link-default" style={{fontStyle:'italic'}} onClick={() => setAppearsInExpanded(true)}>+ {appearsIn.length - MAX_DISPLAY_APPEARS_IN}</a>
	    </Fragment>
	  }
        </div>
      )}
    </Fragment>
  );

  function renderAppearsIn(appearsIn:string) {
	return (
	<Link
	className="my-2"
	style={{display: 'inline-block'}}
		to={
		"/ontologies/" +
		appearsIn +
		`/${entity.getTypePlural()}/` +
		encodeURIComponent(encodeURIComponent(entity.getIri()))
		}
	>
		<span
		className="link-ontology px-3 py-1 rounded-lg text-sm text-white uppercase mr-1"
		title={appearsIn}
		>
		{appearsIn}
		</span>
	</Link>
	);
  }
}