import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";
import { get } from "../../app/api";
import urlJoin from "url-join";

export interface HomeState {
  bannerText: string;
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
  bannerText: "",
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
export const getBannerText = createAsyncThunk(
  "home_banner",
  async (arg, { rejectWithValue }) => {
    try {
      const res = await fetch(urlJoin(process.env.PUBLIC_URL!, "banner.txt"));
      return res.text();
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
      getBannerText.fulfilled,
      (state: HomeState, action: any) => {
        state.bannerText = action.payload;
      }
    );
    builder.addCase(getBannerText.rejected, (state: HomeState) => {
      state.bannerText = initialState.bannerText;
    });
  },
});

export default homeSlice.reducer;
