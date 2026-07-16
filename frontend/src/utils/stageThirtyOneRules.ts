import type {
  AttentionNoticeGroup,
  AttentionNoticeLevel,
  AttentionNoticeRecord,
  AttentionNoticeSource
} from '@/types/stageThirtyOne';

export const ATTENTION_LEVEL_LABELS: Record<AttentionNoticeLevel, string> = {
  INFO: '提示',
  WARNING: '重点',
  CRITICAL: '高风险'
};

export const ATTENTION_SOURCE_LABELS: Record<AttentionNoticeSource, string> = {
  HEALTH_ARCHIVE: '健康档案',
  MEDICAL_FILE: '审核通过的病历资料',
  SERVICE_ITEM: '本次服务要求',
  ORDER_CONTEXT: '本次服务安排'
};

const levelOrder: AttentionNoticeLevel[] = ['CRITICAL', 'WARNING', 'INFO'];

export function formatShanghaiDateTime(value: string) {
  if (!value.trim()) return '';
  const normalized = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value.trim())
    ? value.trim()
    : `${value.trim()}+08:00`;
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return '时间待同步';
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date);
  const read = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? '';
  return `${read('year')}-${read('month')}-${read('day')} ${read('hour')}:${read('minute')}`;
}

export function groupAttentionNotices(items: AttentionNoticeRecord[]): AttentionNoticeGroup[] {
  return levelOrder
    .map((level) => ({
      level,
      label: ATTENTION_LEVEL_LABELS[level],
      items: items.filter((item) => item.level === level)
    }))
    .filter((group) => group.items.length > 0);
}

export function pendingRequiredNotices(items: AttentionNoticeRecord[]) {
  return items.filter((item) => item.requiredAck && !item.acknowledged);
}

export function selectedPendingNoticeIds(items: AttentionNoticeRecord[], selectedIds: string[]) {
  const pendingIds = new Set(pendingRequiredNotices(items).map((item) => item.noticeId));
  return Array.from(new Set(selectedIds.filter((noticeId) => pendingIds.has(noticeId))));
}

export function canStartServiceAfterAttention(input: {
  loaded: boolean;
  hasReadError: boolean;
  taskStatus: string;
  items: AttentionNoticeRecord[];
}) {
  return input.loaded
    && !input.hasReadError
    && input.taskStatus === 'ON_THE_WAY'
    && pendingRequiredNotices(input.items).length === 0;
}

export function attentionNoticeErrorMessage(code: number, action: 'read' | 'ack') {
  if (code === 401) return '登录状态已失效，请重新登录后再试。';
  if (code === 403) return action === 'ack'
    ? '当前账号无权确认这笔任务的服务前注意事项。'
    : '当前账号无权查看这笔任务的服务前注意事项。';
  if (code === 404) return '当前任务或服务前注意事项不存在，请刷新任务后重试。';
  if (code === 409) return '任务状态已经变化，正在重新读取任务和注意事项。';
  if (code === 422) return action === 'ack'
    ? '所选注意事项已失效或不属于当前任务，请刷新后重新确认。'
    : '当前任务暂不满足读取服务前注意事项的条件。';
  if (code === 502) return '服务前注意事项内容不完整，请联系平台维护人员。';
  return action === 'ack'
    ? '服务前注意事项暂时无法确认，请稍后重试。'
    : '服务前注意事项暂时无法读取，请稍后重试。';
}
