import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated, Page } from "../../app/api";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";
import { Suggest } from "../../model/Suggest";
import Thing from "../../model/Thing";

export interface HomeState {
  autocomplete: Suggest|null;
  jumpTo: Thing[];
  loadingSuggestions: boolean;
  searchResults: Entity[];
  loadingSearchResults: boolean;
  totalSearchResults: number;
  stats: Stats;
  facets: Object;
}
export interface Stats {
  numberOfOntologies: number;
  numberOfClasses: number;
  numberOfProperties: number;
  numberOfIndividuals: number;
  lastModified: string;
}
const initialState: HomeState = {
  autocomplete: null,
  jumpTo: [],
  searchResults: [],
  loadingSuggestions: false,
  loadingSearchResults: false,
  totalSearchResults: 0,
  stats: {
    numberOfOntologies: 0,
    numberOfClasses: 0,
    numberOfProperties: 0,
    numberOfIndividuals: 0,
    lastModified: "",
  },
  facets: Object.create(null),
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
export const getSuggestions = createAsyncThunk(
  "home_search_suggestions",
  async (query: string, { rejectWithValue }) => {
    const search = query
    try {
      const [entities, ontologies, autocomplete] = await Promise.all([
        getPaginated<any>(
          `api/v2/entities?${new URLSearchParams({
            search: search,
            size: "5",
	    lang: 'all'
          })}`
        ),
        getPaginated<any>(
          `api/v2/ontologies?${new URLSearchParams({
            search: search,
            size: "5",
	    lang: 'all'
          })}`
        ),
        get<Suggest>(
          `api/suggest?${new URLSearchParams({
            q: search
          })}`
        ),
      ]);
      return { jumpTo: [
        ...entities.elements.map((obj) => thingFromProperties(obj)),
        ...ontologies.elements.map((obj) => new Ontology(obj)),
      ], autocomplete: autocomplete }
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);
export const getSearchResults = createAsyncThunk(
  "home_search_results",
  async (
    { page, rowsPerPage, search, ontologyId, type }: any,
    { rejectWithValue }
  ) => {
    try {
      let query = {
        search: search,
        size: rowsPerPage,
        page,
        facetFields: "ontologyId type",
        ontologyId: ontologyId.length > 0 ? ontologyId[0] : null,
        type: type.length > 0 ? type[0] : null,
	lang: 'all'
      };
      for (const param in query) {
        if (
          query[param] === undefined ||
          query[param] === null ||
          query[param] === ""
        ) {
          delete query[param];
        }
      }
      const data = (
        await getPaginated<any>(`api/v2/entities?${new URLSearchParams(query)}`)
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
      getSuggestions.fulfilled,
      (state: HomeState, action: PayloadAction<{jumpTo: Thing[], autocomplete:Suggest}>) => {
        state.jumpTo = action.payload.jumpTo;
	state.autocomplete = action.payload.autocomplete;
        state.loadingSuggestions = false;
      }
    );
    builder.addCase(getSuggestions.pending, (state: HomeState) => {
      state.loadingSuggestions = true;
    });
    builder.addCase(getSuggestions.rejected, (state: HomeState) => {
      state.loadingSuggestions = false;
      state.jumpTo = initialState.jumpTo;
      state.autocomplete = initialState.autocomplete;
    });
    builder.addCase(
      getSearchResults.fulfilled,
      (state: HomeState, action: PayloadAction<Page<Entity>>) => {
        state.searchResults = action.payload.elements;
        state.totalSearchResults = action.payload.totalElements;
        state.facets = action.payload.facetFieldsToCounts;
        state.loadingSearchResults = false;
      }
    );
    builder.addCase(getSearchResults.pending, (state: HomeState) => {
      state.loadingSearchResults = true;
    });
    builder.addCase(getSearchResults.rejected, (state: HomeState) => {
      state.loadingSearchResults = false;
      state.searchResults = initialState.searchResults;
      state.facets = initialState.facets;
    });
  },
});

export default homeSlice.reducer;
