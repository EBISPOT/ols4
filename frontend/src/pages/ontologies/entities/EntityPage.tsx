import { AccountTree, Share } from "@mui/icons-material";
import { useEffect, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import { copyToClipboard } from "../../../app/util";
import ApiLinks from "../../../components/ApiLinks";
import { Banner } from "../../../components/Banner";
import Header from "../../../components/Header";
import LanguagePicker from "../../../components/LanguagePicker";
import LoadingOverlay from "../../../components/LoadingOverlay";
import SearchBox from "../../../components/SearchBox";
import LinkedEntities from "../../../model/LinkedEntities";
import Reified from "../../../model/Reified";
import {
  getClassInstances,
  getEntityWithType,
  getOntology,
} from "../ontologiesSlice";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import ClassInstancesSection from "./entityPageSections/ClassInstancesSection";
import DefiningOntologiesSection from "./entityPageSections/DefiningOntologiesSection";
import DisjointWithSection from "./entityPageSections/DisjointWithSection";
import DomainSection from "./entityPageSections/DomainSection";
import EntityAnnotationsSection from "./entityPageSections/EntityAnnotationsSection";
import EntityDescriptionSection from "./entityPageSections/EntityDescriptionSection";
import EntityEquivalentsSection from "./entityPageSections/EntityEquivalentsSection";
import EntityImagesSection from "./entityPageSections/EntityImagesSection";
import EntityParentsSection from "./entityPageSections/EntityParentsSection";
import EntityRelatedFromSection from "./entityPageSections/EntityRelatedFromSection";
import EntitySynonymsSection from "./entityPageSections/EntitySynonymsSection";
import HasKeySection from "./entityPageSections/HasKeySection";
import IndividualDifferentFromSection from "./entityPageSections/IndividualDifferentFromSection";
import IndividualPropertyAssertionsSection from "./entityPageSections/IndividualPropertyAssertionsSection";
import IndividualSameAsSection from "./entityPageSections/IndividualSameAsSection";
import IndividualTypesSection from "./entityPageSections/IndividualTypesSection";
import MetadataTooltip from "./entityPageSections/MetadataTooltip";
import PropertyChainSection from "./entityPageSections/PropertyChainSection";
import PropertyCharacteristicsSection from "./entityPageSections/PropertyCharacteristicsSection";
import PropertyInverseOfSection from "./entityPageSections/PropertyInverseOfSection";
import RangeSection from "./entityPageSections/RangeSection";
import addLinksToText from "./entityPageSections/addLinksToText";

export default function EntityPage({
  entityType,
}: {
  entityType: "classes" | "properties" | "individuals";
}) {
  const params = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const ontologyId: string = params.ontologyId as string;
  const entityIri: string =
    (params.entityIri as string) || searchParams.get("iri") || "";
  const lang = searchParams.get("lang") || "en";

  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const entity = useAppSelector((state) => state.ontologies.entity);
  const loading = useAppSelector((state) => state.ontologies.loadingEntity);
  const classInstances = useAppSelector(
    (state) => state.ontologies.classInstances
  );
  const errorMessage = useAppSelector((state) => state.ontologies.errorMessage);

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
      dispatch(
        getEntityWithType({
          ontologyId,
          entityType,
          entityIri,
          searchParams,
        })
      );
    }
  }, [dispatch, ontologyId, entityType, entityIri, searchParams]);

  useEffect(() => {
    if (entity && entityType === "classes") {
      dispatch(
        getClassInstances({
          ontologyId: entity.getOntologyId(),
          classIri: entity.getIri(),
          searchParams,
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
      <main className="container mx-auto px-4">
        {ontology && entity ? (
          <div className="my-8">
            <div className="flex flex-wrap justify-between items-center gap-y-2 px-1 mb-4">
              <div className="flex flex-wrap items-center gap-y-2">
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
                  className="link-entity px-2 py-1 rounded-md text-sm text-white"
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
                {entityIri ? (
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
                ) : null}
              </div>
            </div>
            {entity.isDeprecated() && (
              <Banner type="error">
                <strong className="inline-block mb-2">
                  This {entity.getType()} is deprecated.
                </strong>
                {entity.getDeprecationVersion() && (
                  <div>
                    Deprecated since version&thinsp;
                    <i>{entity.getDeprecationVersion()}</i>
                  </div>
                )}
                {entity.getDeprecationReplacement() && (
                  <div>
                    Replaced by&thinsp;
                    <i>
                      {addLinksToText(
                        entity.getDeprecationReplacement(),
                        linkedEntities,
                        ontologyId,
                        entity,
                        entityType
                      )}
                    </i>
                  </div>
                )}
                {entity.getDeprecationReason() &&
                  entity.getDeprecationReason().length > 0 && (
                    <div>
                      Reason:&thinsp;
                      <i>
                        {entity
                          .getDeprecationReason()
                          .map((reason: Reified<any>) => {
                            return (
                              <span key={reason.value.toString()}>
                                {addLinksToText(
                                  reason.value,
                                  linkedEntities,
                                  ontologyId,
                                  entity,
                                  entityType
                                )}
                                {reason.hasMetadata() ? (
                                  <MetadataTooltip
                                    metadata={reason.getMetadata()}
                                    linkedEntities={linkedEntities}
                                  />
                                ) : null}
                              </span>
                            );
                          })}
                      </i>
                    </div>
                  )}
              </Banner>
            )}
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="overflow-x-auto mb-4">
                <div className="font-bold mb-4 flex flex-row items-center">
                  <span className="text-2xl mr-3">{entity.getName()}</span>
                  {!entity.isCanonical() && (
                    <span className="text-white text-xs bg-neutral-default px-2 py-1 mr-1 rounded-md uppercase">
                      Imported
                    </span>
                  )}
                  {entity.isDeprecated() && (
                    <span className="text-white text-xs bg-red-500 px-2 py-1 rounded-md uppercase">
                      Deprecated
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
              </div>
              <DefiningOntologiesSection
                entity={entity}
                linkedEntities={linkedEntities}
              />
              <EntitySynonymsSection
                entity={entity}
                linkedEntities={linkedEntities}
              />
              <EntityImagesSection
                entity={entity}
                linkedEntities={linkedEntities}
              />
              <SearchBox
                ontologyId={ontologyId}
                placeholder={`Search ${ontologyId.toUpperCase()}...`}
              />
            </div>
            <div className="flex flex-col-reverse lg:grid lg:grid-cols-3 lg:gap-4">
              <div className="lg:col-span-2">
                <div className="py-2">
                  <button
                    className={`font-bold mr-3 ${
                      viewMode === "tree"
                        ? "button-orange-active"
                        : "button-orange"
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
                    onNavigateToEntity={(ontology, entity) =>
                      navigate(
                        `/ontologies/${ontology.getOntologyId()}/${entity.getTypePlural()}/${encodeURIComponent(
                          encodeURIComponent(entity.getIri())
                        )}?lang=${lang}`
                      )
                    }
                    onNavigateToOntology={(ontologyId, entity) =>
                      navigate(
                        `/ontologies/${ontologyId}/${entity.getTypePlural()}/${encodeURIComponent(
                          encodeURIComponent(entity.getIri())
                        )}?lang=${lang}`
                      )
                    }
                  />
                )}
              </div>
              <div className="lg:col-span-1">
                <details open className="p-2">
                  <summary className="p-2 mb-2 border-b-2 border-grey-default text-lg link-orange">
                    <span className="capitalize">
                      {entity.getType()} Information
                    </span>
                  </summary>
                  <div className="py-2 break-words space-y-4">
                    <HasKeySection
                      entity={entity}
                      linkedEntities={linkedEntities}
                    />
                    <PropertyCharacteristicsSection entity={entity} />
                    <DomainSection
                      entity={entity}
                      linkedEntities={linkedEntities}
                    />
                    <RangeSection
                      entity={entity}
                      linkedEntities={linkedEntities}
                    />
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
          </div>
        ) : null}
        {!ontology || loading ? (
          <LoadingOverlay message="Loading entity..." />
        ) : null}
      </main>
    </div>
  );
}
