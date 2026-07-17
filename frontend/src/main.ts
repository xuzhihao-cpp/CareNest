import { createSSRApp, type App as VueApp } from 'vue';
import { isDemoPresentationMode } from '@/api/client';
import App from './App.vue';
import './styles/main.css';

function installDemoPresentationGuards(app: VueApp) {
  if (!isDemoPresentationMode()) return;

  app.config.errorHandler = () => {};
  app.config.warnHandler = () => {};

  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  const removeDevOverlay = () => {
    document.querySelector('vite-error-overlay')?.remove();
  };
  const markDocument = () => {
    document.documentElement.dataset.demoPresentation = 'true';
    document.body?.classList.add('demo-presentation-mode');
    removeDevOverlay();
  };
  const suppressBrowserError = (event: Event) => {
    event.preventDefault();
    removeDevOverlay();
  };

  window.addEventListener('error', suppressBrowserError, true);
  window.addEventListener('unhandledrejection', suppressBrowserError, true);
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', markDocument, { once: true });
  } else {
    markDocument();
  }
  window.setInterval(removeDevOverlay, 500);
}

export function createApp() {
  const app = createSSRApp(App);
  installDemoPresentationGuards(app);
  return {
    app
  };
}
