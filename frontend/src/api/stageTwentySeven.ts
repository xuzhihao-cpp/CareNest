import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  QualificationReviewRequest,
  QualificationReviewResult,
  QualificationReviewDecision
} from '@/types/stageTwentySeven';
import type { QualificationApplicationRecord } from '@/types/stageTwentySix';
import { getQualificationApplications } from '@/api/stageTwentySix';
import { normalizeQualificationAuditStatus } from '@/utils/stageTwentySixRules';

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

export async function reviewQualificationApplication(
  applicationId: string,
  payload: QualificationReviewRequest
): Promise<ApiResponse<QualificationReviewResult>> {
  const response = await request<unknown>({
    method: 'POST',
    url: `/admin/nurse-qualification-applications/${encodeURIComponent(applicationId)}/review`,
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as QualificationReviewResult };
  if (!isRecord(response.data)) {
    return failure(502, '资质审核响应不完整', {} as QualificationReviewResult, response.traceId);
  }
  const nurseId = typeof response.data.nurseId === 'string' ? response.data.nurseId.trim() : '';
  const qualificationStatus = normalizeQualificationAuditStatus(response.data.qualificationStatus);
  if (!nurseId || !qualificationStatus) {
    return failure(502, '资质审核响应不完整', {} as QualificationReviewResult, response.traceId);
  }
  return { ...response, data: { nurseId, qualificationStatus } };
}

export async function findReviewedQualificationApplication(
  applicationId: string,
  targetStatus: QualificationReviewDecision,
  isCurrent: () => boolean = () => true
): Promise<ApiResponse<QualificationApplicationRecord>> {
  const pageSize = 100;
  let page = 1;
  let totalPages = 1;
  do {
    if (!isCurrent()) {
      return failure(499, '审核结果读取已取消', {} as QualificationApplicationRecord, 'frontend-stage27-refresh');
    }
    const response = await getQualificationApplications({
      auditStatus: targetStatus,
      page,
      size: pageSize
    });
    if (!isCurrent()) {
      return failure(499, '审核结果读取已取消', {} as QualificationApplicationRecord, response.traceId);
    }
    if (response.code !== 0) {
      return { ...response, data: {} as QualificationApplicationRecord };
    }
    const record = response.data.records.find((item) => item.applicationId === applicationId);
    if (record) return { ...response, data: record };
    const effectiveSize = Math.max(1, response.data.size || pageSize);
    totalPages = Math.max(1, Math.ceil(response.data.total / effectiveSize));
    page += 1;
  } while (page <= totalPages);
  return failure(404, '未找到最新审核记录', {} as QualificationApplicationRecord, 'frontend-stage27-refresh');
}
