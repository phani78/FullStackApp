import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./authSlice";
import { batchApi, originationApi } from "./originationApi";

export const store = configureStore({
  reducer: {
    auth: authReducer,
    [originationApi.reducerPath]: originationApi.reducer,
    [batchApi.reducerPath]: batchApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(originationApi.middleware, batchApi.middleware),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
