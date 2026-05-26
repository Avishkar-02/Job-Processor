/**
 * vite.config.js — Vite build tool configuration.
 *
 * KEY PART — the proxy:
 * In development, the React dev server runs on port 5173.
 * The Spring Boot backend runs on port 8080.
 * If the browser calls http://localhost:8080/jobs directly from code
 * served at localhost:5173, it's a cross-origin request → CORS error
 * (unless the backend has CORS configured, which this backend does NOT).
 *
 * The proxy solution:
 * Any request to /api/* from the frontend is intercepted by Vite's
 * dev server and forwarded to localhost:8080, stripping the /api prefix.
 * So frontend calls /api/jobs → Vite forwards to http://localhost:8080/jobs.
 * From the browser's perspective, it's same-origin (localhost:5173).
 * No CORS header needed on the backend at all in dev.
 *
 * In Docker (production build):
 * nginx does the same proxying — /api/* → backend:8080.
 * See nginx.conf for the production equivalent of this proxy config.
 *
 * This is a standard pattern for React + Spring Boot projects.
 */

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],

  server: {
    port: 5173, // Vite dev server port

    proxy: {
      // All frontend requests to /api/* get forwarded to the backend
      "/api": {
        target: "http://localhost:8080", // Spring Boot
        changeOrigin: true,              // Sets Host header to the target host
        rewrite: (path) => path.replace(/^\/api/, ""), // /api/jobs → /jobs
      },
    },
  },

  build: {
    outDir: "dist",      // Output folder — nginx serves from here in Docker
    sourcemap: false,    // Disable sourcemaps in production (smaller build)
  },
});
