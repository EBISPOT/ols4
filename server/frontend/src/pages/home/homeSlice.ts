import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated } from "../../app/api";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";

export interface HomeState {
  searchOptions: Thing[];
  loadingSearchOptions: boolean;
  stats: Stats | null;
}
export interface Stats {
  numberOfOntologies: number;
  numberOfClasses: number;
  numberOfProperties: number;
  numberOfIndividuals: number;
}
const initialState: HomeState = {
  searchOptions: [],
  loadingSearchOptions: false,
  stats: null,
};

export const getStats = createAsyncThunk("home/stats", async () => {
  return await get<Stats>(`/api/v2/stats`);
});
export const getSearchOptions = createAsyncThunk(
  "home/search/options",
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
        state.searchOptions = action.payload;
        state.loadingSearchOptions = false;
      }
    );
    builder.addCase(getSearchOptions.pending, (state) => {
      state.loadingSearchOptions = true;
    });
    builder.addCase(getSearchOptions.rejected, (state) => {
      state.loadingSearchOptions = false;
    });
  },
});

export default homeSlice.reducer;
