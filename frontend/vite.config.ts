import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    // MapLibre GL alone is about 1 MB minified (about 300 KB gzipped); that is expected for a WebGL map
    chunkSizeWarningLimit: 1500,
  },
  server: {
    // during development the backend runs on 8080; the browser only talks to Vite
    proxy: { "/api": "http://localhost:8080" },
  },
  preview: {
    proxy: { "/api": "http://localhost:8080" },
  },
  test: {
    environment: "node",
  },
});
