import integrationStatusEmptyMock from '@/mock/phase-18/integration-status-empty.json';
import integrationStatusErrorMock from '@/mock/phase-18/integration-status-error.json';
import integrationStatusMock from '@/mock/phase-18/integration-status.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  StageEighteenFlowStep,
  StageEighteenScenario,
  StageEighteenStatusResponse
} from '@/types/stageEighteen';

const integrationHealthPath = '/health';
const adminDemoDataStatusPath = '/admin/demo-data/status';

function emptyStatus(): StageEighteenStatusResponse {
  return {
    ready: false,
    accounts: 0,
    scenarioCount: 0
  };
}

function requireAdminStatus(): ApiResponse<StageEighteenStatusResponse> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyStatus(), 'mock-18-admin-demo-status-unauthorized');
  }
  if (!session.user.roles.includes('ADMIN')) {
    return failure(403, '无权限', emptyStatus(), 'mock-18-admin-demo-status-forbidden');
  }
  return null;
}

function statusByScenario(
  scenario: StageEighteenScenario,
  traceId: string
): ApiResponse<StageEighteenStatusResponse> {
  if (scenario === 'empty') {
    return integrationStatusEmptyMock as ApiResponse<StageEighteenStatusResponse>;
  }
  if (scenario === 'error') {
    return integrationStatusErrorMock as ApiResponse<StageEighteenStatusResponse>;
  }
  return success((integrationStatusMock as ApiResponse<StageEighteenStatusResponse>).data, traceId);
}

export function getStageEighteenEndpointSummary() {
  return ['GET /api/v1/health', 'GET /api/v1/admin/demo-data/status'];
}

export function getStageEighteenFlowSteps(): StageEighteenFlowStep[] {
  return [
    { stepId: 'login', label: '登录四类演示账号', ownerRole: 'ALL', sourceStage: '2', status: 'READY' },
    { stepId: 'binding', label: '长辈/家属绑定授权', ownerRole: 'FAMILY', sourceStage: '6', status: 'READY' },
    { stepId: 'profile', label: '长辈基础档案读取', ownerRole: 'FAMILY', sourceStage: '7', status: 'READY' },
    { stepId: 'service', label: '服务项目选择', ownerRole: 'FAMILY', sourceStage: '8', status: 'READY' },
    { stepId: 'address', label: '默认服务地址选择', ownerRole: 'FAMILY', sourceStage: '9', status: 'READY' },
    { stepId: 'order', label: '预约下单 WAIT_DISPATCH', ownerRole: 'FAMILY', sourceStage: '10', status: 'READY' },
    { stepId: 'admin-orders', label: '管理端订单筛选', ownerRole: 'ADMIN', sourceStage: '11', status: 'READY' },
    { stepId: 'dispatch', label: '管理端派单', ownerRole: 'ADMIN', sourceStage: '12', status: 'READY' },
    { stepId: 'task', label: '护理端任务工作台', ownerRole: 'NURSE', sourceStage: '13', status: 'READY' },
    { stepId: 'execution', label: '护理执行记录提交', ownerRole: 'NURSE', sourceStage: '14', status: 'READY' },
    { stepId: 'report', label: '服务报告生成与查看', ownerRole: 'FAMILY', sourceStage: '15', status: 'READY' },
    { stepId: 'ack', label: '长辈/家属确认服务', ownerRole: 'ELDER', sourceStage: '16', status: 'READY' },
    { stepId: 'change', label: '取消/改期状态一致', ownerRole: 'FAMILY', sourceStage: '17', status: 'READY' }
  ];
}

export async function getIntegrationHealth(
  scenario: StageEighteenScenario = 'normal'
): Promise<ApiResponse<StageEighteenStatusResponse>> {
  if (isMockEnabled()) {
    return statusByScenario(scenario, 'mock-18-health');
  }

  return request<StageEighteenStatusResponse>({
    method: 'GET',
    url: integrationHealthPath,
    mock: integrationStatusMock as ApiResponse<StageEighteenStatusResponse>,
    mockFallback: true
  });
}

export async function getAdminDemoDataStatus(
  scenario: StageEighteenScenario = 'normal'
): Promise<ApiResponse<StageEighteenStatusResponse>> {
  if (isMockEnabled()) {
    const denied = requireAdminStatus();
    if (denied) {
      return denied;
    }
    return statusByScenario(scenario, 'mock-18-admin-demo-data-status');
  }

  return request<StageEighteenStatusResponse>({
    method: 'GET',
    url: adminDemoDataStatusPath,
    mock: integrationStatusMock as ApiResponse<StageEighteenStatusResponse>,
    mockFallback: true
  });
}
