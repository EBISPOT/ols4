import { Fragment } from "react";
import { randomString, sortByKeys } from "../../../../app/util";
import ClassExpression from "../../../../components/ClassExpression";
import EntityLink from "../../../../components/EntityLink";
import Entity from "../../../../model/Entity";
import LinkedEntities from "../../../../model/LinkedEntities";
import Reified from "../../../../model/Reified";
import MetadataTooltip from "./MetadataTooltip";
import addLinksToText from "./addLinksToText";

export default function EntityAnnotationsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let annotationPredicates = entity.getAnnotationPredicates();
  console.log("annotationPredicates.length = " + annotationPredicates.length)

  if (annotationPredicates.length > 0)
      console.log("annotationPredicates[0]=" + annotationPredicates[0])

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
              <div className="font-bold">{title}</div>
              {annotations.length === 1 ? (
                <p>
                  {renderAnnotation(annotations[0])}
                  {annotations[0].hasMetadata() && (
                    <MetadataTooltip
                      metadata={annotations[0].getMetadata()}
                      linkedEntities={linkedEntities}
                    />
                  )}
                </p>
              ) : (
                <ul className="list-disc list-inside">
                  {annotations
                    .map((annotation: Reified<any>) => {
                      return (
                        <li
                          key={
                            annotation.value.toString().substring(0, 10) + randomString()
                          }
                        >
                          <span>{renderAnnotation(annotation)}</span>
                          {annotation.hasMetadata() && (
                            <MetadataTooltip
                              metadata={annotation.getMetadata()}
                              linkedEntities={linkedEntities}
                            />
                          )}
                        </li>
                      );
                    })
                    .sort((a, b) => sortByKeys(a, b))}
                </ul>
              )}
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
      if (typeof value.value !== "string") {
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
