import { defineConfig } from 'vite';
import uniPlugin from '@dcloudio/vite-plugin-uni';

const uni = (uniPlugin as unknown as { default?: typeof uniPlugin }).default ?? uniPlugin;

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/v1': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 5173,
    strictPort: true
  }
});
