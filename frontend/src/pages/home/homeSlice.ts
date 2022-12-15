import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated, Page } from "../../app/api";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";

export interface HomeState {
  searchOptions: Thing[];
  loadingSearchOptions: boolean;
  searchResults: Entity[];
  loadingSearchResults: boolean;
  totalSearchResults: number;
  stats: Stats;
}
export interface Stats {
  numberOfOntologies: number;
  numberOfClasses: number;
  numberOfProperties: number;
  numberOfIndividuals: number;
}
const initialState: HomeState = {
  searchResults: [],
  loadingSearchResults: false,
  totalSearchResults: 0,
  stats: {
    numberOfOntologies: 0,
    numberOfClasses: 0,
    numberOfProperties: 0,
    numberOfIndividuals: 0,
  },
  searchOptions: [],
  loadingSearchOptions: false,
};

export const getStats = createAsyncThunk(
  "home_stats",
  async (arg, { rejectWithValue }) => {
    try {
      return await get<Stats>(`api/v2/stats`);
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getSearchOptions = createAsyncThunk(
  "home_search_options",
  async (query: string, { rejectWithValue }) => {
    const search = "*" + query + "*";
    try {
      const [entities, ontologies] = await Promise.all([
        getPaginated<any>(
          `api/v2/entities?${new URLSearchParams({
            search: search,
            size: "5",
          })}`
        ),
        getPaginated<any>(
          `api/v2/ontologies?${new URLSearchParams({
            search: search,
            size: "5",
          })}`
        ),
      ]);
      return [
        ...entities.elements.map((obj) => thingFromProperties(obj)),
        ...ontologies.elements.map((obj) => new Ontology(obj)),
      ];
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getSearchResults = createAsyncThunk(
  "home_search_results",
  async ({ page, rowsPerPage, search }: any, { rejectWithValue }) => {
    const query = "*" + search + "*";
    try {
      const data = (
        await getPaginated<any>(
          `api/v2/entities?${new URLSearchParams({
            search: search,
            size: rowsPerPage,
            page: page,
          })}`
        )
      ).map((e) => thingFromProperties(e));
      return data;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

const homeSlice = createSlice({
  name: "home",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getStats.fulfilled,
      (state: HomeState, action: PayloadAction<Stats>) => {
        state.stats = action.payload;
      }
    );
    builder.addCase(getStats.rejected, (state: HomeState) => {
      state.stats = initialState.stats;
    });
    builder.addCase(
      getSearchOptions.fulfilled,
      (state: HomeState, action: PayloadAction<Thing[]>) => {
        state.searchOptions = action.payload;
        state.loadingSearchOptions = false;
      }
    );
    builder.addCase(getSearchOptions.pending, (state: HomeState) => {
      state.loadingSearchOptions = true;
    });
    builder.addCase(getSearchOptions.rejected, (state: HomeState) => {
      state.loadingSearchOptions = false;
      state.searchOptions = initialState.searchOptions;
    });
    builder.addCase(
      getSearchResults.fulfilled,
      (state: HomeState, action: PayloadAction<Page<Entity>>) => {
        state.searchResults = action.payload.elements;
        state.totalSearchResults = action.payload.totalElements;
        state.loadingSearchResults = false;
      }
    );
    builder.addCase(getSearchResults.pending, (state: HomeState) => {
      state.loadingSearchResults = true;
    });
    builder.addCase(getSearchResults.rejected, (state: HomeState) => {
      state.loadingSearchResults = false;
      state.searchResults = initialState.searchResults;
    });
  },
});

export default homeSlice.reducer;
