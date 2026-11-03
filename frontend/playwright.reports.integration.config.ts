import { defineConfig } from "@playwright/test";

// A separate pair of ports keeps a running development/demo server out of these tests.
export default defineConfig({
  testDir: "./tests",
  testMatch: "auth-integration.spec.ts",
  workers: 1,
  use: { baseURL: "http://127.0.0.1:4175", channel: "chrome", trace: "retain-on-failure" },
  webServer: [
    {
      command: process.env.SICC_MAVEN_COMMAND ?? 'mvn -q spring-boot:run "-Dspring-boot.run.profiles=test" "-Dspring-boot.run.jvmArguments=-Dserver.port=18089 -Dserver.address=127.0.0.1"',
      cwd: "..", url: "http://127.0.0.1:18089/api/v1/public/processos", reuseExistingServer: false, timeout: 120_000
    },
    {
      command: "node tests/support/serve-reports-integration.mjs",
      url: "http://127.0.0.1:4175", reuseExistingServer: false
    }
  ]
});
