import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function RangeSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  let ranges = entity.getRange();

  if (!ranges || ranges.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={ranges}
      title="Range"
      renderValue={(range) => (
        typeof range.value === "object" && !Array.isArray(range.value) ? (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={range.value}
            linkedEntities={linkedEntities}
          />
        ) : (
          <EntityLink
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType={
              entity.getType() === "property" ? "properties" : "classes"
            }
            iri={range.value}
            linkedEntities={linkedEntities}
          />
        )
      )}
      searchFilter={(range, searchQuery) => {
        if (typeof range.value === "string") {
          const iri = range.value.toLowerCase();
          const label = linkedEntities.getLabelForIri(range.value)?.toLowerCase() || '';
          return iri.includes(searchQuery) || label.includes(searchQuery);
        }
        // For complex expressions, convert to string
        const exprStr = JSON.stringify(range.value).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}