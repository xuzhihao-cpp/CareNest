import type { ApiResponse } from './api';

export type { ApiResponse } from './api';

export type RoleCode = 'ELDER' | 'FAMILY' | 'NURSE' | 'ADMIN';

export interface HealthResponse {
  status: 'UP' | 'DOWN';
  appName: string;
  version: string;
  dbConnected: boolean;
  serverTime: string;
}

export interface VersionResponse {
  gitCommit: string;
  buildTime: string;
  apiPrefix: string;
}

export interface RouteEntry {
  roleCode: RoleCode;
  routePath: string;
  appTitle: string;
  entryLabel: string;
  emptyStateTitle: string;
  emptyStateDescription: string;
}

export interface StageOneSnapshot {
  health: ApiResponse<HealthResponse>;
  version: ApiResponse<VersionResponse>;
  routes: RouteEntry[];
}
