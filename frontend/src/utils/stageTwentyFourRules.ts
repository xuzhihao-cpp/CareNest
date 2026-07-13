import type {
  HealthArchiveDecisionDraft,
  HealthArchiveFieldDecision,
  HealthArchiveFieldDecisionRequest,
  HealthArchiveChangeLogRecord,
  HealthReviewFieldDetail
} from '@/types/stageTwentyFour';
import type { HealthSuggestionFieldName } from '@/types/stageTwentyThree';
import {
  formatHealthSuggestionValue,
  healthSuggestionFieldLabel,
  isHealthSuggestionField
} from '@/utils/stageTwentyThreeRules';

export const HEALTH_ARCHIVE_DECISION_OPTIONS: Array<{
  value: HealthArchiveFieldDecision;
  label: string;
  help: string;
}> = [
  { value: 'APPROVE', label: '采纳并归档', help: '使用规范化后的内容更新正式健康档案。' },
  { value: 'REJECT', label: '不采纳', help: '保留当前档案内容，并记录不采纳原因。' },
  { value: 'NEED_MORE', label: '需要补充', help: '暂不修改档案，等待补充资料后再次审核。' }
];

export function isHealthArchiveFieldDecision(value: string): value is HealthArchiveFieldDecision {
  return value === 'APPROVE' || value === 'REJECT' || value === 'NEED_MORE';
}

export function buildArchiveDecisionDrafts(fields: HealthReviewFieldDetail[]): HealthArchiveDecisionDraft[] {
  return fields.map((field) => ({
    sourceField: field.sourceField,
    targetField: field.targetField,
    normalizedValue: field.normalizedValue,
    decision: '',
    comment: '',
    fieldLabel: field.fieldLabel
  }));
}

export function archiveDecisionCommentRequired(decision: HealthArchiveFieldDecision) {
  return decision === 'REJECT' || decision === 'NEED_MORE';
}

export function validateArchiveDecisions(decisions: HealthArchiveDecisionDraft[]) {
  if (!decisions.length) return '当前任务没有可处理的健康档案字段。';
  for (const item of decisions) {
    if (!item.sourceField.trim() || !isHealthSuggestionField(item.targetField)) {
      return '审核字段信息不完整，请刷新任务后重试。';
    }
    if (!isHealthArchiveFieldDecision(item.decision)) return '请选择每一项建议的处理决定。';
    const comment = item.comment.trim();
    if (archiveDecisionCommentRequired(item.decision) && comment.length < 5) {
      return `${item.fieldLabel}选择不采纳或需要补充时，请填写至少 5 个字的审核说明。`;
    }
    if (comment.length > 255) return `${item.fieldLabel}的审核说明不能超过 255 个字。`;
  }
  return '';
}

export function toArchiveDecisionRequests(
  decisions: HealthArchiveDecisionDraft[]
): HealthArchiveFieldDecisionRequest[] {
  return decisions.map(({ fieldLabel: _fieldLabel, ...item }) => ({
    ...item,
    decision: item.decision as HealthArchiveFieldDecision,
    comment: item.comment.trim()
  }));
}

function parseStoredValue(value: unknown): unknown {
  if (typeof value !== 'string') return value;
  const text = value.trim();
  if (!text || (!text.startsWith('{') && !text.startsWith('['))) return value;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return value;
  }
}

export function formatArchiveChangeValue(fieldName: HealthSuggestionFieldName | '', value: unknown) {
  const parsed = parseStoredValue(value);
  if (fieldName) return formatHealthSuggestionValue(fieldName, parsed);
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return typeof parsed === 'string' && parsed.trim() ? parsed : '暂无记录';
  }
  const archive = parsed as Record<string, unknown>;
  const summaries = (['diseases', 'medications', 'allergies', 'riskTags', 'carePlan'] as const)
    .filter((key) => archive[key] !== undefined)
    .map((key) => `${healthSuggestionFieldLabel(key)}：${formatHealthSuggestionValue(key, archive[key])}`);
  return summaries.join('；') || '健康档案内容已更新';
}

export function changeLogBusinessTitle(record: Pick<HealthArchiveChangeLogRecord, 'fieldName' | 'fieldLabel'>) {
  return record.fieldLabel.trim()
    || (record.fieldName ? healthSuggestionFieldLabel(record.fieldName) : '')
    || '健康档案更新';
}
