import adminOverviewMock from '@/mock/home/admin-dashboard-overview.json';
import elderHomeMock from '@/mock/home/elder-home-summary.json';
import familyHomeMock from '@/mock/home/family-home-summary.json';
import nurseWorkbenchMock from '@/mock/home/nurse-workbench-summary.json';
import { failure, getApiBase, isMockEnabled, readAuthSession, request } from '@/api/client';
import type { ApiResponse, RoleCode } from '@/types/stageOne';
import type { HomeSummaryRequest, HomeSummaryResponse } from '@/types/stageFour';

const endpointByRole: Record<RoleCode, string> = {
  ELDER: '/elder/home-summary',
  FAMILY: '/family/home-summary',
  NURSE: '/nurse/workbench-summary',
  ADMIN: '/admin/dashboard/overview',
  CUSTOMER_SERVICE: '/admin/dashboard/overview'
};

const mockByRole: Record<RoleCode, ApiResponse<HomeSummaryResponse>> = {
  ELDER: elderHomeMock as ApiResponse<HomeSummaryResponse>,
  FAMILY: familyHomeMock as ApiResponse<HomeSummaryResponse>,
  NURSE: nurseWorkbenchMock as ApiResponse<HomeSummaryResponse>,
  ADMIN: adminOverviewMock as ApiResponse<HomeSummaryResponse>,
  CUSTOMER_SERVICE: adminOverviewMock as ApiResponse<HomeSummaryResponse>
};

export function getHomeEndpoint(roleCode: RoleCode) {
  return `${getApiBase()}${endpointByRole[roleCode]}`;
}

export async function getHomeSummary(payload: HomeSummaryRequest): Promise<ApiResponse<HomeSummaryResponse>> {
  if (isMockEnabled()) {
    const session = readAuthSession();
    if (!session) {
      return failure(401, '未登录', {} as HomeSummaryResponse, 'mock-4');
    }
    if (!session.user.roles.includes(payload.role)) {
      return failure(403, '无权限', {} as HomeSummaryResponse, 'mock-4');
    }
    return mockByRole[payload.role];
  }

  return request<HomeSummaryResponse>({
    method: 'GET',
    url: endpointByRole[payload.role],
    data: payload,
    mock: mockByRole[payload.role],
    mockFallback: true
  });
}
