import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated } from "../../app/api";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";

export interface HomeState {
  searchResult: Thing[];
  loadingSearchResult: boolean;
  stats: Stats;
}
export interface Stats {
  numberOfOntologies: number;
  numberOfClasses: number;
  numberOfProperties: number;
  numberOfIndividuals: number;
}
const initialState: HomeState = {
  searchResult: [],
  loadingSearchResult: false,
  stats: {
    numberOfOntologies: 0,
    numberOfClasses: 0,
    numberOfProperties: 0,
    numberOfIndividuals: 0,
  },
};

export const getStats = createAsyncThunk(
  "home_stats",
  async (arg, { rejectWithValue }) => {
    try {
      return await get<Stats>(`/api/v2/stats`);
    } catch (error) {
      return rejectWithValue(error);
    }
  }
);
export const getSearchOptions = createAsyncThunk(
  "home_search_options",
  async (query: string, { rejectWithValue }) => {
    try {
      const search = "*" + query + "*";
      const [entities, ontologies] = await Promise.all([
        getPaginated<any>(
          `/api/v2/entities?${new URLSearchParams({
            search: search,
            size: "10",
          })}`
        ),
        getPaginated<any>(
          `/api/v2/ontologies?${new URLSearchParams({
            search: search,
            size: "3",
          })}`
        ),
      ]);
      return [
        ...ontologies.elements.map((obj) => new Ontology(obj)),
        ...entities.elements.map((obj) => thingFromProperties(obj)),
      ];
    } catch (error) {
      return rejectWithValue(error);
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
        state.searchResult = action.payload;
        state.loadingSearchResult = false;
      }
    );
    builder.addCase(getSearchOptions.pending, (state: HomeState) => {
      state.loadingSearchResult = true;
    });
    builder.addCase(getSearchOptions.rejected, (state: HomeState) => {
      state.loadingSearchResult = false;
      state.searchResult = initialState.searchResult;
    });
  },
});

export default homeSlice.reducer;
