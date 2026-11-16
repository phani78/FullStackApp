import React from "react";
import ReactDOM from "react-dom/client";
import { Provider } from "react-redux";
import { store } from "./app/store";
import { App } from "./features/App";
import { ErrorBoundary } from "./features/ErrorBoundary";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Provider store={store}>
      <ErrorBoundary><App /></ErrorBoundary>
    </Provider>
  </React.StrictMode>,
);
