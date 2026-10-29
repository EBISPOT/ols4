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
  treeRequestKey: string | null;
  treeRequests: Record<string, { requestId: string; loading: boolean }>;
  ontology: Ontology | undefined;
  entity: Entity | undefined;
  nodesWithChildrenLoaded: string[];
  nodeChildren: any;
  requiredPathChildren: Record<string, TreeNode[]>;
  rootNodes: TreeNode[];
  subsets: SubsetOption[];
  selectedSubsets: string[];
  filteredMemberCounts: { [iri: string]: number };
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
  treeRequestKey: null,
  treeRequests: {},
  ontology: undefined,
  entity: undefined,
  nodesWithChildrenLoaded: [],
  nodeChildren: {},
  requiredPathChildren: {},
  rootNodes: [],
  subsets: [],
  selectedSubsets: [],
  filteredMemberCounts: {},
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

// A subset belongs to one API, ontology and entity tab, not to the whole app.
export const treeScopeKey = (apiUrl: string | undefined, ontologyId: string, entityType: string) =>
  JSON.stringify([apiUrl || "", ontologyId, entityType]);
export const setTreeScope = createAction<string>("ontologies_set_tree_scope");
export const setTreeRequestContext = createAction<{ scopeKey: string; requestKey: string }>(
  "ontologies_set_tree_request_context"
);

function clearTreeContent(state: Draft<OntologiesState>) {
  state.nodesWithChildrenLoaded = [];
  state.nodeChildren = {};
  state.requiredPathChildren = {};
  state.rootNodes = [];
  state.automaticallyExpandedNodes = [];
  state.manuallyExpandedNodes = [];
  state.directChildrenCounts = {};
  state.directChildrenEntities = [];
  state.totalDirectChildrenEntities = 0;
  state.loadingDirectChildrenEntities = false;
  state.filteredMemberCounts = {};
  state.treeRequests = {};
  state.numPendingTreeRequests = 0;
}

function ownsTreeRequest(state: Draft<OntologiesState>, arg: any) {
  return arg.treeScopeKey === state.treeScopeKey && arg.requestKey === state.treeRequestKey;
}

// Reject effects captured by an earlier render before they can issue a fetch.
const treeThunkOptions = {
  condition: (arg: any, { getState }: any) => ownsTreeRequest(getState().ontologies, arg),
};

function treeRequestSlot(action: any): string {
  const type = action.type.slice(0, action.type.lastIndexOf("/"));
  const arg = action.meta.arg;
  if (type === "ontologies_node_children") return `${type}:${arg.absoluteIdentity}`;
  if (type === "ontologies_direct_children_count") return `${type}:${arg.entityIri}`;
  return type;
}

function startTreeRequest(state: Draft<OntologiesState>, action: any, loading = false) {
  if (!ownsTreeRequest(state, action.meta.arg)) return false;
  const slot = treeRequestSlot(action);
  if (state.treeRequests[slot]?.loading) --state.numPendingTreeRequests;
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
  return true;
}

export const resetTreeContent = createAction("ontologies_tree_reset_content");
export const resetTreeSettings = createAction<{
  entityType: string;
  selectedEntity?: Entity;
}>("ontologies_tree_reset_settings");

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

// Restricts a tree request to the selected subsets' members and the entities above them
function subsetTreeParams(subsetTree?: string[]): Record<string, string> {
  return subsetTree && subsetTree.length > 0
    ? { subsetTree: subsetTree.join(",") }
    : {};
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
    showObsoleteEnabled,
    apiUrl,
  }: any, { signal }) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    const entity = selectedEntity || thingFromJsonProperties(await get<any>(
      `api/v2/ontologies/${ontologyId}/entities/${doubleEncodedUri}`,
      { lang }, apiUrl, { signal }
    )) as Entity;
    var ancestorsPage: any;
    if (entityType === "classes") {
      ancestorsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/hierarchicalAncestors?${new URLSearchParams(
          {
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
            // Ancestry explains the selected path, including imported connectors.
            // Optional roots and child branches still use the user's filter.
            includeImportedEntities: "true",
          }
        )}`,
        undefined,
        apiUrl,
        { signal }
      );
    } else if (entityType === "individuals") {
      // An individual's tree path can climb through other individuals (e.g.
      // sub-cohorts linked by a hierarchical property like COHO isSubCohortOf)
      // before reaching its rdf:type classes, so fetch both closures.
      const [classAncestors, hierarchicalAncestors] = await Promise.all([
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/individuals/${doubleEncodedUri}/ancestors?${new URLSearchParams(
            {
              size: "1000",
              lang,
              includeObsoleteEntities: showObsoleteEnabled,
              includeImportedEntities: "true",
            }
          )}`,
          undefined,
          apiUrl,
          { signal }
        ),
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/individuals/${doubleEncodedUri}/hierarchicalAncestors?${new URLSearchParams(
            {
              size: "1000",
              lang,
              includeObsoleteEntities: showObsoleteEnabled,
              includeImportedEntities: "true",
            }
          )}`,
          undefined,
          apiUrl,
          { signal }
        ),
      ]);
      const seenAncestorIris = new Set<string>();
      ancestorsPage = {
        elements: [
          ...classAncestors.elements,
          ...hierarchicalAncestors.elements,
        ].filter((obj: any) => {
          if (seenAncestorIris.has(obj.iri)) return false;
          seenAncestorIris.add(obj.iri);
          return true;
        }),
      };
    } else {
      ancestorsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/ancestors?${new URLSearchParams(
          {
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
            includeImportedEntities: "true",
          }
        )}`,
        undefined,
        apiUrl,
        { signal }
      );
    }
    return [entity, ...ancestorsPage.elements.map((obj: any) =>
      thingFromJsonProperties(obj) as Entity
    )];
  },
  treeThunkOptions
);
// The list endpoints the tree roots come from take a dynamic isDefiningOntology
// filter; the children/ancestor endpoints take includeImportedEntities instead.
function importedRootParams(showImportedEnabled: boolean) {
  return showImportedEnabled ? {} : { isDefiningOntology: "true" };
}

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
    if (entityType === "individuals") {
      const [classesWithIndividuals, orphanedIndividuals] = await Promise.all([
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/classes?${new URLSearchParams({
            hasIndividuals: "true",
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
            ...importedRootParams(showImportedEnabled),
            ...subsetTreeParams(subsetTree),
          })}`,
          undefined,
          apiUrl,
          { signal }
        ),
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/individuals?${new URLSearchParams({
            hasDirectParents: "false",
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
            ...importedRootParams(showImportedEnabled),
            ...subsetTreeParams(subsetTree),
          })}`,
          undefined,
          apiUrl,
          { signal }
        ),
      ]);
      return {
        entityType,
        rootTerms: null,
        classesWithIndividuals: classesWithIndividuals.elements.map((obj) =>
          thingFromJsonProperties(obj) as Entity
        ),
        orphanedIndividuals: orphanedIndividuals.elements.map((obj) =>
          thingFromJsonProperties(obj) as Entity
        ),
      };
    } else if (entityType === "classes" && preferredRoots) {
      const rootsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
          isPreferredRoot: "true",
          size: "1000",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
          ...importedRootParams(showImportedEnabled),
          ...subsetTreeParams(subsetTree),
        })}`,
        undefined,
        apiUrl,
        { signal }
      );
      return {
        entityType,
        rootTerms: rootsPage.elements.map((obj) =>
          thingFromJsonProperties(obj) as Entity
        ),
        classesWithIndividuals: null,
        orphanedIndividuals: null,
      };
    } else {
      const rootsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
          hasDirectParents: "false",
          size: "1000",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
          ...importedRootParams(showImportedEnabled),
          ...subsetTreeParams(subsetTree),
        })}`,
        undefined,
        apiUrl,
        { signal }
      );
      return {
        entityType,
        rootTerms: rootsPage.elements.map((obj) =>
          thingFromJsonProperties(obj) as Entity
        ),
        classesWithIndividuals: null,
        orphanedIndividuals: null,
      };
    }
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
    apiUrl,
  }: any, { signal }): Promise<SubsetOption[]> => {
    const [subsetsPage, facetResponse] = await Promise.all([
      getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/properties/${encodeURIComponent(
          encodeURIComponent(SUBSET_PROPERTY_IRI)
        )}/children?${new URLSearchParams({ size: "1000", lang })}`,
        undefined,
        apiUrl,
        { signal }
      ),
      get<any>(
        `api/v2/ontologies/${ontologyId}/entities?${new URLSearchParams({
          type: subsetMemberTypes(entityType),
          size: "1",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
          facetFields: "subset",
        })}`,
        undefined,
        apiUrl,
        { signal }
      ),
    ]);
    const numMembers: { [subsetIri: string]: number } =
      facetResponse.facetFieldsToCounts?.["subset"] || {};
    return subsetsPage.elements
      .map((obj: any) => thingFromJsonProperties(obj) as Entity)
      .filter((subset: Entity) => numMembers[subset.getIri()] > 0)
      .map((subset: Entity) => ({
        iri: subset.getIri(),
        label: subset.getName(),
        numMembers: numMembers[subset.getIri()],
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
    console.log('getDirectChildrenEntities called with:', {
      ontologyId,
      entityIri: entityIri?.substring(0, 50) + '...',
      entityType,
      page,
      size,
      search
    });
    
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    let response: any;
    
    const searchParams = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      lang: lang || "en",
      includeObsoleteEntities: showObsoleteEnabled?.toString() || "false",
      includeImportedEntities: showImportedEnabled?.toString() || "true",
      ...subsetTreeParams(subsetTree),
    });
    
    if (search) {
      searchParams.set("searchQuery", `${search}`);
    }
    
    if (entityType === "classes") {
      response = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/children?${searchParams}`,
        undefined,
        apiUrl,
        { signal }
      );
    } else if (entityType === "individuals") {
      response = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/individuals?${searchParams}`,
        undefined,
        apiUrl,
        { signal }
      );
    } else {
      response = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/children?${searchParams}`,
        undefined,
        apiUrl,
        { signal }
      );
    }
    
    return response.map((obj: any) => thingFromJsonProperties(obj) as Entity);
  },
  treeThunkOptions
);

export const getDirectChildrenCount = createAsyncThunk(
  "ontologies_direct_children_count",
  async ({
    ontologyId,
    entityIri,
    entityType,
    lang,
    showObsoleteEnabled,
    showImportedEnabled,
    subsetTree,
    apiUrl,
  }: any, { signal }) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    let response: any;
    
    if (entityType === "classes") {
      response = await get<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/children?${new URLSearchParams({
          size: "1",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
          includeImportedEntities: showImportedEnabled,
          ...subsetTreeParams(subsetTree),
        })}`,
        undefined,
        apiUrl,
        { signal }
      );
    } else if (entityType === "individuals") {
      response = await get<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/individuals?${new URLSearchParams({
          size: "1",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
          includeImportedEntities: showImportedEnabled,
          ...subsetTreeParams(subsetTree),
        })}`,
        undefined,
        apiUrl,
        { signal }
      );
    } else {
      response = await get<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/children?${new URLSearchParams({
          size: "1",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
          includeImportedEntities: showImportedEnabled,
          ...subsetTreeParams(subsetTree),
        })}`,
        undefined,
        apiUrl,
        { signal }
      );
    }
    
    return {
      entityIri,
      totalElements: response.totalElements || 0,
    };
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
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    var childrenPage: any;
    if (entityTypePlural === "classes") {
        const hierarchicalChildrenPromise = getPaginated<any>(
            `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/hierarchicalChildren?${new URLSearchParams({
                size: "1000",
                lang,
                includeObsoleteEntities: showObsoleteEnabled,
                includeImportedEntities: showImportedEnabled,
                ...subsetTreeParams(subsetTree),
            })}`,
            undefined,
            apiUrl,
            { signal }
        );
        const directChildrenPromise = getPaginated<any>(
            `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/children?${new URLSearchParams({
                size: "1000",
                lang,
                includeObsoleteEntities: showObsoleteEnabled,
                includeImportedEntities: showImportedEnabled,
                ...subsetTreeParams(subsetTree),
            })}`,
            undefined,
            apiUrl,
            { signal }
        );
        const individualsPromise = getPaginated<any>(
            `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/individuals?${new URLSearchParams({
                size: "1000",
                lang,
                includeObsoleteEntities: showObsoleteEnabled,
                includeImportedEntities: showImportedEnabled,
                ...subsetTreeParams(subsetTree),
            })}`,
            undefined,
            apiUrl,
            { signal }
        );
        // Individuals nested under this node by a configured hierarchical property.
        // The parent can be another individual (a direct triple) or a class (an
        // existential restriction the individual is typed with).
        const individualHierarchicalChildrenPromise = getPaginated<any>(
            `api/v2/ontologies/${ontologyId}/individuals/${doubleEncodedUri}/hierarchicalChildren?${new URLSearchParams({
                size: "1000",
                lang,
                includeObsoleteEntities: showObsoleteEnabled,
                includeImportedEntities: showImportedEnabled,
                ...subsetTreeParams(subsetTree),
            })}`,
            undefined,
            apiUrl,
            { signal }
        );

        const [hierarchicalChildren, directChildren, individuals, individualHierarchicalChildren] =
          await Promise.all([
            hierarchicalChildrenPromise,
            directChildrenPromise,
            individualsPromise,
            individualHierarchicalChildrenPromise,
          ]);

        // A class tree displays both subclass relationships and rdf:type instances,
        // while the V2 class hierarchy endpoints remain class-only.
        childrenPage = {
            elements: [
                ...hierarchicalChildren.elements,
                ...directChildren.elements,
                ...individuals.elements,
                ...individualHierarchicalChildren.elements,
            ],
        };
    } else if (entityTypePlural === "individuals") {
      const [classIndividuals, individualHierarchicalChildren] = await Promise.all([
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/individuals?${new URLSearchParams(
            {
              size: "1000",
              lang,
              includeObsoleteEntities: showObsoleteEnabled,
              includeImportedEntities: showImportedEnabled,
              ...subsetTreeParams(subsetTree),
            }
          )}`,
          undefined,
          apiUrl,
          { signal }
        ),
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/individuals/${doubleEncodedUri}/hierarchicalChildren?${new URLSearchParams(
            {
              size: "1000",
              lang,
              includeObsoleteEntities: showObsoleteEnabled,
              includeImportedEntities: showImportedEnabled,
              ...subsetTreeParams(subsetTree),
            }
          )}`,
          undefined,
          apiUrl,
          { signal }
        ),
      ]);
      childrenPage = {
        elements: [
          ...classIndividuals.elements,
          ...individualHierarchicalChildren.elements,
        ],
      };
    } else {
      childrenPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityTypePlural}/${doubleEncodedUri}/children?${new URLSearchParams(
          {
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
            includeImportedEntities: showImportedEnabled,
            ...subsetTreeParams(subsetTree),
          }
        )}`,
        undefined,
        apiUrl,
        { signal }
      );
    }
    const seenChildIris = new Set<string>();
    return {
      absoluteIdentity,
      children: childrenPage.elements
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
      if (state.treeScopeKey === action.payload) return;
      clearTreeContent(state);
      state.treeScopeKey = action.payload;
      state.treeRequestKey = null;
      state.selectedSubsets = [];
      state.subsets = [];
      state.specificRootIri = "";
    });
    builder.addCase(setTreeRequestContext, (state, action) => {
      if (state.treeScopeKey !== action.payload.scopeKey) return;
      // A remounted tree owns fresh requests even when its settings are identical.
      clearTreeContent(state);
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
    builder.addCase(getDirectChildrenCount.pending, (state, action) => {
      startTreeRequest(state, action);
    });
    builder.addCase(getSubsets.pending, (state, action) => {
      startTreeRequest(state, action);
    });
    builder.addCase(getSubsets.rejected, (state, action) => {
      finishTreeRequest(state, action);
    });
    builder.addCase(getTreeMemberCounts.rejected, (state, action) => {
      finishTreeRequest(state, action);
    });
    builder.addCase(
      getDirectChildrenCount.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.directChildrenCounts = {
          ...state.directChildrenCounts,
          [action.payload.entityIri]: action.payload.totalElements,
        };
      }
    );
    builder.addCase(
      getDirectChildrenCount.rejected,
      (state, error) => {
        if (!finishTreeRequest(state, error)) return;
        console.warn('Failed to fetch direct children count:', error.payload);
      }
    );
    builder.addCase(
      getDirectChildrenEntities.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.directChildrenEntities = action.payload.elements;
        state.totalDirectChildrenEntities = action.payload.totalElements;
        state.loadingDirectChildrenEntities = false;
      }
    );
    builder.addCase(getDirectChildrenEntities.pending, (state, action) => {
      if (!startTreeRequest(state, action)) return;
      state.loadingDirectChildrenEntities = true;
      state.errorMessage = initialState.errorMessage;
    });
    builder.addCase(
      getDirectChildrenEntities.rejected,
      (state, error) => {
        if (!finishTreeRequest(state, error)) return;
        state.loadingDirectChildrenEntities = false;
        state.errorMessage = typeof error.payload === "string" ? error.payload : "";
      }
    );
    builder.addCase(getAncestors.pending, (state, action) => {
      startTreeRequest(state, action, true);
    });
    builder.addCase(
      getAncestors.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        let { rootNodes, nodeChildren, automaticallyExpandedNodes } =
          createTreeFromEntities(
            action.payload,
            action.meta.arg.preferredRoots,
            action.meta.arg.ontology,
            action.meta.arg.specifiedRootIri
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
      finishTreeRequest(state, action);
    });
    builder.addCase(getNodeChildren.pending, (state, action) => {
      startTreeRequest(state, action, true);
    });
    builder.addCase(
      getNodeChildren.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
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
      startTreeRequest(state, action, true);
    });
    builder.addCase(
      getRootEntities.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
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
      finishTreeRequest(state, action);
    });
    builder.addCase(
      getSubsets.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.subsets = action.payload;
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
    });
    builder.addCase(
      getTreeMemberCounts.fulfilled,
      (state, action) => {
        if (!finishTreeRequest(state, action)) return;
        state.filteredMemberCounts = action.payload;
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
    builder.addCase(
      resetTreeSettings,
      (
        state: OntologiesState,
        action: PayloadAction<{ entityType: string; selectedEntity?: Entity }>
      ) => {
        state.preferredRoots =
          action.payload.entityType === "classes" &&
          state.ontology!.getPreferredRoots().length > 0;

        if (action.payload.selectedEntity) {
          let selectedIsDescendantOfPreferredRoots = false;
          for (let root of state.ontology!.getPreferredRoots()) {
            if (
              action.payload.selectedEntity
                .getHierarchicalAncestorIris()
                .indexOf(root) !== -1
            ) {
              selectedIsDescendantOfPreferredRoots = true;
              break;
            }
          }
          if (!selectedIsDescendantOfPreferredRoots) {
            state.preferredRoots = false;
          }
        }
        state.displayObsolete = false;
        state.displayImported = true;
        state.displaySiblings = false;
        state.displayCounts = true;
        state.manuallyExpandedNodes = [];
      }
    );
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
        delete state.nodeChildren[action.payload.absoluteIdentity];
        state.nodesWithChildrenLoaded = state.nodesWithChildrenLoaded.filter(
          (absId) => absId !== action.payload.absoluteIdentity
        );
      }
    );
  },
});

export default ontologiesSlice.reducer;
