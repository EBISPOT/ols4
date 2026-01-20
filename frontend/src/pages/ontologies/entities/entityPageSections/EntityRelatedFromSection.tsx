import { Fragment } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { randomString } from "../../../../app/util";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Class from "../../../../model/Class";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Property from "../../../../model/Property";

export default function EntityRelatedFromSection({
  entity,
  relatedFrom,
  linkedEntities,
}: {
  entity: Entity;
  relatedFrom: Entity[] | null;
  linkedEntities: LinkedEntities;
}) {
  const [searchParams] = useSearchParams();
  const lang = searchParams.get("lang") || "en";

  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  if (!relatedFrom || relatedFrom.length === 0) {
    return <Fragment />;
  }

  // Group entities by their relationship property
  const entitiesByProperty = new Map<string, Entity[]>();

  relatedFrom.forEach((relatedEntity) => {
    // Extract the property from relatedTo that references the current entity
    const relatedToArray = relatedEntity.properties["relatedTo"];
    if (relatedToArray && Array.isArray(relatedToArray)) {
      relatedToArray.forEach((relatedTo: any) => {
        if (relatedTo.value === entity.getIri()) {
          const propertyIri = relatedTo.property;
          if (!entitiesByProperty.has(propertyIri)) {
            entitiesByProperty.set(propertyIri, []);
          }
          entitiesByProperty.get(propertyIri)!.push(relatedEntity);
        }
      });
    }
  });

  // If no property grouping found, show all entities without grouping
  if (entitiesByProperty.size === 0) {
    return (
      <PropertyValuesList
        values={relatedFrom}
        title="Related from"
        renderValue={(relatedEntity: Entity) => {
          const encodedIri = encodeURIComponent(
            encodeURIComponent(relatedEntity.getIri())
          );
          const label = relatedEntity.getName() || relatedEntity.getShortForm();

          return (
            <Link
              className="link-default"
              to={`/ontologies/${relatedEntity.getOntologyId()}/classes/${encodedIri}?lang=${lang}`}
            >
              {label}
            </Link>
          );
        }}
        searchFilter={(relatedEntity: Entity, searchQuery: string) => {
          const name = relatedEntity.getName()?.toLowerCase() || '';
          const iri = relatedEntity.getIri().toLowerCase();
          return name.includes(searchQuery) || iri.includes(searchQuery);
        }}
      />
    );
  }

  // Render grouped by property
  return (
    <div>
      <div className="font-bold">Related from</div>
      {Array.from(entitiesByProperty.entries()).map(([propertyIri, entities]) => {
        // Try to get property label from the first entity's linkedEntities (since all share same property)
        const firstEntityLinkedEntities = entities[0]?.getLinkedEntities();
        const propertyLabel = firstEntityLinkedEntities?.getLabelForIri(propertyIri) ||
                             linkedEntities.getLabelForIri(propertyIri) ||
                             propertyIri.split('/').pop() ||
                             propertyIri;

        return (
          <div key={propertyIri + randomString()}>
            <div className="mb-2">
              <i>{propertyLabel}</i>
            </div>
            <PropertyValuesList
              values={entities}
              renderValue={(relatedEntity: Entity) => {
                const encodedIri = encodeURIComponent(
                  encodeURIComponent(relatedEntity.getIri())
                );
                const label = relatedEntity.getName() || relatedEntity.getShortForm();

                return (
                  <Link
                    className="link-default"
                    to={`/ontologies/${relatedEntity.getOntologyId()}/classes/${encodedIri}?lang=${lang}`}
                  >
                    {label}
                  </Link>
                );
              }}
              searchFilter={(relatedEntity: Entity, searchQuery: string) => {
                const name = relatedEntity.getName()?.toLowerCase() || '';
                const iri = relatedEntity.getIri().toLowerCase();
                return name.includes(searchQuery) || iri.includes(searchQuery);
              }}
            />
          </div>
        );
      })}
    </div>
  );
}
