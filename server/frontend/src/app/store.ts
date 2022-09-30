import { configureStore } from "@reduxjs/toolkit";
import homeReducer from "../pages/home/homeSlice";
import ontologiesReducer from "../pages/ontologies/ontologiesSlice";

export const store = configureStore({
  reducer: {
    home: homeReducer,
    ontologies: ontologiesReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
