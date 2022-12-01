import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated } from "../../app/api";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";

export interface OntologiesState {
  ontology: Ontology | undefined;
  entity: Entity | undefined;
  ancestors: Entity[];
  nodeChildren: Map<String, TreeNode[]>;
  rootEntities: Entity[];
  ontologies: Ontology[];
  entities: Entity[];
  loadingOntologies: boolean;
  loadingEntities: boolean;
  loadingNodeChildren: boolean;
  loadingOntology: boolean;
  loadingEntity: boolean;
}
export interface TreeNode {
  absoluteIdentity: string; // the IRIs of this node and its ancestors delimited by a ;
  iri: string;
  title: string;
  expandable: boolean;
  entity: Entity;
}
const initialState: OntologiesState = {
  ontology: undefined,
  entity: undefined,
  ancestors: [],
  nodeChildren: new Map<String, TreeNode[]>(),
  rootEntities: [],
  ontologies: [],
  entities: [],
  loadingOntologies: false,
  loadingEntities: false,
  loadingNodeChildren: false,
  loadingOntology: false,
  loadingEntity: false,
};

export const getOntology = createAsyncThunk(
  "ontologies_ontology",
  async (ontologyId: string) => {
    const ontologyProperties = await get<any>(
      `api/v2/ontologies/${ontologyId}`
    );
    return new Ontology(ontologyProperties);
  }
);
export const getEntity = createAsyncThunk(
  "ontologies_entity",
  async ({ ontologyId, entityType, entityIri }: any) => {
    const doubleEncodedTermUri = encodeURIComponent(
      encodeURIComponent(entityIri)
    );
    const termProperties = await get<any>(
      `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedTermUri}`
    );
    return thingFromProperties(termProperties);
  }
);
export const getOntologies = createAsyncThunk(
  "ontologies_ontologies",
  async ({ page, rowsPerPage, search }: any, { rejectWithValue }) => {
    console.log(search);
    try {
      const data = (
        await getPaginated<any>(
          `api/v2/ontologies?page=${page}&size=${rowsPerPage}${
            search ? "&search=" + search : ""
          }`
        )
      ).map((o) => new Ontology(o));
      return data.elements;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getEntities = createAsyncThunk(
  "ontologies_entities",
  async ({ ontologyId, entityType }: any, { rejectWithValue }) => {
    try {
      const data = (
        await getPaginated<any>(`api/v2/ontologies/${ontologyId}/${entityType}`)
      ).map((e) => thingFromProperties(e));
      return data.elements;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getAncestors = createAsyncThunk(
  "ontologies_ancestors",
  async ({ ontologyId, entityType, entityIri }: any) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    const ancestorsPage = await getPaginated<any>(
      `api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/ancestors?${new URLSearchParams(
        { size: "100" }
      )}`
    );
    return ancestorsPage.elements.map((obj) => thingFromProperties(obj));
  }
);
export const getNodeChildren = createAsyncThunk(
  "ontologies_node_children",
  async ({
    ontologyId,
    entityTypePlural,
    entityIri,
    absoluteIdentity,
  }: any) => {
    const doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityIri));
    const childrenPage = await getPaginated<any>(
      `api/v2/ontologies/${ontologyId}/${entityTypePlural}/${doubleEncodedUri}/children?${new URLSearchParams(
        {
          size: "100",
        }
      )}`
    );
    return new Map([
      [
        absoluteIdentity,
        childrenPage.elements
          .map((obj) => thingFromProperties(obj))
          .map((term) => {
            return {
              iri: term.getIri(),
              absoluteIdentity: absoluteIdentity + ";" + term.getIri(),
              title: term.getName(),
              expandable: term.hasChildren(),
              entity: term,
            };
          }),
      ],
    ]);
  }
);
export const getRootEntities = createAsyncThunk(
  "ontologies_roots",
  async ({ ontologyId, entityType }: any) => {
    const rootsPage = await getPaginated<any>(
      `api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
        isRoot: "true",
        size: "100",
      })}`
    );
    return rootsPage.elements.map((obj) => thingFromProperties(obj));
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
    });
    builder.addCase(
      getEntity.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity>) => {
        state.entity = action.payload;
        state.loadingEntity = false;
      }
    );
    builder.addCase(getEntity.pending, (state: OntologiesState) => {
      state.loadingEntity = true;
    });
    builder.addCase(
      getAncestors.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity[]>) => {
        state.ancestors = action.payload;
      }
    );
    builder.addCase(
      getNodeChildren.fulfilled,
      (
        state: OntologiesState,
        action: PayloadAction<Map<String, TreeNode[]>>
      ) => {
        state.nodeChildren = action.payload;
        state.loadingNodeChildren = false;
      }
    );
    builder.addCase(getNodeChildren.pending, (state: OntologiesState) => {
      state.loadingNodeChildren = true;
    });
    builder.addCase(
      getRootEntities.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity[]>) => {
        state.rootEntities = action.payload;
      }
    );
    builder.addCase(
      getOntologies.fulfilled,
      (state: OntologiesState, action: PayloadAction<Ontology[]>) => {
        state.ontologies = action.payload;
        state.loadingOntologies = false;
      }
    );
    builder.addCase(getOntologies.pending, (state: OntologiesState) => {
      state.loadingOntologies = true;
    });
    builder.addCase(getOntologies.rejected, (state: OntologiesState) => {
      state.ontologies = initialState.ontologies;
      state.loadingOntologies = false;
    });
    builder.addCase(
      getEntities.fulfilled,
      (state: OntologiesState, action: PayloadAction<Entity[]>) => {
        state.entities = action.payload;
        state.loadingEntities = false;
      }
    );
    builder.addCase(getEntities.pending, (state: OntologiesState) => {
      state.loadingEntities = true;
    });
    builder.addCase(getEntities.rejected, (state: OntologiesState) => {
      state.entities = initialState.entities;
      state.loadingEntities = false;
    });
  },
});

export default ontologiesSlice.reducer;
