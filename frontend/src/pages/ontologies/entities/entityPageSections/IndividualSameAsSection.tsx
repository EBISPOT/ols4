import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Individual from "../../../../model/Individual";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function IndividualSameAsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let sameAses = entity.getSameAs();

  if (!sameAses || sameAses.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={sameAses}
      title="Same as"
      renderValue={(sameAs) => (
        typeof sameAs === "object" && !Array.isArray(sameAs) ? (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={sameAs}
            linkedEntities={linkedEntities}
          />
        ) : (
          <EntityLink
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType={"individuals"}
            iri={sameAs}
            linkedEntities={linkedEntities}
          />
        )
      )}
      searchFilter={(sameAs, searchQuery) => {
        if (typeof sameAs === "string") {
          const iri = sameAs.toLowerCase();
          const label = linkedEntities.getLabelForIri(sameAs)?.toLowerCase() || '';
          return iri.includes(searchQuery) || label.includes(searchQuery);
        }
        // For complex expressions, convert to string
        const exprStr = JSON.stringify(sameAs).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}