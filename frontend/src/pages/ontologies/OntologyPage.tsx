import {
  AccountTree,
  BugReport,
  Download,
  Email,
  Home,
} from "@mui/icons-material";
import FormatListBulletedIcon from "@mui/icons-material/FormatListBulleted";
import { Fragment, useEffect, useState } from "react";
import { Link, Navigate, useParams, useSearchParams } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import { randomString, sortByKeys } from "../../app/util";
import ApiLinks from "../../components/ApiLinks";
import EntityLink from "../../components/EntityLink";
import Header from "../../components/Header";
import LanguagePicker from "../../components/LanguagePicker";
import LoadingOverlay from "../../components/LoadingOverlay";
import SearchBox from "../../components/SearchBox";
import { Tab, Tabs } from "../../components/Tabs";
import Ontology from "../../model/Ontology";
import Reified from "../../model/Reified";
import EntityList from "./entities/EntityList";
import MetadataTooltip from "./entities/entityPageSections/MetadataTooltip";
import EntityTree from "./entities/EntityTree";
import { getOntology } from "./ontologiesSlice";

export default function OntologyPage({
  tab,
}: {
  tab: "classes" | "properties" | "individuals";
}) {
  const params = useParams();
  let ontologyId: string = params.ontologyId as string;

  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const loading = useAppSelector((state) => state.ontologies.loadingOntology);

  const [currentTab, setTab] = useState<
    "classes" | "properties" | "individuals"
  >(tab || "classes");

  const [viewMode, setViewMode] = useState<"tree" | "list">("tree");

  const [searchParams, setSearchParams] = useSearchParams();
  let lang = searchParams.get("lang") || "en";

  useEffect(() => {
    dispatch(getOntology({ ontologyId, lang }));
  }, [dispatch, ontologyId, lang, searchParams]);

  useEffect(() => {
    if (currentTab === "individuals") setViewMode("list");
  }, [currentTab]);

  if (searchParams.get("iri")) {
    let iri = searchParams.get("iri") as string;

    let newSearchParams = new URLSearchParams(searchParams);
    newSearchParams.delete("iri");

    return (
      <Navigate
        to={`/ontologies/${ontologyId}/${currentTab}/${encodeURIComponent(
          encodeURIComponent(iri)
        )}`}
      />
    );
  }

  document.title = ontology?.getName() || ontologyId;

  let version =
    (ontology?.getVersion()
      ? ontology.getVersion()
      : ontology?.getVersionFromIri()) || undefined;

  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto" style={{ position: "relative" }}>
        {ontology ? (
          <div className="my-8 mx-2">
            <div className="flex flex-row justify-between items-center px-2 mb-4">
              <div>
                <Link
                  className="link-default"
                  to={"/ontologies"}
                  style={{ color: "black" }}
                >
                  Ontologies
                </Link>
                <span className="px-2 text-sm" style={{ color: "grey" }}>
                  ▸
                </span>
                <span
                  className="link-ontology px-2 py-1 rounded-md text-sm text-white uppercase"
                  title={ontologyId.toUpperCase()}
                >
                  {ontologyId}
                </span>
              </div>
              <div className="flex flex-row items-center gap-4">
                <LanguagePicker
                  ontology={ontology}
                  lang={lang}
                  onChangeLang={(lang) => setSearchParams({ lang: lang })}
                />
                <ApiLinks
                  apiUrl={`${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}`}
                  betaApiUrl={`${process.env.REACT_APP_APIURL}api/v2/ontologies/${ontologyId}`}
                />
              </div>
            </div>
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="text-2xl font-bold mb-4">
                {ontology.getName() || ontology.getOntologyId()}
              </div>
              {version && (
                <div className="mb-4">
                  <span className="font-bold">Version {version}</span>
                </div>
              )}
              <div className="mb-6">
                <p>
                  {ontology.getDescription() ? ontology.getDescription() : ""}
                </p>
              </div>
              <div className="flex gap-2 mb-6">
                {ontology.getOntologyPurl() && (
                  <Link
                    to={ontology.getOntologyPurl()}
                    target="_blank"
                    download={true}
                  >
                    <button className="button-secondary font-bold self-center">
                      <div className="flex gap-2">
                        <Download />
                        <div>Download</div>
                      </div>
                    </button>
                  </Link>
                )}
                {ontology.getHomepage() && (
                  <Link to={ontology.getHomepage()} target="_blank">
                    <button className="button-secondary font-bold self-center">
                      <div className="flex gap-2">
                        <Home />
                        <div>Homepage</div>
                      </div>
                    </button>
                  </Link>
                )}
                {ontology.getMailingList() && (
                  <Link
                    to={"mailto:" + ontology.getMailingList()}
                    target="_blank"
                  >
                    <button className="button-secondary font-bold self-center">
                      <div className="flex gap-2">
                        <Email />
                        <div>Mailing List</div>
                      </div>
                    </button>
                  </Link>
                )}
                {ontology.getTracker() && (
                  <Link to={ontology.getTracker()} target="_blank">
                    <button className="button-secondary font-bold self-center">
                      <div className="flex gap-2">
                        <BugReport />
                        <div>Issue Tracker</div>
                      </div>
                    </button>
                  </Link>
                )}
              </div>
              <div className="flex flex-nowrap gap-4">
                <SearchBox
                  ontologyId={ontologyId}
                  placeholder={`Search ${ontologyId.toUpperCase()}...`}
                />
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
                  <div className="py-2 mb-1 flex justify-between">
                    <div>
                      <button
                        disabled={currentTab === "individuals"}
                        className={`font-bold mr-3 ${
                          viewMode === "tree"
                            ? "button-primary-active"
                            : "button-primary"
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
                          viewMode === "list"
                            ? "button-primary-active"
                            : "button-primary"
                        }`}
                        onClick={() => setViewMode("list")}
                      >
                        <div className="flex gap-2">
                          <FormatListBulletedIcon />
                          <div>List</div>
                        </div>
                      </button>
                    </div>
                  </div>
                ) : null}
                {viewMode === "list" ? (
                  <EntityList ontologyId={ontologyId} entityType={currentTab} />
                ) : (
                  <EntityTree
                    ontology={ontology}
                    entityType={currentTab}
                    lang={lang}
                  />
                )}
              </div>
              <div className="col-span-1">
                <details open className="p-2">
                  <summary className="p-2 mb-2 border-b-2 border-grey-default text-lg link-default">
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
                    {/* todo remove hack when datarelease has completed; this should always be present */}
                    {ontology.getSourceFileTimestamp() && (
                      <div>
                        <span className="font-bold">Last loaded: </span>
                        <a
                          id="lastLoaded"
                          href={ontology.getSourceFileTimestamp()}
                        >
                          {ontology.getSourceFileTimestamp()}
                        </a>
                      </div>
                    )}
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

function OntologyAnnotationsSection({ ontology }: { ontology: Ontology }) {
  let annotationPredicates = ontology.getAnnotationPredicates();

  return (
    <Fragment>
      {annotationPredicates
        .map((annotationPredicate) => {
          const title = ontology.getLabelForIri(annotationPredicate)
            ? ontology.getLabelForIri(annotationPredicate)
            : annotationPredicate
                .substring(annotationPredicate.lastIndexOf("/") + 1)
                .substring(
                  annotationPredicate
                    .substring(annotationPredicate.lastIndexOf("/") + 1)
                    .lastIndexOf("#") + 1
                );

          let annotations: Reified<any>[] =
            ontology.getAnnotationById(annotationPredicate);

          return (
            <div key={title.toString().toUpperCase() + randomString()}>
              <div className="font-bold">{title}</div>

              {annotations.length === 1 ? (
                <p>
                  {renderAnnotation(annotations[0])}
                  {annotations[0].hasMetadata() && (
                    <MetadataTooltip
                      metadata={annotations[0].getMetadata()}
                      linkedEntities={ontology.getLinkedEntities()}
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
                              linkedEntities={ontology.getLinkedEntities()}
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
    let linkedEntity = ontology.getLinkedEntities().get(value.value);

    if (linkedEntity) {
      return (
        <EntityLink
          ontologyId={ontology.getOntologyId()}
          currentEntity={undefined}
          entityType={"ontologies"}
          iri={value.value}
          linkedEntities={ontology.getLinkedEntities()}
        />
      );
    } else {
      if (typeof value.value !== "string") {
        return <span>{JSON.stringify(value.value)}</span>;
      }
      if (value.value.toString().indexOf("://") !== -1) {
        return (
          <Link className="link-default" to={value.value}>
            {value.value}
          </Link>
        );
      } else {
        return <span>{value.value.toString()}</span>;
      }
    }
  }
}
