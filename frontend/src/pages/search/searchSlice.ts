import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { getPaginated, Page } from "../../app/api";
import { thingFromJsonProperties } from "../../app/util";
import Thing from "../../model/Thing";

export interface SearchState {
  searchResults: Thing[];
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
    { page, rowsPerPage, search, ontologyId, excludeOntologyId, type, searchParams }: any,
    { rejectWithValue }
  ) => {
    try {
      // Check if this is an embedding search (model parameter provided and not "lexical")
      const model = searchParams.get("model");
      console.log("searchSlice: model param =", model, "searchParams =", searchParams.toString());
      const isEmbeddingSearch = model && model !== "" && model !== "lexical";
      console.log("searchSlice: isEmbeddingSearch =", isEmbeddingSearch);

      if (isEmbeddingSearch) {
        console.log("searchSlice: Using LLM search endpoint");
        // Use the llm_search endpoint for embedding-based search
        let llmQuery: any = {
          q: search,
          size: rowsPerPage,
          page,
          model: model,
        };
        // Add ontologyId filter if specified
        if (ontologyId && ontologyId.length > 0) {
          llmQuery.ontologyId = ontologyId[0]; // llm_search only supports single ontology
        }
        const parsedQuery = new URLSearchParams(llmQuery);
        const data = (
          await getPaginated<any>(`api/v2/entities/llm_search?${parsedQuery}`)
        ).map((e) => thingFromJsonProperties(e));
        return data;
      }

      // Standard lexical search using entities endpoint
      let query = {
        search: search,
        size: rowsPerPage,
        page,
        facetFields: "ontologyId type",
        ontologyId: ontologyId ? ontologyId.join(',') : null,
        excludeOntologyId: excludeOntologyId ? excludeOntologyId.join(',') : null,
        type: type ? type.join(',') : null,
        // lang: "all",

        ...Object.fromEntries(searchParams as URLSearchParams),
      };
      // Remove model from query since entities endpoint no longer supports it
      delete query.model;
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
      (state: SearchState, action: PayloadAction<Page<Thing>>) => {
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
