import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    // Surchargeable : `E2E_BASE_URL` (pile isolée, voir scripts/e2e-stack.mjs).
    baseUrl: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    setupNodeEvents(_on, config) {
      // Sans cypress.env.json (ignoré par git), on retombe sur les identifiants factices de l'exemple.
      if (!existsSync(join(config.projectRoot, 'cypress.env.json'))) {
        const example = JSON.parse(readFileSync(join(config.projectRoot, 'cypress.env.example.json'), 'utf8'));
        config.env = { ...example, ...config.env };
      }
      return config;
    },
  },
});
