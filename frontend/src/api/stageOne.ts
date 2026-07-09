import healthMock from '@/mock/phase-01/health.json';
import versionMock from '@/mock/phase-01/version.json';
import routesMock from '@/mock/routes.json';
import { request } from '@/api/client';
import type { ApiResponse, HealthResponse, RouteEntry, VersionResponse } from '@/types/stageOne';

export function getHealth() {
  return request<HealthResponse>({
    method: 'GET',
    url: '/health',
    mock: healthMock as ApiResponse<HealthResponse>,
    mockFallback: true
  });
}

export function getVersion() {
  return request<VersionResponse>({
    method: 'GET',
    url: '/version',
    mock: versionMock as ApiResponse<VersionResponse>,
    mockFallback: true
  });
}

export function getRoutes(): RouteEntry[] {
  return (routesMock as { records: RouteEntry[] }).records;
}
