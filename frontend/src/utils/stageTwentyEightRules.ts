import type { QualificationAuditStatus, QualificationPermissionContext } from '@/types/stageTwentySix';
import type { TrainingDisplayStatus, TrainingReviewStatus } from '@/types/stageTwentyEight';

export const TRAINING_BATCH_MAX_LENGTH = 64;
export const TRAINING_REMARK_MAX_LENGTH = 500;
export const TRAINING_TIME_ZONE = 'Asia/Shanghai';

const trainingStatusLabels: Record<TrainingDisplayStatus, string> = {
  PENDING: '待审核',
  APPROVED: '培训有效',
  REJECTED: '未通过',
  NEED_MORE: '需补充',
  EXPIRED: '培训已过期'
};

export function normalizeTrainingReviewStatus(value: unknown): TrainingReviewStatus | null {
  return value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED' || value === 'NEED_MORE'
    ? value
    : null;
}

export function normalizeTrainingDisplayStatus(value: unknown): TrainingDisplayStatus | null {
  return value === 'EXPIRED' ? value : normalizeTrainingReviewStatus(value);
}

export function trainingDateTimeTimestamp(value: string) {
  const normalized = value.trim().replace(' ', 'T');
  if (!normalized) return Number.NaN;
  const hasTimeZone = /(?:Z|[+-]\d{2}:\d{2})$/i.test(normalized);
  const shanghaiValue = hasTimeZone ? normalized : `${normalized}+08:00`;
  return Date.parse(shanghaiValue);
}

export function effectiveTrainingDisplayStatus(
  status: unknown,
  expiredAt: string,
  now: Date = new Date()
): TrainingDisplayStatus | null {
  const normalized = normalizeTrainingDisplayStatus(status);
  if (!normalized) return null;
  if (normalized === 'EXPIRED') return normalized;
  if (normalized !== 'APPROVED' || !expiredAt) return normalized;
  const expiryTime = trainingDateTimeTimestamp(expiredAt);
  return Number.isFinite(expiryTime) && expiryTime <= now.getTime()
    ? 'EXPIRED'
    : normalized;
}

export function trainingStatusLabel(status: TrainingDisplayStatus) {
  return trainingStatusLabels[status];
}

export function canReviewTrainingByPermission(context: QualificationPermissionContext) {
  return (context.roleCode === 'ADMIN' || context.roleCode === 'CUSTOMER_SERVICE')
    && context.permissions.includes('NURSE_TRAINING_REVIEW');
}

export function localDateValue(now: Date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: TRAINING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  const year = values.year;
  const month = values.month;
  const day = values.day;
  return `${year}-${month}-${day}`;
}

export function combineTrainingExpiry(date: string, time: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) return '';
  const [year, month, day] = date.split('-').map(Number);
  const [hour, minute] = time.split(':').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day, hour, minute, 0, 0));
  if (parsed.getUTCFullYear() !== year || parsed.getUTCMonth() !== month - 1
      || parsed.getUTCDate() !== day || parsed.getUTCHours() !== hour || parsed.getUTCMinutes() !== minute) {
    return '';
  }
  return `${date}T${time}:00+08:00`;
}

export function expiryFieldsForTrainingStatus(
  status: TrainingReviewStatus,
  currentDate: string,
  currentTime: string
) {
  return status === 'APPROVED'
    ? { expiryDate: currentDate, expiryTime: currentTime }
    : { expiryDate: '', expiryTime: '' };
}

export function validateTrainingReview(input: {
  qualificationStatus: QualificationAuditStatus;
  status: TrainingReviewStatus | '';
  trainingBatch: string;
  expiredAt: string;
  remark: string;
  now?: Date;
}) {
  if (input.qualificationStatus !== 'APPROVED') return '只有资质已通过的护理人员可以进行培训审核。';
  if (!input.status) return '请选择培训审核结果。';
  const batch = input.trainingBatch.trim();
  if (!batch) return '请填写培训批次。';
  if (batch.length > TRAINING_BATCH_MAX_LENGTH) return `培训批次不能超过 ${TRAINING_BATCH_MAX_LENGTH} 个字符。`;
  const remark = input.remark.trim();
  if ((input.status === 'REJECTED' || input.status === 'NEED_MORE') && !remark) {
    return input.status === 'REJECTED' ? '培训未通过时必须填写原因。' : '要求补充时必须填写具体说明。';
  }
  if (remark.length > TRAINING_REMARK_MAX_LENGTH) return `审核说明不能超过 ${TRAINING_REMARK_MAX_LENGTH} 个字符。`;
  if (input.status === 'APPROVED') {
    if (!input.expiredAt) return '培训通过时必须选择有效期。';
    const expiryTime = trainingDateTimeTimestamp(input.expiredAt);
    if (!Number.isFinite(expiryTime) || expiryTime <= (input.now ?? new Date()).getTime()) {
      return '培训有效期必须晚于当前时间。';
    }
  }
  return '';
}

export function canAcceptFormalOrders(input: {
  qualificationStatus: QualificationAuditStatus;
  trainingStatus: unknown;
  expiredAt: string;
  now?: Date;
}) {
  return input.qualificationStatus === 'APPROVED'
    && effectiveTrainingDisplayStatus(input.trainingStatus, input.expiredAt, input.now) === 'APPROVED';
}

export function trainingReviewErrorMessage(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有审核培训资格的权限。';
  if (code === 404) return '该护理人员或资质记录已不存在，请刷新列表。';
  if (code === 409) return '培训资格已被其他审核人员更新，请刷新后确认。';
  if (code === 422) return '培训审核内容不符合要求，请检查资质状态、有效期和审核说明。';
  if (code === 502) return '培训审核响应内容不完整，请刷新后确认最终状态。';
  return '培训资格暂时无法提交，请稍后重试。';
}
