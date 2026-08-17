import { defineConfig } from "@playwright/test";

const backendCommand = process.env.SICC_MAVEN_COMMAND
  ?? 'mvn -q spring-boot:run "-Dspring-boot.run.profiles=test"';

export default defineConfig({
  testDir: "./tests",
  testMatch: "auth-integration.spec.ts",
  workers: 1,
  use: {
    baseURL: "http://127.0.0.1:4173",
    channel: "chrome",
    trace: "retain-on-failure"
  },
  webServer: [
    {
      command: backendCommand,
      cwd: "..",
      url: "http://127.0.0.1:8080/api/v1/public/processos",
      reuseExistingServer: false,
      timeout: 120_000
    },
    {
      command: "npm run dev -- --host 127.0.0.1 --port 4173",
      url: "http://127.0.0.1:4173",
      reuseExistingServer: false
    }
  ]
});
