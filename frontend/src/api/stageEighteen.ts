import demoDataStatusEmptyMock from '@/mock/phase-18/demo-data-status-empty.json';
import demoDataStatusErrorMock from '@/mock/phase-18/demo-data-status-error.json';
import demoDataStatusMock from '@/mock/phase-18/demo-data-status.json';
import healthEmptyMock from '@/mock/phase-18/health-empty.json';
import healthErrorMock from '@/mock/phase-18/health-error.json';
import healthMock from '@/mock/phase-18/health.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import { stageImplementationStatus } from '@/contracts/stageImplementationStatus';
import type { ApiResponse } from '@/types/api';
import type {
  DemoDataStatusResponse,
  HealthStatusResponse,
  StageEighteenFlowStep,
  StageEighteenScenario
} from '@/types/stageEighteen';

const integrationHealthPath = '/health';
const adminDemoDataStatusPath = '/admin/demo-data/status';

function emptyHealth(): HealthStatusResponse {
  return {
    status: 'DOWN',
    appName: 'carenest-user',
    version: 'unknown',
    dbConnected: false,
    serverTime: new Date().toISOString()
  };
}

function emptyDemoDataStatus(): DemoDataStatusResponse {
  return { ready: false, accounts: [], scenarioCount: 0 };
}

function requireAdminStatus(): ApiResponse<DemoDataStatusResponse> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, 'Not authenticated', emptyDemoDataStatus(), 'mock-18-admin-demo-status-unauthorized');
  }
  if (!session.user.roles.includes('ADMIN')) {
    return failure(403, 'Forbidden', emptyDemoDataStatus(), 'mock-18-admin-demo-status-forbidden');
  }
  return null;
}

function healthByScenario(scenario: StageEighteenScenario, traceId: string): ApiResponse<HealthStatusResponse> {
  if (scenario === 'empty') {
    return healthEmptyMock as ApiResponse<HealthStatusResponse>;
  }
  if (scenario === 'error') {
    return healthErrorMock as ApiResponse<HealthStatusResponse>;
  }
  return success((healthMock as ApiResponse<HealthStatusResponse>).data, traceId);
}

function demoDataStatusByScenario(
  scenario: StageEighteenScenario,
  traceId: string
): ApiResponse<DemoDataStatusResponse> {
  if (scenario === 'empty') {
    return demoDataStatusEmptyMock as ApiResponse<DemoDataStatusResponse>;
  }
  if (scenario === 'error') {
    return demoDataStatusErrorMock as ApiResponse<DemoDataStatusResponse>;
  }
  return success((demoDataStatusMock as ApiResponse<DemoDataStatusResponse>).data, traceId);
}

export function getStageEighteenEndpointSummary() {
  return ['GET /api/v1/health', 'GET /api/v1/admin/demo-data/status'];
}

export function getStageEighteenFlowSteps(): StageEighteenFlowStep[] {
  const statusOf = (stage: string): StageEighteenFlowStep['status'] =>
    stageImplementationStatus.find((item) => item.stage === stage)?.status ?? 'PENDING';
  return [
    { stepId: 'login', label: 'Login demo accounts', ownerRole: 'ALL', sourceStage: '2', status: 'READY' },
    { stepId: 'binding', label: 'Binding and authorization', ownerRole: 'FAMILY', sourceStage: '6', status: statusOf('6') },
    { stepId: 'profile', label: 'Elder profile', ownerRole: 'FAMILY', sourceStage: '7', status: statusOf('7') },
    { stepId: 'service', label: 'Service items', ownerRole: 'FAMILY', sourceStage: '8', status: statusOf('8') },
    { stepId: 'address', label: 'Service address', ownerRole: 'FAMILY', sourceStage: '9', status: statusOf('9') },
    { stepId: 'order', label: 'Create order', ownerRole: 'FAMILY', sourceStage: '10', status: statusOf('10') },
    { stepId: 'admin-orders', label: 'Admin order list', ownerRole: 'ADMIN', sourceStage: '11', status: statusOf('11') },
    { stepId: 'dispatch', label: 'Dispatch order', ownerRole: 'ADMIN', sourceStage: '12', status: statusOf('12') },
    { stepId: 'task', label: 'Nurse task workbench', ownerRole: 'NURSE', sourceStage: '13', status: statusOf('13') },
    { stepId: 'execution', label: 'Care execution records', ownerRole: 'NURSE', sourceStage: '14', status: statusOf('14') },
    { stepId: 'report', label: 'Service report', ownerRole: 'FAMILY', sourceStage: '15', status: statusOf('15') },
    { stepId: 'ack', label: 'Report acknowledgement', ownerRole: 'ELDER', sourceStage: '16', status: statusOf('16') },
    { stepId: 'change', label: 'Cancel or reschedule', ownerRole: 'FAMILY', sourceStage: '17', status: statusOf('17') }
  ];
}

export async function getIntegrationHealth(
  scenario: StageEighteenScenario = 'normal'
): Promise<ApiResponse<HealthStatusResponse>> {
  if (isMockEnabled()) {
    return healthByScenario(scenario, 'mock-18-health');
  }
  return request<HealthStatusResponse>({ method: 'GET', url: integrationHealthPath });
}

export async function getAdminDemoDataStatus(
  scenario: StageEighteenScenario = 'normal'
): Promise<ApiResponse<DemoDataStatusResponse>> {
  if (isMockEnabled()) {
    const denied = requireAdminStatus();
    if (denied) {
      return denied;
    }
    return demoDataStatusByScenario(scenario, 'mock-18-admin-demo-data-status');
  }
  return request<DemoDataStatusResponse>({ method: 'GET', url: adminDemoDataStatusPath });
}
