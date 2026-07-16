import { failure, request } from '@/api/client';
import { getQualificationTrainingOverview } from '@/api/stageTwentySix';
import type { ApiResponse } from '@/types/api';
import type { TrainingReviewRequest, TrainingReviewResult } from '@/types/stageTwentyEight';
import {
  normalizeTrainingReviewStatus,
  trainingDateTimeTimestamp
} from '@/utils/stageTwentyEightRules';

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

export const getCurrentTrainingStatus = getQualificationTrainingOverview;

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
