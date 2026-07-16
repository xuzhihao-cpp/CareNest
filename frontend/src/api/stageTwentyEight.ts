import { failure, request } from '@/api/client';
import { getQualificationTrainingOverview } from '@/api/stageTwentySix';
import type { ApiResponse } from '@/types/api';
import type { QualificationTrainingOverview } from '@/types/stageTwentySix';
import type { TrainingReviewRequest, TrainingReviewResult } from '@/types/stageTwentyEight';
import {
  normalizeTrainingDisplayStatus,
  normalizeTrainingReviewStatus,
  trainingDateTimeTimestamp
} from '@/utils/stageTwentyEightRules';
import { normalizeQualificationAuditStatus } from '@/utils/stageTwentySixRules';

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

export const getCurrentTrainingStatus = getQualificationTrainingOverview;

export async function getAdminTrainingStatus(
  nurseId: string
): Promise<ApiResponse<QualificationTrainingOverview>> {
  const response = await request<unknown>({
    method: 'GET',
    url: `/admin/nurses/${encodeURIComponent(nurseId.trim())}/training-status`
  });
  if (response.code !== 0) return { ...response, data: {} as QualificationTrainingOverview };
  if (!isRecord(response.data)) {
    return failure(502, '培训状态响应不完整', {} as QualificationTrainingOverview, response.traceId);
  }
  const nurseName = typeof response.data.nurseName === 'string' ? response.data.nurseName.trim() : '';
  const qualificationStatus = normalizeQualificationAuditStatus(response.data.qualificationStatus);
  const trainingStatus = normalizeTrainingDisplayStatus(response.data.trainingStatus);
  const trainingBatch = typeof response.data.trainingBatch === 'string' ? response.data.trainingBatch.trim() : '';
  const passedAt = typeof response.data.passedAt === 'string' ? response.data.passedAt.trim() : '';
  const expiredAt = typeof response.data.expiredAt === 'string' ? response.data.expiredAt.trim() : '';
  if (!nurseName || !qualificationStatus
      || !trainingStatus || !trainingBatch) {
    return failure(502, '培训状态响应不完整', {} as QualificationTrainingOverview, response.traceId);
  }
  return {
    ...response,
    data: {
      nurseName,
      qualificationStatus,
      trainingStatus,
      trainingBatch,
      passedAt,
      expiredAt,
      remark: typeof response.data.remark === 'string' ? response.data.remark.trim() : ''
    }
  };
}

export async function reviewNurseTraining(
  nurseId: string,
  payload: TrainingReviewRequest
): Promise<ApiResponse<TrainingReviewResult>> {
  const response = await request<unknown>({
    method: 'POST',
    url: `/admin/nurses/${encodeURIComponent(nurseId)}/training-review`,
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as TrainingReviewResult };
  if (!isRecord(response.data)) {
    return failure(502, '培训审核响应不完整', {} as TrainingReviewResult, response.traceId);
  }
  const resultNurseId = typeof response.data.nurseId === 'string' ? response.data.nurseId.trim() : '';
  const trainingStatus = normalizeTrainingReviewStatus(response.data.trainingStatus);
  const expiredAt = typeof response.data.expiredAt === 'string' ? response.data.expiredAt.trim() : '';
  const expiryTime = expiredAt ? trainingDateTimeTimestamp(expiredAt) : Number.NaN;
  if (!resultNurseId || !trainingStatus
      || (trainingStatus === 'APPROVED' && (!Number.isFinite(expiryTime) || expiryTime <= Date.now()))) {
    return failure(502, '培训审核响应不完整', {} as TrainingReviewResult, response.traceId);
  }
  return { ...response, data: { nurseId: resultNurseId, trainingStatus, expiredAt } };
}
