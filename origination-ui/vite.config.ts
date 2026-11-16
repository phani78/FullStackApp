import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api": "http://127.0.0.1:8080",
      "/batch-api": {
        target: "http://127.0.0.1:8081",
        rewrite: (path) => path.replace(/^\/batch-api/, ""),
      },
    },
  },
});
