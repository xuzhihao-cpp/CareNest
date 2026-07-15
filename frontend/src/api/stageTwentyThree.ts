import { failure, request } from '@/api/client';
import type { ApiResponse, PageResult } from '@/types/api';
import type {
  AdminHealthReviewTaskPageResult,
  AdminHealthReviewTaskQuery,
  AdminHealthReviewTaskRecord,
  HealthReviewSourceType,
  HealthReviewTaskStatus,
  HealthSuggestionFieldName,
  HealthSuggestionSourceType,
  HealthSuggestionStatus,
  HealthUpdateSuggestionRequest,
  HealthUpdateSuggestionResult
} from '@/types/stageTwentyThree';
import {
  isHealthSuggestionField,
  isHealthReviewSourceType,
  normalizeHealthReviewStatus,
  normalizeHealthSuggestionStatus
} from '@/utils/stageTwentyThreeRules';

const suggestionPath = (orderId: string) =>
  `/orders/${encodeURIComponent(orderId)}/health-update-suggestions`;
const adminReviewTasksPath = '/admin/health-review-tasks';

interface HealthReviewPermissionWireResponse {
  permissions?: unknown;
}

interface SuggestionWireResult {
  suggestionId?: string;
  status?: string;
}

interface AdminHealthReviewTaskWire {
  taskId?: string;
  reviewTaskId?: string;
  suggestionId?: string;
  status?: string;
  elderName?: string;
  elder?: { displayName?: string; name?: string };
  serviceName?: string;
  orderServiceName?: string;
  sourceType?: string;
  sourceSummary?: string;
  fieldName?: string;
  currentValue?: unknown;
  originalValue?: unknown;
  suggestedValue?: unknown;
  newValue?: unknown;
  reason?: string;
  submittedAt?: string;
  createdAt?: string;
  suggestion?: Partial<AdminHealthReviewTaskWire>;
}

function emptyPage(query: AdminHealthReviewTaskQuery): AdminHealthReviewTaskPageResult {
  return { records: [], total: 0, page: query.page, size: query.size };
}

function cleanQuery(query: AdminHealthReviewTaskQuery) {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== ''));
}

function normalizeAdminTask(record: AdminHealthReviewTaskWire): AdminHealthReviewTaskRecord | null {
  const source = record.suggestion ?? record;
  const taskId = record.taskId?.trim() || record.reviewTaskId?.trim() || '';
  const suggestionId = source.suggestionId?.trim() || record.suggestionId?.trim() || '';
  const status = normalizeHealthReviewStatus(record.status ?? source.status ?? '');
  const fieldName = source.fieldName ?? '';
  const sourceType = source.sourceType ?? '';
  const submittedAt = record.submittedAt || source.submittedAt || record.createdAt || source.createdAt || '';
  if (!taskId || !status || !isHealthSuggestionField(fieldName)
    || !isHealthReviewSourceType(sourceType) || !submittedAt) return null;
  return {
    taskId,
    suggestionId: suggestionId || undefined,
    status,
    elderName: record.elderName?.trim() || record.elder?.displayName?.trim() || record.elder?.name?.trim() || '未命名长辈',
    serviceName: record.serviceName?.trim() || record.orderServiceName?.trim() || source.serviceName?.trim() || '上门护理服务',
    sourceType,
    sourceSummary: source.sourceSummary?.trim() || record.sourceSummary?.trim() || '',
    fieldName,
    currentValue: source.currentValue ?? source.originalValue ?? record.currentValue ?? record.originalValue ?? null,
    suggestedValue: source.suggestedValue ?? source.newValue ?? record.suggestedValue ?? record.newValue ?? null,
    reason: source.reason?.trim() || record.reason?.trim() || '未填写原因',
    submittedAt
  };
}

export async function getHealthReviewPermissions(): Promise<ApiResponse<string[]>> {
  const response = await request<HealthReviewPermissionWireResponse>({
    method: 'GET',
    url: '/auth/permissions'
  });
  if (response.code !== 0) return { ...response, data: [] };
  if (!response.data || !Array.isArray(response.data.permissions)
    || response.data.permissions.some((permission) => typeof permission !== 'string')) {
    return failure(502, '账号权限响应不完整', [], response.traceId);
  }
  return { ...response, data: response.data.permissions };
}

export async function createHealthUpdateSuggestion(
  orderId: string,
  payload: HealthUpdateSuggestionRequest
): Promise<ApiResponse<HealthUpdateSuggestionResult>> {
  const response = await request<SuggestionWireResult>({
    method: 'POST',
    url: suggestionPath(orderId),
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as HealthUpdateSuggestionResult };
  const status = normalizeHealthSuggestionStatus(response.data.status ?? '');
  if (!response.data.suggestionId?.trim() || !status) {
    return failure(502, '健康档案建议响应不完整', {} as HealthUpdateSuggestionResult, response.traceId);
  }
  return { ...response, data: { suggestionId: response.data.suggestionId, status } };
}

export async function getAdminHealthReviewTasks(
  query: AdminHealthReviewTaskQuery
): Promise<ApiResponse<AdminHealthReviewTaskPageResult>> {
  const response = await request<PageResult<AdminHealthReviewTaskWire>>({
    method: 'GET',
    url: adminReviewTasksPath,
    data: cleanQuery(query)
  });
  if (response.code !== 0) return { ...response, data: emptyPage(query) };
  if (!Array.isArray(response.data?.records)) {
    return failure(502, '健康档案审核任务列表响应不完整', emptyPage(query), response.traceId);
  }
  const records = response.data.records.map(normalizeAdminTask);
  if (records.some((record) => record === null)) {
    return failure(502, '健康档案审核任务内容不完整', emptyPage(query), response.traceId);
  }
  return {
    ...response,
    data: {
      records: records as AdminHealthReviewTaskRecord[],
      total: Number(response.data.total) || 0,
      page: Number(response.data.page) || query.page,
      size: Number(response.data.size) || query.size
    }
  };
}

export type {
  HealthReviewTaskStatus,
  HealthReviewSourceType,
  HealthSuggestionFieldName,
  HealthSuggestionSourceType,
  HealthSuggestionStatus
};
