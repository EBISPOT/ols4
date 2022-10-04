import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated } from "../../app/api";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";

export interface HomeState {
  searchResult: Thing[];
  loadingSearchResult: boolean;
  stats: Stats | null;
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
  stats: null,
};

export const getStats = createAsyncThunk("home_stats", async () => {
  return await get<Stats>(`/api/v2/stats`);
});
export const getSearchOptions = createAsyncThunk(
  "home_search_options",
  async (query: string) => {
    let search = "*" + query + "*";

    let [entities, ontologies] = await Promise.all([
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
  }
);

const homeSlice = createSlice({
  name: "home",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getStats.fulfilled,
      (state, action: PayloadAction<Stats>) => {
        state.stats = action.payload;
      }
    );
    builder.addCase(
      getSearchOptions.fulfilled,
      (state, action: PayloadAction<Thing[]>) => {
        state.searchResult = action.payload;
        state.loadingSearchResult = false;
      }
    );
    builder.addCase(getSearchOptions.pending, (state) => {
      state.loadingSearchResult = true;
    });
    builder.addCase(getSearchOptions.rejected, (state) => {
      state.loadingSearchResult = false;
    });
  },
});

export default homeSlice.reducer;
