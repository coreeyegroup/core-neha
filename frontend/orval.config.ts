import { defineConfig } from 'orval';

// Generates typed API functions from the backend's OpenAPI contract (contracts/openapi.json).
export default defineConfig({
  coreNeha: {
    input: '../contracts/openapi.json',
    output: {
      mode: 'single',
      target: 'src/api/generated/core-neha.ts',
      client: 'fetch',
      override: {
        mutator: { path: 'src/api/http.ts', name: 'apiFetch' },
        fetch: { includeHttpResponseReturnType: false },
      },
      clean: true,
    },
  },
});
