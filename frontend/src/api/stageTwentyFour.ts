import { failure, request } from '@/api/client';
import type { ApiResponse, PageResult } from '@/types/api';
import type { ArchiveVersion } from '@/types/stageNineteen';
import type { HealthReviewSourceType } from '@/types/stageTwentyThree';
import type {
  HealthArchiveChangeLogRecord,
  HealthArchiveChangeLogResult,
  HealthReviewArchiveRequest,
  HealthReviewArchiveResult,
  HealthReviewFieldDetail,
  HealthReviewTaskDetail
} from '@/types/stageTwentyFour';
import {
  HEALTH_REVIEW_SOURCE_LABELS,
  healthSuggestionFieldLabel,
  isHealthReviewSourceType,
  isHealthSuggestionField,
  normalizeHealthReviewStatus
} from '@/utils/stageTwentyThreeRules';

const reviewTaskDetailPath = (taskId: string) =>
  `/admin/health-review-tasks/${encodeURIComponent(taskId)}`;
const archiveReviewTaskPath = (taskId: string) =>
  `/admin/health-review-tasks/${encodeURIComponent(taskId)}/archive`;
const archiveChangeLogsPath = (elderId: string) =>
  `/elders/${encodeURIComponent(elderId)}/health-archive/change-logs`;

interface HealthReviewFieldWire {
  sourceField?: string;
  targetField?: string;
  fieldName?: string;
  fieldLabel?: string;
  currentValue?: unknown;
  oldValue?: unknown;
  suggestedValue?: unknown;
  newValue?: unknown;
  normalizedValue?: unknown;
  normalizationNote?: string;
}

interface HealthReviewTaskDetailWire {
  taskId?: string;
  reviewTaskId?: string;
  status?: string;
  reviewStatus?: string;
  elderId?: string;
  elderName?: string;
  elder?: { displayName?: string; name?: string };
  serviceName?: string;
  orderServiceName?: string;
  submittedAt?: string;
  createdAt?: string;
  archiveVersion?: ArchiveVersion;
  sourceType?: string;
  sourceTitle?: string;
  sourceSummary?: string;
  sourceOccurredAt?: string;
  evidence?: {
    sourceType?: string;
    title?: string;
    summary?: string;
    occurredAt?: string;
  };
  fields?: HealthReviewFieldWire[];
  reviewItems?: HealthReviewFieldWire[];
}

interface HealthReviewArchiveResultWire {
  taskId?: string;
  reviewTaskId?: string;
  status?: string;
  reviewStatus?: string;
  archiveVersion?: ArchiveVersion;
}

interface HealthArchiveChangeLogWire {
  changeLogId?: string;
  fieldName?: string;
  targetField?: string;
  fieldLabel?: string;
  changeType?: string;
  beforeValue?: unknown;
  oldValue?: unknown;
  afterValue?: unknown;
  newValue?: unknown;
  sourceType?: string;
  sourceSummary?: string;
  comment?: string;
  reviewComment?: string;
  archiveVersion?: ArchiveVersion;
  changedAt?: string;
  createdAt?: string;
}

function normalizeField(item: HealthReviewFieldWire): HealthReviewFieldDetail | null {
  const targetField = item.targetField || item.fieldName || '';
  const sourceField = item.sourceField?.trim() || item.fieldName?.trim() || '';
  const hasNormalizedValue = Object.prototype.hasOwnProperty.call(item, 'normalizedValue');
  if (!sourceField || !isHealthSuggestionField(targetField) || !hasNormalizedValue) return null;
  return {
    sourceField,
    targetField,
    fieldLabel: item.fieldLabel?.trim() || healthSuggestionFieldLabel(targetField),
    currentValue: item.currentValue ?? item.oldValue ?? null,
    suggestedValue: item.suggestedValue ?? item.newValue ?? null,
    normalizedValue: item.normalizedValue,
    normalizationNote: item.normalizationNote?.trim() || ''
  };
}

function normalizeTaskDetail(value: HealthReviewTaskDetailWire): HealthReviewTaskDetail | null {
  const taskId = value.taskId?.trim() || value.reviewTaskId?.trim() || '';
  const status = normalizeHealthReviewStatus(value.status || value.reviewStatus || '');
  const sourceType = value.evidence?.sourceType || value.sourceType || '';
  const fields = (value.fields ?? value.reviewItems ?? []).map(normalizeField);
  const submittedAt = value.submittedAt || value.createdAt || '';
  if (!taskId || !status || !value.elderId?.trim() || !isHealthReviewSourceType(sourceType)
    || !submittedAt || value.archiveVersion === undefined || !fields.length || fields.some((item) => item === null)) {
    return null;
  }
  return {
    taskId,
    status,
    elderId: value.elderId,
    elderName: value.elderName?.trim() || value.elder?.displayName?.trim() || value.elder?.name?.trim() || '未命名长辈',
    serviceName: value.serviceName?.trim() || value.orderServiceName?.trim() || '健康档案审核',
    submittedAt,
    archiveVersion: value.archiveVersion,
    evidence: {
      sourceType,
      title: value.evidence?.title?.trim() || value.sourceTitle?.trim() || HEALTH_REVIEW_SOURCE_LABELS[sourceType],
      summary: value.evidence?.summary?.trim() || value.sourceSummary?.trim() || '暂无来源摘要',
      occurredAt: value.evidence?.occurredAt || value.sourceOccurredAt || ''
    },
    fields: fields as HealthReviewFieldDetail[]
  };
}

function normalizeChangeLog(value: HealthArchiveChangeLogWire): HealthArchiveChangeLogRecord | null {
  const changeLogId = value.changeLogId?.trim() || '';
  const fieldCandidate = value.fieldName || value.targetField || '';
  const fieldName = isHealthSuggestionField(fieldCandidate) ? fieldCandidate : '';
  const changedAt = value.changedAt || value.createdAt || '';
  const sourceType = value.sourceType && isHealthReviewSourceType(value.sourceType)
    ? value.sourceType as HealthReviewSourceType
    : undefined;
  if (!changeLogId || !changedAt) return null;
  return {
    changeLogId,
    fieldName,
    fieldLabel: value.fieldLabel?.trim()
      || (fieldName ? healthSuggestionFieldLabel(fieldName) : '')
      || (value.changeType === 'PROFILE_UPDATE' ? '健康档案维护' : '健康档案更新'),
    beforeValue: value.beforeValue ?? value.oldValue ?? null,
    afterValue: value.afterValue ?? value.newValue ?? null,
    sourceType,
    sourceSummary: value.sourceSummary?.trim() || '',
    comment: value.comment?.trim() || value.reviewComment?.trim() || '',
    archiveVersion: value.archiveVersion,
    changedAt
  };
}

export async function getHealthReviewTaskDetail(
  taskId: string
): Promise<ApiResponse<HealthReviewTaskDetail>> {
  const response = await request<HealthReviewTaskDetailWire>({
    method: 'GET',
    url: reviewTaskDetailPath(taskId)
  });
  if (response.code !== 0) return { ...response, data: {} as HealthReviewTaskDetail };
  const detail = normalizeTaskDetail(response.data);
  return detail
    ? { ...response, data: detail }
    : failure(502, '健康信息审核详情响应不完整', {} as HealthReviewTaskDetail, response.traceId);
}

export async function archiveHealthReviewTask(
  taskId: string,
  payload: HealthReviewArchiveRequest
): Promise<ApiResponse<HealthReviewArchiveResult>> {
  const response = await request<HealthReviewArchiveResultWire>({
    method: 'POST',
    url: archiveReviewTaskPath(taskId),
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as HealthReviewArchiveResult };
  const responseTaskId = response.data.taskId?.trim() || response.data.reviewTaskId?.trim() || '';
  const status = normalizeHealthReviewStatus(response.data.status || response.data.reviewStatus || '');
  if (!responseTaskId || !status || response.data.archiveVersion === undefined) {
    return failure(502, '健康档案归档响应不完整', {} as HealthReviewArchiveResult, response.traceId);
  }
  return { ...response, data: { taskId: responseTaskId, status, archiveVersion: response.data.archiveVersion } };
}

export async function getHealthArchiveChangeLogs(
  elderId: string
): Promise<ApiResponse<HealthArchiveChangeLogResult>> {
  const response = await request<HealthArchiveChangeLogWire[] | PageResult<HealthArchiveChangeLogWire>>({
    method: 'GET',
    url: archiveChangeLogsPath(elderId)
  });
  if (response.code !== 0) return { ...response, data: { records: [] } };
  const wireRecords = Array.isArray(response.data) ? response.data : response.data?.records;
  if (!Array.isArray(wireRecords)) {
    return failure(502, '健康档案变更记录响应不完整', { records: [] }, response.traceId);
  }
  const records = wireRecords.map(normalizeChangeLog);
  if (records.some((item) => item === null)) {
    return failure(502, '健康档案变更记录内容不完整', { records: [] }, response.traceId);
  }
  return { ...response, data: { records: records as HealthArchiveChangeLogRecord[] } };
}
