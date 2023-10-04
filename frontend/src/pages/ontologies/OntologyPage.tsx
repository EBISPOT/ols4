import {
  AccountTree,
  BugReport,
  Download,
  Email,
  FormatListBulleted,
  Home,
} from "@mui/icons-material";
import { Fragment, useEffect, useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
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
import EntityTree from "./entities/EntityTree";
import MetadataTooltip from "./entities/entityPageSections/MetadataTooltip";
import addLinksToText from "./entities/entityPageSections/addLinksToText";
import { getOntology } from "./ontologiesSlice";

export default function OntologyPage() {
  const params = useParams();
  const ontologyId: string = params.ontologyId as string;

  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const loading = useAppSelector((state) => state.ontologies.loadingOntology);
  const errorMessage = useAppSelector((state) => state.ontologies.errorMessage);

  const [searchParams, setSearchParams] = useSearchParams();
  const lang = searchParams.get("lang") || "en";
  const viewMode = searchParams.get("viewMode") || "tree";
  const tab: "classes" | "properties" | "individuals" = ({
    classes: "classes",
    properties: "properties",
    individuals: "individuals",
  }[searchParams.get("tab") || "classes"] || "classes") as
    | "classes"
    | "properties"
    | "individuals";
  const iri = searchParams.get("iri");

  const version =
    (ontology?.getVersion()
      ? ontology.getVersion()
      : ontology?.getVersionFromIri()) || undefined;

  useEffect(() => {
    if (tab === "individuals" && ontology && ontology.getNumIndividuals() > 0) {
      setSearchParams((params) => {
        params.set("viewMode", "list");
        return params;
      });
    } else if (
      (tab === "properties" && ontology && !ontology.getNumProperties()) ||
      (tab === "individuals" && ontology && !ontology.getNumIndividuals())
    ) {
      setSearchParams((params) => {
        params.set("tab", "classes");
        return params;
      });
    }
  }, [tab, ontology, setSearchParams]);

  useEffect(() => {
    dispatch(getOntology({ ontologyId, lang }));
  }, [dispatch, ontologyId, lang]);

  const navigate = useNavigate();
  useEffect(() => {
    if (iri)
      navigate(
        `/ontologies/${ontologyId}/${tab}/${encodeURIComponent(
          encodeURIComponent(iri)
        )}`
      );
  }, [iri, navigate, ontologyId, tab]);
  useEffect(() => {
    if (errorMessage) navigate("/error", { state: { message: errorMessage } });
  }, [errorMessage, navigate]);

  document.title = ontology?.getName() || ontologyId;
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto px-4">
        {ontology ? (
          <div className="my-8">
            <div className="flex flex-wrap justify-between items-center gap-y-2 px-1 mb-4">
              <div className="flex flex-wrap items-center gap-y-2">
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
                  onChangeLang={(lang) => setSearchParams({ lang })}
                />
                <ApiLinks
                  apiUrl={`${process.env.REACT_APP_APIURL}api/ontologies/${ontologyId}`}
                  betaApiUrl={`${process.env.REACT_APP_APIURL}api/v2/ontologies/${ontologyId}`}
                />
              </div>
            </div>
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="overflow-x-auto mb-4">
                <div className="text-2xl font-bold mb-4">
                  {ontology.getName() || ontology.getOntologyId()}
                </div>
                {version && (
                  <div className="mb-4">
                    <span className="font-bold">Version {version}</span>
                  </div>
                )}
                <div>
                  <p>
                    {ontology.getDescription() ? ontology.getDescription() : ""}
                  </p>
                </div>
              </div>
              <OntologyImportsSection ontology={ontology} />
              <OntologyImportedBySection ontology={ontology} />
              <SearchBox
                ontologyId={ontologyId}
                placeholder={`Search ${ontologyId.toUpperCase()}...`}
              />
              <div className="flex flex-wrap gap-2 mt-4">
                {ontology.getOntologyPurl() && ontology.getAllowDownload() && (
                  <Link
                    to={ontology.getOntologyPurl()}
                    target="_blank"
                    rel="noopener noreferrer"
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
                  <Link
                    to={ontology.getHomepage()}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
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
                    rel="noopener noreferrer"
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
            </div>
            <div className="flex flex-col-reverse lg:grid lg:grid-cols-3 lg:gap-4">
              <div className="lg:col-span-2 flex flex-col">
                <Tabs
                  value={tab}
                  onChange={(value: any) => {
                    setSearchParams((params) => {
                      params.set("tab", value);
                      return params;
                    });
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
                {tab !== "classes" || ontology.getNumClasses() > 0 ? (
                  <div className="py-2">
                    <button
                      disabled={tab === "individuals"}
                      className={`font-bold mr-3 ${
                        viewMode === "tree"
                          ? "button-primary-active"
                          : "button-primary"
                      }`}
                      onClick={() =>
                        setSearchParams((params) => {
                          params.set("viewMode", "tree");
                          return params;
                        })
                      }
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
                      onClick={() =>
                        setSearchParams((params) => {
                          params.set("viewMode", "list");
                          return params;
                        })
                      }
                    >
                      <div className="flex gap-2">
                        <FormatListBulleted />
                        <div>List</div>
                      </div>
                    </button>
                  </div>
                ) : null}
                {viewMode === "list" ? (
                  <EntityList ontologyId={ontologyId} entityType={tab} />
                ) : (
                  <EntityTree
                    ontology={ontology}
                    entityType={tab}
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
                  <summary className="p-2 mb-2 border-b-2 border-grey-default text-lg link-default">
                    Ontology Information
                  </summary>
                  <div className="p-2 break-words space-y-2">
                    <div>
                      <span className="font-bold">Ontology IRI: </span>
                      <a
                        id="ontologyIri"
                        href={ontology.getIri() || ontology.getOntologyPurl()}
                        className="link-default"
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {ontology.getIri() || ontology.getOntologyPurl()}
                      </a>
                    </div>
                    {ontology.getVersionIri() && (
                      <div>
                        <span className="font-bold">Version IRI: </span>
                        <a
                          id="versionIri"
                          href={ontology.getVersionIri()}
                          className="link-default"
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {ontology.getVersionIri()}
                        </a>
                      </div>
                    )}
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
            <div key={title.toString() + randomString()}>
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
                        <li key={JSON.stringify(annotation)}>
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
      return (
        <span>
          {addLinksToText(
            value.value.toString(),
            ontology.getLinkedEntities(),
            ontology.getOntologyId(),
            undefined,
            "ontologies"
          )}
        </span>
      );
    }
  }
}

function OntologyImportsSection({ ontology }: { ontology: Ontology }) {
  let [expanded, setExpanded] = useState<boolean>(false);
  const MAX_UNEXPANDED = 5;

  let imports = ontology.getImportsFrom();

  if (!imports) return <Fragment />;

  return (
    <Fragment>
      {imports && imports.length > 0 && (
        <div className="mb-2" style={{ maxWidth: "100%", inlineSize: "100%" }}>
          <span className="font-bold mr-2">Imports from</span>
          {imports.length <= MAX_UNEXPANDED || expanded ? (
            imports.map(renderOntId)
          ) : (
            <Fragment>
              {imports.slice(0, MAX_UNEXPANDED).map(renderOntId)}
              &nbsp;
              <span
                className="link-default italic"
                onClick={() => setExpanded(true)}
              >
                &thinsp;&nbsp;+&nbsp;{imports.length - MAX_UNEXPANDED}
              </span>
            </Fragment>
          )}
        </div>
      )}
    </Fragment>
  );

  function renderOntId(ontId: string) {
    return (
      <Link
        key={ontId}
        className="my-1"
        style={{ display: "inline-block" }}
        to={"/ontologies/" + ontId}
      >
        <span
          className="link-ontology px-2 py-1 rounded-md text-sm text-white uppercase mr-1"
          title={ontId.toUpperCase()}
        >
          {ontId}
        </span>
      </Link>
    );
  }
}

function OntologyImportedBySection({ ontology }: { ontology: Ontology }) {
  let [expanded, setExpanded] = useState<boolean>(false);
  const MAX_UNEXPANDED = 5;

  let imports = ontology.getExportsTo();

  if (!imports) return <Fragment />;

  return (
    <Fragment>
      {imports && imports.length > 0 && (
        <div className="mb-2" style={{ maxWidth: "100%", inlineSize: "100%" }}>
          <span className="font-bold mr-2">Exports to</span>
          {imports.length <= MAX_UNEXPANDED || expanded ? (
            imports.map(renderOntId)
          ) : (
            <Fragment>
              {imports.slice(0, MAX_UNEXPANDED).map(renderOntId)}
              &nbsp;
              <span
                className="link-default italic"
                onClick={() => setExpanded(true)}
              >
                &thinsp;&nbsp;+&nbsp;{imports.length - MAX_UNEXPANDED}
              </span>
            </Fragment>
          )}
        </div>
      )}
    </Fragment>
  );

  function renderOntId(ontId: string) {
    return (
      <Link
        key={ontId}
        className="my-1"
        style={{ display: "inline-block" }}
        to={"/ontologies/" + ontId}
      >
        <span
          className="link-ontology px-2 py-1 rounded-md text-sm text-white uppercase mr-1"
          title={ontId.toUpperCase()}
        >
          {ontId}
        </span>
      </Link>
    );
  }
}
