import {
  Draft,
  createAction,
  createAsyncThunk,
  createSlice,
  PayloadAction,
} from "@reduxjs/toolkit";
import { get, getPaginated, Page } from "../../app/api";
import { mapToApiParams, thingFromJsonProperties } from "../../app/util";
import Entity from "../../model/Entity";
import Ontology from "../../model/Ontology";
import createTreeFromEntities from "./entities/createTreeFromEntities";

const RDF_TYPE_IRI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
const SUBSET_PROPERTY_IRI =
  "http://www.geneontology.org/formats/oboInOwl#SubsetProperty";

export interface OntologiesState {
  treeScopeKey: string | null;
  treePreferencesScopeKey: string | null;
  treeRequestKey: string | null;
  rootStatus: "idle" | "loading" | "succeeded" | "failed";
  treeRequestErrors: Record<string, boolean>;
  treeRequests: Record<string, { requestId: string; loading: boolean }>;
  ontology: Ontology | undefined;
  entity: Entity | undefined;
  nodesWithChildrenLoaded: string[];
  nodeChildren: any;
  requiredPathChildren: Record<string, TreeNode[]>;
  rootNodes: TreeNode[];
  subsets: SubsetOption[];
  subsetsResultKey: string | null;
  memberCountsResultKey: string | null;
  selectedSubsets: string[];
  filteredMemberCounts: { [iri: string]: number };
  filteredMemberCountsStatus: "idle" | "loading" | "succeeded" | "failed";
  ontologies: Ontology[];
  totalOntologies: number;
  entities: Entity[];
  totalEntities: number;
  loadingOntologies: boolean;
  loadingEntities: boolean;
  numPendingTreeRequests: number;
  loadingOntology: boolean;
  loadingEntity: boolean;
  classInstances: Page<Entity> | null;
  loadingClassInstances: boolean;
  relatedFrom: Entity[] | null;
  loadingRelatedFrom: boolean;
  directChildrenCounts: { [key: string]: number };
  directChildrenTables: Record<string, { elements: Entity[]; totalElements: number; loading: boolean }>;
  directChildrenEntities: Entity[];
  totalDirectChildrenEntities: number;
  loadingDirectChildrenEntities: boolean;
  automaticallyExpandedNodes: string[];
  manuallyExpandedNodes: string[];
  preferredRoots: boolean;
  displayObsolete: boolean;
  displayImported: boolean;
  displaySiblings: boolean;
  displayCounts: boolean;
  errorMessage: string;
  specificRootIri: string;
}
export interface TreeNode {
  absoluteIdentity: string; // the IRIs of this node and its ancestors delimited by a ;
  iri: string;
  title: string;
  expandable: boolean;
  entity: Entity;
  numDescendants: number;
  numHierarchicalDescendants: number;
  parentRelationToChild: string | null; // if applicable, relation from the parent node to this node (e.g. has_part)
  childRelationToParent: string | null; // if applicable, relation from this node to the parent node (e.g. part_of)
}
export interface SubsetOption {
  iri: string;
  label: string;
  numMembers: number;
}

const initialState: OntologiesState = {
  treeScopeKey: null,
  treePreferencesScopeKey: null,
  treeRequestKey: null,
  treeRequests: {},
  treeRequestErrors: {},
  rootStatus: "idle",
  ontology: undefined,
  entity: undefined,
  nodesWithChildrenLoaded: [],
  nodeChildren: {},
  requiredPathChildren: {},
  rootNodes: [],
  subsets: [],
  subsetsResultKey: null,
  memberCountsResultKey: null,
  selectedSubsets: [],
  filteredMemberCounts: {},
  filteredMemberCountsStatus: "idle",
  ontologies: [],
  totalOntologies: 0,
  entities: [],
  totalEntities: 0,
  loadingOntologies: false,
  loadingEntities: false,
  numPendingTreeRequests: 0,
  loadingOntology: false,
  loadingEntity: false,
  classInstances: null,
  loadingClassInstances: false,
  relatedFrom: null,
  loadingRelatedFrom: false,
  directChildrenCounts: {},
  directChildrenTables: {},
  directChildrenEntities: [],
  totalDirectChildrenEntities: 0,
  loadingDirectChildrenEntities: false,
  automaticallyExpandedNodes: [],
  manuallyExpandedNodes: [],
  preferredRoots: false,
  displayObsolete: false,
  displayImported: true,
  displaySiblings: false,
  displayCounts: true,
  errorMessage: "",
  specificRootIri: "",
};

// Results and subsets stay type-specific; display choices follow mixed class /
// individual navigation and explicit tab changes within the same API/ontology.
export const treeScopeKey = (apiUrl: string | undefined, ontologyId: string, entityType: string) =>
  JSON.stringify([apiUrl || "", ontologyId, entityType]);
export const treePreferencesScopeKey = (apiUrl: string | undefined, ontologyId: string) =>
  JSON.stringify([apiUrl || "", ontologyId]);
export const setTreeScope = createAction<{
  scopeKey: string; preferencesScopeKey: string; preferredRoots: boolean;
}>("ontologies_set_tree_scope");
export const setTreeRequestContext = createAction<{
  scopeKey: string; requestKey: string; memberCountsResultKey?: string;
}>(
  "ontologies_set_tree_request_context"
);

function clearTreeContent(state: Draft<OntologiesState>, keepMemberCounts = false) {
  state.nodesWithChildrenLoaded = [];
  state.nodeChildren = {};
  state.requiredPathChildren = {};
  state.rootNodes = [];
  state.automaticallyExpandedNodes = [];
  state.manuallyExpandedNodes = [];
  state.directChildrenCounts = {};
  state.directChildrenTables = {};
  state.directChildrenEntities = [];
  state.totalDirectChildrenEntities = 0;
  state.loadingDirectChildrenEntities = false;
  if (!keepMemberCounts) {
    state.filteredMemberCounts = {};
    state.filteredMemberCountsStatus = "idle";
    state.memberCountsResultKey = null;
  }
  state.treeRequests = {};
  state.treeRequestErrors = {};
  state.rootStatus = "idle";
  state.numPendingTreeRequests = 0;
}

function ownsTreeRequest(state: Draft<OntologiesState>, arg: any) {
  return arg.treeScopeKey === state.treeScopeKey && arg.requestKey === state.treeRequestKey;
}

// Reject effects captured by an earlier render before they can issue a fetch.
const treeThunkOptions = {
  condition: (arg: any, { getState }: any) => ownsTreeRequest(getState().ontologies, arg),
};

// absolute path distinguishes the same parent shown on two branches of one tree.
export const directChildrenTableKey = (arg: { tableKey?: string; entityType?: string; entityIri?: string }) =>
  arg.tableKey || JSON.stringify([arg.entityType, arg.entityIri]);

function treeRequestSlot(action: any): string {
  const type = action.type.slice(0, action.type.lastIndexOf("/"));
  const arg = action.meta.arg;
  if (type === "ontologies_direct_children_entities") return `${type}:${directChildrenTableKey(arg)}`;
  if (type === "ontologies_node_children") return `${type}:${arg.absoluteIdentity}`;
  return type;
}

function startTreeRequest(state: Draft<OntologiesState>, action: any, loading = false) {
  if (!ownsTreeRequest(state, action.meta.arg)) return false;
  const slot = treeRequestSlot(action);
  if (state.treeRequests[slot]?.loading) --state.numPendingTreeRequests;
  delete state.treeRequestErrors[slot];
  state.treeRequests[slot] = { requestId: action.meta.requestId, loading };
  if (loading) ++state.numPendingTreeRequests;
  return true;
}

function finishTreeRequest(state: Draft<OntologiesState>, action: any) {
  if (!ownsTreeRequest(state, action.meta.arg)) return false;
  const slot = treeRequestSlot(action);
  const request = state.treeRequests[slot];
  if (request?.requestId !== action.meta.requestId) return false;
  if (request.loading) --state.numPendingTreeRequests;
  delete state.treeRequests[slot];
  if (action.type.endsWith("/rejected") && !action.meta.aborted && !action.meta.condition) {
    state.treeRequestErrors[slot] = true;
  }
  return true;
}

export const resetTreeContent = createAction("ontologies_tree_reset_content");

export const enablePreferredRoots = createAction(
  "ontologies_preferred_enabled"
);
export const disablePreferredRoots = createAction(
  "ontologies_preferred_disabled"
);
export const openNode = createAction<TreeNode>("ontologies_node_open");
export const closeNode = createAction<TreeNode>("ontologies_node_close");

export const showObsolete = createAction("ontologies_show_obsolete");
export const hideObsolete = createAction("ontologies_hide_obsolete");
export const showImported = createAction("ontologies_show_imported");
export const hideImported = createAction("ontologies_hide_imported");

export const showSiblings = createAction("ontologies_show_siblings");
export const hideSiblings = createAction("ontologies_hide_siblings");

export const showCounts = createAction("ontologies_show_counts");
export const hideCounts = createAction("ontologies_hide_counts");

export const setSelectedSubsets = createAction<string[]>(
  "ontologies_set_selected_subsets"
);

// One tree contract for roots, expansions and paginated child fallback. Counts of
// members keep using direct native/subset filters instead of counting connectors.
function treeContextParams(subsetTree?: string[]): Record<string, string> {
  return {
    ...(subsetTree?.length ? { subsetTree: subsetTree.join(",") } : {}),
    treeContext: "true",
  };
}
// Tree requests ask for nodes, not documents: identity, label, parent links, child
// flags and counts (the backend's view=tree). The full documents made a page of
// 1000 cohorts 38 MB. Listings that show descriptions keep the full view.
const TREE_NODE_VIEW = { view: "tree" };
const TREE_PAGE_SIZE = 1000;

// A deployment may cap the requested page size. Start with the returned page
// metadata, then read the remaining pages in bounded batches and in server order.
async function readTreePages(
  first: Page<any>,
  loadPage: (page: number) => Promise<Page<any>>,
  limit = Infinity
): Promise<any[]> {
  const pages = first.elements.length
    ? Math.min(first.totalPages, Math.ceil(limit / first.elements.length))
    : first.totalPages;
  const elements = [...first.elements];
  const batchSize = 4;
  for (let page = 1; page < pages; page += batchSize) {
    const next = await Promise.all(Array.from(
      { length: Math.min(batchSize, pages - page) },
      (_, index) => loadPage(page + index)
    ));
    for (const response of next) elements.push(...response.elements);
  }
  return elements.slice(0, limit);
}

function treePageLoader(
  path: string, params: Record<string, string>, apiUrl: string | undefined, signal: AbortSignal
) {
  return (page: number) => getPaginated<any>(
    path, { ...params, ...(page ? { page: String(page) } : {}) }, apiUrl, { signal }
  );
}

function treeChildrenRoute(ontologyId: string, entityType: string, entityIri: string) {
  const iri = encodeURIComponent(encodeURIComponent(entityIri));
  if (entityType === "classes" || entityType === "individuals") {
    return `api/v2/ontologies/${ontologyId}/classes/${iri}/treeChildren`;
  }
  return `api/v2/ontologies/${ontologyId}/${entityType}/${iri}/children`;
}
export const setSpecificRootIri = createAction<string>("ontologies_set_specific_root_iri");

export const getOntology = createAsyncThunk(
  "ontologies_ontology",
  async (
    {
      ontologyId,
      lang,
      apiUrl,
    }: { ontologyId: string; lang: string; apiUrl?: string },
    { rejectWithValue }
  ) => {
    const path = `api/v2/ontologies/${ontologyId}`;
    try {
      const ontologyProperties = await get<any>(path, { lang }, apiUrl);
      return new Ontology(ontologyProperties);
    } catch (error: any) {
      return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
    }
  }
);
export const getEntityWithType = createAsyncThunk(
  "ontologies_entity_type",
  async (
    {
      ontologyId,
      entityType,
      entityIri,
      searchParams,
    }: {
      ontologyId: string;
      entityType: string;
      entityIri: string;
      searchParams: URLSearchParams;
    },
    { rejectWithValue }
  ) => {
    const apiSearchParams = mapToApiParams(searchParams);
    apiSearchParams.set("includeObsoleteEntities", "true");
    const doubleEncodedTermUri = encodeURIComponent(
      encodeURIComponent(entityIri)
    );
    let path = "";
    try {
      let entityJsonProperties = null;
      if (entityIri) {
        path = `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedTermUri}?${new URLSearchParams(
          apiSearchParams
        )}`;
        entityJsonProperties = await get<any>(path);
      } else {
        path = `api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams(
          apiSearchParams
        )}`;
        const results = await getPaginated<any>(path);
        if (results.elements.length === 1 && results.totalElements === 1) {
          entityJsonProperties = results.elements[0];
        } else {
          return rejectWithValue(
            `Error accessing: ${path}; Retrieved nondistinct entity: ${JSON.stringify(
              results
            )}`
          );
        }
      }
      return thingFromJsonProperties(entityJsonProperties);
    } catch (error: any) {
      return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
    }
  }
);
export const getEntity = createAsyncThunk(
  "ontologies_entity",
  async (
    {
      ontologyId,
      entityIri,
      apiUrl,
    }: {
      ontologyId: string;
      entityIri: string;
      apiUrl?: string;
    },
    { rejectWithValue }
  ) => {
    const doubleEncodedTermUri = encodeURIComponent(
      encodeURIComponent(entityIri)
    );
    let path = "";
    try {
      path = `api/v2/ontologies/${ontologyId}/entities/${doubleEncodedTermUri}`;
      const entityJsonProperties = await get<any>(path, undefined, apiUrl);
      return thingFromJsonProperties(entityJsonProperties);
    } catch (error: any) {
      return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
    }
  }
);
export const getClassInstances = createAsyncThunk(
  "ontologies_entity_class_instances",
  async (
    {
      ontologyId,
      classIri,
      searchParams,
    }: {
      ontologyId: string;
      classIri: string;
      searchParams: URLSearchParams;
    },
    { rejectWithValue }
  ) => {
    let path = "";
    try {
      if (classIri) {
        const apiSearchParams = mapToApiParams(searchParams);
        const doubleEncodedTermUri = encodeURIComponent(
          encodeURIComponent(classIri)
        );
        path = `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedTermUri}/individuals?${new URLSearchParams(
          apiSearchParams
        )}`;
        const instances = (await getPaginated<any>(path)).map((i) =>
          thingFromJsonProperties(i)
        );
        return instances;
      } else {
        return rejectWithValue(
          `Warning accessing: ${path}; Class IRI not provided`
        );
      }
    } catch (error: any) {
      return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
    }
  }
);

export const getRelatedFrom = createAsyncThunk(
  "ontologies_entity_related_from",
  async (
    {
      ontologyId,
      classIri,
      searchParams,
    }: {
      ontologyId: string;
      classIri: string;
      searchParams: URLSearchParams;
    },
    { rejectWithValue }
  ) => {
    let path = "";
    try {
      if (classIri) {
        const apiSearchParams = mapToApiParams(searchParams);
        const doubleEncodedTermUri = encodeURIComponent(
          encodeURIComponent(classIri)
        );

        // Fetch first page to get total pages
        path = `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedTermUri}/relatedFrom?${new URLSearchParams(
          apiSearchParams
        )}`;
        const firstPage = await get<any>(path);

        // Collect all elements
        let allElements = firstPage.elements || [];
        const totalPages = firstPage.totalPages || 1;

        // Fetch remaining pages if there are more than 1 page
        if (totalPages > 1) {
          const pagePromises = [];
          for (let page = 1; page < totalPages; page++) {
            const pageParams = new URLSearchParams(apiSearchParams);
            pageParams.set("page", page.toString());
            const pagePath = `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedTermUri}/relatedFrom?${pageParams}`;
            pagePromises.push(get<any>(pagePath));
          }

          // Wait for all pages to complete
          const remainingPages = await Promise.all(pagePromises);

          // Combine all elements
          remainingPages.forEach((page) => {
            if (page.elements) {
              allElements = allElements.concat(page.elements);
            }
          });
        }

        // Transform to entities and return array
        return allElements.map((i) => thingFromJsonProperties(i));
      } else {
        return rejectWithValue(
          `Warning accessing: ${path}; Class IRI not provided`
        );
      }
    } catch (error: any) {
      return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
    }
  }
);
export const getOntologies = createAsyncThunk(
  "ontologies_ontologies",
  async ({ page, rowsPerPage, search }: any, { rejectWithValue }) => {
    if (search.length > 1 && !search.includes(" ")) search = "*" + search + "*";
    const path = `api/v2/ontologies?page=${page}&size=${rowsPerPage}${
      search ? "&search=" + search : ""
    }`;
    try {
      const data = (await getPaginated<any>(path)).map((o) => new Ontology(o));
      return data;
    } catch (error: any) {
      return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
    }
  }
);

export const getAllOntologies = createAsyncThunk(
    "ontologies_all",
    async (_, { rejectWithValue }) => {
        const path = `api/v2/ontologies?size=1`;
        try {
            const response = await get<any>(path);
            const totalElements = response.totalElements;

            const allOntologiesPath = `api/v2/ontologies?size=${totalElements}`;
            const allOntologiesResponse = await get<any>(allOntologiesPath);
            const data = allOntologiesResponse.elements.map((o: any) => new Ontology(o));

            //const data = (await getPaginated<any>(path)).map((o) => new Ontology(o));

            return data;
        } catch (error: any) {
            return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
        }
    }
);
export const getEntities = createAsyncThunk(
  "ontologies_entities",
  async (
    { ontologyId, entityType, page, rowsPerPage, search }: any,
    { rejectWithValue }
  ) => {
    const path = `api/v2/ontologies/${ontologyId}/${entityType}?page=${page}&size=${rowsPerPage}${
      search ? "&search=" + encodeURIComponent(`*${search}*`) : ""
    }`;
    try {
      const data = (await getPaginated<any>(path)).map((e) =>
        thingFromJsonProperties(e)
      );
      return data;
    } catch (error: any) {
      return rejectWithValue(`Error accessing: ${path}; ${error.message}`);
    }
  }
);
export const getAncestors = createAsyncThunk(
  "ontologies_ancestors",
  async ({
    ontologyId,
    entityType,
    entityIri,
    selectedEntity,
    lang,
    apiUrl,
  }: any, { signal }) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    const entity = selectedEntity || thingFromJsonProperties(await get<any>(
      `api/v2/ontologies/${ontologyId}/entities/${doubleEncodedUri}`,
      { lang }, apiUrl, { signal }
    )) as Entity;
    const mixed = entityType === "classes" || entityType === "individuals";
    const route = mixed ? `entities/${doubleEncodedUri}/treeAncestors` : `${entityType}/${doubleEncodedUri}/ancestors`;
    // The additive mixed ancestry route is unfiltered context by definition.
    const loadPage = treePageLoader(`api/v2/ontologies/${ontologyId}/${route}`, {
      size: String(TREE_PAGE_SIZE), lang, ...TREE_NODE_VIEW,
      ...(mixed ? {} : { includeObsoleteEntities: "true", includeImportedEntities: "true" }),
    }, apiUrl, signal);
    const ancestors = await readTreePages(await loadPage(0), loadPage);
    return [entity, ...ancestors.map((obj) => thingFromJsonProperties(obj) as Entity)];
  },
  treeThunkOptions
);
export const getRootEntities = createAsyncThunk(
  "ontologies_roots",
  async ({
    ontologyId,
    entityType,
    preferredRoots,
    lang,
    showObsoleteEnabled,
    showImportedEnabled,
    subsetTree,
    apiUrl,
  }: any, { signal }) => {
    async function loadRoots(type: string, rootFilter: Record<string, string>) {
      const loadPage = treePageLoader(`api/v2/ontologies/${ontologyId}/${type}`, {
        ...rootFilter,
        size: String(TREE_PAGE_SIZE),
        lang,
        includeObsoleteEntities: String(showObsoleteEnabled),
        ...treeContextParams(subsetTree),
        ...TREE_NODE_VIEW,
        ...(showImportedEnabled === false ? { isDefiningOntology: "true" } : {}),
      }, apiUrl, signal);
      return (await readTreePages(await loadPage(0), loadPage, TREE_PAGE_SIZE))
        .map((obj) => thingFromJsonProperties(obj) as Entity);
    }
    if (entityType === "individuals") {
      const [classesWithIndividuals, orphanedIndividuals] = await Promise.all([
        loadRoots("classes", { hasIndividuals: "true" }),
        loadRoots("individuals", { hasDirectParents: "false" }),
      ]);
      return { entityType, rootTerms: null, classesWithIndividuals, orphanedIndividuals };
    }
    const rootFilter: Record<string, string> = entityType === "classes" && preferredRoots
      ? { isPreferredRoot: "true" }
      : { hasDirectParents: "false" };
    const rootTerms = await loadRoots(entityType, rootFilter);
    return { entityType, rootTerms, classesWithIndividuals: null, orphanedIndividuals: null };
  },
  treeThunkOptions
);
// The entity types a tree tab lists under a subset (the class tree shows instances
// too), as a value of the entities endpoint's type filter
function subsetMemberTypes(entityType: string): string {
  if (entityType === "classes") return "class,individual";
  if (entityType === "individuals") return "individual";
  return "property";
}

// The subsets used by entities of the tree's type, with how many entities each has
export const getSubsets = createAsyncThunk(
  "ontologies_subsets",
  async ({
    ontologyId,
    entityType,
    lang,
    showObsoleteEnabled,
    showImportedEnabled,
    apiUrl,
  }: any, { signal }): Promise<SubsetOption[]> => {
    const encodedIri = encodeURIComponent(encodeURIComponent(SUBSET_PROPERTY_IRI));
    const loadPage = treePageLoader(
      `api/v2/ontologies/${ontologyId}/properties/${encodedIri}/children`,
      { size: String(TREE_PAGE_SIZE), lang, ...TREE_NODE_VIEW }, apiUrl, signal
    );
    const first = await loadPage(0);
    if (first.totalPages === 0 && first.elements.length === 0) return [];
    // Definitions are stable identity, independent of eligible member counts.
    const [definitions, facetResponse] = await Promise.all([
      readTreePages(first, loadPage),
      get<any>(
        `api/v2/ontologies/${ontologyId}/entities`, {
          type: subsetMemberTypes(entityType),
          size: "1",
          lang,
          includeObsoleteEntities: String(showObsoleteEnabled),
          ...(showImportedEnabled === false ? { isDefiningOntology: "true" } : {}),
          facetFields: "subset",
        },
        apiUrl,
        { signal }
      ),
    ]);
    const numMembers: Record<string, number> = facetResponse.facetFieldsToCounts?.subset || {};
    return definitions
      .map((obj: any) => thingFromJsonProperties(obj) as Entity)
      .map((subset: Entity) => ({
        iri: subset.getIri(),
        label: subset.getName(),
        numMembers: numMembers[subset.getIri()] || 0,
      }))
      .sort((a: SubsetOption, b: SubsetOption) => a.label.localeCompare(b.label));
  },
  treeThunkOptions
);

// Distinct filtered members below each entity through all supported tree paths.
export const getTreeMemberCounts = createAsyncThunk(
  "ontologies_tree_member_counts",
  async ({
    ontologyId,
    entityType,
    selectedSubsets = [],
    lang,
    showObsoleteEnabled,
    showImportedEnabled,
    apiUrl,
  }: any, { signal }): Promise<{ [iri: string]: number }> => {
    const res = await get<any>(
      `api/v2/ontologies/${ontologyId}/entities?${new URLSearchParams({
        type: subsetMemberTypes(entityType),
        ...(selectedSubsets.length ? { subset: selectedSubsets.join(",") } : {}),
        ...(showImportedEnabled === false ? { isDefiningOntology: "true" } : {}),
        size: "1",
        lang,
        includeObsoleteEntities: showObsoleteEnabled,
        facetFields: "treeAncestor",
      })}`,
      undefined,
      apiUrl,
      { signal }
    );
    return res.facetFieldsToCounts?.treeAncestor || {};
  },
  treeThunkOptions
);

export const getDirectChildrenEntities = createAsyncThunk(
  "ontologies_direct_children_entities",
  async ({
    ontologyId,
    entityIri,
    entityType,
    lang,
    showObsoleteEnabled,
    showImportedEnabled,
    apiUrl,
    page,
    size,
    search,
    subsetTree,
  }: any, { signal }) => {
    const path = treeChildrenRoute(ontologyId, entityType, entityIri);
    const response = await getPaginated<any>(path, {
      page: String(page),
      size: String(size),
      lang: lang || "en",
      includeObsoleteEntities: String(showObsoleteEnabled ?? false),
      includeImportedEntities: String(showImportedEnabled ?? true),
      ...treeContextParams(subsetTree),
      ...(entityType === "individuals" ? { memberType: "individual" } : {}),
      ...(search ? { searchQuery: search } : {}),
    }, apiUrl, { signal });
    return response.map((obj) => thingFromJsonProperties(obj) as Entity);
  },
  treeThunkOptions
);

export const getNodeChildren = createAsyncThunk(
  "ontologies_node_children",
  async ({
    ontologyId,
    entityTypePlural,
    entityIri,
    absoluteIdentity,
    lang,
    apiUrl,
    includeObsoleteEntities: showObsoleteEnabled,
    includeImportedEntities: showImportedEnabled,
    subsetTree,
  }: any, { signal }) => {
    const path = treeChildrenRoute(ontologyId, entityTypePlural, entityIri);
    const loadPage = treePageLoader(path, {
      size: String(TREE_PAGE_SIZE),
      lang,
      includeObsoleteEntities: String(showObsoleteEnabled),
      includeImportedEntities: String(showImportedEnabled),
      ...treeContextParams(subsetTree),
      ...TREE_NODE_VIEW,
      maxInlineChildren: String(TREE_PAGE_SIZE),
      ...(entityTypePlural === "individuals" ? { memberType: "individual" } : {}),
    }, apiUrl, signal);
    const first = await loadPage(0);
    // Older servers can still send the first page of a wide branch. That
    // branch uses its searchable table and never needs inline child rows.
    const elements = first.totalElements > TREE_PAGE_SIZE ? [] : await readTreePages(first, loadPage);
    const seenChildIris = new Set<string>();
    return {
      absoluteIdentity,
      entityIri,
      totalElements: first.totalElements,
      children: elements
        .map((obj: any) => thingFromJsonProperties(obj) as Entity)
        .filter((term: Entity) => {
          if (seenChildIris.has(term.getIri())) return false;
          seenChildIris.add(term.getIri());
          return true;
        })
        .map((term: Entity) => {
          const parenthoodMetadata =
            term.getHierarchicalParentReificationAxioms(entityIri);
          const isIndividual = term.getType() === "individual";
          return {
            iri: term.getIri(),
            absoluteIdentity: absoluteIdentity + ";" + term.getIri(),
            title: term.getName(),
            expandable: term.isExpandableInTree(),
            entity: term,
            numDescendants: term.getNumDescendants(),
            numHierarchicalDescendants: term.getNumHierarchicalDescendants(),
            // Reified hierarchicalParent metadata wins (e.g. isSubCohortOf between
            // individuals); individuals without it hang under their rdf:type class.
            parentRelationToChild:
              parenthoodMetadata?.["parentRelationToChild"]?.[0] || null,
            childRelationToParent:
              parenthoodMetadata?.["childRelationToParent"]?.[0] ||
              (isIndividual ? RDF_TYPE_IRI : null),
          };
        }),
    };
  },
  treeThunkOptions
);

const ontologiesSlice = createSlice({
  name: "ontologies",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(setTreeScope, (state, action) => {
      // Remounts retain settings; entity-type changes still invalidate all data.
      if (state.treeScopeKey === action.payload.scopeKey) return;
      clearTreeContent(state);
      state.treeScopeKey = action.payload.scopeKey;
      state.treeRequestKey = null;
      state.selectedSubsets = [];
      state.subsets = [];
      state.subsetsResultKey = null;
      state.specificRootIri = "";
      if (state.treePreferencesScopeKey === action.payload.preferencesScopeKey) return;
      state.treePreferencesScopeKey = action.payload.preferencesScopeKey;
      state.preferredRoots = action.payload.preferredRoots;
      state.displayObsolete = initialState.displayObsolete;
      state.displayImported = initialState.displayImported;
      state.displaySiblings = initialState.displaySiblings;
      state.displayCounts = initialState.displayCounts;
    });
    builder.addCase(setTreeRequestContext, (state, action) => {
      if (state.treeScopeKey !== action.payload.scopeKey) return;
      // A remounted tree owns fresh branch requests. Counts can be reused within
      // the same data scope because they depend on filters, not the selected path.
      const keepMemberCounts = state.filteredMemberCountsStatus === "succeeded" &&
        state.memberCountsResultKey === action.payload.memberCountsResultKey;
      clearTreeContent(state, keepMemberCounts);
      state.treeRequestKey = action.payload.requestKey;
    });
    builder.addCase(
      getOntology.fulfilled,
      (state: OntologiesState, action: PayloadAction<Ontology>) => {
        state.ontology = action.payload;
        state.loadingOntology = false;
      }
    );
    builder.addCase(getOntology.pending, (state: OntologiesState) => {
      state.loadingOntology = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getOntology.rejected,
      (state: OntologiesState, error: any) => {
        state.ontology = initialState.ontology;
        state.loadingOntology = false;
        state.errorMessage = error.payload;
      }
    );
    builder.addCase(
      getEntityWithType.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity>) => {
        state.entity = action.payload;
        state.loadingEntity = false;
      }
    );
    builder.addCase(getEntityWithType.pending, (state: OntologiesState) => {
      state.loadingEntity = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getEntityWithType.rejected,
      (state: OntologiesState, error: any) => {
        state.loadingEntity = false;
        state.entity = initialState.entity;
        state.errorMessage = error.payload;
      }
    );
    builder.addCase(
      getEntity.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity>) => {
        state.entity = action.payload;
        state.loadingEntity = false;
      }
    );
    builder.addCase(getEntity.pending, (state: OntologiesState) => {
      state.loadingEntity = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getEntity.rejected,
      (state: OntologiesState, error: any) => {
        state.loadingEntity = false;
        state.entity = initialState.entity;
        state.errorMessage = error.payload;
      }
    );
    builder.addCase(
      getClassInstances.fulfilled,
      (state: OntologiesState, action: PayloadAction<Page<Entity>>) => {
        state.classInstances = action.payload;
        state.loadingClassInstances = false;
      }
    );
    builder.addCase(getClassInstances.pending, (state: OntologiesState) => {
      state.loadingClassInstances = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getClassInstances.rejected,
      (state: OntologiesState, error: any) => {
        state.loadingClassInstances = false;
        state.classInstances = initialState.classInstances;
        state.errorMessage = error.payload;
      }
    );
    builder.addCase(
      getRelatedFrom.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity[]>) => {
        state.relatedFrom = action.payload;
        state.loadingRelatedFrom = false;
      }
    );
    builder.addCase(getRelatedFrom.pending, (state: OntologiesState) => {
      state.loadingRelatedFrom = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getRelatedFrom.rejected,
      (state: OntologiesState, error: any) => {
        state.loadingRelatedFrom = false;
        state.relatedFrom = initialState.relatedFrom;
        state.errorMessage = error.payload;
      }
    );
    builder.addCase(getSubsets.pending, (state, action) => {
      startTreeRequest(state, action);
    });
    builder.addCase(getSubsets.rejected, (state, action) => {
      finishTreeRequest(state, action);
    });
    builder.addCase(getTreeMemberCounts.rejected, (state, action) => {
      if (!finishTreeRequest(state, action)) return;
      state.filteredMemberCountsStatus = action.meta.aborted ? "idle" : "failed";
    });
    builder.addCase(
      getDirectChildrenEntities.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.directChildrenTables[directChildrenTableKey(action.meta.arg)] = {
          elements: action.payload.elements, totalElements: action.payload.totalElements, loading: false,
        };
        // Retained for existing store consumers; embedded tables read their own keyed entry.
        state.directChildrenEntities = action.payload.elements;
        state.totalDirectChildrenEntities = action.payload.totalElements;
        state.loadingDirectChildrenEntities = false;
      }
    );
    builder.addCase(getDirectChildrenEntities.pending, (state, action) => {
      if (!startTreeRequest(state, action)) return;
      state.directChildrenTables[directChildrenTableKey(action.meta.arg)] = {
        elements: [], totalElements: 0, loading: true,
      };
      state.loadingDirectChildrenEntities = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getDirectChildrenEntities.rejected,
      (state, error) => {
        if (!finishTreeRequest(state, error)) return;
        const table = state.directChildrenTables[directChildrenTableKey(error.meta.arg)];
        if (table) table.loading = false;
        state.loadingDirectChildrenEntities = false;
        state.errorMessage = typeof error.payload === "string" ? error.payload : "";
      }
    );
    builder.addCase(getAncestors.pending, (state, action) => {
      if (!startTreeRequest(state, action, true)) return;
      state.rootStatus = "loading";
    });
    builder.addCase(
      getAncestors.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.rootStatus = "succeeded";
        let { rootNodes, nodeChildren, automaticallyExpandedNodes } =
          createTreeFromEntities(
            action.payload,
            action.meta.arg.preferredRoots,
            action.meta.arg.ontology,
            action.meta.arg.specifiedRootIri,
            true
          );
        state.rootNodes = rootNodes;
        state.nodeChildren = nodeChildren;
        state.requiredPathChildren = nodeChildren;
        state.automaticallyExpandedNodes = Array.from(
          new Set([
            ...state.automaticallyExpandedNodes,
            ...Array.from(automaticallyExpandedNodes),
          ])
        );
      }
    );
    builder.addCase(getAncestors.rejected, (state, action) => {
      if (!finishTreeRequest(state, action)) return;
      state.rootStatus = action.meta.aborted ? "idle" : "failed";
    });
    builder.addCase(getNodeChildren.pending, (state, action) => {
      startTreeRequest(state, action, true);
    });
    builder.addCase(
      getNodeChildren.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.directChildrenCounts[action.payload.entityIri] = action.payload.totalElements;
        state.nodeChildren = {
          ...state.nodeChildren,
          // Filtered children/siblings may omit an imported connector. Retain
          // the actual ancestry edges (and their relationship metadata).
          [action.payload.absoluteIdentity]: Array.from(new Map([
            ...action.payload.children,
            ...(state.requiredPathChildren[action.payload.absoluteIdentity] || []),
          ].map(node => [node.absoluteIdentity, node])).values()),
        };
        state.nodesWithChildrenLoaded.push(action.payload.absoluteIdentity);
      }
    );
    builder.addCase(getNodeChildren.rejected, (state, action) => {
      finishTreeRequest(state, action);
    });
    builder.addCase(getRootEntities.pending, (state, action) => {
      if (!startTreeRequest(state, action, true)) return;
      state.rootStatus = "loading";
    });
    builder.addCase(
      getRootEntities.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.rootStatus = "succeeded";
        let { rootTerms, classesWithIndividuals, orphanedIndividuals } =
          action.payload;
        // console.log("rootTerms");
        // console.dir(rootTerms);
        let { rootNodes, nodeChildren, automaticallyExpandedNodes } =
          createTreeFromEntities(
            [
              ...(rootTerms || []),
              ...(classesWithIndividuals || []),
              ...(orphanedIndividuals || []),
            ],
            action.meta.arg.preferredRoots,
            action.meta.arg.ontology,
            action.meta.arg.specifiedRootIri
          );

        state.rootNodes = rootNodes;
        state.nodeChildren = nodeChildren;
        state.automaticallyExpandedNodes = Array.from(
          new Set([
            ...state.automaticallyExpandedNodes,
            ...Array.from(automaticallyExpandedNodes),
          ])
        );
      }
    );
    builder.addCase(getRootEntities.rejected, (state, action) => {
      if (!finishTreeRequest(state, action)) return;
      state.rootStatus = action.meta.aborted ? "idle" : "failed";
    });
    builder.addCase(
      getSubsets.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.subsets = action.payload;
        state.subsetsResultKey = action.meta.arg.resultKey ?? null;
        // drop selections the ontology or tree type no longer offers
        const available = new Set(action.payload.map((subset) => subset.iri));
        const stillSelected = state.selectedSubsets.filter((iri) => available.has(iri));
        if (stillSelected.length !== state.selectedSubsets.length) {
          state.selectedSubsets = stillSelected;
        }
      }
    );
    builder.addCase(getTreeMemberCounts.pending, (state, action) => {
      if (!startTreeRequest(state, action)) return;
      state.filteredMemberCounts = {};
      state.filteredMemberCountsStatus = "loading";
    });
    builder.addCase(
      getTreeMemberCounts.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.filteredMemberCounts = action.payload;
        state.memberCountsResultKey = action.meta.arg.resultKey ?? null;
        state.filteredMemberCountsStatus = "succeeded";
      }
    );
    builder.addCase(
      getOntologies.fulfilled,
      (state: OntologiesState, action: PayloadAction<Page<Ontology>>) => {
        state.ontologies = action.payload.elements;
        state.totalOntologies = action.payload.totalElements;
        state.loadingOntologies = false;
      }
    );
    builder.addCase(getOntologies.pending, (state: OntologiesState) => {
      state.loadingOntologies = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getOntologies.rejected,
      (state: OntologiesState, error: any) => {
        state.ontologies = initialState.ontologies;
        state.loadingOntologies = false;
        state.errorMessage = error.payload;
      }
    );
      builder.addCase(
          getAllOntologies.fulfilled,
          (state: OntologiesState, action: PayloadAction<Ontology[]>) => {
              state.ontologies = action.payload;
              state.loadingOntologies = false;
          }
      );
      builder.addCase(getAllOntologies.pending, (state: OntologiesState) => {
          state.loadingOntologies = true;
          state.errorMessage = initialState.errorMessage;
      });
      builder.addCase(
          getAllOntologies.rejected,
          (state: OntologiesState, error: any) => {
              state.ontologies = initialState.ontologies;
              state.loadingOntologies = false;
              state.errorMessage = error.payload;
          }
      );
    builder.addCase(
      getEntities.fulfilled,
      (state: OntologiesState, action: PayloadAction<Page<Entity>>) => {
        state.entities = action.payload.elements;
        state.totalEntities = action.payload.totalElements;
        state.loadingEntities = false;
      }
    );
    builder.addCase(getEntities.pending, (state: OntologiesState) => {
      state.loadingEntities = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getEntities.rejected,
      (state: OntologiesState, error: any) => {
        state.entities = initialState.entities;
        state.loadingEntities = false;
        state.errorMessage = error.payload;
      }
    );
    builder.addCase(resetTreeContent, (state) => {
      clearTreeContent(state);
      state.treeRequestKey = null;
    });
    builder.addCase(enablePreferredRoots, (state: OntologiesState) => {
      state.preferredRoots = true;
    });
    builder.addCase(disablePreferredRoots, (state: OntologiesState) => {
      state.preferredRoots = false;
    });
    builder.addCase(showObsolete, (state: OntologiesState) => {
      state.displayObsolete = true;
    });
    builder.addCase(hideObsolete, (state: OntologiesState) => {
      state.displayObsolete = false;
    });
    builder.addCase(showImported, (state: OntologiesState) => {
      state.displayImported = true;
    });
    builder.addCase(hideImported, (state: OntologiesState) => {
      state.displayImported = false;
    });
    builder.addCase(showSiblings, (state: OntologiesState) => {
      state.displaySiblings = true;
    });
    builder.addCase(hideSiblings, (state: OntologiesState) => {
      state.displaySiblings = false;
    });
    builder.addCase(showCounts, (state: OntologiesState) => {
      state.displayCounts = true;
    });
    builder.addCase(hideCounts, (state: OntologiesState) => {
      state.displayCounts = false;
    });
    builder.addCase(
      setSelectedSubsets,
      (state: OntologiesState, action: PayloadAction<string[]>) => {
        state.selectedSubsets = action.payload;
      }
    );
    builder.addCase(setSpecificRootIri, (state: OntologiesState, action: PayloadAction<string>) => {
      state.specificRootIri = action.payload;
    });
    builder.addCase(
      openNode,
      (state: OntologiesState, action: PayloadAction<TreeNode>) => {
        state.manuallyExpandedNodes = Array.from(
          new Set([
            ...state.manuallyExpandedNodes,
            action.payload.absoluteIdentity,
          ])
        );
      }
    );
    builder.addCase(
      closeNode,
      (state: OntologiesState, action: PayloadAction<TreeNode>) => {
        state.manuallyExpandedNodes = state.manuallyExpandedNodes.filter(
          (node) => node !== action.payload.absoluteIdentity
        );
        state.automaticallyExpandedNodes =
          state.automaticallyExpandedNodes.filter(
            (node) => node !== action.payload.absoluteIdentity
          );

      }
    );
  },
});

export default ontologiesSlice.reducer;
