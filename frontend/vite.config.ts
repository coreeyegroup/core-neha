import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The base path is the public path prefix (ADR-0004); set at build time from CORE_NEHA_BASE_PATH.
const basePath = (process.env.CORE_NEHA_BASE_PATH ?? '/core-neha').replace(/\/$/, '');

export default defineConfig({
  base: `${basePath}/`,
  plugins: [react()],
  define: {
    __BASE_PATH__: JSON.stringify(basePath),
  },
  build: {
    sourcemap: false,
    target: 'es2023',
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
