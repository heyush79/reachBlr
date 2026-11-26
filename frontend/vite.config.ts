import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  // where the backend runs during development (its default port is 8081); to use another one,
  // e.g. PORT=8085 ./mvnw spring-boot:run  and  BACKEND_URL=http://localhost:8085 npm run dev
  const backend = loadEnv(mode, ".", "").BACKEND_URL || "http://localhost:8081";
  return {
    plugins: [react()],
    build: {
      // MapLibre GL alone is about 1 MB minified (about 300 KB gzipped); that is expected for a WebGL map
      chunkSizeWarningLimit: 1500,
    },
    server: {
      // 5180 rather than Vite's usual 5173, which other projects often use; if 5180 is taken, Vite picks the next free port
      port: 5180,
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
