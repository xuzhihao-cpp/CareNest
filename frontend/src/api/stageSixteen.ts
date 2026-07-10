import reportAckEmptyMock from '@/mock/phase-16/report-ack-empty.json';
import reportAckErrorMock from '@/mock/phase-16/report-ack-error.json';
import reportAckMock from '@/mock/phase-16/report-ack.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import { STAGE_FIFTEEN_REPORTS_STORAGE_KEY } from '@/api/stageFifteen';
import { getStageSixBindingSnapshot } from '@/api/stageSix';
import {
  STAGE_TWELVE_ORDER_STORAGE_KEY,
  STAGE_TWELVE_TASKS_STORAGE_KEY
} from '@/api/stageTwelve';
import type { ApiResponse } from '@/types/api';
import type { AdminOrderRecord, AdminOrderStatus, OrderStatusLogRecord } from '@/types/stageEleven';
import type { ServiceReportRecord } from '@/types/stageFifteen';
import type { NurseTaskRecord } from '@/types/stageTwelve';
import type {
  HealthInfoReviewTaskRecord,
  ReportAckRecord,
  ReportAckRequest,
  ReportAckResponse,
  ServiceReportStatus,
  StageSixteenScenario
} from '@/types/stageSixteen';

const elderAckPath = (reportId: string) => `/elder/reports/${reportId}/ack`;
const familyAckPath = (reportId: string) => `/family/reports/${reportId}/ack`;
const archiveDecisionPath = (reportId: string) => `/family/reports/${reportId}/archive-suggestions/decision`;

export const STAGE_SIXTEEN_ACKS_STORAGE_KEY = 'carenest_stage_16_report_acks';
export const STAGE_SIXTEEN_REVIEW_TASKS_STORAGE_KEY = 'carenest_stage_16_health_review_tasks';

function emptyAck(): ReportAckResponse {
  return {
    ackId: '',
    ackResult: 'REJECTED',
    reportStatus: 'WAIT_CONFIRM'
  };
}

function readReports(): ServiceReportRecord[] {
  const stored = uni.getStorageSync(STAGE_FIFTEEN_REPORTS_STORAGE_KEY);
  return stored ? (stored as ServiceReportRecord[]) : [];
}

function readAcks(): ReportAckRecord[] {
  const stored = uni.getStorageSync(STAGE_SIXTEEN_ACKS_STORAGE_KEY);
  return stored ? (stored as ReportAckRecord[]) : [];
}

function writeAcks(records: ReportAckRecord[]) {
  uni.setStorageSync(STAGE_SIXTEEN_ACKS_STORAGE_KEY, records);
}

function readReviewTasks(): HealthInfoReviewTaskRecord[] {
  const stored = uni.getStorageSync(STAGE_SIXTEEN_REVIEW_TASKS_STORAGE_KEY);
  return stored ? (stored as HealthInfoReviewTaskRecord[]) : [];
}

function writeReviewTasks(records: HealthInfoReviewTaskRecord[]) {
  uni.setStorageSync(STAGE_SIXTEEN_REVIEW_TASKS_STORAGE_KEY, records);
}

function readOrders(): AdminOrderRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY);
  return stored ? (stored as AdminOrderRecord[]) : [];
}

function writeOrders(records: AdminOrderRecord[]) {
  uni.setStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY, records);
}

function readTasks(): NurseTaskRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY);
  return stored ? (stored as NurseTaskRecord[]) : [];
}

function writeTasks(records: NurseTaskRecord[]) {
  uni.setStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY, records);
}

function nextAckId(records: ReportAckRecord[]) {
  return `ack-${String(records.length + 1).padStart(3, '0')}`;
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

function resolveOrderElderId(report: ServiceReportRecord) {
  const order = readOrders().find((item) => item.orderId === report.orderId);
  return order?.elderId ?? 'elder-001';
}

function hasActiveReportConfirmBinding(elderId: string) {
  return getStageSixBindingSnapshot().some(
    (binding) =>
      binding.elderId === elderId &&
      binding.bindingStatus === 'ACTIVE' &&
      binding.scopeCodes.includes('REPORT_CONFIRM')
  );
}

function requireRole(
  allowedRoles: Array<'ELDER' | 'FAMILY'>,
  report: ServiceReportRecord,
  traceId: string
): ApiResponse<ReportAckResponse> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyAck(), traceId);
  }
  if (!allowedRoles.some((role) => session.user.roles.includes(role))) {
    return failure(403, '无权限', emptyAck(), traceId);
  }
  if (session.user.roles.includes('FAMILY')) {
    const elderId = resolveOrderElderId(report);
    if (!hasActiveReportConfirmBinding(elderId)) {
      return failure(403, '无权限', emptyAck(), 'mock-16-family-binding-scope-forbidden');
    }
  }
  return null;
}

function validatePayload(payload: ReportAckRequest) {
  return ['ACCEPTED', 'REJECTED'].includes(payload.ackResult) && payload.satisfaction >= 1 && payload.satisfaction <= 5;
}

function reportStatusFromAck(payload: ReportAckRequest, archiveDecision: boolean): ServiceReportStatus {
  if (payload.ackResult === 'REJECTED') {
    return 'DISPUTED';
  }
  return archiveDecision && payload.acceptedSuggestionIds.length > 0 ? 'ARCHIVE_PENDING' : 'CONFIRMED';
}

function orderStatusFromReportStatus(reportStatus: ServiceReportStatus): 'WAIT_CONFIRM' | 'COMPLETED' {
  return reportStatus === 'DISPUTED' ? 'WAIT_CONFIRM' : 'COMPLETED';
}

function upsertFallbackOrder(orderId: string, orderStatus: 'WAIT_CONFIRM' | 'COMPLETED', operatorId: string) {
  const orders = readOrders();
  const index = orders.findIndex((item) => item.orderId === orderId);
  if (index >= 0) {
    const previous = orders[index];
    orders[index] = {
      ...previous,
      orderStatus,
      statusLogs: [
        ...previous.statusLogs,
        statusLog(previous.orderId, previous.statusLogs.length, previous.orderStatus, orderStatus, operatorId, '阶段16报告确认')
      ]
    };
    writeOrders(orders);
    return;
  }

  writeOrders([
    {
      orderId,
      orderNo: orderId === 'order-002' ? 'NO202607100002' : orderId,
      orderStatus,
      elderId: 'elder-001',
      serviceId: 'service-001',
      addressId: 'address-001',
      scheduledStart: '2026-07-10T09:00',
      statusLogs: [
        statusLog(orderId, 0, 'WAIT_CONFIRM', orderStatus, operatorId, '阶段16报告确认生成状态同步记录')
      ]
    },
    ...orders
  ]);
}

function syncTaskStatus(orderId: string, orderStatus: 'WAIT_CONFIRM' | 'COMPLETED') {
  const tasks = readTasks();
  writeTasks(tasks.map((task) => (task.orderId === orderId ? { ...task, taskStatus: orderStatus, orderStatus } : task)));
}

function createReviewTasks(reportId: string, orderId: string, suggestionIds: string[]) {
  if (suggestionIds.length === 0) {
    return;
  }
  const existing = readReviewTasks().filter((item) => item.reportId !== reportId);
  const created = suggestionIds.map<HealthInfoReviewTaskRecord>((suggestionId, index) => ({
    taskId: `review-task-${String(existing.length + index + 1).padStart(3, '0')}`,
    reportId,
    orderId,
    suggestionId,
    fieldName: suggestionId === 'suggestion-bp' ? 'bloodPressureCarePlan' : 'sleepCarePlan',
    newValue: suggestionId === 'suggestion-bp' ? '继续观察血压并记录晨晚数值' : '增加睡眠质量观察',
    status: 'PENDING'
  }));
  writeReviewTasks([...created, ...existing]);
}

function toResponse(record: ReportAckRecord): ReportAckResponse {
  return {
    ackId: record.ackId,
    ackResult: record.ackResult,
    reportStatus: record.reportStatus
  };
}

function submitAck(
  reportId: string,
  payload: ReportAckRequest,
  allowedRoles: Array<'ELDER' | 'FAMILY'>,
  archiveDecision: boolean,
  traceId: string
): ApiResponse<ReportAckResponse> {
  if (!validatePayload(payload)) {
    return failure(422, '业务规则不满足', emptyAck(), 'mock-16-report-ack-invalid');
  }

  const report = readReports().find((item) => item.reportId === reportId);
  if (!report) {
    return reportAckEmptyMock as ApiResponse<ReportAckResponse>;
  }
  const denied = requireRole(allowedRoles, report, traceId);
  if (denied) {
    return denied;
  }

  const session = readAuthSession();
  const acks = readAcks();
  const reportStatus = reportStatusFromAck(payload, archiveDecision);
  const orderStatus = orderStatusFromReportStatus(reportStatus);
  const ack: ReportAckRecord = {
    ackId: nextAckId(acks),
    reportId,
    orderId: report.orderId,
    operatorId: session?.user.userId ?? 'user-001',
    operatorRole: session?.user.roles.includes('ELDER') ? 'ELDER' : 'FAMILY',
    ackResult: payload.ackResult,
    satisfaction: payload.satisfaction,
    remark: payload.remark,
    acceptedSuggestionIds: payload.acceptedSuggestionIds,
    reportStatus,
    orderStatus,
    createdAt: '2026-07-10T11:00'
  };

  writeAcks([ack, ...acks.filter((item) => item.reportId !== reportId || item.operatorId !== ack.operatorId)]);
  upsertFallbackOrder(report.orderId, orderStatus, ack.operatorId);
  syncTaskStatus(report.orderId, orderStatus);
  if (archiveDecision && reportStatus === 'ARCHIVE_PENDING') {
    createReviewTasks(reportId, report.orderId, payload.acceptedSuggestionIds);
  }

  return success(toResponse(ack), traceId);
}

export function getStageSixteenEndpointSummary() {
  return [
    'POST /api/v1/elder/reports/{reportId}/ack',
    'POST /api/v1/family/reports/{reportId}/ack',
    'POST /api/v1/family/reports/{reportId}/archive-suggestions/decision'
  ];
}

export function getLatestReportAck(reportId: string): ReportAckRecord | null {
  return readAcks().find((item) => item.reportId === reportId) ?? null;
}

export function getHealthInfoReviewTasks(reportId: string): HealthInfoReviewTaskRecord[] {
  return readReviewTasks().filter((item) => item.reportId === reportId);
}

export async function ackElderReport(
  reportId: string,
  payload: ReportAckRequest,
  scenario: StageSixteenScenario = 'normal'
): Promise<ApiResponse<ReportAckResponse>> {
  if (isMockEnabled()) {
    if (scenario === 'empty') {
      return reportAckEmptyMock as ApiResponse<ReportAckResponse>;
    }
    if (scenario === 'error') {
      return reportAckErrorMock as ApiResponse<ReportAckResponse>;
    }
    return submitAck(reportId, payload, ['ELDER', 'FAMILY'], false, 'mock-16-elder-report-ack');
  }

  return request<ReportAckResponse>({
    method: 'POST',
    url: elderAckPath(reportId),
    data: payload,
    mock: reportAckMock as ApiResponse<ReportAckResponse>,
    mockFallback: true
  });
}

export async function ackFamilyReport(
  reportId: string,
  payload: ReportAckRequest,
  scenario: StageSixteenScenario = 'normal'
): Promise<ApiResponse<ReportAckResponse>> {
  if (isMockEnabled()) {
    if (scenario === 'empty') {
      return reportAckEmptyMock as ApiResponse<ReportAckResponse>;
    }
    if (scenario === 'error') {
      return reportAckErrorMock as ApiResponse<ReportAckResponse>;
    }
    return submitAck(reportId, payload, ['FAMILY'], false, 'mock-16-family-report-ack');
  }

  return request<ReportAckResponse>({
    method: 'POST',
    url: familyAckPath(reportId),
    data: payload,
    mock: reportAckMock as ApiResponse<ReportAckResponse>,
    mockFallback: true
  });
}

export async function decideArchiveSuggestions(
  reportId: string,
  payload: ReportAckRequest,
  scenario: StageSixteenScenario = 'normal'
): Promise<ApiResponse<ReportAckResponse>> {
  if (isMockEnabled()) {
    if (scenario === 'empty') {
      return reportAckEmptyMock as ApiResponse<ReportAckResponse>;
    }
    if (scenario === 'error') {
      return reportAckErrorMock as ApiResponse<ReportAckResponse>;
    }
    return submitAck(reportId, payload, ['FAMILY'], true, 'mock-16-archive-suggestions-decision');
  }

  return request<ReportAckResponse>({
    method: 'POST',
    url: archiveDecisionPath(reportId),
    data: payload,
    mock: reportAckMock as ApiResponse<ReportAckResponse>,
    mockFallback: true
  });
}
