import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Dev-Proxy statt CORS: der Browser spricht nur mit :5173, Vite reicht /api intern an das
    // Backend weiter (`./gradlew bootRun`, lokales Profil, Port 8080).
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
