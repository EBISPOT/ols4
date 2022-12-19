import { AccountTree, Share } from "@mui/icons-material";
import { Tooltip } from "@mui/material";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { copyToClipboard, randomString, sortByKeys } from "../../app/util";
import ClassExpression from "../../components/ClassExpression";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import Class from "../../model/Class";
import Property from "../../model/Property";
import Reified from "../../model/Reified";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import { getEntity, getOntology } from "./ontologiesSlice";

export default function EntityPage({
  ontologyId,
  entityIri,
  entityType,
}: {
  ontologyId: string;
  entityIri: string;
  entityType: "classes" | "properties" | "individuals";
}) {
  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const entity = useAppSelector((state) => state.ontologies.entity);
  const loading = useAppSelector((state) => state.ontologies.loadingEntity);

  const [viewMode, setViewMode] = useState<"tree" | "graph">("tree");
  const iriToLabel = entity ? entity.getIriToLabel() : {};

  const [isShortFormCopied, setIsShortFormCopied] = useState(false);
  const copyShortForm = (text: string) => {
    copyToClipboard(text)
      .then(() => {
        setIsShortFormCopied(true);
        // revert after a few seconds
        setTimeout(() => {
          setIsShortFormCopied(false);
        }, 1500);
      })
      .catch((err) => {
        console.log(err);
      });
  };
  const [isIriCopied, setIsIriCopied] = useState(false);
  const copyIri = (text: string) => {
    copyToClipboard(text)
      .then(() => {
        setIsIriCopied(true);
        // revert after a few seconds
        setTimeout(() => {
          setIsIriCopied(false);
        }, 1500);
      })
      .catch((err) => {
        console.log(err);
      });
  };

  useEffect(() => {
    if (!ontology) dispatch(getOntology(ontologyId));
    dispatch(getEntity({ ontologyId, entityType, entityIri }));
  }, [dispatch, ontology, ontologyId, entityType, entityIri]);

  if (entity) document.title = entity.getShortForm() || entity.getName();
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto">
        {ontology && entity ? (
          <div className="my-8 mx-2">
            <div className="px-2 mb-4">
              <Link className="link-default" to="/ontologies">
                Ontologies
              </Link>
              <span className="px-2 text-sm">&gt;</span>
              <Link className="link-default" to={"/ontologies/" + ontologyId}>
                {ontology.getName() || ontology.getOntologyId()}
              </Link>
              <span className="px-2 text-sm">&gt;</span>
              <span className="capitalize">{entity.getType()}</span>
              <span className="px-2 text-sm">&gt;</span>
              <span className="font-bold mr-3">
                {entity.getShortForm() || entity.getName()}
              </span>
              <span className="text-sm text-neutral-default">
                <button
                  onClick={() => {
                    copyShortForm(entity.getShortForm() || entity.getName());
                  }}
                >
                  <i className="icon icon-common icon-copy icon-spacer" />
                  <span>{isShortFormCopied ? "Copied!" : "Copy"}</span>
                </button>
              </span>
            </div>
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="text-2xl font-bold mb-4">{entity.getName()}</div>
              <div className="mb-4 leading-relaxed text-sm text-neutral-default">
                <span>
                  <a href={entity.getIri()}>
                    <i className="icon icon-common icon-external-link-alt icon-spacer" />
                  </a>
                </span>
                <span className="mr-3">{entity.getIri()}</span>
                <button
                  onClick={() => {
                    copyIri(entity.getIri());
                  }}
                >
                  <i className="icon icon-common icon-copy icon-spacer" />
                  <span>{isIriCopied ? "Copied!" : "Copy"}</span>
                </button>
              </div>
              <div className="mb-4">
                <p>
                  {entity
                    .getDescriptionAsArray()
                    .map((definition: Reified<any>) => {
                      const hasMetadata =
                        definition.metadata?.iriToLabel &&
                        Object.keys(definition.metadata).length > 0 &&
                        Object.keys(definition.metadata.iriToLabel).length > 0;
                      return (
                        <span key={randomString()}>
                          {definition.value}
                          {hasMetadata ? (
                            <Tooltip
                              title={Object.keys(definition.metadata)
                                .map((key) => {
                                  if (definition.metadata.iriToLabel[key]) {
                                    return (
                                      "*" +
                                      definition.metadata[key] +
                                      " (" +
                                      definition.metadata.iriToLabel[
                                        key
                                      ].replaceAll("_", " ") +
                                      ")"
                                    );
                                  }
                                  return "";
                                })
                                .join("\n")}
                              placement="top"
                              arrow
                            >
                              <i className="icon icon-common icon-info text-neutral-default text-sm ml-1 mr-2" />
                            </Tooltip>
                          ) : null}
                        </span>
                      );
                    })}
                </p>
              </div>
              {entity.getSynonyms() && entity.getSynonyms().length !== 0 ? (
                <div>
                  <div className="font-bold mb-4">Synonym</div>
                  <div className="flex flex-row flex-wrap">
                    {entity
                      .getSynonyms()
                      .map((synonym: Reified<any>) => {
                        const hasMetadata =
                          synonym.metadata?.iriToLabel &&
                          Object.keys(synonym.metadata).length > 0 &&
                          Object.keys(synonym.metadata.iriToLabel).length > 0;
                        return (
                          <div
                            key={
                              synonym.value.toString().toUpperCase() +
                              randomString()
                            }
                            className="flex-none bg-grey-default rounded-sm font-mono py-1 px-3 mb-2 mr-2 text-sm"
                          >
                            {synonym.value}
                            {hasMetadata ? (
                              <Tooltip
                                title={Object.keys(synonym.metadata)
                                  .map((key) => {
                                    if (synonym.metadata.iriToLabel[key]) {
                                      return (
                                        "*" +
                                        synonym.metadata[key] +
                                        " (" +
                                        synonym.metadata.iriToLabel[
                                          key
                                        ].replaceAll("_", " ") +
                                        ")"
                                      );
                                    }
                                    return "";
                                  })
                                  .join("\n")}
                                placement="top"
                                arrow
                              >
                                <i className="icon icon-common icon-info text-neutral-default ml-1" />
                              </Tooltip>
                            ) : null}
                          </div>
                        );
                      })
                      .sort((a, b) => sortByKeys(a, b))}
                  </div>
                </div>
              ) : null}
            </div>
            <div className="grid grid-cols-3 gap-8">
              <div className="col-span-2">
                <div className="py-2 mb-1">
                  <Tooltip title="Tree view" placement="top">
                    <button
                      className={`button-primary font-bold mr-3 ${
                        viewMode === "tree"
                          ? "shadow-button-active translate-x-2 translate-y-2 hover:shadow-button-active hover:translate-x-2 hover:translate-y-2"
                          : ""
                      }`}
                      onClick={() => setViewMode("tree")}
                    >
                      <AccountTree fontSize="small" />
                    </button>
                  </Tooltip>
                  <Tooltip title="Graph view" placement="top">
                    <button
                      className={`button-primary font-bold ${
                        viewMode === "graph"
                          ? "shadow-button-active translate-x-2 translate-y-2 hover:shadow-button-active hover:translate-x-2 hover:translate-y-2"
                          : ""
                      }`}
                      onClick={() => setViewMode("graph")}
                    >
                      <Share fontSize="small" />
                    </button>
                  </Tooltip>
                </div>
                {viewMode === "graph" ? (
                  <EntityGraph
                    ontologyId={ontologyId}
                    entityType={
                      {
                        class: "classes",
                        property: "properties",
                        individual: "individuals",
                      }[entity.getType()]
                    }
                    selectedEntity={entity}
                  />
                ) : (
                  <EntityTree
                    ontologyId={ontologyId}
                    entityType={
                      {
                        class: "classes",
                        property: "properties",
                        individual: "individuals",
                      }[entity.getType()]
                    }
                    selectedEntity={entity}
                  />
                )}
              </div>
              <div className="col-span-1">
                <details open className="p-2">
                  <summary className="p-2 mb-2 border-b-2 border-grey-default text-link-default text-lg cursor-pointer hover:text-link-hover hover:underline ">
                    <span className="capitalize">
                      {entity.getType()} Information
                    </span>
                  </summary>
                  <div className="py-2 break-words space-y-2">
                    {entity.getAnnotationPredicate() &&
                    entity.getAnnotationPredicate().length !== 0
                      ? entity
                          .getAnnotationPredicate()
                          .map((annotationPredicate) => {
                            const title = entity.getLabelForIri(
                              annotationPredicate
                            )
                              ? entity
                                  .getLabelForIri(annotationPredicate)
                                  .replaceAll("_", " ")
                              : annotationPredicate
                                  .substring(
                                    annotationPredicate.lastIndexOf("/") + 1
                                  )
                                  .substring(
                                    annotationPredicate
                                      .substring(
                                        annotationPredicate.lastIndexOf("/") + 1
                                      )
                                      .lastIndexOf("#") + 1
                                  )
                                  .replaceAll("_", " ");
                            return (
                              <div
                                key={
                                  title.toString().toUpperCase() +
                                  randomString()
                                }
                              >
                                <div className="font-bold capitalize">
                                  {title}
                                </div>
                                <ul className="list-disc list-inside">
                                  {entity
                                    .getAnnotationById(annotationPredicate)
                                    .map((annotation: any) => {
                                      const value =
                                        annotation &&
                                        typeof annotation === "object"
                                          ? annotation.value
                                          : annotation;
                                      return (
                                        <li
                                          key={
                                            value.toString().toUpperCase() +
                                            randomString()
                                          }
                                        >
                                          {value}
                                        </li>
                                      );
                                    })
                                    .sort((a, b) => sortByKeys(a, b))}
                                </ul>
                              </div>
                            );
                          })
                          .sort((a, b) => sortByKeys(a, b))
                      : null}
                  </div>
                </details>
                <details open className="p-2">
                  <summary className="p-2 mb-2 border-b-2 border-grey-default text-link-default text-lg cursor-pointer hover:text-link-hover hover:underline ">
                    <span className="capitalize">
                      {entity.getType()} Relations
                    </span>
                  </summary>
                  <div className="py-2 break-words space-y-2">
                    {(entity instanceof Class || entity instanceof Property) &&
                    entity?.getParents()?.length > 0 ? (
                      <div>
                        <div className="font-bold">
                          Sub{entity.getType().toString().toLowerCase()} of
                        </div>
                        <ul className="list-disc list-inside">
                          {entity.getParents().map((parent: Reified<any>) => {
                            const hasMetadata =
                              parent.metadata?.iriToLabel &&
                              Object.keys(parent.metadata).length > 0 &&
                              Object.keys(parent.metadata.iriToLabel).length >
                                0;
                            return (
                              <li key={randomString()}>
                                <ClassExpression
                                  expr={parent.value}
                                  iriToLabel={iriToLabel}
                                />
                                {hasMetadata ? (
                                  <Tooltip
                                    title={Object.keys(parent.metadata)
                                      .map((key) => {
                                        if (parent.metadata.iriToLabel[key]) {
                                          return (
                                            "*" +
                                            parent.metadata[key] +
                                            " (" +
                                            parent.metadata.iriToLabel[
                                              key
                                            ].replaceAll("_", " ") +
                                            ")"
                                          );
                                        }
                                        return "";
                                      })
                                      .join("\n")}
                                    placement="top"
                                    arrow
                                  >
                                    <i className="icon icon-common icon-info text-neutral-default text-sm ml-1" />
                                  </Tooltip>
                                ) : null}
                              </li>
                            );
                          })}
                        </ul>
                      </div>
                    ) : null}
                  </div>
                </details>
              </div>
            </div>
          </div>
        ) : null}
        {!ontology || loading ? (
          <LoadingOverlay message="Loading entity..." />
        ) : null}
      </main>
    </div>
  );
}
