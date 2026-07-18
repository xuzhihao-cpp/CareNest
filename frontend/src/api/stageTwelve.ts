import { request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  DispatchRequest,
  NurseTaskPageResult,
  NurseTaskQuery,
  NurseTaskRecord,
  StageTwelveScenario,
  TaskActionResponse,
  TaskStatusRequest
} from '@/types/stageTwelve';

const dispatchPath = (orderId: string) => `/admin/orders/${encodeURIComponent(orderId)}/dispatch`;
const acceptPath = (taskId: string) => `/nurse/tasks/${encodeURIComponent(taskId)}/accept`;
const statusPath = (taskId: string) => `/nurse/tasks/${encodeURIComponent(taskId)}/status`;

// Retained as compatibility exports for older modules; stage 12 no longer reads or writes these keys.
export const STAGE_TWELVE_TASKS_STORAGE_KEY = 'carenest_stage_12_nurse_tasks';
export const STAGE_TWELVE_ORDER_STORAGE_KEY = 'carenest_stage_12_order_overrides';

const defaultTaskQuery: NurseTaskQuery = {
  status: '',
  page: 1,
  size: 10
};

type BackendTask = Omit<NurseTaskRecord, 'orderNo' | 'nurseName' | 'elderName' | 'serviceId' | 'serviceName' | 'addressId'> & {
  elderId?: string;
  nurseName?: string;
  elderName?: string;
  serviceName?: string;
};
type BackendTaskPage = { records: BackendTask[]; total: number; page: number; size: number };
type BackendTaskAction = Omit<TaskActionResponse, 'orderNo'>;

function fromBackendTask(task: BackendTask): NurseTaskRecord {
  return {
    ...task,
    orderNo: task.orderId,
    nurseName: task.nurseName || '',
    elderId: task.elderId || '',
    elderName: task.elderName || '',
    serviceId: '',
    serviceName: task.serviceName || '',
    addressId: ''
  };
}

function fromBackendAction(result: BackendTaskAction): TaskActionResponse {
  return { ...result, orderNo: result.orderId };
}

export function getStageTwelveEndpointSummary() {
  return [
    'POST /api/v1/admin/orders/{orderId}/dispatch',
    'POST /api/v1/nurse/tasks/{taskId}/accept',
    'POST /api/v1/nurse/tasks/{taskId}/status'
  ];
}

export async function dispatchAdminOrder(
  orderId: string,
  payload: DispatchRequest
): Promise<ApiResponse<TaskActionResponse>> {
  const response = await request<BackendTaskAction>({
    method: 'POST',
    url: dispatchPath(orderId),
    data: payload
  });
  return response.code === 0
    ? success(fromBackendAction(response.data), response.traceId)
    : (response as ApiResponse<TaskActionResponse>);
}

export async function getStageTwelveNurseTasks(
  query: Partial<NurseTaskQuery> = {},
  _scenario: StageTwelveScenario = 'normal'
): Promise<ApiResponse<NurseTaskPageResult>> {
  const nextQuery = { ...defaultTaskQuery, ...query };
  const response = await request<BackendTaskPage>({
    method: 'GET',
    url: '/nurse/tasks',
    data: nextQuery
  });
  return response.code === 0
    ? success({ ...response.data, records: response.data.records.map(fromBackendTask) }, response.traceId)
    : (response as ApiResponse<NurseTaskPageResult>);
}

export async function acceptNurseTask(
  taskId: string,
  payload: TaskStatusRequest
): Promise<ApiResponse<TaskActionResponse>> {
  const response = await request<BackendTaskAction>({
    method: 'POST',
    url: acceptPath(taskId),
    data: payload
  });
  return response.code === 0
    ? success(fromBackendAction(response.data), response.traceId)
    : (response as ApiResponse<TaskActionResponse>);
}

export async function updateNurseTaskStatus(
  taskId: string,
  payload: TaskStatusRequest
): Promise<ApiResponse<TaskActionResponse>> {
  const response = await request<BackendTaskAction>({
    method: 'POST',
    url: statusPath(taskId),
    data: payload
  });
  return response.code === 0
    ? success(fromBackendAction(response.data), response.traceId)
    : (response as ApiResponse<TaskActionResponse>);
}
