import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get, getPaginated, Page } from "../../app/api";
import Entity from "../../model/Entity";
import { thingFromProperties } from "../../model/fromProperties";
import Ontology from "../../model/Ontology";
import { Suggest } from "../../model/Suggest";
import Thing from "../../model/Thing";

export interface HomeState {
  stats: Stats;
}
export interface Stats {
  numberOfOntologies: number;
  numberOfClasses: number;
  numberOfProperties: number;
  numberOfIndividuals: number;
  lastModified: string;
}
const initialState: HomeState = {
  stats: {
    numberOfOntologies: 0,
    numberOfClasses: 0,
    numberOfProperties: 0,
    numberOfIndividuals: 0,
    lastModified: "",
  }
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
  },
});

export default homeSlice.reducer;
