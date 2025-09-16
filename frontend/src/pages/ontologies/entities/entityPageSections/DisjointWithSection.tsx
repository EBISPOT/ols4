import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function DisjointWithSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property) && !(entity instanceof Class)) {
    return <Fragment />;
  }

  let disjointWiths = entity.getDisjointWith();

  if (!disjointWiths || disjointWiths.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={disjointWiths}
      title="Disjoint with"
      renderValue={(disjointWith) => (
        typeof disjointWith === "object" && !Array.isArray(disjointWith) ? (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={disjointWith}
            linkedEntities={linkedEntities}
          />
        ) : (
          <EntityLink
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType={
              entity.getType() === "property" ? "properties" : "classes"
            }
            iri={disjointWith}
            linkedEntities={linkedEntities}
          />
        )
      )}
      searchFilter={(disjointWith, searchQuery) => {
        if (typeof disjointWith === "string") {
          const iri = disjointWith.toLowerCase();
          const label = linkedEntities.getLabelForIri(disjointWith)?.toLowerCase() || '';
          return iri.includes(searchQuery) || label.includes(searchQuery);
        }
        // For complex expressions, convert to string
        const exprStr = JSON.stringify(disjointWith).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}