import { AccountTree, Share } from "@mui/icons-material";
import { Tooltip } from "@mui/material";
import { Fragment, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { copyToClipboard, randomString, sortByKeys } from "../../app/util";
import ClassExpression from "../../components/ClassExpression";
import EntityLink from "../../components/EntityLink";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import Class from "../../model/Class";
import Entity from "../../model/Entity";
import Individual from "../../model/Individual";
import Property from "../../model/Property";
import ReferencedEntities from "../../model/ReferencedEntities";
import Reified from "../../model/Reified";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import { getClassInstances, getEntity, getOntology } from "./ontologiesSlice";
import { Page } from "../../app/api";
import { useParams, useSearchParams } from "react-router-dom";
import LanguagePicker from "../../components/LanguagePicker";

export default function EntityPage({
  entityType,
}: {
  entityType: "classes" | "properties" | "individuals";
}) {

  const params = useParams()
  let ontologyId:string = params.ontologyId as string
  let entityIri:string = decodeURIComponent(params.entityIri as string)

  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const entity = useAppSelector((state) => state.ontologies.entity);
  const loading = useAppSelector((state) => state.ontologies.loadingEntity);
  const loadingClassInstances = useAppSelector((state) => state.ontologies.loadingClassInstances);
  const classInstances = useAppSelector((state) => state.ontologies.classInstances);

  const [searchParams, setSearchParams] = useSearchParams();
  let lang = searchParams.get("lang") || "en"
  console.log('lang is ' + lang)

  const [viewMode, setViewMode] = useState<"tree" | "graph">("tree");
  const referencedEntities = entity
    ? entity.getReferencedEntities()
    : new ReferencedEntities({});

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

    if (!ontology)
	dispatch(getOntology({ontologyId, lang}));

    dispatch(getEntity({ ontologyId, entityType, entityIri, lang }));
 
   if (entityType === 'classes') {
	dispatch(getClassInstances({ ontologyId, classIri:entityIri, lang }))
   }

  }, [dispatch, ontology, ontologyId, entityType, entityIri, searchParams ]);

  if (entity) document.title = entity.getShortForm() || entity.getName();
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto" style={{position: 'relative'}}>
        {ontology && entity ? (
		<Fragment>
	<LanguagePicker ontology={ontology} lang={lang} onChangeLang={(lang) => setSearchParams({lang:lang}) } />
          <div className="my-8 mx-2">
            <div className="px-2 mb-4">
              <Link className="link-default" to={"/ontologies"} >
                Ontologies
              </Link>
              <span className="px-2 text-sm"  style={{color:'grey'}}>▸</span>
              <Link className="link-default" to={"/ontologies/" + ontologyId}>
		<span
		className="bg-link-default px-3 py-1 rounded-lg text-sm text-white uppercase"
		title={ontologyId}
		>
		{ontologyId}
		</span>
              </Link>
              <span className="px-2 text-sm" style={{color:'gray'}}>▸</span>
              <span className="capitalize">{entity.getTypePlural()}</span>
              <span className="px-2 text-sm" style={{color:'gray'}}>▸</span>
		<span
		className="bg-orange-default px-3 py-1 rounded-lg text-sm text-white uppercase"
		title={entity.getShortForm()}
		>
		{entity.getShortForm()}
		</span>
              <span className="text-sm text-neutral-default">
		&nbsp;
		&nbsp;
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
                <EntityDescriptionSection
                  entity={entity}
                  referencedEntities={referencedEntities}
                />
              </div>
              <EntitySynonymsSection
                entity={entity}
                referencedEntities={referencedEntities}
              />
            </div>
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
		{ viewMode === 'graph' && 
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
		}
		{viewMode === 'tree' && 
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
					<EntityAnnotationsSection entity={entity} referencedEntities={referencedEntities} />
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
				referencedEntities={referencedEntities}
				/>
				<IndividualSameAsSection
				entity={entity}
				referencedEntities={referencedEntities}
				/>
				<IndividualDifferentFromSection
				entity={entity}
				referencedEntities={referencedEntities}
				/>
				<DisjointWithSection
				entity={entity}
				referencedEntities={referencedEntities}
				/>
				<EntityEquivalentsSection
				entity={entity}
				referencedEntities={referencedEntities}
				/>
				<EntityParentsSection
				entity={entity}
				referencedEntities={referencedEntities}
				/>
				<EntityRelatedFromSection
				entity={entity}
				referencedEntities={referencedEntities}
				/>
				<ClassInstancesSection 
					entity={entity}
					classInstances={classInstances}
					referencedEntities={referencedEntities}
				/>
				</div>
				</details>
			</div>
			</div>
}
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
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
}) {
  return (
    <p>
      {entity.getDescriptionAsArray().map((definition: Reified<any>) => {
        const hasMetadata = definition.hasMetadata();
        return (
          <span key={randomString()}>
            {definition.value}
            {hasMetadata ? (
              <Tooltip
                title={Object.keys(definition.getMetadata())
                  .map((key) => {
                    let label = referencedEntities.getLabelForIri(key);
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
        );
      })}
    </p>
  );
}
function EntityAnnotationsSection({entity, referencedEntities}:{entity:Entity, referencedEntities:ReferencedEntities}) {

	let annotationPredicates = entity.getAnnotationPredicates()

	return <Fragment>
		 {annotationPredicates.map((annotationPredicate) => {
			const title = entity.getLabelForIri(annotationPredicate)
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

			let annotations:Reified<any>[] = entity.getAnnotationById(annotationPredicate)

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

			{ annotations.length === 1 ?
				<p>
					{renderAnnotation(annotations[0])}
					{annotations[0].hasMetadata() && <MetadataTooltip metadata={annotations[0].getMetadata()} referencedEntities={referencedEntities} />}
				</p> :
			<ul className="list-disc list-inside">
				{annotations.map((annotation: Reified<any>) => {
				return (
				<li
					key={
					randomString()
					}
				>
					{renderAnnotation(annotation)}
					{annotation.hasMetadata() && <MetadataTooltip metadata={annotation.getMetadata()} referencedEntities={referencedEntities} />}
				</li>
				);
				})
				.sort((a, b) => sortByKeys(a, b))}
			</ul>
		 }
			</div>
			);
			})
			.sort((a, b) => sortByKeys(a, b))
		}</Fragment>


	function renderAnnotation(value:Reified<any>) {

		let referencedEntity = referencedEntities.get(value.value)

		if(referencedEntity) {
			// The annotation value refers to an entity.
			// This may be a CURIE with a URL; or an IRI with label(s)

			if(referencedEntity.url) {
				// CURIE
				return <Link to={referencedEntity.url}>{value.value}</Link>
			} else {
				// entity IRI in this ontology
				return <EntityLink
					ontologyId = {entity.getOntologyId()}
					entityType = "classes" // TODO
					iri = {value.value}
					referencedEntities = {referencedEntities}
				/>
			}
		} else {
			return value.value
		}
	}
}

function EntitySynonymsSection({
  entity,
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
}) {
  let synonyms = entity.getSynonyms();

  if (!synonyms || synonyms.length === 0) {
    return <Fragment />;
  }

  return (
    <div>
      <div className="font-bold mb-4">Synonym</div>
      <div className="flex flex-row flex-wrap">
        {synonyms
          .map((synonym: Reified<any>) => {
            const hasMetadata = synonym.hasMetadata();
            return (
              <div
                key={synonym.value.toString().toUpperCase() + randomString()}
                className="flex-none bg-grey-default rounded-sm font-mono py-1 px-3 mb-2 mr-2 text-sm"
              >
                {synonym.value}
                {hasMetadata && (
                  <MetadataTooltip
                    metadata={synonym.getMetadata()}
                    referencedEntities={referencedEntities}
                  />
                )}
              </div>
            );
          })
          .sort((a, b) => sortByKeys(a, b))}
      </div>
    </div>
  );
}

function ClassInstancesSection({ entity, classInstances, referencedEntities }: {
	entity: Entity,
	classInstances: Page<Entity>|null,
	referencedEntities: ReferencedEntities
}) {

if (entity.getType() != 'class')
	return <Fragment />

  return (
    <div>
      <div className="font-bold">Instances</div>
      <ul className="list-disc list-inside">
        {classInstances && classInstances.elements.map((instance:Entity) => {
          return (
            <li key={randomString()}>
		<EntityLink ontologyId={entity.getOntologyId()} entityType="individuals" iri={instance.getIri()} referencedEntities={referencedEntities} />
            </li>
          );
        })}
      </ul>
    </div>
  );

}

function EntityEquivalentsSection({
  entity,
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
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
                expr={eqClass.value}
                referencedEntities={referencedEntities}
              />
              {hasMetadata && (
                <MetadataTooltip
                  metadata={eqClass.getMetadata()}
                  referencedEntities={referencedEntities}
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
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
}) {
  if (!(entity instanceof Class || entity instanceof Property)) {
    return <Fragment />;
  }

  let parents = entity?.getParents();

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
            expr={parents[0].value}
            referencedEntities={referencedEntities}
          />
          {parents[0].hasMetadata() && (
            <MetadataTooltip
              metadata={parents[0].getMetadata()}
              referencedEntities={referencedEntities}
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
                  expr={parent.value}
                  referencedEntities={referencedEntities}
                />
                {parent.hasMetadata() && (
                  <MetadataTooltip
                    metadata={parent.getMetadata()}
                    referencedEntities={referencedEntities}
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
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
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
        let label = referencedEntities.getLabelForIri(p);
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
                    // let label = referencedEntities.getLabelForIri(relatedIri);
                    return (
                      <li key={relatedIri + randomString()}>
                        <EntityLink
                          ontologyId={entity.getOntologyId()}
                          entityType={"classes"}
                          iri={relatedIri}
                          referencedEntities={referencedEntities}
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

function MetadataTooltip({metadata, referencedEntities}:{metadata:any, referencedEntities:ReferencedEntities }) {

	return <Tooltip
		title={Object.keys(metadata)
		.map((key) => {
			let label = referencedEntities.getLabelForIri(key) || key
		if (label) {
			return ("*" + metadata[key] + " (" +
					label.replaceAll( "_", " ") + ")");
		}
		return "";
		})
		.join("\n")}
		placement="top"
		arrow
		>
		<i className="icon icon-common icon-info text-neutral-default text-sm ml-1" />
		</Tooltip>
}

function IndividualTypesSection({
  entity,
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
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
              expr={types[0]}
              referencedEntities={referencedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
              entityType={"classes"}
              iri={types[0]}
              referencedEntities={referencedEntities}
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
                    expr={type}
                    referencedEntities={referencedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
                    entityType={"classes"}
                    iri={type}
                    referencedEntities={referencedEntities}
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
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
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
              expr={sameAses[0]}
              referencedEntities={referencedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
              entityType={"individuals"}
              iri={sameAses[0]}
              referencedEntities={referencedEntities}
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
                    expr={sameAs}
                    referencedEntities={referencedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
                    entityType={"individuals"}
                    iri={sameAs}
                    referencedEntities={referencedEntities}
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
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
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
              expr={differentFroms[0]}
              referencedEntities={referencedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
              entityType={"individuals"}
              iri={differentFroms[0]}
              referencedEntities={referencedEntities}
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
                    expr={differentFrom}
                    referencedEntities={referencedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
                    entityType={"individuals"}
                    iri={differentFrom}
                    referencedEntities={referencedEntities}
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
  referencedEntities,
}: {
  entity: Entity;
  referencedEntities: ReferencedEntities;
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
              expr={disjointWiths[0]}
              referencedEntities={referencedEntities}
            />
          ) : (
            <EntityLink
              ontologyId={entity.getOntologyId()}
              entityType={
                entity.getType() === "property" ? "properties" : "classes"
              }
              iri={disjointWiths[0]}
              referencedEntities={referencedEntities}
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
                    expr={disjointWith}
                    referencedEntities={referencedEntities}
                  />
                ) : (
                  <EntityLink
                    ontologyId={entity.getOntologyId()}
                    entityType={
                      entity.getType() === "property" ? "properties" : "classes"
                    }
                    iri={disjointWith}
                    referencedEntities={referencedEntities}
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
