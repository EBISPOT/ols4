import { ThemeProvider } from "@emotion/react";
import {
  Checkbox,
  FormControl,
  FormControlLabel,
  Radio,
  RadioGroup,
} from "@mui/material";
import { useCallback, useEffect, useRef } from "react";
import urlJoin from "url-join";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import { theme } from "../../../app/mui";
import { randomString } from "../../../app/util";
import Node from "../../../components/Node";
import Entity from "../../../model/Entity";
import Ontology from "../../../model/Ontology";
import {
  TreeNode,
  closeNode,
  disablePreferredRoots,
  enablePreferredRoots,
  getAncestors,
  getNodeChildren,
  getRootEntities,
  hideCounts,
  hideObsolete,
  hideSiblings,
  openNode,
  resetTreeContent,
  resetTreeSettings,
  showCounts,
  showObsolete,
  showSiblings,
  setSpecificRootIri,
  getEntity,
} from "../ontologiesSlice";

export default function EntityTree({
  ontology,
  entityType,
  specifiedRootIri,
  selectedEntity,
  lang,
  onNavigateToEntity,
  onNavigateToOntology,
  apiUrl,
}: {
  ontology: Ontology;
  selectedEntity?: Entity;
  entityType: "entities" | "classes" | "properties" | "individuals";
  specifiedRootIri?: string;
  lang: string;
  onNavigateToEntity: (ontology: Ontology, entity: Entity) => void;
  onNavigateToOntology: (ontologyId: string, entity: Entity) => void;
  apiUrl?: string;
}) {
  const dispatch = useAppDispatch();
  const nodesWithChildrenLoaded = useAppSelector(
    (state) => state.ontologies.nodesWithChildrenLoaded
  );
  const nodeChildren = useAppSelector((state) => state.ontologies.nodeChildren);
  const rootNodes = useAppSelector((state) => state.ontologies.rootNodes);
  const numPendingTreeRequests = useAppSelector(
    (state) => state.ontologies.numPendingTreeRequests
  );
  const preferredRoots = useAppSelector(
    (state) => state.ontologies.preferredRoots
  );
  const automaticallyExpandedNodes = useAppSelector(
    (state) => state.ontologies.automaticallyExpandedNodes
  );
  const manuallyExpandedNodes = useAppSelector(
    (state) => state.ontologies.manuallyExpandedNodes
  );

  const showObsoleteEnabled = selectedEntity
    ? selectedEntity.isDeprecated()
    : useAppSelector((state) => state.ontologies.displayObsolete);
  const showSiblingsEnabled = useAppSelector(
    (state) => state.ontologies.displaySiblings
  );
  const showCountsEnabled = useAppSelector(
    (state) => state.ontologies.displayCounts
  );

  const toggleNode = useCallback(
    (node: any) => {
      let isExpanded =
        manuallyExpandedNodes.indexOf(node.absoluteIdentity) !== -1 ||
        automaticallyExpandedNodes.indexOf(node.absoluteIdentity) !== -1;

      if (isExpanded) {
        dispatch(closeNode(node));
      } else {
        dispatch(openNode(node));
      }
    },
    [
      dispatch,
      JSON.stringify(automaticallyExpandedNodes),
      JSON.stringify(manuallyExpandedNodes),
    ]
  );

  // If the ontology, entity type, selected entity IRI, or lang change, reset the tree settings (including showobsolete/preferred roots)
  useEffect(() => {
    dispatch(resetTreeSettings({ entityType, selectedEntity }));
  }, [
    dispatch,
    ontology.getOntologyId(),
    entityType,
    selectedEntity?.getIri(),
  ]);

  // If the ontology, entity type, selected entity, lang OR the showObsoleteEnabled/preferredRoots change, reset the tree content but not the settings
  useEffect(() => {
    dispatch(resetTreeContent());
  }, [
    dispatch,
    ontology.getOntologyId(),
    entityType,
    JSON.stringify(selectedEntity),
    showObsoleteEnabled,
    preferredRoots,
    lang,
  ]);

  useEffect(() => {
    if (selectedEntity) {
      const entityIri = selectedEntity.getIri();
      let promise = dispatch(
        getAncestors({
          ontologyId: ontology.getOntologyId(),
          entityType,
          entityIri,
          lang,
          showObsoleteEnabled,
          showSiblingsEnabled,
          apiUrl,
        })
      );
      return () => promise.abort(); // component was unmounted
    } else if (specifiedRootIri) {
            let promise = dispatch(
                getAncestors({
                    ontologyId: ontology.getOntologyId(),
                    entityType,
                    entityIri: specifiedRootIri,
                    lang,
                    showObsoleteEnabled,
                    showSiblingsEnabled,
                    apiUrl,
                })
            );
            return () => promise.abort(); // component was unmounted
    } else {
      let promise = dispatch(
        getRootEntities({
          ontologyId: ontology.getOntologyId(),
          entityType,
          preferredRoots,
          lang,
          showObsoleteEnabled,
          apiUrl,
        })
      );
      return () => promise.abort(); // component was unmounted
    }
  }, [
    dispatch,
    entityType,
    JSON.stringify(selectedEntity),
    ontology.getOntologyId(),
    preferredRoots,
    lang,
    showObsoleteEnabled,
    specifiedRootIri,
  ]);

    useEffect(() => {
        if (specifiedRootIri) {
            dispatch(
                getEntity({
                    ontologyId: ontology.getOntologyId(),
                    entityIri: specifiedRootIri,
                    apiUrl: apiUrl,
                })
            );
        }
    }, [dispatch, specifiedRootIri, ontology.getOntologyId(),]);

    useEffect(() => {
        if (specifiedRootIri) {
            dispatch(setSpecificRootIri(specifiedRootIri));
        }
    }, [dispatch, specifiedRootIri]);

  const prevNodeChildrenRef = useRef(nodeChildren);

  const haveRelevantPartsChanged = (prev, next) => {
    if (!prev || !next) return true;
    const prevKeys = Object.keys(prev);
    const nextKeys = Object.keys(next);
    if (prevKeys.length !== nextKeys.length) return true;
    for (let key of prevKeys) {
      if (prev[key] !== next[key]) return true;
    }
    return false;
  };

  useEffect(() => {
    const prevNodeChildren = prevNodeChildrenRef.current;
    if (!haveRelevantPartsChanged(prevNodeChildren, nodeChildren)) {
      return;
    }
    let nodesMissingChildren: string[] = manuallyExpandedNodes.filter(
      (absoluteIdentity) =>
        nodesWithChildrenLoaded.indexOf(absoluteIdentity) === -1
    );

    if (showSiblingsEnabled) {
      nodesMissingChildren = [
        ...nodesMissingChildren,
        ...automaticallyExpandedNodes.filter(
          (absoluteIdentity) =>
            nodesWithChildrenLoaded.indexOf(absoluteIdentity) === -1
        ),
      ];
    }

    let promises: any = [];
    for (let absId of nodesMissingChildren) {
      promises.push(
        dispatch(
          getNodeChildren({
            ontologyId: ontology.getOntologyId(),
            entityTypePlural: entityType,
            entityIri: absId.split(";").pop(),
            absoluteIdentity: absId,
            lang,
            showObsoleteEnabled,
            apiUrl,
          })
        )
      );
    }

    return () => {
      for (let promise of promises) promise.abort(); // component was unmounted
    };
  }, [
    dispatch,
    lang,
    JSON.stringify(manuallyExpandedNodes),
    JSON.stringify(automaticallyExpandedNodes),
    nodeChildren,
    ontology.getOntologyId(),
    entityType,
    preferredRoots,
    showObsoleteEnabled,
    showSiblingsEnabled,
  ]);

  let toggleShowObsolete = useCallback(() => {
    if (showObsoleteEnabled) dispatch(hideObsolete());
    else dispatch(showObsolete());
  }, [dispatch, showObsoleteEnabled]);

  let toggleShowSiblings = useCallback(() => {
    if (showSiblingsEnabled) dispatch(hideSiblings());
    else dispatch(showSiblings());
  }, [dispatch, showSiblingsEnabled]);

  let toggleShowCounts = useCallback(() => {
    if (showCountsEnabled) dispatch(hideCounts());
    else dispatch(showCounts());
  }, [dispatch, showCountsEnabled]);

  function renderNodeChildren(
    children: TreeNode[],
    debugNumIterations: number
  ) {
    if (debugNumIterations > 100) {
      throw new Error("probable cyclic tree (renderNodeChildren)");
    }
    const childrenCopy = [...children];
    childrenCopy.sort((a, b) => {
      const titleA = a?.title ? a.title.toString().toUpperCase() : "";
      const titleB = b?.title ? b.title.toString().toUpperCase() : "";
      return titleA === titleB ? 0 : titleA > titleB ? 1 : -1;
    });
    const childrenSorted = childrenCopy.filter(
      (child, i, arr) =>
        !i || child.absoluteIdentity !== arr[i - 1].absoluteIdentity
    );
    return (
      <ul
        role="group"
        className="jstree-container-ul jstree-children jstree-no-icons"
      >
        {childrenSorted.map((childNode: TreeNode, i) => {
          const isExpanded =
            manuallyExpandedNodes.indexOf(childNode.absoluteIdentity) !== -1 ||
            automaticallyExpandedNodes.indexOf(childNode.absoluteIdentity) !==
              -1;
          const isLast = i === childrenSorted!.length - 1;
          const highlight =
            (selectedEntity && childNode.iri === selectedEntity.getIri()) ||
            false;
          return (
            <Node
              expandable={childNode.expandable}
              expanded={isExpanded}
              highlight={highlight}
              isLast={isLast}
              onClick={() => {
                toggleNode(childNode);
              }}
              key={randomString()}
            >
              {childNode.childRelationToParent ===
                  "http://purl.obolibrary.org/obo/BFO_0000050" && (
                <img
                  className="mr-1"
                  src={urlJoin(process.env.PUBLIC_URL!, "/part.svg")}
                  style={{ height: "1em", display: "inline" }}
                />
              )}
              {childNode.childRelationToParent ===
                  "http://purl.obolibrary.org/obo/ms#part_of" && (
                      <img
                          className="mr-1"
                          src={urlJoin(process.env.PUBLIC_URL!, "/part.svg")}
                          style={{ height: "1em", display: "inline" }}
                      />
              )}
              {childNode.childRelationToParent ===
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" && (
                <img
                  className="mr-1"
                  src={urlJoin(process.env.PUBLIC_URL!, "/instance.svg")}
                  style={{ height: "1em", display: "inline" }}
                />
              )}
              <TreeLink
                ontology={ontology}
                entity={childNode.entity}
                title={childNode.title}
                lang={lang}
                onNavigateToEntity={onNavigateToEntity}
                onNavigateToOntology={onNavigateToOntology}
              />
              {!showObsoleteEnabled &&
                showCountsEnabled &&
                childNode.numDescendants > 0 && (
                  <span style={{ color: "gray" }}>
                    {" (" + childNode.numDescendants.toLocaleString() + ")"}
                  </span>
                )}
              {isExpanded &&
                renderNodeChildren(
                  nodeChildren[childNode.absoluteIdentity] || [],
                  debugNumIterations + 1
                )}
            </Node>
          );
        })}
      </ul>
    );
  }
  return (
    <ThemeProvider theme={theme}>
      <div className="flex flex-col-reverse lg:grid lg:grid-cols-3 lg:gap-4">
        {rootNodes ? (
          <div className="lg:col-span-2 relative">
            <div
              className="px-3 pb-3 jstree jstree-1 jstree-proton overflow-x-auto"
              role="tree"
            >
              {renderNodeChildren(rootNodes, 0)}
            </div>
            {numPendingTreeRequests > 0 ? (
              <div className="absolute spinner-default w-7 h-7 -top-1 -left-4 z-auto" />
            ) : null}
          </div>
        ) : null}
        <div className="lg:col-span-1 flex flex-col bg-white px-2 mb-2 rounded-lg">
          {entityType === "classes" &&
            ontology.getPreferredRoots().length > 0 && !specifiedRootIri && (
              <div className="mb-2">
                <FormControl>
                  <RadioGroup
                    name="radio-buttons-group"
                    value={preferredRoots ? "true" : "false"}
                  >
                    <FormControlLabel
                      value="true"
                      onClick={() => dispatch(enablePreferredRoots())}
                      control={<Radio />}
                      label="Preferred roots"
                    />
                    <FormControlLabel
                      value="false"
                      onClick={() => dispatch(disablePreferredRoots())}
                      control={<Radio />}
                      label="All classes"
                    />
                  </RadioGroup>
                </FormControl>
              </div>
            )}
          <FormControlLabel
            control={
              <Checkbox
                disabled={showObsoleteEnabled}
                checked={!showObsoleteEnabled && showCountsEnabled}
                onClick={toggleShowCounts}
              />
            }
            label="Show counts"
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={showObsoleteEnabled}
                onClick={toggleShowObsolete}
              />
            }
            label="Show obsolete terms"
          />
          {selectedEntity && (
            <FormControlLabel
              control={
                <Checkbox
                  checked={showSiblingsEnabled}
                  onClick={toggleShowSiblings}
                />
              }
              label="Show all siblings"
            />
          )}
        </div>
      </div>
    </ThemeProvider>
  );
}

function TreeLink({
  ontology,
  entity,
  title,
  lang,
  onNavigateToEntity,
  onNavigateToOntology,
}: {
  ontology: Ontology;
  entity: Entity;
  title: string;
  lang: string;
  onNavigateToEntity: (ontology: Ontology, entity: Entity) => void;
  onNavigateToOntology: (ontologyId: string, entity: Entity) => void;
}) {
  let definedBy: string[] = entity.getDefinedBy();

  if (definedBy.indexOf(ontology.getOntologyId()) !== -1) definedBy = []; // don't show definedBy links for terms in current ontology

  return (
    <span>
      <a
        title={`${title} [${entity.getIri()}]`}
        className={"link-default"}
        onClick={() => onNavigateToEntity(ontology, entity)}
      >
        {title}
      </a>
      {definedBy.length > 0 &&
        definedBy.map((definingOntology) => {
          return (
            <span
              onClick={() => onNavigateToOntology(definingOntology, entity)}
              title={definingOntology.toUpperCase()}
              className="mx-1 link-ontology px-2 py-0.5 rounded-md text-sm text-white uppercase ml-1"
            >
              {definingOntology}
            </span>
          );
        })}
    </span>
  );
}
