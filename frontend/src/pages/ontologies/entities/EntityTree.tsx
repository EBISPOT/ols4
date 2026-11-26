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
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import urlJoin from "url-join";
import { useAppDispatch, useAppSelector } from "../../../app/hooks";
import { theme } from "../../../app/mui";
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
  treePreferencesScopeKey,
  setTreeScope,
  setTreeRequestContext,
  showCounts,
  showObsolete,
  showImported,
  hideImported,
  showSiblings,
  setSelectedSubsets,
} from "../ontologiesSlice";
import EntityList from "./EntityList";
import OntologyTreeView from "./OntologyTreeView";

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
  const preferencesScopeKey = treePreferencesScopeKey(apiUrl, ontology.getOntologyId());
  const ownsPreferences = tree.treePreferencesScopeKey === preferencesScopeKey;
  // Effects from this first render must never capture another tree's subsets.
  const selectedSubsets = ownsScope ? tree.selectedSubsets : [];
  const subsets = ownsScope ? tree.subsets : [];
  const filteringBySubsets = selectedSubsets.length > 0 && !specifiedRootIri;
  const subsetTree = filteringBySubsets ? selectedSubsets : undefined;
  // Remember the class root choice even if this ontology is first opened on a
  // different tab. Preferred-root filtering itself only applies to classes.
  const defaultPreferredRoots = ontology.getPreferredRoots().length > 0;
  const preferredRoots = entityType === "classes" && (ownsPreferences ? tree.preferredRoots : defaultPreferredRoots);
  const showObsoleteEnabled = !!selectedEntity?.isDeprecated() || (ownsPreferences && tree.displayObsolete);
  const showImportedEnabled = !ownsPreferences || tree.displayImported;
  const importedTermsHelpId = useId();
  const showSiblingsEnabled = ownsPreferences && tree.displaySiblings;
  const showCountsEnabled = !ownsPreferences || tree.displayCounts;
  // Stored totals include hidden obsolete members, including retained connectors.
  const needsFilteredCounts = filteringBySubsets || !showImportedEnabled || !showObsoleteEnabled;
  // Ordinary counts retain their existing obsolete-mode restriction.
  const showNodeCounts = showCountsEnabled && (filteringBySubsets || !showObsoleteEnabled);
  const requestKey = JSON.stringify([
    scopeKey, lang, selectedEntity, specifiedRootIri || "", preferredRoots,
    showObsoleteEnabled, showImportedEnabled, showSiblingsEnabled, subsetTree || [],
  ]);
  const subsetsResultKey = JSON.stringify([scopeKey, lang, showObsoleteEnabled, showImportedEnabled]);
  const memberCountsResultKey = JSON.stringify([subsetsResultKey, subsetTree || []]);
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
  const childRequests = useRef(new Map<string, { abort: () => void }>());

  useEffect(() => {
    dispatch(setTreeScope({ scopeKey, preferencesScopeKey, preferredRoots: defaultPreferredRoots }));
  }, [dispatch, scopeKey, preferencesScopeKey, defaultPreferredRoots]);

  useEffect(() => {
    dispatch(setTreeRequestContext({ scopeKey, requestKey, memberCountsResultKey }));
    const pending = childRequests.current;
    return () => {
      pending.forEach((request) => request.abort());
      pending.clear();
    };
  }, [dispatch, scopeKey, requestKey, memberCountsResultKey]);

  const expandedItems = useMemo(
    () => Array.from(new Set([...manuallyExpandedNodes, ...automaticallyExpandedNodes])),
    [manuallyExpandedNodes, automaticallyExpandedNodes]
  );

  const isWide = useCallback((node: TreeNode) => directChildrenCounts[node.iri] > 1000, [directChildrenCounts]);

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

  const rootsReady = rootStatus === "succeeded";
  const hasRoots = rootsReady && rootNodes.length > 0;

  useEffect(() => {
    if (!rootsReady || specifiedRootIri || tree.subsetsResultKey === subsetsResultKey) return;
    const promise = dispatch(getSubsets({
      ontologyId: ontology.getOntologyId(), entityType, lang,
      showObsoleteEnabled, showImportedEnabled, apiUrl, treeScopeKey: scopeKey, requestKey,
      resultKey: subsetsResultKey,
    }));
    return () => promise.abort();
  }, [dispatch, requestKey, rootsReady, subsetAttempt]);

  useEffect(() => {
    if (!hasRoots || !needsFilteredCounts || !showNodeCounts ||
        tree.memberCountsResultKey === memberCountsResultKey) return;
    const promise = dispatch(getTreeMemberCounts({
      ontologyId: ontology.getOntologyId(), entityType, selectedSubsets: subsetTree, lang,
      showObsoleteEnabled, showImportedEnabled, apiUrl, treeScopeKey: scopeKey, requestKey,
      resultKey: memberCountsResultKey,
    }));
    return () => promise.abort();
  }, [dispatch, requestKey, hasRoots, needsFilteredCounts, showNodeCounts, countAttempt]);

  useEffect(() => {
    if (!ownsContent) return;
    const expanded = showSiblingsEnabled
      ? [...manuallyExpandedNodes, ...automaticallyExpandedNodes]
      : manuallyExpandedNodes;
    const loaded = new Set(nodesWithChildrenLoaded);
    const pending = childRequests.current;
    for (const absoluteIdentity of expanded) {
      if (loaded.has(absoluteIdentity) || pending.has(absoluteIdentity)) continue;
      const request = dispatch(getNodeChildren({
        ontologyId: ontology.getOntologyId(), entityTypePlural: entityType,
        entityIri: absoluteIdentity.split(";").pop(), absoluteIdentity, lang,
        includeObsoleteEntities: showObsoleteEnabled, includeImportedEntities: showImportedEnabled,
        apiUrl, subsetTree, treeScopeKey: scopeKey, requestKey,
      }));
      pending.set(absoluteIdentity, request);
      void request.then(() => {
        if (pending.get(absoluteIdentity) === request) pending.delete(absoluteIdentity);
      });
    }
  }, [dispatch, requestKey, ownsContent, manuallyExpandedNodes, automaticallyExpandedNodes, childrenAttempt]);

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

  function renderLabel(node: TreeNode) {
    const numDescendants = needsFilteredCounts
      ? filteredMemberCounts[node.iri]
      : Math.max(node.numHierarchicalDescendants, node.numDescendants);
    const relationIcon = node.childRelationToParent === "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
      ? { file: "instance.svg", alt: "Instance of" }
      : ["http://purl.obolibrary.org/obo/BFO_0000050", "http://purl.obolibrary.org/obo/ms#part_of"]
        .includes(node.childRelationToParent || "")
        ? { file: "part.svg", alt: "Part of" }
        : undefined;
    return (
      <>
        {relationIcon && (
          <img className="ols-tree-relation" src={urlJoin(process.env.PUBLIC_URL!, relationIcon.file)} alt={relationIcon.alt} />
        )}
        <TreeLink
          ontology={ontology}
          entity={node.entity}
          title={node.title}
          importedContext={!showImportedEnabled && !node.entity.isCanonical()}
          obsoleteContext={!showObsoleteEnabled && node.entity.isDeprecated()}
          onNavigateToEntity={onNavigateToEntity}
          onNavigateToOntology={onNavigateToOntology}
        />
        {showNodeCounts && (needsFilteredCounts &&
          (filteredMemberCountsStatus === "idle" || filteredMemberCountsStatus === "loading")
          ? <span style={{ color: "gray" }} aria-label="Descendant count loading">{" (…)"}</span>
          : numDescendants > 0 && (
            <span style={{ color: "gray" }}>{" (" + numDescendants.toLocaleString() + ")"}</span>
          ))}
      </>
    );
  }

  function renderDetails(node: TreeNode) {
    const failed = requestErrors[`ontologies_node_children:${node.absoluteIdentity}`];
    const wide = isWide(node);
    if (!failed && !wide) return null;

    return (
      <>
        {failed && (
          <div role="alert">Unable to load children. <button type="button" className="link-default" onClick={() => retryChildren(n => n + 1)}>Retry children</button></div>
        )}
        {wide && (
          <EntityList
            ontologyId={ontology.getOntologyId()}
            entityType={entityType === "entities" ? "classes" : entityType}
            parentEntityIri={node.iri}
            tableKey={node.absoluteIdentity}
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
        )}
      </>
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
            {hasRoots && (
              <div className="px-3 pb-3 overflow-x-auto">
                <OntologyTreeView
                  key={requestKey}
                  roots={rootNodes}
                  childrenByPath={nodeChildren}
                  requiredByPath={requiredPathChildren}
                  expandedItems={expandedItems}
                  selectedIri={selectedEntity?.getIri()}
                  isWide={isWide}
                  setExpanded={(node, expanded) => dispatch(expanded ? openNode(node) : closeNode(node))}
                  activate={node => onNavigateToEntity(ontology, node.entity)}
                  renderLabel={renderLabel}
                  renderDetails={renderDetails}
                />
              </div>
            )}
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
              label="Show imported terms in tree"
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

function TreeLink({
  ontology,
  entity,
  title,
  importedContext,
  obsoleteContext,
  onNavigateToEntity,
  onNavigateToOntology,
}: {
  ontology: Ontology;
  entity: Entity;
  title: string;
  importedContext: boolean;
  obsoleteContext: boolean;
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
