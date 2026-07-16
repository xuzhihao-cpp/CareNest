import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  NurseRecommendationRecord,
  NurseRecommendationRequest,
  NurseRecommendationResult
} from '@/types/stageTwentyNine';
import {
  normalizeRecommendationScheduledStart,
  recommendationConditionsComplete,
  recommendationReasonIsBusinessReadable
} from '@/utils/stageTwentyNineRules';

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function normalizeRecommendation(value: unknown): NurseRecommendationRecord | null {
  if (!isRecord(value)) return null;
  const nurseId = typeof value.nurseId === 'string' ? value.nurseId.trim() : '';
  const nurseName = typeof value.nurseName === 'string' ? value.nurseName.trim() : '';
  const score = typeof value.score === 'number' ? value.score : Number(value.score);
  const recommendReason = typeof value.recommendReason === 'string' ? value.recommendReason.trim() : '';
  const matchedSkills = Array.isArray(value.matchedSkills)
    && value.matchedSkills.every((item) => typeof item === 'string' && item.trim())
    ? Array.from(new Set((value.matchedSkills as string[]).map((item) => item.trim())))
    : null;
  if (!nurseId || !nurseName || !Number.isFinite(score) || score < 0 || score > 100
      || !matchedSkills || !recommendationReasonIsBusinessReadable(recommendReason)
      || typeof value.available !== 'boolean') {
    return null;
  }
  return { nurseId, nurseName, score, matchedSkills, recommendReason, available: value.available };
}

function normalizeRecommendationResult(
  response: ApiResponse<unknown>
): ApiResponse<NurseRecommendationResult> {
  if (response.code !== 0) return { ...response, data: { nurses: [] } };
  if (!isRecord(response.data) || !Array.isArray(response.data.nurses)) {
    return failure(502, '护理推荐响应不完整', { nurses: [] }, response.traceId);
  }
  const nurses = response.data.nurses.map(normalizeRecommendation);
  const nurseIds = nurses.map((item) => item?.nurseId ?? '');
  if (nurses.some((item) => !item) || new Set(nurseIds).size !== nurseIds.length) {
    return failure(502, '护理推荐响应不完整', { nurses: [] }, response.traceId);
  }
  return { ...response, data: { nurses: nurses as NurseRecommendationRecord[] } };
}

export async function recommendNurses(
  input: NurseRecommendationRequest
): Promise<ApiResponse<NurseRecommendationResult>> {
  if (!recommendationConditionsComplete(input)) {
    return failure(422, '预约条件不完整', { nurses: [] }, 'frontend-stage-29-validation');
  }
  const response = await request<unknown>({
    method: 'POST',
    url: '/orders/recommend-nurses',
    data: {
      elderId: input.elderId.trim(),
      serviceId: input.serviceId.trim(),
      addressId: input.addressId.trim(),
      scheduledStart: normalizeRecommendationScheduledStart(input.scheduledStart)
    }
  });
  return normalizeRecommendationResult(response);
}

export async function getOrderRecommendations(
  orderId: string
): Promise<ApiResponse<NurseRecommendationResult>> {
  const normalizedOrderId = orderId.trim();
  if (!normalizedOrderId) {
    return failure(422, '订单信息不完整', { nurses: [] }, 'frontend-stage-29-validation');
  }
  const response = await request<unknown>({
    method: 'GET',
    url: `/orders/${encodeURIComponent(normalizedOrderId)}/recommendations`
  });
  return normalizeRecommendationResult(response);
}
