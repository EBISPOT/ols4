import { AccountTree } from "@mui/icons-material";
import FormatListBulletedIcon from "@mui/icons-material/FormatListBulleted";
import { Link, Tooltip } from "@mui/material";
import moment from "moment";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import Header from "../../components/Header";
import Spinner from "../../components/Spinner";
import { Tab, Tabs } from "../../components/Tabs";
import EntityList from "./EntityList";
import EntityTree from "./EntityTree";
import { getOntology } from "./ontologiesSlice";

export default function OntologyPage(props: { ontologyId: string }) {
  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);

  const { ontologyId } = props;
  const [tab, setTab] = useState<
    "entities" | "classes" | "properties" | "individuals"
  >("classes");
  const [viewMode, setViewMode] = useState<"tree" | "list">("tree");

  useEffect(() => {
    dispatch(getOntology(ontologyId));
  }, []);

  document.title = ontology ? ontology.getName() : "";
  return (
    <div>
      <Header section="ontologies" />
      <main className="container mx-auto">
        {ontology ? (
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
              <span className="font-bold">{ontology!.getName()}</span>
            </div>
            <div className="bg-gradient-to-r from-neutral-light to-white rounded-lg p-8 mb-4 text-neutral-black">
              <div className="text-2xl font-bold mb-4">
                {ontology!.getName()}
              </div>
              <div>
                <p>{ontology!.getDescription()}</p>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-8">
              <div className="col-span-2">
                <Tabs
                  value={tab}
                  onChange={(value: any) => {
                    setTab(value);
                  }}
                >
                  <Tab
                    label={`Classes (${ontology!
                      .getNumClasses()
                      .toLocaleString()})`}
                    value="classes"
                    disabled={ontology!.getNumClasses() <= 0}
                  />
                  <Tab
                    label={`Properties (${ontology!
                      .getNumProperties()
                      .toLocaleString()})`}
                    value="properties"
                    disabled={ontology!.getNumProperties() <= 0}
                  />
                  <Tab
                    label={`Individuals (${ontology!
                      .getNumIndividuals()
                      .toLocaleString()})`}
                    value="individuals"
                    disabled={ontology!.getNumIndividuals() <= 0}
                  />
                </Tabs>
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
                {viewMode === "list" ? (
                  <EntityList ontologyId={ontologyId} entityType={tab} />
                ) : (
                  <EntityTree ontologyId={ontologyId} entityType={tab} />
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
                        <a id="ontologyIri" href={ontology.getIri() || ontology.getOntologyPurl()}>
                            {ontology.getIri() || ontology.getOntologyPurl()}</a>
                    </div>
                    <div>
                      <span className="font-bold">Version IRI: </span>
                        <a id="versionIri" href={ontology.getVersionIri()}>{ontology.getVersionIri()}</a>
                    </div>
                    <div>
                      <span className="font-bold">Ontology ID: </span>
                      <span id="ontologyId">{ontology.getOntologyId()}</span>
                    </div>
                    <div>
                      <span className="font-bold">Version: </span>
                      <span id="version">{ontology.getVersion()}</span>
                    </div>
                    <div>
                      <span className="font-bold">Number of terms: </span>
                      <span id="numberOfEntities">{ontology.getNumEntities()}</span>
                    </div>
                    <div>
                      <span className="font-bold">Last loaded: </span>
                      <span id='lastLoaded'>
                          {moment(ontology.getLoaded()).format(
                            "D MMM YYYY ddd HH:mm:SSZ"
                          )}
                      </span>
                    </div>
                  </div>
                </details>
              </div>
            </div>
          </div>
        ) : (
          <Spinner />
        )}
      </main>
    </div>
  );
}
