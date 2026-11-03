import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => ({
  plugins: [react(), ...(mode === "demo" ? [{
    name: "sicc-demo-notice",
    transformIndexHtml() {
      return [{ tag: "div", attrs: { class: "demo-banner", role: "status" },
        children: "AMBIENTE DE DEMONSTRAÇÃO — inclui campos, datas e PDFs fictícios, sem valor oficial.",
        injectTo: "body-prepend" as const }];
    }
  }] : [])],
  server: {
    port: 5173,
    proxy: {
      // Mock tests must not send unmatched requests to the user's running API.
      "/api": mode === "test" ? "http://127.0.0.1:9"
        : mode === "demo" ? "http://127.0.0.1:18080" : "http://localhost:8080"
    }
  }
}));
