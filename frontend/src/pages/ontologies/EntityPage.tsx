import { AccountTree, Share } from "@mui/icons-material";
import { Tooltip } from "@mui/material";
import { Fragment, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { asArray, copyToClipboard, randomString, sortByKeys } from "../../app/util";
import ClassExpression from "../../components/ClassExpression";
import EntityLink from "../../components/EntityLink";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import Class from "../../model/Class";
import Entity from "../../model/Entity";
import Individual from "../../model/Individual";
import Property from "../../model/Property";
import LinkedEntities from "../../model/LinkedEntities";
import Reified from "../../model/Reified";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import { getClassInstances, getEntity, getOntology } from "./ontologiesSlice";
import { Page } from "../../app/api";
import { useParams, useSearchParams } from "react-router-dom";
import LanguagePicker from "../../components/LanguagePicker";
import SearchBox from "../../components/SearchBox";
import ApiLinks from "../../components/ApiLinks";

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
      style={{ position: "absolute", top: "-16px", right: 0, width: "150px" }}
    >
	<div className="flex gap-4">
            <LanguagePicker
              ontology={ontology}
              lang={lang}
              onChangeLang={(lang) => setSearchParams({ lang: lang })}
            />
		<ApiLinks apiUrl={`${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}/${ols3EntityType}/${encodeURIComponent(encodeURIComponent(entityIri))}`} />
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
                    className={`button-primary font-bold mr-3 ${
                      viewMode === "tree"
                        ? "shadow-button-active translate-x-2 translate-y-2 hover:shadow-button-active hover:translate-x-2 hover:translate-y-2"
                        : ""
                    }`}
                    onClick={() => setViewMode("tree")}
                  >
			<div className="flex gap-2"><AccountTree/><div>Tree</div></div>
                  </button>
                  <button
                    className={`button-primary font-bold ${
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
                        <IndividualNegativePropertyAssertionsSection entity={entity} linkedEntities={linkedEntities} />
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

function EntityDescriptionSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
	let desc = entity.getDescriptionAsArray()
  return (
    <p>
      {desc.map((definition: Reified<any>, i:number) => {
        const hasMetadata = definition.hasMetadata();
        return (
		<Fragment>
            <p>
          <span key={randomString()}>
            {addEntityLinksToText(definition.value, linkedEntities, entity.getOntologyId(), entity, entity.getTypePlural())}
            {hasMetadata ? (
              <Tooltip
                title={Object.keys(definition.getMetadata())
                  .map((key) => {
                    let label = linkedEntities.getLabelForIri(key);
                    if (label) {
                      return (
                        "*" +
                        definition.getMetadata()[key] +
                        " (" +
                        label.replaceAll("_", " ") +
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
            </p>
	{i <desc.length -1 ? <div className="py-1"/>:<Fragment/>}
	</Fragment>
        );
      })}
    </p>
  );
}
function EntityAnnotationsSection({
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
            ? entity.getLabelForIri(annotationPredicate).replaceAll("_", " ")
            : annotationPredicate
                .substring(annotationPredicate.lastIndexOf("/") + 1)
                .substring(
                  annotationPredicate
                    .substring(annotationPredicate.lastIndexOf("/") + 1)
                    .lastIndexOf("#") + 1
                )
                .replaceAll("_", " ");

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
                        <li key={randomString()}>
                          {renderAnnotation(annotation)}
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
	if(value.value.indexOf("://") !== -1) {
		return <Link className="link-default" to={value.value}>{value.value}</Link>
	} else {
		return <span>{value.value}</span>
	}
    }
  }
}

function DefiningOntologiesSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let definedBy = entity
    .getDefinedBy()
    .filter((ontId) => ontId !== entity.getOntologyId());
  let appearsIn = entity
    .getAppearsIn()
    .filter(
      (ontId) =>
        ontId !== entity.getOntologyId() && definedBy.indexOf(ontId) === -1
    );

    let [appearsInExpanded,setAppearsInExpanded] = useState<boolean>(false);

    const MAX_DISPLAY_APPEARS_IN = 5;

  return (
    <Fragment>
      {definedBy && definedBy.length > 0 && (
        <div className="mb-2">
          <span className="font-bold mr-2">Defined by</span>
          {definedBy.map((definedBy: string) => {
            return (
              <Link
                className="link-default"
                to={
                  "/ontologies/" +
                  definedBy +
                  `/${entity.getTypePlural()}/` +
                  encodeURIComponent(encodeURIComponent(entity.getIri()))
                }
              >
                <span
                  className="link-ontology px-3 py-1 rounded-lg text-sm text-white uppercase mr-1"
                  title={definedBy}
                >
                  {definedBy}
                </span>
              </Link>
            );
          })}
        </div>
      )}
      {appearsIn && appearsIn.length > 0 && (
        <div className="mb-2" style={{maxWidth: "100%", inlineSize:"100%" }}>
          <span className="font-bold mr-2">Also appears in</span>
	  { (appearsIn.length <= MAX_DISPLAY_APPEARS_IN || appearsInExpanded) ?
		appearsIn.map(renderAppearsIn)
	    : <Fragment>
		{
		appearsIn.slice(0, MAX_DISPLAY_APPEARS_IN).map(renderAppearsIn)
		}
		&nbsp;
		<a className="link-default" style={{fontStyle:'italic'}} onClick={() => setAppearsInExpanded(true)}>+ {appearsIn.length - MAX_DISPLAY_APPEARS_IN}</a>
	    </Fragment>
	  }
        </div>
      )}
    </Fragment>
  );

  function renderAppearsIn(appearsIn:string) {
	return (
	<Link
	className="my-2"
	style={{display: 'inline-block'}}
		to={
		"/ontologies/" +
		appearsIn +
		`/${entity.getTypePlural()}/` +
		encodeURIComponent(encodeURIComponent(entity.getIri()))
		}
	>
		<span
		className="link-ontology px-3 py-1 rounded-lg text-sm text-white uppercase mr-1"
		title={appearsIn}
		>
		{appearsIn}
		</span>
	</Link>
	);
  }
}

function EntitySynonymsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  let synonyms = entity.getSynonyms();

  if (!synonyms || synonyms.length === 0) {
    return <Fragment />;
  }

  return (
    <div className="mb-2">
      <span className="font-bold mr-2">Synonym</span>
      {synonyms
        .map((synonym: Reified<any>) => {
          const hasMetadata = synonym.hasMetadata();
          return (
            <span
              key={synonym.value.toString().toUpperCase() + randomString()}
              className="flex-none bg-grey-default rounded-sm font-mono py-1 px-3 mr-2 text-sm"
            >
              {synonym.value}
              {hasMetadata && (
                <MetadataTooltip
                  metadata={synonym.getMetadata()}
                  linkedEntities={linkedEntities}
                />
              )}
            </span>
          );
        })
        .sort((a, b) => sortByKeys(a, b))}
    </div>
  );
}

function ClassInstancesSection({
  entity,
  classInstances,
  linkedEntities,
}: {
  entity: Entity;
  classInstances: Page<Entity> | null;
  linkedEntities: LinkedEntities;
}) {
  if (entity.getType() != "class") return <Fragment />;

  if (!classInstances || classInstances.elements.length === 0)
    return <Fragment />;

  return (
    <div>
      <div className="font-bold">Instances</div>
      <ul className="list-disc list-inside">
        {classInstances &&
          classInstances.elements.map((instance: Entity) => {
            return (
              <li key={randomString()}>
                <EntityLink
                  ontologyId={entity.getOntologyId()}
		  currentEntity={entity}
                  entityType="individuals"
                  iri={instance.getIri()}
                  linkedEntities={linkedEntities}
                />
              </li>
            );
          })}
      </ul>
    </div>
  );
}

function EntityEquivalentsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  let equivalents = entity?.getEquivalents();

  if (!equivalents || equivalents.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Equivalent to</div>
      <ul className="list-disc list-inside">
        {equivalents.map((eqClass: Reified<any>) => {
          const hasMetadata = eqClass.hasMetadata();
          return (
            <li key={randomString()}>
              <ClassExpression
                ontologyId={entity.getOntologyId()}
		currentEntity={entity} 
                expr={eqClass.value}
                linkedEntities={linkedEntities}
              />
              {hasMetadata && (
                <MetadataTooltip
                  metadata={eqClass.getMetadata()}
                  linkedEntities={linkedEntities}
                />
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function EntityParentsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  let parents = entity?.getSuperEntities();

  if (!parents || parents.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">
        Sub{entity.getType().toString().toLowerCase()} of
      </div>
      {parents.length === 1 ? (
        <p>
          <ClassExpression
            ontologyId={entity.getOntologyId()}
	    currentEntity={entity}
            expr={parents[0].value}
            linkedEntities={linkedEntities}
          />
          {parents[0].hasMetadata() && (
            <MetadataTooltip
              metadata={parents[0].getMetadata()}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {parents.map((parent: Reified<any>) => {
            return (
              <li key={randomString()}>
                <ClassExpression
                  ontologyId={entity.getOntologyId()}
		  currentEntity={entity}
                  expr={parent.value}
                  linkedEntities={linkedEntities}
                />
                {parent.hasMetadata() && (
                  <MetadataTooltip
                    metadata={parent.getMetadata()}
                    linkedEntities={linkedEntities}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function EntityRelatedFromSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  let relatedFroms = entity?.getRelatedFrom();

  if (!relatedFroms || relatedFroms.length === 0) {
    return <Fragment />;
  }

  let predicates = Array.from(
    new Set(relatedFroms.map((relatedFrom) => relatedFrom.value.property))
  );

  return (
    <div>
      <div className="font-bold">Related from</div>
      {predicates.map((p) => {
        let label = linkedEntities.getLabelForIri(p);
        return (
          <div key={p + randomString()}>
            <div>
              <i>{label || p}</i>
            </div>
            <div className="pl-4">
              <ul className="list-disc list-inside">
                {relatedFroms
                  .filter((relatedFrom) => relatedFrom.value.property === p)
                  .map((relatedFrom) => {
                    let relatedIri = relatedFrom.value.value;
                    // let label = linkedEntities.getLabelForIri(relatedIri);
                    return (
                      <li key={relatedIri + randomString()}>
                        <EntityLink
                          ontologyId={entity.getOntologyId()}
			  currentEntity={entity}
                          entityType={"classes"}
                          iri={relatedIri}
                          linkedEntities={linkedEntities}
                        />
                      </li>
                    );
                  })}
              </ul>
            </div>
          </div>
        );
      })}

      <ul className="list-disc list-inside"></ul>
    </div>
  );
}

function MetadataTooltip({
  metadata,
  linkedEntities,
}: {
  metadata: any;
  linkedEntities: LinkedEntities;
}) {
  return (
    <Tooltip
      title={Object.keys(metadata)
        .map((key) => {
          let label = linkedEntities.getLabelForIri(key) || key;
          if (label) {
            return (
              "*" + metadata[key] + " (" + label.replaceAll("_", " ") + ")"
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
  );
}

function IndividualTypesSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let types = entity.getIndividualTypes();

  if (!types || types.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Type</div>
      {types.length === 1 ? (
        <p>
          {typeof types[0] === "object" && !Array.isArray(types[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
		     currentEntity={entity}
              expr={types[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"classes"}
              iri={types[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {types.map((type) => {
            return (
              <li key={randomString()}>
                {typeof type === "object" && !Array.isArray(type) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
		     currentEntity={entity}
                    expr={type}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"classes"}
                    iri={type}
                    linkedEntities={linkedEntities}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function IndividualSameAsSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let sameAses = entity.getSameAs();

  if (!sameAses || sameAses.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Same as</div>
      {sameAses.length === 1 ? (
        <p>
          {typeof sameAses[0] === "object" && !Array.isArray(sameAses[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={sameAses[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"individuals"}
              iri={sameAses[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {sameAses.map((sameAs) => {
            return (
              <li key={randomString()}>
                {typeof sameAs === "object" && !Array.isArray(sameAs) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    expr={sameAs}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"individuals"}
                    iri={sameAs}
                    linkedEntities={linkedEntities}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function IndividualDifferentFromSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Individual)) {
    return <Fragment />;
  }

  let differentFroms = entity.getDifferentFrom();

  if (!differentFroms || differentFroms.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Different from</div>
      {differentFroms.length === 1 ? (
        <p>
          {typeof differentFroms[0] === "object" &&
          !Array.isArray(differentFroms[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
              currentEntity={entity}
              expr={differentFroms[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"individuals"}
              iri={differentFroms[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {differentFroms.map((differentFrom) => {
            return (
              <li key={randomString()}>
                {typeof differentFrom === "object" &&
                !Array.isArray(differentFrom) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                    currentEntity={entity}
                    expr={differentFrom}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"individuals"}
                    iri={differentFrom}
                    linkedEntities={linkedEntities}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function DisjointWithSection({
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
    <div>
      <div className="font-bold">Disjoint with</div>
      {disjointWiths.length === 1 ? (
        <p>
          {typeof disjointWiths[0] === "object" &&
          !Array.isArray(disjointWiths[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
                currentEntity={entity}
              expr={disjointWiths[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={
                entity.getType() === "property" ? "properties" : "classes"
              }
              iri={disjointWiths[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {disjointWiths.map((disjointWith) => {
            return (
              <li key={randomString()}>
                {typeof disjointWith === "object" &&
                !Array.isArray(disjointWith) ? (
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
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function PropertyInverseOfSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  let inverseOfs = entity.getInverseOf();

  if (!inverseOfs || inverseOfs.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold">Inverse of</div>
      {inverseOfs.length === 1 ? (
        <p>
          {typeof inverseOfs[0] === "object" &&
          !Array.isArray(inverseOfs[0]) ? (
            <ClassExpression
              ontologyId={entity.getOntologyId()}
                currentEntity={entity}
              expr={inverseOfs[0]}
              linkedEntities={linkedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
	      currentEntity={entity}
              entityType={"properties"}
              iri={inverseOfs[0]}
              linkedEntities={linkedEntities}
            />
          )}
        </p>
      ) : (
        <ul className="list-disc list-inside">
          {inverseOfs.map((inverseOf) => {
            return (
              <li key={randomString()}>
                {typeof inverseOf === "object" &&
                !Array.isArray(inverseOf) ? (
                  <ClassExpression
                    ontologyId={entity.getOntologyId()}
                currentEntity={entity}
                    expr={inverseOf}
                    linkedEntities={linkedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
		    currentEntity={entity}
                    entityType={"properties"}
                    iri={inverseOf}
                    linkedEntities={linkedEntities}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function PropertyChainSection({
  entity,
  linkedEntities,
}: {
  entity: Entity;
  linkedEntities: LinkedEntities;
}) {
  if (!(entity instanceof Property)) {
    return <Fragment />;
  }

  // TODO: reification discarded here
  let propertyChains:any[] = entity.getPropertyChains().map(rf => rf.value)

  if (!propertyChains || propertyChains.length === 0) {
    return <Fragment />;
  }

  let hasMultipleChains = propertyChains.filter(chain => Array.isArray(chain)).length > 0

  return (
    <div>
      <div className="font-bold">{hasMultipleChains ? "Property chains" : "Property chain"}</div>
      { (!hasMultipleChains) ?
        <p>
		<PropertyChain propertyChain={propertyChains} entity={entity} linkedEntities={linkedEntities} />
        </p>
       : (
        <ul className="list-disc list-inside">
          {propertyChains.map((propertyChain) => {
            return (
              <li key={randomString()}>
		<PropertyChain propertyChain={propertyChain} entity={entity} linkedEntities={linkedEntities} />
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function PropertyChain({propertyChain, entity, linkedEntities}:{propertyChain:any, entity:Entity, linkedEntities:any}) {

	let chain = asArray(propertyChain)

	return <Fragment>
		{
			chain.reverse().map((propertyIri, i) => {
				return <Fragment>
					<EntityLink 
						ontologyId={entity.getOntologyId()}
						currentEntity={entity}
						entityType={"properties"}
						iri={propertyIri}
						linkedEntities={linkedEntities}
					/>
					<Fragment>
						{i < chain.length - 1 &&
						<span className="px-2 text-sm" style={{color:'gray'}}>◂</span>
						}
					</Fragment>
				</Fragment>
			})
		}
	</Fragment>
}

function addEntityLinksToText(text:string, linkedEntities:LinkedEntities, ontologyId:string, currentEntity:Entity, entityType:"ontologies"|"classes"|"properties"|"individuals") {

	let linksToSplice:Array<{start:number, end:number, link:JSX.Element}> = []

	for(let entityId of Object.keys(linkedEntities.linkedEntities)) {

		for(let n = text.indexOf(entityId, 0); n !== -1; n = text.indexOf(entityId, n)) {

			linksToSplice.push({
				start: n,
				end: n + entityId.length,
				link: <EntityLink
					ontologyId={ontologyId}
					currentEntity={currentEntity}
					entityType={entityType}
					iri={entityId}
					linkedEntities={linkedEntities}
				/>});

			n += entityId.length
		}
	}

	if(linksToSplice.length === 0)
		return text;

	linksToSplice.sort((a, b) => a.start - b.start);
	console.dir(linksToSplice);

	let res:JSX.Element[] = []

	let n = 0;

	for(let link of linksToSplice) {
		res.push(<Fragment>{text.substring(n, link.start)}</Fragment>);
		res.push(link.link);
		n = link.end;
	}

	res.push(<Fragment>{text.slice(n)}</Fragment>);

	return res;
}

function PropertyCharacteristicsSection({entity}:{entity:Entity}) {

if(entity.getType() !== 'property')
  return <Fragment/>  

  let characteristics = entity.getRdfTypes().map(type => {

    return ({
      'http://www.w3.org/2002/07/owl#FunctionalProperty': 'Functional',
      'http://www.w3.org/2002/07/owl#InverseFunctionalProperty': 'Inverse Functional',
      'http://www.w3.org/2002/07/owl#TransitiveProperty': 'Transitive',
      'http://www.w3.org/2002/07/owl#SymmetricProperty': 'Symmetric',
      'http://www.w3.org/2002/07/owl#AsymmetricProperty': 'Asymmetric',
      'http://www.w3.org/2002/07/owl#ReflexiveProperty': 'Reflexive',
      'http://www.w3.org/2002/07/owl#IrreflexiveProperty': 'Irreflexive',
    })[type]

  }).filter((type) => !!type)

  if(characteristics.length === 0)
    return <Fragment/>

  return <div>
              <div className="font-bold">Characteristics</div>
              {characteristics.length === 1 ? (
                <p>{characteristics[0]}</p>
              ) : (
                <ul className="list-disc list-inside">
                  {characteristics
                    .map((characteristic) => {
                      return (
                        <li key={randomString()}>
                          {characteristic}
                        </li>
                      );
                    })
                    .sort((a, b) => sortByKeys(a, b))}
                </ul>
              )}
          </div>

}

function IndividualNegativePropertyAssertionsSection({entity, linkedEntities}:{entity:Entity, linkedEntities:LinkedEntities}) {

	if(entity.getType() !== 'individual')
		return <Fragment/>  

	let negativePropertyAssertionKeys =
		Object.keys(entity.properties)
			.filter(k => k.startsWith("negativePropertyAssertion+"));

  if(negativePropertyAssertionKeys.length === 0)
    return <Fragment/>
	
    let negativePropertyAssertions:JSX.Element[] = []

    for(let k of negativePropertyAssertionKeys) {

	let iri = k.slice("negativePropertyAssertion+".length)
	let values = asArray(entity.properties[k])

	for(let v of values) {
		negativePropertyAssertions.push(
			<span>
				<EntityLink ontologyId={entity.getOntologyId()} currentEntity={entity} entityType="properties" iri={iri} linkedEntities={linkedEntities} />
				{" "}
				{ v.indexOf('://') !== -1 ?
					<EntityLink ontologyId={entity.getOntologyId()} currentEntity={entity} entityType="individuals" iri={v} linkedEntities={linkedEntities} />
				:
					{v}
				}
			</span>
		)
	}
    }

  return <div>
              <div className="font-bold">Negative property assertions</div>
              {negativePropertyAssertions.length === 1 ? (
                <p>{negativePropertyAssertions[0]}</p>
              ) : (
                <ul className="list-disc list-inside">
                  {negativePropertyAssertions
                    .map((npa) => {
                      return (
                        <li key={randomString()}>
                          {npa}
                        </li>
                      );
                    })
                    .sort((a, b) => sortByKeys(a, b))}
                </ul>
              )}
          </div>

}




