import type { MockServerPath } from '@/types/api';

export const mockServerPaths: MockServerPath[] = [
  {
    method: 'GET',
    url: '/api/v1/health',
    mockFile: 'mock/phase-01/health.json',
    responseShape: 'ApiResponse<HealthResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/version',
    mockFile: 'mock/phase-01/version.json',
    responseShape: 'ApiResponse<VersionResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/auth/login',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<LoginResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/auth/logout',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<{}>'
  },
  {
    method: 'GET',
    url: '/api/v1/auth/me',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<AuthUser>'
  },
  {
    method: 'GET',
    url: '/api/v1/auth/menus',
    mockFile: 'mock/phase-02/auth-fixtures.json',
    responseShape: 'ApiResponse<{menus: AuthMenu[]}>'
  },
  {
    method: 'GET',
    url: '/api/v1/auth/permissions',
    mockFile: 'mock/phase-03/permissions-fixtures.json',
    responseShape: 'ApiResponse<PermissionResponse>'
  },
  {
    method: 'POST',
    url: '/api/v1/admin/roles/{roleId}/permissions',
    mockFile: 'mock/phase-03/permissions-fixtures.json',
    responseShape: 'ApiResponse<PermissionResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/elder/home-summary',
    mockFile: 'mock/home/elder-home-summary.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/family/home-summary',
    mockFile: 'mock/home/family-home-summary.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/nurse/workbench-summary',
    mockFile: 'mock/home/nurse-workbench-summary.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  },
  {
    method: 'GET',
    url: '/api/v1/admin/dashboard/overview',
    mockFile: 'mock/home/admin-dashboard-overview.json',
    responseShape: 'ApiResponse<HomeSummaryResponse>'
  }
];

