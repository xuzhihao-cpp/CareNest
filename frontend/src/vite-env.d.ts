/// <reference types="vite/client" />
/// <reference types="@dcloudio/types" />

interface ImportMetaEnv {
  readonly VITE_FRONTEND_API_BASE?: string;
  readonly VITE_USE_MOCK?: string;
  readonly FRONTEND_API_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
