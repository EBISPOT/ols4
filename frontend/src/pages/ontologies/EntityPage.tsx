import { AccountTree, Share } from "@mui/icons-material";
import {
  Box,
  Breadcrumbs,
  Button,
  ButtonGroup,
  Link,
  Tooltip,
  Typography
} from "@mui/material";
import { Fragment, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import Header from "../../components/Header";
import Spinner from "../../components/Spinner";
import EntityGraph from "./EntityGraph";
import EntityTree from "./EntityTree";
import { getEntity, getOntology } from "./ontologiesSlice";

export default function EntityPage(props: {
  ontologyId: string;
  entityUri: string;
  entityType: "classes" | "properties" | "individuals";
}) {
  const dispatch = useAppDispatch();
  const ontology = useAppSelector((state) => state.ontologies.ontology);
  const entity = useAppSelector((state) => state.ontologies.entity);

  let { ontologyId, entityUri, entityType } = props;
  let [viewMode, setViewMode] = useState<"tree" | "graph">("tree");

  useEffect(() => {
    dispatch(getOntology(ontologyId));
    dispatch(getEntity({ ontologyId, entityType, entityUri }));
  }, []);

  return (
    <Fragment>
      <Header section="ontologies" />
      <main className="container mx-auto">{renderTermPage()}</main>
    </Fragment>
  );

  function renderTermPage() {
    if (!ontology || !entity) {
      return <Spinner />;
    }

    document.title = entity.getName();
    return (
      <Fragment>
        <Breadcrumbs>
          <Link color="inherit" component={RouterLink} to="/ontologies">
            Ontologies
          </Link>
          <Link
            color="inherit"
            component={RouterLink}
            to={"/ontologies/" + ontologyId}
          >
            {ontology.getName()}
          </Link>
          <Typography color="textPrimary">
            {
              {
                class: "Classes",
                property: "Properties",
                individual: "Individuals",
              }[entity.getType()]
            }
          </Typography>
          <Typography color="textPrimary">{entity.getName()}</Typography>
        </Breadcrumbs>

        <h1>{entity!.getName()}</h1>

        <Box>
          <p>{entity!.getDescription()}</p>
        </Box>
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
          <Tooltip title="Graph view" placement="top">
            <Button
              variant={viewMode === "graph" ? "contained" : "outlined"}
              onClick={() => setViewMode("graph")}
            >
              <Share />
            </Button>
          </Tooltip>
        </ButtonGroup>

        <br />

        <Box py={2}>
          {viewMode === "tree" ? (
            <EntityTree
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
        </Box>
      </Fragment>
    );
  }
}
