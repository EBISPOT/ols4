import { Fragment } from "react";
import { randomString, sortByKeys } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import PropertyValuesList from "../../../../components/PropertyValuesList";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Reified from "../../../../model/Reified";
import MetadataTooltip from "./MetadataTooltip";
import addLinksToText from "./addLinksToText";
import Link from "@mui/material/Link";

export default function EntityAnnotationsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let annotationPredicates = entity.getAnnotationPredicates();

  return (
    <Fragment>
      {annotationPredicates
        .map((annotationPredicate) => {
          const title = entity.getLabelForIri(annotationPredicate)
            ? entity.getLabelForIri(annotationPredicate)
            : annotationPredicate
                .substring(annotationPredicate.lastIndexOf("/") + 1)
                .substring(
                  annotationPredicate
                    .substring(annotationPredicate.lastIndexOf("/") + 1)
                    .lastIndexOf("#") + 1
                );

          let annotations: Reified<any>[] =
            entity.getAnnotationById(annotationPredicate);

          return (
            <div key={title.toString().toUpperCase() + randomString()}>
              <PropertyValuesList
                values={annotations}
                title={title}
                renderValue={(annotation: Reified<any>) => (
                  <span>
                    {renderAnnotation(annotation)}
                    {annotation.hasMetadata() && (
                      <MetadataTooltip
                        metadata={annotation.getMetadata()}
                        linkedEntities={linkedEntities}
                      />
                    )}
                  </span>
                )}
                searchFilter={(annotation: Reified<any>, searchQuery: string) => {
                  const text = annotation.value.toString().toLowerCase();
                  const linkedEntity = linkedEntities.get(annotation.value);
                  const entityLabel = linkedEntity ? entity.getLabelForIri(annotation.value)?.toLowerCase() : '';
                  return text.includes(searchQuery) || (entityLabel && entityLabel.includes(searchQuery));
                }}
              />
            </div>
          );
        })
        .sort((a, b) => sortByKeys(a, b))}
    </Fragment>
  );

  function renderAnnotation(value: Reified<any>) {
    let linkedEntity = linkedEntities.get(value.value);

    if (linkedEntity) {
      return (
        <EntityLink
          ontologyId={entity.getOntologyId()}
          currentEntity={entity}
          entityType={entity.getTypePlural()}
          iri={value.value}
          linkedEntities={linkedEntities}
        />
      );
    } else {
      // Allows overriding the label of a link with an rdfs:label annotation
      // on the link annotation.
      //
      if (typeof(value.value) === 'string' && value.value.indexOf('://') !== -1) {
        let metadata = value.getMetadata();
        if(metadata) {
          let linkLabel = metadata["http://www.w3.org/2000/01/rdf-schema#label"];
          if(linkLabel) {
            return <Link className="link-default" href={value.value}>{linkLabel}</Link>
          }
        }
      }
      if (typeof value.value !== "string" && typeof value.value !== "boolean" && typeof value.value !== "number") {
        return (
          <ClassExpression
            ontologyId={entity.getOntologyId()}
            currentEntity={entity}
            expr={value.value}
            entityType={entity.getTypePlural() as any}
            linkedEntities={linkedEntities}
          />
        );
      }
      return (
        <span>
          {addLinksToText(
            value.value.toString(),
            linkedEntities,
            entity.getOntologyId(),
            entity,
            entity.getTypePlural()
          )}
        </span>
      );
    }
  }
}
