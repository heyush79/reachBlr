import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  // where the backend runs during development; set BACKEND_URL if 8080 is taken,
  // e.g. PORT=8081 ./mvnw spring-boot:run  and  BACKEND_URL=http://localhost:8081 npm run dev
  const backend = loadEnv(mode, ".", "").BACKEND_URL || "http://localhost:8080";
  return {
    plugins: [react()],
    build: {
      // MapLibre GL alone is about 1 MB minified (about 300 KB gzipped); that is expected for a WebGL map
      chunkSizeWarningLimit: 1500,
    },
    server: {
      // the browser only talks to Vite; Vite forwards /api to the backend
      proxy: { "/api": backend },
    },
    preview: {
      proxy: { "/api": backend },
    },
    test: {
      environment: "node",
    },
  };
});
