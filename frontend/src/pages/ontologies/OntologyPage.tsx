import { AccountTree } from "@mui/icons-material";
import FormatListBulletedIcon from "@mui/icons-material/FormatListBulleted";
import { Tooltip } from "@mui/material";
import { Fragment, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString, sortByKeys } from "../../app/util";
import Header from "../../components/Header";
import LoadingOverlay from "../../components/LoadingOverlay";
import SearchBox from "../../components/SearchBox";
import { Tab, Tabs } from "../../components/Tabs";
import Ontology from "../../model/Ontology";
import EntityList from "./EntityList";
import EntityTree from "./EntityTree";
import { getOntology } from "./ontologiesSlice";

export default function OntologyPage({ ontologyId, tab }: { ontologyId: string, tab:'classes'|'properties'|'individuals' }) {
  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const loading = useAppSelector((state) => state.ontologies.loadingOntology);

  const [currentTab, setTab] = useState<
   "classes" | "properties" | "individuals"
  >(tab || "classes");

  const [viewMode, setViewMode] = useState<"tree" | "list">("tree");

  useEffect(() => {
    dispatch(getOntology(ontologyId));
  }, [dispatch, ontologyId]);

  document.title = ontology?.getName() || ontologyId;
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto">
        {ontology ? (
          <div className="my-8 mx-2">
            <div className="px-2 mb-4">
              <Link className="link-default" to={process.env.PUBLIC_URL + "/ontologies"}>
                Ontologies
              </Link>
              <span className="px-2 text-sm">▸</span>
		<span
		className="bg-link-default px-3 py-1 rounded-lg text-sm text-white uppercase"
		title={ontologyId}
		>
		{ontologyId}
		</span>
            </div>
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="text-2xl font-bold mb-4">
                {ontology.getName() || ontology.getOntologyId()}
              </div>
              <div className="mb-4">
                <p>
                  {ontology.getDescription() ? ontology.getDescription() : ""}
                </p>
              </div>
		<div className="flex flex-nowrap gap-4">
			<SearchBox ontologyId={ontologyId} placeholder={`Search ${ontologyId.toUpperCase()}...`}/>
		</div>
            </div>
            <div className="grid grid-cols-3 gap-8">
              <div className="col-span-2">
                <Tabs
                  value={currentTab}
                  onChange={(value: any) => {
                    setTab(value);
                  }}
                >
                  <Tab
                    label={`Classes (${ontology
                      .getNumClasses()
                      .toLocaleString()})`}
                    value="classes"
                    disabled={!(ontology.getNumClasses() > 0)} // !(value) handles NaN
                  />
                  <Tab
                    label={`Properties (${ontology
                      .getNumProperties()
                      .toLocaleString()})`}
                    value="properties"
                    disabled={!(ontology.getNumProperties() > 0)}
                  />
                  <Tab
                    label={`Individuals (${ontology
                      .getNumIndividuals()
                      .toLocaleString()})`}
                    value="individuals"
                    disabled={!(ontology.getNumIndividuals() > 0)}
                  />
                </Tabs>
                {currentTab !== "classes" || ontology.getNumClasses() > 0 ? (
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
                    <Tooltip title="List view" placement="top">
                      <button
                        className={`button-primary font-bold ${
                          viewMode === "list"
                            ? "shadow-button-active translate-x-2 translate-y-2 hover:shadow-button-active hover:translate-x-2 hover:translate-y-2"
                            : ""
                        }`}
                        onClick={() => setViewMode("list")}
                      >
                        <FormatListBulletedIcon fontSize="small" />
                      </button>
                    </Tooltip>
                  </div>
                ) : null}
                {viewMode === "list" ? (
                  <EntityList ontologyId={ontologyId} entityType={currentTab} />
                ) : (
                  <EntityTree ontologyId={ontologyId} entityType={currentTab} />
                )}
              </div>
              <div className="col-span-1">
                <details open className="p-2">
                  <summary className="p-2 mb-2 border-b-2 border-grey-default text-link-default text-lg cursor-pointer hover:text-link-hover hover:underline ">
                    Ontology Information
                  </summary>
                  <div className="p-2 break-words space-y-2">
                    <div>
                      <span className="font-bold">Ontology IRI: </span>
                      <a
                        id="ontologyIri"
                        href={ontology.getIri() || ontology.getOntologyPurl()}
                      >
                        {ontology.getIri() || ontology.getOntologyPurl()}
                      </a>
                    </div>
                    <div>
                      <span className="font-bold">Version IRI: </span>
                      <a id="versionIri" href={ontology.getVersionIri()}>
                        {ontology.getVersionIri()}
                      </a>
                    </div>
                    <div>
                      <span className="font-bold">Ontology ID: </span>
                      <span id="ontologyId">{ontology.getOntologyId()}</span>
                    </div>
                    <div>
                      <span className="font-bold">Version: </span>
                      <span id="version">
                        {ontology.getVersion()
                          ? ontology.getVersion()
                          : ontology.getVersionFromIri()}
                      </span>
                    </div>
		    <OntologyAnnotationsSection ontology={ontology} />
                  </div>
                </details>
              </div>
            </div>
          </div>
        ) : null}
        {loading ? <LoadingOverlay message="Loading ontology..." /> : null}
      </main>
    </div>
  );
}

function OntologyAnnotationsSection({ontology}:{ontology:Ontology}) {

	let annotationPredicates = ontology.getAnnotationPredicates()

	return <Fragment>
		 {annotationPredicates.map((annotationPredicate) => {
			const title = ontology.getLabelForIri(annotationPredicate)
			? ontology
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

			let annotations = ontology
			.getAnnotationById(annotationPredicate)

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
			{annotations.length === 1 ?
			<p>{getAnnotationValue(annotations[0])}</p>
			:
			<ul className="list-disc list-inside">
				{annotations
				.map((annotation: any) => {
				const value = getAnnotationValue(annotation)
				return (
				<li
					key={
					value.toString().toUpperCase() +
					randomString()
					}>
					{value}
				</li>
				);
				})
				.sort((a, b) => sortByKeys(a, b))}
			</ul>}
			</div>
			);
			})
			.sort((a, b) => sortByKeys(a, b))
		}</Fragment>

	function getAnnotationValue(annotation) {
		return annotation && typeof annotation === "object" ? annotation.value : annotation;
	}
}
