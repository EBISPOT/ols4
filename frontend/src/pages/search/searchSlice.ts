import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { getPaginated, Page } from "../../app/api";
import { thingFromJsonProperties } from "../../app/util";
import Entity from "../../model/Entity";

export interface SearchState {
  searchResults: Entity[];
  loadingSearchResults: boolean;
  totalSearchResults: number;
  facets: Object;
}
const initialState: SearchState = {
  searchResults: [],
  loadingSearchResults: false,
  totalSearchResults: 0,
  facets: Object.create(null),
};

export const getSearchResults = createAsyncThunk(
  "search_results",
  async (
    { page, rowsPerPage, search, ontologyId, type, searchParams }: any,
    { rejectWithValue }
  ) => {
    try {
      let query = {
        search: search,
        size: rowsPerPage,
        page,
        facetFields: "ontologyId type",
        ontologyId: ontologyId ? ontologyId.join(',') : null,
        type: type ? type.join(',') : null,
        // lang: "all",

        ...Object.fromEntries(searchParams as URLSearchParams),
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
      const parsedQuery = new URLSearchParams(query);
      // remove redundant parameters
      if (searchParams.get("ontology")) {
        parsedQuery.set("ontologyId", searchParams.get("ontology"));
        parsedQuery.delete("ontology");
      }
      if (searchParams.get("q")) parsedQuery.delete("q");

      const data = (
        await getPaginated<any>(`api/v2/entities?${parsedQuery}`)
      ).map((e) => thingFromJsonProperties(e));
      return data;
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

const searchSlice = createSlice({
  name: "search",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(
      getSearchResults.fulfilled,
      (state: SearchState, action: PayloadAction<Page<Entity>>) => {
        state.searchResults = action.payload.elements;
        state.totalSearchResults = action.payload.totalElements;
        state.facets = action.payload.facetFieldsToCounts;
        state.loadingSearchResults = false;
      }
    );
    builder.addCase(getSearchResults.pending, (state: SearchState) => {
      state.loadingSearchResults = true;
    });
    builder.addCase(getSearchResults.rejected, (state: SearchState) => {
      state.loadingSearchResults = false;
      state.searchResults = initialState.searchResults;
      state.facets = initialState.facets;
    });
  },
});

export default searchSlice.reducer;
