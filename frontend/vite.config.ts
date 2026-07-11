import { defineConfig } from 'vite';
import uniPlugin from '@dcloudio/vite-plugin-uni';

const uni = (uniPlugin as unknown as { default?: typeof uniPlugin }).default ?? uniPlugin;

export default defineConfig({
  plugins: [uni()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/v1/auth': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/family/bindings': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/elder/bindings': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/family/elders': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/elders': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/service-addresses': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/elder/reports': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/family/reports': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/admin/roles': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/health': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/version': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/elder/home-summary': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1/family/home-summary': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/api/v1': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 5173,
    strictPort: true
  }
});
