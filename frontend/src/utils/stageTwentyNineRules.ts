import type { NurseRecommendationRequest } from '@/types/stageTwentyNine';
import { trainingDateTimeTimestamp } from '@/utils/stageTwentyEightRules';

const technicalCodePattern = /\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/;

export function normalizeRecommendationScheduledStart(value: string) {
  const normalized = value.trim().replace(' ', 'T');
  if (!normalized) return '';
  return /(?:Z|[+-]\d{2}:\d{2})$/i.test(normalized)
    ? normalized
    : `${normalized.length === 16 ? `${normalized}:00` : normalized}+08:00`;
}

export function recommendationConditionsComplete(
  input: NurseRecommendationRequest,
  now: Date = new Date()
) {
  const scheduledTime = trainingDateTimeTimestamp(
    normalizeRecommendationScheduledStart(input.scheduledStart)
  );
  return Boolean(
    input.elderId.trim()
    && input.serviceId.trim()
    && input.addressId.trim()
    && Number.isFinite(scheduledTime)
    && scheduledTime > now.getTime()
  );
}

export function recommendationConditionKey(input: NurseRecommendationRequest) {
  if (!recommendationConditionsComplete(input)) return '';
  return [
    input.elderId.trim(),
    input.serviceId.trim(),
    input.addressId.trim(),
    normalizeRecommendationScheduledStart(input.scheduledStart)
  ].join('|');
}

export function recommendationReasonIsBusinessReadable(reason: string) {
  const normalized = reason.trim();
  return Boolean(normalized)
    && /\p{Script=Han}/u.test(normalized)
    && !technicalCodePattern.test(normalized);
}

export function recommendationScoreText(score: number) {
  return Number.isInteger(score) ? String(score) : score.toFixed(1);
}

export function recommendationErrorMessage(code: number, mode: 'conditions' | 'order') {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) {
    return mode === 'order'
      ? '当前账号无权查看这笔订单的护理推荐。'
      : '当前绑定未获得预约服务权限，无法获取护理推荐。';
  }
  if (code === 404) return mode === 'order' ? '订单不存在或已发生变化。' : '预约所需资料不存在，请重新选择。';
  if (code === 409) return '预约条件已发生变化，请重新获取护理推荐。';
  if (code === 422) return '预约条件不符合要求，请检查服务对象、服务、地址和时间。';
  if (code === 502) return '护理推荐内容不完整，请稍后重试。';
  return '护理推荐服务暂时不可用，请稍后重试。';
}

export function recommendationSkillDictionaryErrorMessage(code: number) {
  return code === 0
    ? ''
    : '护理技能名称暂时无法读取，推荐结果仍可查看，请稍后重新读取。';
}
