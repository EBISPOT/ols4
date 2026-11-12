import { ThemeProvider } from "@emotion/react";
import {
  Checkbox,
  FormControl,
  FormControlLabel,
  Radio,
  RadioGroup,
  Tooltip,
} from "@mui/material";
import { unstable_useId as useId } from "@mui/material/utils";
import { useCallback, useEffect, useRef, useState } from "react";
import urlJoin from "url-join";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import { theme } from "../../../app/mui";
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
  getSubsets,
  getTreeMemberCounts,
  hideCounts,
  hideObsolete,
  hideSiblings,
  openNode,
  treeScopeKey,
  setTreeScope,
  setTreeRequestContext,
  resetTreeSettings,
  showCounts,
  showObsolete,
  showImported,
  hideImported,
  showSiblings,
  setSelectedSubsets,
  getDirectChildrenCount,
} from "../ontologiesSlice";
import EntityList from "./EntityList";

const importedTermsHelp =
  "When unchecked, imported terms are hidden unless they are needed to show a path in the tree. These terms appear in grey.";

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
  const tree = useAppSelector((state) => state.ontologies);
  const scopeKey = treeScopeKey(apiUrl, ontology.getOntologyId(), entityType);
  const ownsScope = tree.treeScopeKey === scopeKey;
  // Effects from this first render must never capture another tree's subsets.
  const selectedSubsets = ownsScope ? tree.selectedSubsets : [];
  const subsets = ownsScope ? tree.subsets : [];
  const filteringBySubsets = selectedSubsets.length > 0 && !specifiedRootIri;
  const subsetTree = filteringBySubsets ? selectedSubsets : undefined;
  const preferredRoots = ownsScope ? tree.preferredRoots : false;
  const showObsoleteEnabled = !!selectedEntity?.isDeprecated() || (ownsScope && tree.displayObsolete);
  const showImportedEnabled = !ownsScope || tree.displayImported;
  const importedTermsHelpId = useId();
  const showSiblingsEnabled = ownsScope && tree.displaySiblings;
  const showCountsEnabled = !ownsScope || tree.displayCounts;
  // Stored totals include hidden obsolete members, including retained connectors.
  const needsFilteredCounts = filteringBySubsets || !showImportedEnabled || !showObsoleteEnabled;
  // Ordinary counts retain their existing obsolete-mode restriction.
  const showNodeCounts = showCountsEnabled && (filteringBySubsets || !showObsoleteEnabled);
  const requestKey = JSON.stringify([
    scopeKey, lang, selectedEntity, specifiedRootIri || "", preferredRoots,
    showObsoleteEnabled, showImportedEnabled, showSiblingsEnabled, subsetTree || [],
  ]);
  const ownsContent = ownsScope && tree.treeRequestKey === requestKey;
  const nodesWithChildrenLoaded = ownsContent ? tree.nodesWithChildrenLoaded : [];
  const nodeChildren = ownsContent ? tree.nodeChildren : {};
  const requiredPathChildren = ownsContent ? tree.requiredPathChildren : {};
  const rootNodes = ownsContent ? tree.rootNodes : [];
  const filteredMemberCounts = ownsContent ? tree.filteredMemberCounts : {};
  const filteredMemberCountsStatus = ownsContent ? tree.filteredMemberCountsStatus : "idle";
  const numPendingTreeRequests = ownsContent ? tree.numPendingTreeRequests : 0;
  const automaticallyExpandedNodes = ownsContent ? tree.automaticallyExpandedNodes : [];
  const manuallyExpandedNodes = ownsContent ? tree.manuallyExpandedNodes : [];
  const directChildrenCounts = ownsContent ? tree.directChildrenCounts : {};
  const rootStatus = ownsContent ? tree.rootStatus : "idle";
  const requestErrors = ownsContent ? tree.treeRequestErrors : {};
  // Retry reruns the owning effect with the latest render's settings and abort cleanup.
  const [rootAttempt, retryRoot] = useState(0);
  const [countAttempt, retryCounts] = useState(0);
  const [subsetAttempt, retrySubsets] = useState(0);
  const [childrenAttempt, retryChildren] = useState(0);
  const countRequests = useRef<any[]>([]);

  useEffect(() => {
    dispatch(setTreeScope(scopeKey));
  }, [dispatch, scopeKey]);

  useEffect(() => {
    dispatch(resetTreeSettings({ entityType, selectedEntity }));
  }, [dispatch, scopeKey, selectedEntity?.getIri()]);

  useEffect(() => {
    dispatch(setTreeRequestContext({ scopeKey, requestKey }));
    return () => {
      for (const promise of countRequests.current) promise.abort();
      countRequests.current = [];
    };
  }, [dispatch, scopeKey, requestKey]);

  const requestDirectCount = (node: TreeNode) => {
    const promise = dispatch(getDirectChildrenCount({
      ontologyId: ontology.getOntologyId(), entityIri: node.iri,
      entityType: entityType === "entities" ? "classes" : entityType, lang,
      showObsoleteEnabled, showImportedEnabled, subsetTree, apiUrl,
      treeScopeKey: scopeKey, requestKey,
    }));
    countRequests.current.push(promise);
  };

  const toggleNode = useCallback(
    (node: any) => {
      let isExpanded =
        manuallyExpandedNodes.indexOf(node.absoluteIdentity) !== -1 ||
        automaticallyExpandedNodes.indexOf(node.absoluteIdentity) !== -1;

      if (isExpanded) {
        dispatch(closeNode(node));
      } else {
        // Combined class and individual pages already return the exact child total.
        if (entityType !== "classes" && entityType !== "individuals" &&
            directChildrenCounts[node.iri] === undefined) {
          requestDirectCount(node);
        }
        dispatch(openNode(node));
      }
    },
    [
      dispatch,
      JSON.stringify(automaticallyExpandedNodes),
      JSON.stringify(manuallyExpandedNodes),
      directChildrenCounts,
      ontology,
      entityType,
      lang,
      showObsoleteEnabled,
      showImportedEnabled,
      JSON.stringify(subsetTree),
      apiUrl,
      scopeKey,
      requestKey,
    ]
  );

  useEffect(() => {
    const args = {
      ontologyId: ontology.getOntologyId(), entityType, ontology,
      preferredRoots, lang, showObsoleteEnabled, showImportedEnabled,
      subsetTree, apiUrl, treeScopeKey: scopeKey, requestKey, specifiedRootIri,
    };
    const promise = selectedEntity || specifiedRootIri
      ? dispatch(getAncestors({ ...args, selectedEntity, entityIri: selectedEntity?.getIri() || specifiedRootIri }))
      : dispatch(getRootEntities(args));
    return () => promise.abort();
  }, [dispatch, requestKey, rootAttempt]);

  useEffect(() => {
    if (specifiedRootIri) return;
    const promise = dispatch(getSubsets({
      ontologyId: ontology.getOntologyId(), entityType, lang,
      showObsoleteEnabled, showImportedEnabled, apiUrl, treeScopeKey: scopeKey, requestKey,
    }));
    return () => promise.abort();
  }, [dispatch, requestKey, subsetAttempt]);

  useEffect(() => {
    if (!needsFilteredCounts || !showNodeCounts) return;
    const promise = dispatch(getTreeMemberCounts({
      ontologyId: ontology.getOntologyId(), entityType, selectedSubsets: subsetTree, lang,
      showObsoleteEnabled, showImportedEnabled, apiUrl, treeScopeKey: scopeKey, requestKey,
    }));
    return () => promise.abort();
  }, [dispatch, requestKey, needsFilteredCounts, showNodeCounts, countAttempt]);

  useEffect(() => {
    const expanded = showSiblingsEnabled
      ? [...manuallyExpandedNodes, ...automaticallyExpandedNodes]
      : manuallyExpandedNodes;
    const missing = Array.from(new Set(expanded)).filter(id => !nodesWithChildrenLoaded.includes(id));
    const promises = missing.map(absoluteIdentity => dispatch(getNodeChildren({
      ontologyId: ontology.getOntologyId(), entityTypePlural: entityType,
      entityIri: absoluteIdentity.split(";").pop(), absoluteIdentity, lang,
      includeObsoleteEntities: showObsoleteEnabled, includeImportedEntities: showImportedEnabled,
      apiUrl, subsetTree, treeScopeKey: scopeKey, requestKey,
    })));
    return () => { for (const promise of promises) promise.abort(); };
  }, [dispatch, requestKey, JSON.stringify(manuallyExpandedNodes), JSON.stringify(automaticallyExpandedNodes), childrenAttempt]);

  let toggleShowImported = useCallback(() => {
    if (showImportedEnabled) dispatch(hideImported());
    else dispatch(showImported());
  }, [dispatch, showImportedEnabled]);

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

  let toggleSubset = useCallback(
    (subsetIri: string) => {
      dispatch(
        setSelectedSubsets(
          selectedSubsets.includes(subsetIri)
            ? selectedSubsets.filter((iri) => iri !== subsetIri)
            : [...selectedSubsets, subsetIri]
        )
      );
    },
    [dispatch, selectedSubsets]
  );

  function renderNodeChildren(
    children: TreeNode[],
    ancestorIris: Set<string> = new Set()
  ) {
    // Expansion responses may contain a cycle; only omit repeated IRIs on the
    // current path, preserving legitimate deep paths and alternate parent paths.
    const childrenCopy = children.filter(child => !ancestorIris.has(child.iri));
    // Classes sort before individuals, so a node's subcategories are not
    // buried under its members; alphabetical within each group.
    const isIndividual = (node: TreeNode) =>
      node?.entity?.getType() === "individual";
    childrenCopy.sort((a, b) => {
      if (isIndividual(a) !== isIndividual(b)) {
        return isIndividual(a) ? 1 : -1;
      }
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
          const numDescendants = needsFilteredCounts
            ? filteredMemberCounts[childNode.iri]
            : getNumDescendants(childNode.numHierarchicalDescendants, childNode.numDescendants);
          return (
            <Node
              expandable={childNode.expandable}
              expanded={isExpanded}
              highlight={highlight}
              isLast={isLast}
              onClick={() => {
                toggleNode(childNode);
              }}
              key={childNode.absoluteIdentity}
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
                importedContext={!showImportedEnabled && !childNode.entity.isCanonical()}
                obsoleteContext={!showObsoleteEnabled && childNode.entity.isDeprecated()}
                lang={lang}
                onNavigateToEntity={onNavigateToEntity}
                onNavigateToOntology={onNavigateToOntology}
              />
              {showNodeCounts && (needsFilteredCounts && filteredMemberCountsStatus === "loading"
                ? <span style={{ color: "gray" }} aria-label="Descendant count loading">{" (…)"}</span>
                : numDescendants > 0 && (
                  <span style={{ color: "gray" }}>
                    {" (" + numDescendants.toLocaleString() + ")"}
                  </span>
                ))}
              {isExpanded && directChildrenCounts[childNode.iri] === undefined && requestErrors[`ontologies_direct_children_count:${childNode.iri}`] && (
                <div role="alert">Unable to load the child count. <button type="button" className="link-default" onClick={() => requestDirectCount(childNode)}>Retry child count</button></div>
              )}
              {isExpanded && requestErrors[`ontologies_node_children:${childNode.absoluteIdentity}`] && (
                <div role="alert">Unable to load children. <button type="button" className="link-default" onClick={() => retryChildren(n => n + 1)}>Retry children</button></div>
              )}
              {isExpanded && (() => {
                const directChildCount = directChildrenCounts[childNode.iri];
                if (directChildCount !== undefined && directChildCount > 1000) {
                  // Show EntityList for nodes with >1000 direct children
                  return (
                    <>
                      {/* Keep the selected ancestry visible beside paginated siblings. */}
                      {renderNodeChildren(requiredPathChildren[childNode.absoluteIdentity] || [], new Set(ancestorIris).add(childNode.iri))}
                      <div className="ml-4">
                        <EntityList
                          ontologyId={ontology.getOntologyId()}
                          entityType={entityType === "entities" ? "classes" : entityType}
                          parentEntityIri={childNode.iri}
                          tableKey={childNode.absoluteIdentity}
                          lang={lang}
                          showObsoleteEnabled={showObsoleteEnabled}
                          subsetTree={subsetTree}
                          apiUrl={apiUrl}
                          showImportedEnabled={showImportedEnabled}
                          treeScopeKey={scopeKey}
                          requestKey={requestKey}
                          onNavigateToEntity={(ontologyId, entity) => {
                            if (entity.getOntologyId() === ontologyId) {
                              onNavigateToEntity(ontology, entity);
                            } else {
                              onNavigateToOntology(entity.getOntologyId(), entity);
                            }
                          }}
                          title="Direct Children"
                        />
                      </div>
                    </>
                  );
                } else {
                  // Show regular tree for nodes with ≤1000 direct children
                  return renderNodeChildren(
                    nodeChildren[childNode.absoluteIdentity] || [],
                    new Set(ancestorIris).add(childNode.iri)
                  );
                }
              })()}
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
            {rootStatus === "failed" && (
              <div role="alert">Unable to load the tree. <button type="button" className="link-default" onClick={() => retryRoot(n => n + 1)}>Retry tree</button></div>
            )}
            {rootStatus === "loading" && <p role="status">Loading tree…</p>}
            {rootStatus === "succeeded" && rootNodes.length === 0 && <p role="status">No terms match the current tree settings.</p>}
            {showNodeCounts && needsFilteredCounts && filteredMemberCountsStatus === "failed" && (
              <div role="alert">Unable to load descendant counts. <button type="button" className="link-default" onClick={() => retryCounts(n => n + 1)}>Retry counts</button></div>
            )}
            <div
              className="px-3 pb-3 jstree jstree-1 jstree-proton overflow-x-auto"
              role="tree"
            >
              {renderNodeChildren(rootNodes)}
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
                disabled={showObsoleteEnabled && !filteringBySubsets}
                checked={(!showObsoleteEnabled || filteringBySubsets) && showCountsEnabled}
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
          <Tooltip title={importedTermsHelp} describeChild>
            <FormControlLabel
              control={
                <Checkbox
                  checked={showImportedEnabled}
                  onClick={toggleShowImported}
                  inputProps={{ "aria-describedby": importedTermsHelpId }}
                />
              }
              label="Show imported terms"
            />
          </Tooltip>
          <span id={importedTermsHelpId} hidden>{importedTermsHelp}</span>
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
          {requestErrors.ontologies_subsets && !specifiedRootIri && (
            <div role="alert">Unable to load subset filters. <button type="button" className="link-default" onClick={() => retrySubsets(n => n + 1)}>Retry subset filters</button></div>
          )}
          {subsets.length > 0 && !specifiedRootIri && (
            <details open className="mt-2">
              <summary className="py-2 link-default">
                {"Filter by subset (" + subsets.length + ")"}
              </summary>
              <div className="flex flex-col max-h-60 overflow-y-auto">
                {subsets.map((subset) => (
                  <FormControlLabel
                    key={subset.iri}
                    title={subset.iri}
                    control={
                      <Checkbox
                        checked={selectedSubsets.includes(subset.iri)}
                        onClick={() => toggleSubset(subset.iri)}
                      />
                    }
                    label={
                      <>
                        {subset.label}
                        <span style={{ color: "gray" }}>
                          {" (" + subset.numMembers.toLocaleString() + ")"}
                        </span>
                      </>
                    }
                  />
                ))}
              </div>
            </details>
          )}
        </div>
      </div>
    </ThemeProvider>
  );
}

function getNumDescendants(hierarchicalDescendents: number, descendents: number): number {
    if (descendents === hierarchicalDescendents) {
        return descendents;
    } else if (descendents > hierarchicalDescendents) {
        return descendents;
    } else {
        return hierarchicalDescendents;
    }
}

function TreeLink({
  ontology,
  entity,
  title,
  importedContext,
  obsoleteContext,
  lang,
  onNavigateToEntity,
  onNavigateToOntology,
}: {
  ontology: Ontology;
  entity: Entity;
  title: string;
  importedContext: boolean;
  obsoleteContext: boolean;
  lang: string;
  onNavigateToEntity: (ontology: Ontology, entity: Entity) => void;
  onNavigateToOntology: (ontologyId: string, entity: Entity) => void;
}) {
  let definedBy: string[] = entity.getDefinedBy();

  if (definedBy.indexOf(ontology.getOntologyId()) !== -1) definedBy = []; // don't show definedBy links for terms in current ontology

  return (
    <span>
      <a
        title={`${title} [${entity.getIri()}]${obsoleteContext ? ' (obsolete ancestry context)' : ''}`}
        className={importedContext ? "link-default imported-tree-link" : "link-default"}
        onClick={() => onNavigateToEntity(ontology, entity)}
      >
        {title}
      </a>
      {obsoleteContext && (
        <span className="ml-1 text-sm text-gray-600" title="Obsolete term retained to show the ancestry path">
          {" (obsolete ancestry context)"}
        </span>
      )}
      {definedBy.length > 0 &&
        definedBy.map((definingOntology) => {
          return (
            <span
              key={definingOntology}
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
