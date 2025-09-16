import { Fragment } from "react";
import { randomString } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import Class from "../../../../model/Class";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function DomainSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  let domains = entity.getDomain();

  if (!domains || domains.length === 0) {
    return <Fragment />;
  }

  return (
    <PropertyValuesList
      values={domains}
      title="Domain"
      renderValue={(domain) => (
        typeof domain.value === "object" && !Array.isArray(domain.value) ? (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={domain.value}
            linkedEntities={linkedEntities}
          />
        ) : (
          <EntityLink
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            entityType={
              entity.getType() === "property" ? "properties" : "classes"
            }
            iri={domain.value}
            linkedEntities={linkedEntities}
          />
        )
      )}
      searchFilter={(domain, searchQuery) => {
        if (typeof domain.value === "string") {
          const iri = domain.value.toLowerCase();
          const label = linkedEntities.getLabelForIri(domain.value)?.toLowerCase() || '';
          return iri.includes(searchQuery) || label.includes(searchQuery);
        }
        // For complex expressions, convert to string
        const exprStr = JSON.stringify(domain.value).toLowerCase();
        return exprStr.includes(searchQuery);
      }}
    />
  );
}