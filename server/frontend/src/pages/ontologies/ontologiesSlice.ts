import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated } from "../../app/api";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";

export interface OntologiesState {
  ontology: Ontology | undefined;
  entity: Entity | undefined;
  ancestors: Entity[];
  rootEntities: Entity[];
  ontologies: Ontology[];
  loadingOntologies: boolean;
  entities: Entity[];
  loadingEntities: boolean;
}
const initialState: OntologiesState = {
  ontology: undefined,
  entity: undefined,
  ancestors: [],
  rootEntities: [],
  ontologies: [],
  loadingOntologies: false,
  entities: [],
  loadingEntities: false,
};

export const getOntology = createAsyncThunk(
  "ontologies_ontology",
  async (ontologyId: string) => {
    let ontologyProperties = await get<any>(`/api/v2/ontologies/${ontologyId}`);
    return new Ontology(ontologyProperties);
  }
);
export const getEntity = createAsyncThunk(
  "ontologies_entity",
  async ({ ontologyId, entityType, entityUri }: any) => {
    let doubleEncodedTermUri = encodeURIComponent(
      encodeURIComponent(entityUri)
    );
    let termProperties = await get<any>(
      `/api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedTermUri}`
    );
    return thingFromProperties(termProperties);
  }
);
export const getOntologies = createAsyncThunk(
  "ontologies_ontologies",
  async ({ page, rowsPerPage, search }: any, { rejectWithValue }) => {
    try {
      const data = (
        await getPaginated<any>(
          `/api/v2/ontologies?page=${page}&size=${rowsPerPage}${
            search != undefined ? "&search=" + search : ""
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
        await getPaginated<any>(
          `/api/v2/ontologies/${ontologyId}/${entityType}`
        )
      ).map((e) => thingFromProperties(e));
      return data.elements;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getAncestors = createAsyncThunk(
  "ontologies_ancestors",
  async ({ ontologyId, entityType, entityUri }: any) => {
    let doubleEncodedUri = encodeURIComponent(encodeURIComponent(entityUri));
    let ancestorsPage = await getPaginated<any>(
      `/api/v2/ontologies/${ontologyId}/${entityType}/${doubleEncodedUri}/ancestors?${new URLSearchParams(
        { size: "100" }
      )}`
    );
    return ancestorsPage.elements.map((obj) => thingFromProperties(obj));
  }
);
export const getRootEntities = createAsyncThunk(
  "ontologies_roots",
  async ({ ontologyId, entityType }: any) => {
    let rootsPage = await getPaginated<any>(
      `/api/v2/ontologies/${ontologyId}/${entityType}?${new URLSearchParams({
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
      (state, action: PayloadAction<Ontology>) => {
        state.ontology = action.payload;
      }
    );
    builder.addCase(
      getEntity.fulfilled,
      (state, action: PayloadAction<Entity>) => {
        state.entity = action.payload;
      }
    );
    builder.addCase(
      getAncestors.fulfilled,
      (state, action: PayloadAction<Entity[]>) => {
        state.ancestors = action.payload;
      }
    );
    builder.addCase(
      getRootEntities.fulfilled,
      (state, action: PayloadAction<Entity[]>) => {
        state.rootEntities = action.payload;
      }
    );
    builder.addCase(
      getOntologies.fulfilled,
      (state, action: PayloadAction<Ontology[]>) => {
        state.ontologies = action.payload;
        state.loadingOntologies = false;
      }
    );
    builder.addCase(getOntologies.pending, (state) => {
      state.loadingOntologies = true;
    });
    builder.addCase(getOntologies.rejected, (state) => {
      state.ontologies = initialState.ontologies;
      state.loadingOntologies = false;
    });
    builder.addCase(
      getEntities.fulfilled,
      (state, action: PayloadAction<Entity[]>) => {
        state.entities = action.payload;
        state.loadingEntities = false;
      }
    );
    builder.addCase(getEntities.pending, (state) => {
      state.loadingEntities = true;
    });
    builder.addCase(getEntities.rejected, (state) => {
      state.entities = initialState.entities;
      state.loadingEntities = false;
    });
  },
});

export default ontologiesSlice.reducer;
