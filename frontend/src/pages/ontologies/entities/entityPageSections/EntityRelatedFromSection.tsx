import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
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
        let predicateRelatedFroms = relatedFroms.filter((relatedFrom) => relatedFrom.value.property === p);
        
        return (
          <div key={p.toString() + randomString()}>
            <div className="mb-2">
              <i>{label || p}</i>
            </div>
            <PropertyValuesList
              values={predicateRelatedFroms}
              renderValue={(relatedFrom) => {
                let relatedIri = relatedFrom.value.value;
                return (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    entityType={"classes"}
                    iri={relatedIri}
                    linkedEntities={linkedEntities}
                  />
                );
              }}
              searchFilter={(relatedFrom, searchQuery) => {
                let relatedIri = relatedFrom.value.value;
                let entityLabel = linkedEntities.getLabelForIri(relatedIri)?.toLowerCase() || '';
                let iriText = relatedIri.toLowerCase();
                return entityLabel.includes(searchQuery) || iriText.includes(searchQuery);
              }}
            />
          </div>
        );
      })}
    </div>
  );
}
