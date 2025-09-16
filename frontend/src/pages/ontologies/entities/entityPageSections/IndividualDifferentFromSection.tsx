import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Individual from "../../../../model/Individual";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function IndividualDifferentFromSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let differentFroms = entity.getDifferentFrom();

  if (!differentFroms || differentFroms.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={differentFroms}
      title="Different from"
      renderValue={(differentFrom) => (
        typeof differentFrom === "object" && !Array.isArray(differentFrom) ? (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={differentFrom}
            linkedEntities={linkedEntities}
          />
        ) : (
          <EntityLink
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType={"individuals"}
            iri={differentFrom}
            linkedEntities={linkedEntities}
          />
        )
      )}
      searchFilter={(differentFrom, searchQuery) => {
        if (typeof differentFrom === "string") {
          const iri = differentFrom.toLowerCase();
          const label = linkedEntities.getLabelForIri(differentFrom)?.toLowerCase() || '';
          return iri.includes(searchQuery) || label.includes(searchQuery);
        }
        // For complex expressions, convert to string
        const exprStr = JSON.stringify(differentFrom).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}