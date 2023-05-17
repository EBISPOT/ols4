import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get } from "../../app/api";

export interface HomeState {
  stats: Stats | undefined;
}
export interface Stats {
  numberOfOntologies: number;
  numberOfClasses: number;
  numberOfProperties: number;
  numberOfIndividuals: number;
  lastModified: string;
}
const initialState: HomeState = {
  stats: undefined,
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
