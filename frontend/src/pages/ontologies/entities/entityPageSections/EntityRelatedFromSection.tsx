import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import EntityLink from "../../../../components/EntityLink";
import Class from "../../../../model/Class";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function EntityRelatedFromSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  let relatedFroms = entity?.getRelatedFrom();

  if (!relatedFroms || relatedFroms.length === 0) {
    return <Fragment />;
  }

  let predicates = Array.from(
    new Set(relatedFroms.map((relatedFrom) => relatedFrom.value.property))
  );

  return (
    <div>
      <div className="font-bold">Related from</div>
      {predicates.map((p) => {
        let label = linkedEntities.getLabelForIri(p);
        return (
          <div key={p.toString() + randomString()}>
            <div>
              <i>{label || p}</i>
            </div>
            <ul className="list-disc list-inside">
              {relatedFroms
                .filter((relatedFrom) => relatedFrom.value.property === p)
                .map((relatedFrom) => {
                  let relatedIri = relatedFrom.value.value;
                  // let label = linkedEntities.getLabelForIri(relatedIri);
                  return (
                    <li key={relatedIri.toString() + randomString()}>
                      <EntityLink
                        ontologyId={entity.getOntologyId()}
                        currentEntity={entity}
                        entityType={"classes"}
                        iri={relatedIri}
                        linkedEntities={linkedEntities}
                      />
                    </li>
                  );
                })}
            </ul>
          </div>
        );
      })}
    </div>
  );
}
