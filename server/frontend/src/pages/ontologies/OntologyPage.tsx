import { AccountTree } from "@mui/icons-material";
import FormatListBulletedIcon from "@mui/icons-material/FormatListBulleted";
import {
  Box,
  Button,
  ButtonGroup,
  Link,
  Tab,
  Tabs,
  Tooltip,
} from "@mui/material";
import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import Header from "../../components/Header";
import Spinner from "../../components/Spinner";
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

            <Tabs
              indicatorColor="primary"
              textColor="primary"
              value={tab}
              onChange={(e, tab) => setTab(tab)}
            >
              <Tab
                label={`Classes (${ontology!
                  .getNumClasses()
                  .toLocaleString()})`}
                value="classes"
                disabled={ontology!.getNumClasses() == 0}
              />
              <Tab
                label={`Properties (${ontology!
                  .getNumProperties()
                  .toLocaleString()})`}
                value="properties"
                disabled={ontology!.getNumProperties() == 0}
              />
              <Tab
                label={`Individuals (${ontology!
                  .getNumIndividuals()
                  .toLocaleString()})`}
                value="individuals"
                disabled={ontology!.getNumIndividuals() == 0}
              />
            </Tabs>
            <br />
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
            <br />
            <Box py={2}>
              {viewMode === "list" ? (
                <EntityList ontologyId={ontologyId} entityType={tab} />
              ) : (
                <EntityTree ontologyId={ontologyId} entityType={tab} />
              )}
            </Box>
          </div>
        ) : (
          <Spinner />
        )}
      </main>
    </div>
  );
}
