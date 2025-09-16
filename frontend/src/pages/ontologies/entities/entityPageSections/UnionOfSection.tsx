import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function UnionOfSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class)) {
    return <Fragment />;
  }

  let unionOfs = entity.getUnionOf();

  if (!unionOfs || unionOfs.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={unionOfs}
      title="Union of"
      renderValue={(unionOf) => (
        typeof unionOf === "object" && !Array.isArray(unionOf) ? (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={unionOf}
            linkedEntities={linkedEntities}
          />
        ) : (
          <EntityLink
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType={
              entity.getType() === "property" ? "properties" : "classes"
            }
            iri={unionOf}
            linkedEntities={linkedEntities}
          />
        )
      )}
      searchFilter={(unionOf, searchQuery) => {
        if (typeof unionOf === "string") {
          const iri = unionOf.toLowerCase();
          const label = linkedEntities.getLabelForIri(unionOf)?.toLowerCase() || '';
          return iri.includes(searchQuery) || label.includes(searchQuery);
        }
        // For complex expressions, convert to string
        const exprStr = JSON.stringify(unionOf).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}