import { AccountTree } from "@mui/icons-material";
import FormatListBulletedIcon from "@mui/icons-material/FormatListBulleted";
import { Button, ButtonGroup, Link, Tooltip } from "@mui/material";
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
              <span className="underline underline-offset-1">
                <Link color="inherit" component={RouterLink} to="/ontologies">
                  Ontologies
                </Link>
              </span>
              <span className="px-2 text-sm">&gt;</span>
              <span className="font-semibold">{ontology!.getName()}</span>
            </div>
            <div className="bg-gradient-to-r from-grey-1 to-white rounded-lg p-8 mb-4">
              <div className="text-2xl font-semibold text-grey-3 mb-4">
                {ontology!.getName()}
              </div>
              <div>
                <p>{ontology!.getDescription()}</p>
              </div>
            </div>

            <ButtonGroup
              variant="contained"
              aria-label="outlined primary button group"
            >
              <Tooltip title="Tree view" placement="top">
                <Button
                  variant={viewMode === "tree" ? "contained" : "outlined"}
                  onClick={() => setViewMode("tree")}
                >
                  <AccountTree />
                </Button>
              </Tooltip>
              <Tooltip title="List view" placement="top">
                <Button
                  variant={viewMode === "list" ? "contained" : "outlined"}
                  onClick={() => setViewMode("list")}
                >
                  <FormatListBulletedIcon />
                </Button>
              </Tooltip>
            </ButtonGroup>

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
              />
              <Tab
                label={`Properties (${ontology!
                  .getNumProperties()
                  .toLocaleString()})`}
                value="properties"
              />
              <Tab
                label={`Individuals (${ontology!
                  .getNumIndividuals()
                  .toLocaleString()})`}
                value="individuals"
              />
            </Tabs>

            {viewMode === "list" ? (
              <EntityList ontologyId={ontologyId} entityType={tab} />
            ) : (
              <EntityTree ontologyId={ontologyId} entityType={tab} />
            )}
          </div>
        ) : (
          <Spinner />
        )}
      </main>
    </div>
  );
}
