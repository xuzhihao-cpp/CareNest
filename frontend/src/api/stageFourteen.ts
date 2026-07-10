import executionResponseMock from '@/mock/phase-14/execution-response.json';
import serviceRecordsEmptyMock from '@/mock/phase-14/service-records-empty.json';
import serviceRecordsErrorMock from '@/mock/phase-14/service-records-error.json';
import serviceRecordsMock from '@/mock/phase-14/service-records.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import {
  STAGE_TWELVE_ORDER_STORAGE_KEY,
  STAGE_TWELVE_TASKS_STORAGE_KEY
} from '@/api/stageTwelve';
import type { ApiResponse } from '@/types/api';
import type { AdminOrderRecord, AdminOrderStatus, OrderStatusLogRecord } from '@/types/stageEleven';
import type { NurseTaskRecord, NurseTaskStatus } from '@/types/stageTwelve';
import type {
  CareExecutionPageResult,
  CareExecutionRecord,
  CareExecutionResponse,
  CareServiceRecordRequest,
  StageFourteenScenario,
  VitalSignRecordRequest
} from '@/types/stageFourteen';

const serviceRecordPath = (orderId: string) => `/nurse/orders/${orderId}/service-records`;
const vitalSignPath = (orderId: string) => `/nurse/orders/${orderId}/vital-signs`;
const orderServiceRecordsPath = (orderId: string) => `/orders/${orderId}/service-records`;
export const STAGE_FOURTEEN_RECORDS_STORAGE_KEY = 'carenest_stage_14_service_records';

function seedRecords(): CareExecutionRecord[] {
  return [...(serviceRecordsMock as ApiResponse<CareExecutionPageResult>).data.records];
}

function readRecords(): CareExecutionRecord[] {
  const stored = uni.getStorageSync(STAGE_FOURTEEN_RECORDS_STORAGE_KEY);
  return stored ? (stored as CareExecutionRecord[]) : [];
}

function writeRecords(records: CareExecutionRecord[]) {
  uni.setStorageSync(STAGE_FOURTEEN_RECORDS_STORAGE_KEY, records);
}

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

function requireExecutionRole<T>(emptyData: T, traceId: string): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, traceId);
  }
  if (!session.user.roles.includes('NURSE') && !session.user.roles.includes('ADMIN')) {
    return failure(403, '无权限', emptyData, traceId);
  }
  return null;
}

function toPage(records: CareExecutionRecord[]): CareExecutionPageResult {
  return {
    records,
    total: records.length,
    page: 1,
    size: 10
  };
}

function nextRecordId(type: CareExecutionRecord['recordType'], count: number) {
  return `${type === 'SERVICE_RECORD' ? 'service-record' : 'vital-sign'}-${String(count + 1).padStart(3, '0')}`;
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

function nextOrderStatus(payload: CareServiceRecordRequest | VitalSignRecordRequest): AdminOrderStatus {
  return payload.abnormalFlag ? 'WAIT_CONFIRM' : 'WAIT_REPORT';
}

function syncOrderAndTask(orderId: string, orderStatus: AdminOrderStatus, reason: string) {
  const taskStatus = orderStatus as NurseTaskStatus;
  const tasks = readTasks();
  const nextTasks = tasks.map((task) =>
    task.orderId === orderId
      ? {
          ...task,
          taskStatus,
          orderStatus
        }
      : task
  );
  writeTasks(nextTasks);

  const orders = readOrderOverrides();
  const index = orders.findIndex((item) => item.orderId === orderId);
  if (index >= 0) {
    const order = orders[index];
    orders[index] = {
      ...order,
      orderStatus,
      statusLogs: [
        ...order.statusLogs,
        statusLog(order.orderId, order.statusLogs.length, order.orderStatus, orderStatus, 'nurse-001', reason)
      ]
    };
    writeOrderOverrides(orders);
  }
}

function buildRecord(
  orderId: string,
  payload: CareServiceRecordRequest | VitalSignRecordRequest,
  type: CareExecutionRecord['recordType']
): CareExecutionRecord | null {
  const task = readTasks().find((item) => item.orderId === orderId);
  if (!task) {
    return null;
  }
  const records = readRecords();
  const orderStatus = nextOrderStatus(payload);
  return {
    recordId: nextRecordId(type, records.length),
    orderId,
    orderStatus,
    recordType: type,
    taskId: task.taskId,
    nurseId: task.nurseId,
    startTime: payload.startTime,
    endTime: payload.endTime,
    content: payload.content,
    nursingAdvice: payload.nursingAdvice,
    abnormalFlag: payload.abnormalFlag
  };
}

function validatePayload(payload: CareServiceRecordRequest | VitalSignRecordRequest) {
  return payload.startTime && payload.endTime && payload.content && payload.nursingAdvice;
}

export function getStageFourteenEndpointSummary() {
  return [
    'POST /api/v1/nurse/orders/{orderId}/service-records',
    'POST /api/v1/nurse/orders/{orderId}/vital-signs',
    'GET /api/v1/orders/{orderId}/service-records'
  ];
}

export async function createServiceRecord(
  orderId: string,
  payload: CareServiceRecordRequest
): Promise<ApiResponse<CareExecutionResponse>> {
  if (isMockEnabled()) {
    const denied = requireExecutionRole({} as CareExecutionResponse, 'mock-14-service-record-forbidden');
    if (denied) {
      return denied;
    }
    if (!validatePayload(payload)) {
      return failure(422, '业务规则不满足', {} as CareExecutionResponse, 'mock-14-service-record-invalid');
    }
    const record = buildRecord(orderId, payload, 'SERVICE_RECORD');
    if (!record) {
      return failure(404, '数据不存在', {} as CareExecutionResponse, 'mock-14-order-not-found');
    }
    writeRecords([record, ...readRecords()]);
    syncOrderAndTask(orderId, record.orderStatus, '护理执行记录已提交');
    return success(
      {
        recordId: record.recordId,
        orderId: record.orderId,
        orderStatus: record.orderStatus
      },
      'mock-14-service-record-create'
    );
  }

  return request<CareExecutionResponse>({
    method: 'POST',
    url: serviceRecordPath(orderId),
    data: payload,
    mock: executionResponseMock as ApiResponse<CareExecutionResponse>
  });
}

export async function createVitalSignRecord(
  orderId: string,
  payload: VitalSignRecordRequest
): Promise<ApiResponse<CareExecutionResponse>> {
  if (isMockEnabled()) {
    const denied = requireExecutionRole({} as CareExecutionResponse, 'mock-14-vital-sign-forbidden');
    if (denied) {
      return denied;
    }
    if (!validatePayload(payload)) {
      return failure(422, '业务规则不满足', {} as CareExecutionResponse, 'mock-14-vital-sign-invalid');
    }
    const record = buildRecord(orderId, payload, 'VITAL_SIGN');
    if (!record) {
      return failure(404, '数据不存在', {} as CareExecutionResponse, 'mock-14-order-not-found');
    }
    writeRecords([record, ...readRecords()]);
    syncOrderAndTask(orderId, record.orderStatus, '生命体征记录已提交');
    return success(
      {
        recordId: record.recordId,
        orderId: record.orderId,
        orderStatus: record.orderStatus
      },
      'mock-14-vital-sign-create'
    );
  }

  return request<CareExecutionResponse>({
    method: 'POST',
    url: vitalSignPath(orderId),
    data: payload,
    mock: executionResponseMock as ApiResponse<CareExecutionResponse>
  });
}

export async function getOrderServiceRecords(
  orderId: string,
  scenario: StageFourteenScenario = 'normal'
): Promise<ApiResponse<CareExecutionPageResult>> {
  if (isMockEnabled()) {
    const denied = requireExecutionRole(toPage([]), 'mock-14-records-forbidden');
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return serviceRecordsEmptyMock as ApiResponse<CareExecutionPageResult>;
    }
    if (scenario === 'error') {
      return serviceRecordsErrorMock as ApiResponse<CareExecutionPageResult>;
    }
    const records = readRecords();
    const visibleRecords = records.length > 0 ? records : seedRecords();
    return success(toPage(visibleRecords.filter((item) => item.orderId === orderId)), 'mock-14-service-records');
  }

  const response = await request<Array<Omit<CareExecutionRecord, 'recordType' | 'taskId' | 'nurseId'>>>({
    method: 'GET',
    url: orderServiceRecordsPath(orderId),
    mock: serviceRecordsMock as unknown as ApiResponse<Array<Omit<CareExecutionRecord, 'recordType' | 'taskId' | 'nurseId'>>>
  });
  if (response.code !== 0) {
    return response as unknown as ApiResponse<CareExecutionPageResult>;
  }
  return success(
    toPage(response.data.map((record) => ({ ...record, recordType: 'SERVICE_RECORD', taskId: '', nurseId: '' }))),
    response.traceId
  );
}
