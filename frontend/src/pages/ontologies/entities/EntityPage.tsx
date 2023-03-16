import { AccountTree, Share } from "@mui/icons-material";
import { Tooltip } from "@mui/material";
import { Fragment, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import { asArray, copyToClipboard, randomString, sortByKeys } from "../../../app/util";
import Header from "../../../components/Header";
import LoadingOverlay from "../../../components/LoadingOverlay";
import LinkedEntities from "../../../model/LinkedEntities";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import { getClassInstances, getEntity, getOntology } from "../ontologiesSlice";
import { useParams, useSearchParams } from "react-router-dom";
import LanguagePicker from "../../../components/LanguagePicker";
import SearchBox from "../../../components/SearchBox";
import ApiLinks from "../../../components/ApiLinks";
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
  let ontologyId: string = params.ontologyId as string;
  let entityIri: string = decodeURIComponent(params.entityIri as string);

  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const entity = useAppSelector((state) => state.ontologies.entity);
  const loading = useAppSelector((state) => state.ontologies.loadingEntity);
  const loadingClassInstances = useAppSelector(
    (state) => state.ontologies.loadingClassInstances
  );
  const classInstances = useAppSelector(
    (state) => state.ontologies.classInstances
  );

  const [searchParams, setSearchParams] = useSearchParams();
  let lang = searchParams.get("lang") || "en";
  console.log("lang is " + lang);

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

  useEffect(() => {
	  dispatch(getOntology({ ontologyId, lang }));
  }, [ontologyId])

  useEffect(() => {

    dispatch(getEntity({ ontologyId, entityType, entityIri, lang }));

    if (entityType === "classes") {
      dispatch(getClassInstances({ ontologyId, classIri: entityIri, lang }));
    }
  }, [dispatch, ontology, entityType, entityIri, searchParams, lang]);

  if (entity) document.title = entity.getShortForm() || entity.getName();



  let ols3EntityType = ({
	'classes': 'terms',
	'properties': 'properties',
	'individuals': 'individuals'
  })[entityType]


  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto" style={{ position: "relative" }}>
        {ontology && entity ? (
          <Fragment>
    <div
      style={{ position: "absolute", top: "-16px", right: 0, width: "200px" }}
    >
	<div className="flex gap-4">
            <LanguagePicker
              ontology={ontology}
              lang={lang}
              onChangeLang={(lang) => setSearchParams({ lang: lang })}
            />
		<ApiLinks
			apiUrl={`${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}/${ols3EntityType}/${encodeURIComponent(encodeURIComponent(entityIri))}`}
			betaApiUrl={`${process.env.REACT_APP_APIURL}api/v2/ontologies/${ontologyId}/${ontology.getTypePlural()}/${encodeURIComponent(encodeURIComponent(entityIri))}`}
			/>
			</div>
			</div>
            <div className="my-8 mx-2">
              <div className="px-2 mb-4">
                <Link className="link-default" to={"/ontologies"}>
                  Ontologies
                </Link>
                <span className="px-2 text-sm" style={{ color: "grey" }}>
                  ▸
                </span>
                <Link to={"/ontologies/" + ontologyId}>
                  <span
                    className="link-ontology px-3 py-1 rounded-lg text-sm text-white uppercase"
                    title={ontologyId}
                  >
                    {ontologyId}
                  </span>
                </Link>
                <span className="px-2 text-sm" style={{ color: "gray" }}>
                  ▸
                </span>
                <span className="capitalize">
			<Link to={"/ontologies/" + ontologyId + "/" + entity.getTypePlural()} className="link-default">
			{entity.getTypePlural()}
			</Link>
		</span>
                <span className="px-2 text-sm" style={{ color: "gray" }}>
                  ▸
                </span>
                <span
                  className="link-entity px-3 py-1 rounded-lg text-sm text-white uppercase"
                  title={entity.getShortForm()}
                >
                  {entity.getShortForm()}
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
	      <div className="py-1"/>{/* spacer */}
		<div className="flex flex-nowrap gap-4 mb-4">
			<SearchBox ontologyId={ontologyId} placeholder={`Search ${ontologyId.toUpperCase()}...`}/>
		</div>
              <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
                <div className="text-2xl font-bold mb-4">
                  {entity.getName()}
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
                <div className="mb-4">
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
              </div>
              <div className="py-2 mb-1">
                  <button
                    className={`button-tertiary font-bold mr-3 ${
                      viewMode === "tree"
                        ? "shadow-button-active translate-x-2 translate-y-2 hover:shadow-button-active hover:translate-x-2 hover:translate-y-2"
                        : ""
                    }`}
                    onClick={() => setViewMode("tree")}
                  >
			<div className="flex gap-2"><AccountTree/><div>Tree</div></div>
                  </button>
                  <button
                    className={`button-tertiary font-bold ${
                      viewMode === "graph"
                        ? "shadow-button-active translate-x-2 translate-y-2 hover:shadow-button-active hover:translate-x-2 hover:translate-y-2"
                        : ""
                    }`}
                    onClick={() => setViewMode("graph")}
                  >
			<div className="flex gap-2"><Share/><div>Graph</div></div>
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
                      <summary className="p-2 mb-2 border-b-2 border-grey-default text-link-default text-lg cursor-pointer hover:text-link-hover hover:underline ">
                        <span className="capitalize">
                          {entity.getType()} Information
                        </span>
                      </summary>
                      <div className="py-2 break-words space-y-4">
                        <PropertyCharacteristicsSection entity={entity} />
                        <IndividualPropertyAssertionsSection entity={entity} linkedEntities={linkedEntities} />
                        <EntityAnnotationsSection
                          entity={entity}
                          linkedEntities={linkedEntities}
                        />
                      </div>
                    </details>
                    <details open className="p-2">
                      <summary className="p-2 mb-2 border-b-2 border-grey-default text-link-default text-lg cursor-pointer hover:text-link-hover hover:underline ">
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
          </Fragment>
        ) : null}
        {!ontology || loading ? (
          <LoadingOverlay message="Loading entity..." />
        ) : null}
      </main>
    </div>
  );
}

