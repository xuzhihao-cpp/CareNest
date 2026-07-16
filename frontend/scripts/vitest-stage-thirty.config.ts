import { fileURLToPath, URL } from 'node:url';
import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: (tag) => tag === 'view' || tag === 'text'
        }
      }
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('../src', import.meta.url))
    }
  },
  test: {
    environment: 'happy-dom',
    include: ['scripts/test-stage-thirty-components.spec.ts'],
    clearMocks: true
  }
});
