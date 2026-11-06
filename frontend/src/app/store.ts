import { configureStore } from "@reduxjs/toolkit";
import homeReducer from "../pages/home/homeSlice";
import ontologiesReducer from "../pages/ontologies/ontologiesSlice";
import searchReducer from '../pages/search/searchSlice';

export const createAppStore = () => configureStore({
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

// The application retains its singleton; embedded widgets create their own store.
export const store = createAppStore();

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
