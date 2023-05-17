import { AccountTree, Share } from "@mui/icons-material";
import { useEffect, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import { copyToClipboard, toCamel } from "../../../app/util";
import ApiLinks from "../../../components/ApiLinks";
import Header from "../../../components/Header";
import LanguagePicker from "../../../components/LanguagePicker";
import LoadingOverlay from "../../../components/LoadingOverlay";
import SearchBox from "../../../components/SearchBox";
import LinkedEntities from "../../../model/LinkedEntities";
import { getClassInstances, getEntity, getOntology } from "../ontologiesSlice";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import ClassInstancesSection from "./entityPageSections/ClassInstancesSection";
import DefiningOntologiesSection from "./entityPageSections/DefiningOntologiesSection";
import DisjointWithSection from "./entityPageSections/DisjointWithSection";
import EntityAnnotationsSection from "./entityPageSections/EntityAnnotationsSection";
import EntityDescriptionSection from "./entityPageSections/EntityDescriptionSection";
import EntityEquivalentsSection from "./entityPageSections/EntityEquivalentsSection";
import EntityParentsSection from "./entityPageSections/EntityParentsSection";
import EntityRelatedFromSection from "./entityPageSections/EntityRelatedFromSection";
import EntitySynonymsSection from "./entityPageSections/EntitySynonymsSection";
import IndividualDifferentFromSection from "./entityPageSections/IndividualDifferentFromSection";
import IndividualPropertyAssertionsSection from "./entityPageSections/IndividualPropertyAssertionsSection";
import IndividualSameAsSection from "./entityPageSections/IndividualSameAsSection";
import IndividualTypesSection from "./entityPageSections/IndividualTypesSection";
import PropertyChainSection from "./entityPageSections/PropertyChainSection";
import PropertyCharacteristicsSection from "./entityPageSections/PropertyCharacteristicsSection";
import PropertyInverseOfSection from "./entityPageSections/PropertyInverseOfSection";

export default function EntityPage({
  entityType,
}: {
  entityType: "classes" | "properties" | "individuals";
}) {
  const params = useParams();
  const ontologyId: string = params.ontologyId as string;
  const entityIri: string = params.entityIri as string;

  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const entity = useAppSelector((state) => state.ontologies.entity);
  const loading = useAppSelector((state) => state.ontologies.loadingEntity);
  const classInstances = useAppSelector(
    (state) => state.ontologies.classInstances
  );
  const errorMessage = useAppSelector((state) => state.ontologies.errorMessage);

  const [searchParams, setSearchParams] = useSearchParams();
  const lang = searchParams.get("lang") || "en";

  const [viewMode, setViewMode] = useState<"tree" | "graph">("tree");
  const linkedEntities = entity
    ? entity.getLinkedEntities()
    : new LinkedEntities({});

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

  const ols3EntityType = {
    classes: "terms",
    properties: "properties",
    individuals: "individuals",
  }[entityType];

  useEffect(() => {
    if (ontologyId) dispatch(getOntology({ ontologyId, lang }));
  }, [dispatch, ontologyId, lang]);

  useEffect(() => {}, [searchParams]);

  useEffect(() => {
    if (entityIri || searchParams) {
      const searchParamsCopy = new URLSearchParams();
      searchParams.forEach((value: string, key: string) => {
        searchParamsCopy.append(toCamel(key), value);
      });
      dispatch(
        getEntity({
          ontologyId,
          entityType,
          entityIri,
          searchParams: searchParamsCopy,
        })
      );
    }
  }, [dispatch, ontologyId, entityType, entityIri, searchParams]);

  useEffect(() => {
    if (entity && entityType === "classes") {
      const searchParamsCopy = new URLSearchParams();
      searchParams.forEach((value: string, key: string) => {
        searchParamsCopy.append(toCamel(key), value);
      });
      dispatch(
        getClassInstances({
          ontologyId: entity.getOntologyId(),
          classIri: entity.getIri(),
          searchParams: searchParamsCopy,
        })
      );
    }
  }, [dispatch, entityType, entity, searchParams]);

  const navigate = useNavigate();
  useEffect(() => {
    if (errorMessage) navigate("/error", { state: { message: errorMessage } });
  }, [errorMessage, navigate]);

  document.title = entity?.getShortForm() || entity?.getName() || ontologyId;
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto" style={{ position: "relative" }}>
        {ontology && entity ? (
          <div className="my-8 mx-2">
            <div className="flex flex-row justify-between items-center px-2 mb-4">
              <div>
                <Link className="link-default" to={"/ontologies"}>
                  Ontologies
                </Link>
                <span className="px-2 text-sm" style={{ color: "grey" }}>
                  ▸
                </span>
                <Link to={"/ontologies/" + ontologyId}>
                  <span
                    className="link-ontology px-2 py-1 rounded-md text-sm text-white uppercase"
                    title={ontologyId.toUpperCase()}
                  >
                    {ontologyId}
                  </span>
                </Link>
                <span className="px-2 text-sm" style={{ color: "gray" }}>
                  ▸
                </span>
                <span>
                  <Link
                    to={"/ontologies/" + ontologyId + "?tab=" + entityType}
                    className="link-default capitalize"
                  >
                    {entity.getTypePlural()}
                  </Link>
                </span>
                <span className="px-2 text-sm" style={{ color: "gray" }}>
                  ▸
                </span>
                <span
                  className="link-entity px-2 py-1 rounded-md text-sm text-white uppercase"
                  title={entity.getShortForm() || entity.getName()}
                >
                  {entity.getShortForm() || entity.getName()}
                </span>
                <span className="text-sm text-neutral-default">
                  &nbsp; &nbsp;
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
              <div className="flex flex-row items-center gap-4">
                <LanguagePicker
                  ontology={ontology}
                  lang={lang}
                  onChangeLang={(lang) => setSearchParams({ lang })}
                />
                <ApiLinks
                  apiUrl={`${
                    process.env.REACT_APP_APIURL
                  }api/ontologies/${ontologyId}/${ols3EntityType}/${encodeURIComponent(
                    encodeURIComponent(entityIri)
                  )}`}
                  betaApiUrl={`${
                    process.env.REACT_APP_APIURL
                  }api/v2/ontologies/${ontologyId}/${entity.getTypePlural()}/${encodeURIComponent(
                    encodeURIComponent(entityIri)
                  )}`}
                />
              </div>
            </div>
            <div className="py-1" />
            {/* spacer */}
            <div className="flex flex-nowrap gap-4 mb-4">
              <SearchBox
                ontologyId={ontologyId}
                placeholder={`Search ${ontologyId.toUpperCase()}...`}
              />
            </div>
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="font-bold mb-4 flex flex-row items-center">
                <span className="text-2xl mr-3">{entity.getName()}</span>
                {!entity.isCanonical() && (
                  <span className="text-white text-xs bg-neutral-default px-2 py-1 rounded-md uppercase">
                    Imported
                  </span>
                )}
              </div>
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
              <EntityDescriptionSection
                entity={entity}
                linkedEntities={linkedEntities}
              />
              <DefiningOntologiesSection
                entity={entity}
                linkedEntities={linkedEntities}
              />
              <EntitySynonymsSection
                entity={entity}
                linkedEntities={linkedEntities}
              />
            </div>
            <div className="py-2 mb-1">
              <button
                className={`font-bold mr-3 ${
                  viewMode === "tree" ? "button-orange-active" : "button-orange"
                }`}
                onClick={() => setViewMode("tree")}
              >
                <div className="flex gap-2">
                  <AccountTree />
                  <div>Tree</div>
                </div>
              </button>
              <button
                className={`font-bold ${
                  viewMode === "graph"
                    ? "button-orange-active"
                    : "button-orange"
                }`}
                onClick={() => setViewMode("graph")}
              >
                <div className="flex gap-2">
                  <Share />
                  <div>Graph</div>
                </div>
              </button>
            </div>
            {viewMode === "graph" && (
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
            )}
            {viewMode === "tree" && (
              <div className="grid grid-cols-3 gap-8">
                <div className="col-span-2">
                  <EntityTree
                    ontology={ontology}
                    entityType={
                      {
                        class: "classes",
                        property: "properties",
                        individual: "individuals",
                      }[entity.getType()]
                    }
                    selectedEntity={entity}
                    lang={lang}
                  />
                </div>
                <div className="col-span-1">
                  <details open className="p-2">
                    <summary className="p-2 mb-2 border-b-2 border-grey-default text-lg link-orange">
                      <span className="capitalize">
                        {entity.getType()} Information
                      </span>
                    </summary>
                    <div className="py-2 break-words space-y-4">
                      <PropertyCharacteristicsSection entity={entity} />
                      <IndividualPropertyAssertionsSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <EntityAnnotationsSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                    </div>
                  </details>
                  <details open className="p-2">
                    <summary className="p-2 mb-2 border-b-2 border-grey-default text-lg link-orange">
                      <span className="capitalize">
                        {entity.getType()} Relations
                      </span>
                    </summary>
                    <div className="py-2 break-words space-y-4">
                      <IndividualTypesSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <IndividualSameAsSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <IndividualDifferentFromSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <DisjointWithSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <PropertyInverseOfSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <PropertyChainSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <EntityEquivalentsSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <EntityParentsSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <EntityRelatedFromSection
                        entity={entity}
                        linkedEntities={linkedEntities}
                      />
                      <ClassInstancesSection
                        entity={entity}
                        classInstances={classInstances}
                        linkedEntities={linkedEntities}
                      />
                    </div>
                  </details>
                </div>
              </div>
            )}
          </div>
        ) : null}
        {!ontology || loading ? (
          <LoadingOverlay message="Loading entity..." />
        ) : null}
      </main>
    </div>
  );
}
