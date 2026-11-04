import { defineConfig, devices } from '@playwright/test';

// Workflow tests of the staff application against the mock API (e2e/mock-server.mjs).
// Tests against the deployed system with real Keycloak login are run on the server.
export default defineConfig({
  testDir: 'e2e',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4173/core-neha/staff/',
    trace: 'retain-on-failure',
    // Optional: a pre-installed Chromium (PW_CHROMIUM_PATH); CI installs the matching browser instead.
    launchOptions: process.env.PW_CHROMIUM_PATH ? { executablePath: process.env.PW_CHROMIUM_PATH } : {},
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'phone', use: { ...devices['Pixel 7'] } },
  ],
  webServer: { command: 'node e2e/mock-server.mjs 4173', url: 'http://localhost:4173/core-neha/staff/', reuseExistingServer: !process.env.CI },
});
