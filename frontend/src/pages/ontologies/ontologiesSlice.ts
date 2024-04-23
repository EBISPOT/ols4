import {
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

export interface OntologiesState {
  ontology: Ontology | undefined;
  entity: Entity | undefined;
  nodesWithChildrenLoaded: string[];
  nodeChildren: any;
  rootNodes: TreeNode[];
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
  automaticallyExpandedNodes: string[];
  manuallyExpandedNodes: string[];
  preferredRoots: boolean;
  displayObsolete: boolean;
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
  parentRelationToChild: string | null; // if applicable, relation from the parent node to this node (e.g. has_part)
  childRelationToParent: string | null; // if applicable, relation from this node to the parent node (e.g. part_of)
}

const initialState: OntologiesState = {
  ontology: undefined,
  entity: undefined,
  nodesWithChildrenLoaded: [],
  nodeChildren: {},
  rootNodes: [],
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
  automaticallyExpandedNodes: [],
  manuallyExpandedNodes: [],
  preferredRoots: false,
  displayObsolete: false,
  displaySiblings: false,
  displayCounts: true,
  errorMessage: "",
  specificRootIri: "",
};

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

export const showSiblings = createAction("ontologies_show_siblings");
export const hideSiblings = createAction("ontologies_hide_siblings");

export const showCounts = createAction("ontologies_show_counts");
export const hideCounts = createAction("ontologies_hide_counts");
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
        path = `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedTermUri}/instances?${new URLSearchParams(
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
export const getEntities = createAsyncThunk(
  "ontologies_entities",
  async (
    { ontologyId, entityType, page, rowsPerPage, search }: any,
    { rejectWithValue }
  ) => {
    const path = `api/v2/ontologies/${ontologyId}/${entityType}?page=${page}&size=${rowsPerPage}${
      search ? "&search=" + search : ""
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
    lang,
    showObsoleteEnabled,
    apiUrl,
  }: any) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    var ancestorsPage: any;
    if (entityType === "classes") {
      ancestorsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/hierarchicalAncestors?${new URLSearchParams(
          { size: "1000", lang, includeObsoleteEntities: showObsoleteEnabled }
        )}`,
        undefined,
        apiUrl
      );
    } else {
      ancestorsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/ancestors?${new URLSearchParams(
          { size: "1000", lang, includeObsoleteEntities: showObsoleteEnabled }
        )}`,
        undefined,
        apiUrl
      );
    }
    return ancestorsPage.elements.map((obj: any) =>
      thingFromJsonProperties(obj)
    );
  }
);
export const getRootEntities = createAsyncThunk(
  "ontologies_roots",
  async ({
    ontologyId,
    entityType,
    preferredRoots,
    lang,
    showObsoleteEnabled,
    apiUrl,
  }: any) => {
    if (entityType === "individuals") {
      const [classesWithIndividuals, orphanedIndividuals] = await Promise.all([
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/classes?${new URLSearchParams({
            hasIndividuals: "true",
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
          })}`,
          undefined,
          apiUrl
        ),
        getPaginated<any>(
          `api/v2/ontologies/${ontologyId}/individuals?${new URLSearchParams({
            hasDirectParent: "false",
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
          })}`,
          undefined,
          apiUrl
        ),
      ]);
      return {
        entityType,
        rootTerms: null,
        classesWithIndividuals: classesWithIndividuals.elements.map((obj) =>
          thingFromJsonProperties(obj)
        ),
        orphanedIndividuals: orphanedIndividuals.elements.map((obj) =>
          thingFromJsonProperties(obj)
        ),
      };
    } else if (entityType === "classes" && preferredRoots) {
      const rootsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
          isPreferredRoot: "true",
          size: "1000",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
        })}`,
        undefined,
        apiUrl
      );
      return {
        entityType,
        rootTerms: rootsPage.elements.map((obj) =>
          thingFromJsonProperties(obj)
        ),
        classesWithIndividuals: null,
        orphanedIndividuals: null,
      };
    } else {
      const rootsPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
          hasDirectParent: "false",
          size: "1000",
          lang,
          includeObsoleteEntities: showObsoleteEnabled,
        })}`,
        undefined,
        apiUrl
      );
      return {
        entityType,
        rootTerms: rootsPage.elements.map((obj) =>
          thingFromJsonProperties(obj)
        ),
        classesWithIndividuals: null,
        orphanedIndividuals: null,
      };
    }
  }
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
  }: any) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    var childrenPage: any;
    if (entityTypePlural === "classes") {
      childrenPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/hierarchicalChildren?${new URLSearchParams(
          {
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
          }
        )}`,
        undefined,
        apiUrl
      );
    } else if (entityTypePlural === "individuals") {
      childrenPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/classes/${doubleEncodedUri}/instances?${new URLSearchParams(
          {
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
          }
        )}`,
        undefined,
        apiUrl
      );
    } else {
      childrenPage = await getPaginated<any>(
        `api/v2/ontologies/${ontologyId}/${entityTypePlural}/${doubleEncodedUri}/children?${new URLSearchParams(
          {
            size: "1000",
            lang,
            includeObsoleteEntities: showObsoleteEnabled,
          }
        )}`,
        undefined,
        apiUrl
      );
    }
    return {
      absoluteIdentity,
      children: childrenPage.elements
        .map((obj: any) => thingFromJsonProperties(obj))
        .map((term: Entity) => {
          let parenthoodMetadata =
            term.getHierarchicalParentReificationAxioms(entityIri);
          return {
            iri: term.getIri(),
            absoluteIdentity: absoluteIdentity + ";" + term.getIri(),
            title: term.getName(),
            expandable: term.hasChildren(),
            entity: term,
            numDescendants:
              term.getNumHierarchicalDescendants() || term.getNumDescendants(),
            parentRelationToChild:
              (parenthoodMetadata &&
                parenthoodMetadata["parentRelationToChild"]?.[0]) ||
              null,
            childRelationToParent:
              (parenthoodMetadata &&
                parenthoodMetadata["childRelationToParent"]?.[0]) ||
              null,
          };
        }),
    };
  }
);

const ontologiesSlice = createSlice({
  name: "ontologies",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
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
    builder.addCase(getAncestors.pending, (state: OntologiesState) => {
      ++state.numPendingTreeRequests;
    });
    builder.addCase(
      getAncestors.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity[]>) => {
        let { rootNodes, nodeChildren, automaticallyExpandedNodes } =
          createTreeFromEntities(
            [state.entity!, ...action.payload],
            state.preferredRoots,
            state.ontology!,
            state.specificRootIri
          );
        state.rootNodes = rootNodes;
        state.nodeChildren = nodeChildren;
        state.automaticallyExpandedNodes = Array.from(
          new Set([
            ...state.automaticallyExpandedNodes,
            ...Array.from(automaticallyExpandedNodes),
          ])
        );
        --state.numPendingTreeRequests;
      }
    );
    builder.addCase(getAncestors.rejected, (state: OntologiesState) => {
      --state.numPendingTreeRequests;
    });
    builder.addCase(getNodeChildren.pending, (state: OntologiesState) => {
      ++state.numPendingTreeRequests;
    });
    builder.addCase(
      getNodeChildren.fulfilled,
      (
        state: OntologiesState,
        action: PayloadAction<{
          absoluteIdentity: string;
          children: TreeNode[];
        }>
      ) => {
        state.nodeChildren = {
          ...state.nodeChildren,
          [action.payload.absoluteIdentity]: action.payload.children,
        };
        state.nodesWithChildrenLoaded.push(action.payload.absoluteIdentity);
        --state.numPendingTreeRequests;
      }
    );
    builder.addCase(getNodeChildren.rejected, (state: OntologiesState) => {
      --state.numPendingTreeRequests;
    });
    builder.addCase(getRootEntities.pending, (state: OntologiesState) => {
      ++state.numPendingTreeRequests;
    });
    builder.addCase(
      getRootEntities.fulfilled,
      (state: OntologiesState, action: PayloadAction<any>) => {
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
            state.preferredRoots,
            state.ontology!,
            state.specificRootIri
          );

        state.rootNodes = rootNodes;
        state.nodeChildren = nodeChildren;
        state.automaticallyExpandedNodes = Array.from(
          new Set([
            ...state.automaticallyExpandedNodes,
            ...Array.from(automaticallyExpandedNodes),
          ])
        );
        --state.numPendingTreeRequests;
      }
    );
    builder.addCase(getRootEntities.rejected, (state: OntologiesState) => {
      --state.numPendingTreeRequests;
    });
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
    builder.addCase(resetTreeContent, (state: OntologiesState) => {
      state.nodesWithChildrenLoaded = [];
      state.nodeChildren = {};
      state.rootNodes = [];
      state.automaticallyExpandedNodes = [];
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
