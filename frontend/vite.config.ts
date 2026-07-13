import { defineConfig, loadEnv } from 'vite';
import uniPlugin from '@dcloudio/vite-plugin-uni';

const uni = (uniPlugin as unknown as { default?: typeof uniPlugin }).default ?? uniPlugin;

function createApiProxy(userApiTarget: string, careApiTarget: string) {
  return {
    '/api/v1/auth': { target: userApiTarget, changeOrigin: true },
    '/api/v1/family/bindings': { target: userApiTarget, changeOrigin: true },
    '/api/v1/elder/bindings': { target: userApiTarget, changeOrigin: true },
    '/api/v1/family/elders': { target: userApiTarget, changeOrigin: true },
    '/api/v1/elders': { target: userApiTarget, changeOrigin: true },
    '/api/v1/service-addresses': { target: userApiTarget, changeOrigin: true },
    '/api/v1/elder/reports': { target: userApiTarget, changeOrigin: true },
    '/api/v1/family/reports': { target: userApiTarget, changeOrigin: true },
    '/api/v1/admin/roles': { target: userApiTarget, changeOrigin: true },
    '/api/v1/health': { target: userApiTarget, changeOrigin: true },
    '/api/v1/version': { target: userApiTarget, changeOrigin: true },
    '/api/v1/elder/home-summary': { target: userApiTarget, changeOrigin: true },
    '/api/v1/family/home-summary': { target: userApiTarget, changeOrigin: true },
    '/api/v1/files': { target: userApiTarget, changeOrigin: true },
    '/api/v1': { target: careApiTarget, changeOrigin: true }
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const devPort = Number(process.env.VITE_DEV_PORT || env.VITE_DEV_PORT || 5173);
  const userApiTarget = process.env.VITE_USER_API_TARGET || env.VITE_USER_API_TARGET || 'http://127.0.0.1:8081';
  const careApiTarget = process.env.VITE_CARE_API_TARGET || env.VITE_CARE_API_TARGET || 'http://127.0.0.1:8082';
  const apiProxy = createApiProxy(userApiTarget, careApiTarget);

  return {
    plugins: [uni()],
    server: {
      host: '0.0.0.0',
      port: devPort,
      strictPort: true,
      proxy: apiProxy
    },
    preview: {
      port: devPort,
      strictPort: true,
      proxy: apiProxy
    }
  };
});
