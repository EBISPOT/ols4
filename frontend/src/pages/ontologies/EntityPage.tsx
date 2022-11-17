import { AccountTree, Share } from "@mui/icons-material";
import { Link, Tooltip } from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString, sortByKeys } from "../../app/util";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import Class from "../../model/Class";
import Property from "../../model/Property";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import { getEntity, getOntology } from "./ontologiesSlice";

export default function EntityPage(props: {
  ontologyId: string;
  entityIri: string;
  entityType: "classes" | "properties" | "individuals";
}) {
  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const entity = useAppSelector((state) => state.ontologies.entity);
  const loading = useAppSelector((state) => state.ontologies.loadingEntity);

  const { ontologyId, entityIri, entityType } = props;
  const [viewMode, setViewMode] = useState<"tree" | "graph">("tree");

  useEffect(() => {
    if (!ontology) dispatch(getOntology(ontologyId));
    dispatch(getEntity({ ontologyId, entityType, entityIri }));
  }, []);

  if (entity) document.title = entity.getName();
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto">
        {ontology && entity ? (
          <div className="my-8 mx-2">
            <div className="px-2 mb-4">
              <span className="link-default">
                <Link
                  color="inherit"
                  style={{ textDecoration: "inherit" }}
                  component={RouterLink}
                  to="/ontologies"
                >
                  Ontologies
                </Link>
              </span>
              <span className="px-2 text-sm">&gt;</span>
              <span className="link-default">
                <Link
                  color="inherit"
                  style={{ textDecoration: "inherit" }}
                  component={RouterLink}
                  to={"/ontologies/" + ontologyId}
                >
                  {ontology.getName()}
                </Link>
              </span>
              <span className="px-2 text-sm">&gt;</span>
              <span className="capitalize">{entity.getType()}</span>
              <span className="px-2 text-sm">&gt;</span>
              <span className="font-bold">{entity.getName()}</span>
            </div>
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="text-2xl font-bold mb-4">{entity.getName()}</div>
              <div>
                <p>{entity.getDescription()}</p>
              </div>
              {entity.getSynonyms() && entity.getSynonyms().length !== 0 ? (
                <div>
                  <div className="font-bold my-4">Synonym</div>
                  {entity
                    .getSynonyms()
                    .map((synonym) => {
                      return (
                        <span
                          key={
                            synonym.toString().toUpperCase() + randomString()
                          }
                          className="bg-grey-default rounded-sm font-mono p-1 mr-2 text-sm"
                        >
                          {synonym}
                        </span>
                      );
                    })
                    .sort((a, b) => sortByKeys(a, b))}
                </div>
              ) : null}
            </div>
            <div className="grid grid-cols-3 gap-8">
              <div className="col-span-2">
                <div className="p-2 mb-1">
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
                  <Tooltip title="List view" placement="top">
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
                            const title = entity.getPropertyLabel(
                              annotationPredicate
                            )
                              ? entity
                                  .getPropertyLabel(annotationPredicate)
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
                          {entity.getParents().map((parent) => {
                            return parent ? (
                              <li key={randomString()}>{parent}</li>
                            ) : null;
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
        {!ontology && loading ? (
          <LoadingOverlay message="Loading entity..." />
        ) : null}
      </main>
    </div>
  );
}
