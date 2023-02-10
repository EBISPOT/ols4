import { configureStore } from "@reduxjs/toolkit";
import homeReducer from "../pages/home/homeSlice";
import ontologiesReducer from "../pages/ontologies/ontologiesSlice";
import searchReducer from '../pages/search/searchSlice';

export const store = configureStore({
  reducer: {
    home: homeReducer,
    search: searchReducer,
    ontologies: ontologiesReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false,
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
