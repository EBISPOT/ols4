import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Individual from "../../../../model/Individual";
import LinkedEntities from "../../../../model/LinkedEntities";

export default function IndividualTypesSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let types = entity.getIndividualTypes();

  if (!types || types.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={types}
      title="Type"
      renderValue={(type) => (
        typeof type === "object" && !Array.isArray(type) ? (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={type}
            linkedEntities={linkedEntities}
          />
        ) : (
          <EntityLink
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType={"classes"}
            iri={type}
            linkedEntities={linkedEntities}
          />
        )
      )}
      searchFilter={(type, searchQuery) => {
        if (typeof type === "string") {
          const iri = type.toLowerCase();
          const label = linkedEntities.getLabelForIri(type)?.toLowerCase() || '';
          return iri.includes(searchQuery) || label.includes(searchQuery);
        }
        // For complex expressions, convert to string
        const exprStr = JSON.stringify(type).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}