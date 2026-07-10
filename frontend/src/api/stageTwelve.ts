import dispatchResponseMock from '@/mock/phase-12/dispatch-response.json';
import nurseTasksEmptyMock from '@/mock/phase-12/nurse-tasks-empty.json';
import nurseTasksErrorMock from '@/mock/phase-12/nurse-tasks-error.json';
import nurseTasksMock from '@/mock/phase-12/nurse-tasks.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import { getAdminOrders } from '@/api/stageEleven';
import type { ApiResponse } from '@/types/api';
import type { AdminOrderRecord, AdminOrderStatus, OrderStatusLogRecord } from '@/types/stageEleven';
import type {
  DispatchRequest,
  NurseTaskPageResult,
  NurseTaskQuery,
  NurseTaskRecord,
  NurseTaskStatus,
  StageTwelveScenario,
  TaskActionResponse,
  TaskStatusRequest
} from '@/types/stageTwelve';

const dispatchPath = (orderId: string) => `/admin/orders/${orderId}/dispatch`;
const acceptPath = (taskId: string) => `/nurse/tasks/${taskId}/accept`;
const statusPath = (taskId: string) => `/nurse/tasks/${taskId}/status`;
export const STAGE_TWELVE_TASKS_STORAGE_KEY = 'carenest_stage_12_nurse_tasks';
export const STAGE_TWELVE_ORDER_STORAGE_KEY = 'carenest_stage_12_order_overrides';

const defaultTaskQuery: NurseTaskQuery = {
  status: '',
  page: 1,
  size: 10
};

function readTasks(): NurseTaskRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY);
  return stored ? (stored as NurseTaskRecord[]) : [];
}

function writeTasks(records: NurseTaskRecord[]) {
  uni.setStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY, records);
}

function readOrderOverrides(): AdminOrderRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY);
  return stored ? (stored as AdminOrderRecord[]) : [];
}

function writeOrderOverrides(records: AdminOrderRecord[]) {
  uni.setStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY, records);
}

function requireRole<T>(roles: Array<'ADMIN' | 'NURSE'>, emptyData: T, traceId: string): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, traceId);
  }
  if (!roles.some((role) => session.user.roles.includes(role))) {
    return failure(403, '无权限', emptyData, traceId);
  }
  return null;
}

function currentNurseId() {
  return readAuthSession()?.user.userId ?? 'nurse-001';
}

function nurseName(nurseId: string) {
  return nurseId === 'nurse-001' ? '护理演示账号' : nurseId;
}

function nextTaskId(records: NurseTaskRecord[]) {
  return `task-${String(records.length + 1).padStart(3, '0')}`;
}

function statusLog(
  orderId: string,
  index: number,
  fromStatus: AdminOrderStatus,
  toStatus: AdminOrderStatus,
  changedBy: string,
  changeReason: string
): OrderStatusLogRecord {
  return {
    statusLogId: `order-status-log-${String(index + 1).padStart(3, '0')}`,
    orderId,
    fromStatus,
    toStatus,
    changedBy,
    changeReason
  };
}

function upsertOrderOverride(record: AdminOrderRecord) {
  const records = readOrderOverrides();
  const index = records.findIndex((item) => item.orderId === record.orderId);
  if (index >= 0) {
    records[index] = record;
  } else {
    records.unshift(record);
  }
  writeOrderOverrides(records);
}

function toTaskPage(records: NurseTaskRecord[], query: NurseTaskQuery): NurseTaskPageResult {
  const session = readAuthSession();
  const visibleRecords = session?.user.roles.includes('NURSE')
    ? records.filter((item) => item.nurseId === session.user.userId)
    : records;
  const filtered = query.status ? visibleRecords.filter((item) => item.taskStatus === query.status) : visibleRecords;
  return {
    records: filtered,
    total: filtered.length,
    page: query.page,
    size: query.size
  };
}

function applyTaskStatus(task: NurseTaskRecord, targetStatus: NurseTaskStatus, remark: string): TaskActionResponse {
  const orderStatus = targetStatus as AdminOrderStatus;
  const nextTask = {
    ...task,
    taskStatus: targetStatus,
    orderStatus,
    dispatchRemark: remark || task.dispatchRemark
  };
  const tasks = readTasks();
  writeTasks(tasks.map((item) => (item.taskId === task.taskId ? nextTask : item)));

  const override = readOrderOverrides().find((item) => item.orderId === task.orderId);
  if (override) {
    const changedBy = targetStatus === 'ACCEPTED' ? task.nurseId : task.nurseId;
    upsertOrderOverride({
      ...override,
      orderStatus,
      statusLogs: [
        ...override.statusLogs,
        statusLog(
          override.orderId,
          override.statusLogs.length,
          override.orderStatus,
          orderStatus,
          changedBy,
          remark || '护理端任务状态更新'
        )
      ]
    });
  }

  return {
    orderId: nextTask.orderId,
    orderNo: nextTask.orderNo,
    orderStatus: nextTask.orderStatus,
    taskId: nextTask.taskId
  };
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
  if (isMockEnabled()) {
    const denied = requireRole(['ADMIN'], {} as TaskActionResponse, 'mock-12-dispatch-forbidden');
    if (denied) {
      return denied;
    }
    if (!payload.nurseId || payload.targetStatus !== 'DISPATCHED') {
      return failure(422, '业务规则不满足', {} as TaskActionResponse, 'mock-12-dispatch-invalid');
    }

    const orderResponse = await getAdminOrders({ page: 1, size: 50, orderStatus: '' }, 'normal');
    const order = orderResponse.data.records.find((item) => item.orderId === orderId);
    if (!order) {
      return failure(404, '数据不存在', {} as TaskActionResponse, 'mock-12-order-not-found');
    }
    if (order.orderStatus !== 'WAIT_DISPATCH') {
      return failure(409, '订单状态不允许派单', {} as TaskActionResponse, 'mock-12-order-status-conflict');
    }

    const tasks = readTasks();
    const taskId = nextTaskId(tasks);
    const nextOrder: AdminOrderRecord = {
      ...order,
      orderStatus: 'DISPATCHED',
      statusLogs: [
        ...order.statusLogs,
        statusLog(
          order.orderId,
          order.statusLogs.length,
          'WAIT_DISPATCH',
          'DISPATCHED',
          'admin-001',
          payload.dispatchRemark || '管理端派单'
        )
      ]
    };
    const createdTask: NurseTaskRecord = {
      taskId,
      orderId: order.orderId,
      orderNo: order.orderNo,
      nurseId: payload.nurseId,
      nurseName: nurseName(payload.nurseId),
      elderId: order.elderId,
      serviceId: order.serviceId,
      addressId: order.addressId,
      scheduledStart: order.scheduledStart,
      dispatchRemark: payload.dispatchRemark,
      taskStatus: 'DISPATCHED',
      orderStatus: 'DISPATCHED'
    };
    upsertOrderOverride(nextOrder);
    writeTasks([createdTask, ...tasks]);

    return success(
      {
        orderId: order.orderId,
        orderNo: order.orderNo,
        orderStatus: 'DISPATCHED',
        taskId
      },
      'mock-12-dispatch'
    );
  }

  return request<TaskActionResponse>({
    method: 'POST',
    url: dispatchPath(orderId),
    data: payload,
    mock: dispatchResponseMock as ApiResponse<TaskActionResponse>
  });
}

export async function getStageTwelveNurseTasks(
  query: Partial<NurseTaskQuery> = {},
  scenario: StageTwelveScenario = 'normal'
): Promise<ApiResponse<NurseTaskPageResult>> {
  const nextQuery = { ...defaultTaskQuery, ...query };
  if (isMockEnabled()) {
    const denied = requireRole(['ADMIN', 'NURSE'], toTaskPage([], nextQuery), 'mock-12-nurse-tasks-forbidden');
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return nurseTasksEmptyMock as ApiResponse<NurseTaskPageResult>;
    }
    if (scenario === 'error') {
      return nurseTasksErrorMock as ApiResponse<NurseTaskPageResult>;
    }
    return success(toTaskPage(readTasks(), nextQuery), 'mock-12-nurse-tasks');
  }

  return request<NurseTaskPageResult>({
    method: 'GET',
    url: '/nurse/tasks',
    data: nextQuery,
    mock: nurseTasksMock as ApiResponse<NurseTaskPageResult>
  });
}

export async function acceptNurseTask(
  taskId: string,
  payload: TaskStatusRequest
): Promise<ApiResponse<TaskActionResponse>> {
  if (isMockEnabled()) {
    const denied = requireRole(['ADMIN', 'NURSE'], {} as TaskActionResponse, 'mock-12-accept-forbidden');
    if (denied) {
      return denied;
    }
    const task = readTasks().find((item) => item.taskId === taskId);
    if (!task) {
      return failure(404, '数据不存在', {} as TaskActionResponse, 'mock-12-task-not-found');
    }
    if (task.taskStatus !== 'DISPATCHED') {
      return failure(409, '任务状态不允许接单', {} as TaskActionResponse, 'mock-12-task-status-conflict');
    }
    const nurseId = payload.nurseId || currentNurseId();
    if (readAuthSession()?.user.roles.includes('NURSE') && task.nurseId !== nurseId) {
      return failure(403, '无权限', {} as TaskActionResponse, 'mock-12-task-owner-forbidden');
    }
    return success(applyTaskStatus(task, 'ACCEPTED', payload.dispatchRemark || '护理端接单'), 'mock-12-accept');
  }

  return request<TaskActionResponse>({
    method: 'POST',
    url: acceptPath(taskId),
    data: payload,
    mock: dispatchResponseMock as ApiResponse<TaskActionResponse>
  });
}

export async function updateNurseTaskStatus(
  taskId: string,
  payload: TaskStatusRequest
): Promise<ApiResponse<TaskActionResponse>> {
  if (isMockEnabled()) {
    const denied = requireRole(['ADMIN', 'NURSE'], {} as TaskActionResponse, 'mock-12-status-forbidden');
    if (denied) {
      return denied;
    }
    const task = readTasks().find((item) => item.taskId === taskId);
    if (!task) {
      return failure(404, '数据不存在', {} as TaskActionResponse, 'mock-12-task-not-found');
    }
    if (!['ACCEPTED', 'ON_THE_WAY', 'SERVING'].includes(payload.targetStatus)) {
      return failure(422, '业务规则不满足', {} as TaskActionResponse, 'mock-12-status-invalid');
    }
    if (readAuthSession()?.user.roles.includes('NURSE') && task.nurseId !== (payload.nurseId || currentNurseId())) {
      return failure(403, '无权限', {} as TaskActionResponse, 'mock-12-task-owner-forbidden');
    }
    return success(
      applyTaskStatus(task, payload.targetStatus, payload.dispatchRemark || '护理端任务状态更新'),
      'mock-12-status'
    );
  }

  return request<TaskActionResponse>({
    method: 'POST',
    url: statusPath(taskId),
    data: payload,
    mock: dispatchResponseMock as ApiResponse<TaskActionResponse>
  });
}

export function resetStageTwelveMockRecords() {
  uni.removeStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY);
  uni.removeStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY);
}
