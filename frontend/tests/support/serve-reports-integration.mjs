import { createServer } from "vite";

const server = await createServer({
  mode: "test",
  server: {
    host: "127.0.0.1", port: 4175, strictPort: true,
    proxy: { "/api": "http://127.0.0.1:18089" }
  }
});
await server.listen();
