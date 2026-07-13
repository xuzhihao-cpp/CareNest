import type {
  AllergyItem,
  CarePlanContent,
  ChronicDiseaseItem,
  HealthRiskTag,
  MedicationItem
} from '@/types/stageNineteen';
import type {
  HealthReviewTaskStatus,
  HealthReviewSourceType,
  HealthSuggestionFieldName,
  HealthSuggestionNewValue,
  HealthSuggestionSourceType,
  HealthSuggestionStatus,
  HealthUpdateSuggestionRequest
} from '@/types/stageTwentyThree';

export const HEALTH_REVIEW_PERMISSION_ALIASES = [
  'HEALTH_REVIEW',
  'HEALTH_ARCHIVE_REVIEW',
  'health:review'
] as const;

export const HEALTH_SUGGESTION_FIELD_OPTIONS: Array<{
  value: HealthSuggestionFieldName;
  label: string;
  help: string;
}> = [
  { value: 'diseases', label: '慢性病情况', help: '建议新增或更新一种疾病的诊断和状态。' },
  { value: 'medications', label: '当前用药', help: '建议新增或更新一种药物的用法。' },
  { value: 'allergies', label: '过敏记录', help: '建议补充过敏原、反应和严重程度。' },
  { value: 'riskTags', label: '健康风险', help: '建议增加需要持续关注的风险。' },
  { value: 'carePlan', label: '照护计划', help: '建议更新照护目标、日常护理和注意事项。' }
];

export const HEALTH_SUGGESTION_SOURCE_LABELS: Record<HealthSuggestionSourceType, string> = {
  SERVICE_RECORD: '服务记录',
  SERVICE_REPORT: '服务报告'
};

export const HEALTH_REVIEW_SOURCE_LABELS: Record<HealthReviewSourceType, string> = {
  ...HEALTH_SUGGESTION_SOURCE_LABELS,
  MEDICAL_FILE: '病历资料',
  REPORT_ACK: '报告确认',
  SUGGESTION: '护理建议',
  MANUAL: '人工复核'
};

export const HEALTH_REVIEW_STATUS_LABELS: Record<HealthReviewTaskStatus, string> = {
  PENDING: '待审核',
  IN_REVIEW: '审核中',
  ARCHIVED: '已归档',
  REJECTED: '未采纳'
};

export const HEALTH_SUGGESTION_STATUS_LABELS: Record<HealthSuggestionStatus, string> = {
  PENDING: '待审核',
  APPROVED: '已通过，待归档',
  REJECTED: '未采纳',
  ARCHIVED: '已归档'
};

const diseaseStatusLabels: Record<string, string> = {
  ACTIVE: '治疗中', MONITORING: '持续观察', STABLE: '情况稳定', RESOLVED: '已恢复'
};
const medicationFrequencyLabels: Record<string, string> = {
  ONCE_DAILY: '每日一次', TWICE_DAILY: '每日两次', THREE_TIMES_DAILY: '每日三次',
  EVERY_OTHER_DAY: '隔日一次', WEEKLY: '每周一次', AS_NEEDED: '按需使用'
};
const allergySeverityLabels: Record<string, string> = {
  MILD: '轻度', MODERATE: '中度', SEVERE: '重度'
};

export function normalizeHealthReviewStatus(value: string): HealthReviewTaskStatus | null {
  if (value === 'PENDING' || value === 'PENDING_REVIEW') return 'PENDING';
  if (value === 'IN_REVIEW' || value === 'REVIEWING') return 'IN_REVIEW';
  if (value === 'ARCHIVED') return 'ARCHIVED';
  if (value === 'REJECTED') return 'REJECTED';
  return null;
}

export function normalizeHealthSuggestionStatus(value: string): HealthSuggestionStatus | null {
  if (value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED' || value === 'ARCHIVED') return value;
  return null;
}

export function isHealthSuggestionField(value: string): value is HealthSuggestionFieldName {
  return HEALTH_SUGGESTION_FIELD_OPTIONS.some((option) => option.value === value);
}

export function isHealthSuggestionSourceType(value: string): value is HealthSuggestionSourceType {
  return value === 'SERVICE_RECORD' || value === 'SERVICE_REPORT';
}

export function isHealthReviewSourceType(value: string): value is HealthReviewSourceType {
  return value === 'SERVICE_RECORD' || value === 'SERVICE_REPORT' || value === 'MEDICAL_FILE'
    || value === 'REPORT_ACK' || value === 'SUGGESTION' || value === 'MANUAL';
}

export function canViewHealthReviewTasks(roles: string[], permissionCodes: string[]) {
  return (roles.includes('ADMIN') || roles.includes('CUSTOMER_SERVICE'))
    && HEALTH_REVIEW_PERMISSION_ALIASES.some((code) => permissionCodes.includes(code));
}

export function healthSuggestionSourceLoadError(
  recordResponse: { code: number; message?: string },
  reportResponse: { code: number; message?: string }
) {
  return [
    recordResponse.code !== 0 && recordResponse.code !== 404
      ? `服务记录读取失败：${recordResponse.message || '服务暂不可用'}`
      : '',
    reportResponse.code !== 0 && reportResponse.code !== 404
      ? `服务报告读取失败：${reportResponse.message || '服务暂不可用'}`
      : ''
  ].filter(Boolean).join('；');
}

function nonEmpty(value: unknown) {
  return typeof value === 'string' && Boolean(value.trim());
}

function validDate(value?: string) {
  if (!value) return true;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}

function validateDisease(value: ChronicDiseaseItem) {
  if (!nonEmpty(value.diseaseName)) return '请填写疾病名称。';
  if (!['ACTIVE', 'MONITORING', 'STABLE', 'RESOLVED'].includes(value.status)) return '请选择疾病状态。';
  if (!validDate(value.diagnosedAt)) return '请选择有效的确诊日期。';
  return '';
}

function validateMedication(value: MedicationItem) {
  if (!nonEmpty(value.medicationName)) return '请填写药物名称。';
  if (!['ONCE_DAILY', 'TWICE_DAILY', 'THREE_TIMES_DAILY', 'EVERY_OTHER_DAY', 'WEEKLY', 'AS_NEEDED'].includes(value.frequency)) {
    return '请选择用药频次。';
  }
  if (!validDate(value.startDate) || !value.startDate) return '请选择有效的开始用药日期。';
  if (!validDate(value.endDate)) return '请选择有效的结束用药日期。';
  if (value.endDate && value.endDate < value.startDate) return '结束用药日期不能早于开始日期。';
  if (value.timePoints.some((time) => !/^([01]\d|2[0-3]):[0-5]\d$/.test(time))) return '请选择有效的用药时间。';
  return '';
}

function validateAllergy(value: AllergyItem) {
  if (!nonEmpty(value.allergenName)) return '请填写过敏原。';
  if (!['MILD', 'MODERATE', 'SEVERE'].includes(value.severity)) return '请选择过敏严重程度。';
  return '';
}

function validateRisk(value: HealthRiskTag) {
  return nonEmpty(value.tagCode) && nonEmpty(value.tagName) ? '' : '请选择一项健康风险。';
}

function validateCarePlan(value: CarePlanContent) {
  if (!nonEmpty(value.careGoals)) return '请填写照护目标。';
  if (!nonEmpty(value.dailyCare)) return '请填写日常护理建议。';
  if (!nonEmpty(value.precautions)) return '请填写注意事项。';
  return '';
}

export function validateHealthSuggestion(payload: HealthUpdateSuggestionRequest) {
  if (!payload.sourceId.trim()) return '请选择建议所依据的服务记录或报告。';
  if (!isHealthSuggestionSourceType(payload.sourceType)) return '建议来源不正确，请重新选择。';
  if (!isHealthSuggestionField(payload.fieldName)) return '请选择需要建议更新的档案内容。';
  const reason = payload.reason.trim();
  if (reason.length < 5) return '请用至少 5 个字说明提出建议的原因。';
  if (reason.length > 255) return '建议原因不能超过 255 个字。';

  if (payload.fieldName === 'diseases') return validateDisease(payload.newValue as ChronicDiseaseItem);
  if (payload.fieldName === 'medications') return validateMedication(payload.newValue as MedicationItem);
  if (payload.fieldName === 'allergies') return validateAllergy(payload.newValue as AllergyItem);
  if (payload.fieldName === 'riskTags') return validateRisk(payload.newValue as HealthRiskTag);
  return validateCarePlan(payload.newValue as CarePlanContent);
}

export function healthSuggestionFieldLabel(value: HealthSuggestionFieldName) {
  return HEALTH_SUGGESTION_FIELD_OPTIONS.find((option) => option.value === value)?.label ?? '健康档案内容';
}

function joinParts(parts: Array<string | undefined>) {
  return parts.filter((part) => part && part.trim()).join('；') || '暂无内容';
}

export function formatHealthSuggestionValue(fieldName: HealthSuggestionFieldName, value: unknown): string {
  if (value === null || value === undefined || value === '') return '暂无记录';
  if (typeof value === 'string') {
    const text = value.trim();
    if (!text) return '暂无记录';
    if (text.startsWith('{') || text.startsWith('[')) {
      try {
        return formatHealthSuggestionValue(fieldName, JSON.parse(text) as unknown);
      } catch {
        return text;
      }
    }
    return text;
  }
  if (Array.isArray(value)) return value.length
    ? value.map((item) => formatHealthSuggestionValue(fieldName, item)).join('；')
    : '暂无记录';
  if (typeof value !== 'object') return String(value);

  if (fieldName === 'diseases') {
    const item = value as Partial<ChronicDiseaseItem>;
    return joinParts([item.diseaseName, item.status ? diseaseStatusLabels[item.status] ?? item.status : undefined,
      item.diagnosedAt ? `确诊于 ${item.diagnosedAt}` : undefined, item.remark]);
  }
  if (fieldName === 'medications') {
    const item = value as Partial<MedicationItem>;
    return joinParts([item.medicationName, item.dosage, item.frequency ? medicationFrequencyLabels[item.frequency] ?? item.frequency : undefined,
      item.timePoints?.length ? `用药时间 ${item.timePoints.join('、')}` : undefined,
      item.startDate ? `自 ${item.startDate} 起` : undefined, item.endDate ? `至 ${item.endDate}` : undefined, item.remark]);
  }
  if (fieldName === 'allergies') {
    const item = value as Partial<AllergyItem>;
    return joinParts([item.allergenName, item.reaction, item.severity ? allergySeverityLabels[item.severity] ?? item.severity : undefined, item.remark]);
  }
  if (fieldName === 'riskTags') {
    const item = value as Partial<HealthRiskTag>;
    return item.tagName?.trim() || '暂无风险名称';
  }
  const item = value as Partial<CarePlanContent>;
  return joinParts([
    item.careGoals ? `照护目标：${item.careGoals}` : undefined,
    item.dailyCare ? `日常护理：${item.dailyCare}` : undefined,
    item.precautions ? `注意事项：${item.precautions}` : undefined
  ]);
}

export function cloneSuggestionValue(value: HealthSuggestionNewValue): HealthSuggestionNewValue {
  return JSON.parse(JSON.stringify(value)) as HealthSuggestionNewValue;
}
