import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  AttentionNoticeAckRequest,
  AttentionNoticeLevel,
  AttentionNoticeRecord,
  AttentionNoticeResult,
  AttentionNoticeSource
} from '@/types/stageThirtyOne';

const levels = new Set<AttentionNoticeLevel>(['INFO', 'WARNING', 'CRITICAL']);
const sources = new Set<AttentionNoticeSource>([
  'HEALTH_ARCHIVE',
  'MEDICAL_FILE',
  'SERVICE_ITEM',
  'ORDER_CONTEXT'
]);
const emptyResult: AttentionNoticeResult = { items: [] };

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function normalizedNotice(value: unknown): AttentionNoticeRecord | null {
  if (!isRecord(value)) return null;
  const noticeId = typeof value.noticeId === 'string' ? value.noticeId.trim() : '';
  const content = typeof value.content === 'string' ? value.content.trim() : '';
  const level = typeof value.level === 'string' && levels.has(value.level as AttentionNoticeLevel)
    ? value.level as AttentionNoticeLevel
    : null;
  const source = typeof value.source === 'string' && sources.has(value.source as AttentionNoticeSource)
    ? value.source as AttentionNoticeSource
    : null;
  const acknowledgedAt = value.acknowledgedAt == null
    ? ''
    : typeof value.acknowledgedAt === 'string' ? value.acknowledgedAt.trim() : null;
  if (!noticeId || !content || !level || !source
      || typeof value.requiredAck !== 'boolean'
      || typeof value.acknowledged !== 'boolean'
      || acknowledgedAt === null
      || (value.acknowledged && !acknowledgedAt)) {
    return null;
  }
  return {
    noticeId,
    level,
    content,
    source,
    requiredAck: value.requiredAck,
    acknowledged: value.acknowledged,
    acknowledgedAt
  };
}

function normalizedResult(value: unknown): AttentionNoticeResult | null {
  if (!isRecord(value) || !Array.isArray(value.items)) return null;
  const items = value.items.map(normalizedNotice);
  if (items.some((item) => item === null)) return null;
  const records = items as AttentionNoticeRecord[];
  if (new Set(records.map((item) => item.noticeId)).size !== records.length) return null;
  return { items: records };
}

function attentionPath(orderId: string) {
  return `/nurse/orders/${encodeURIComponent(orderId.trim())}/attention-notices`;
}

export async function getAttentionPermissions(signal?: AbortSignal): Promise<ApiResponse<string[]>> {
  const response = await request<unknown>({ method: 'GET', url: '/auth/permissions', signal });
  if (response.code !== 0) return { ...response, data: [] };
  if (!isRecord(response.data)) return failure(502, '权限信息响应不完整', [], response.traceId);
  const permissions = Array.isArray(response.data.permissions)
    ? response.data.permissions
    : Array.isArray(response.data.permissionCodes) ? response.data.permissionCodes : null;
  if (!permissions || permissions.some((item) => typeof item !== 'string')) {
    return failure(502, '权限信息响应不完整', [], response.traceId);
  }
  return {
    ...response,
    data: Array.from(new Set((permissions as string[]).map((item) => item.trim()).filter(Boolean)))
  };
}

export async function getAttentionNotices(orderId: string, signal?: AbortSignal): Promise<ApiResponse<AttentionNoticeResult>> {
  if (!orderId.trim()) return failure(422, '订单信息不完整', emptyResult, 'frontend-stage-31-validation');
  const response = await request<unknown>({ method: 'GET', url: attentionPath(orderId), signal });
  if (response.code !== 0) return { ...response, data: emptyResult };
  const result = normalizedResult(response.data);
  return result
    ? { ...response, data: result }
    : failure(502, '服务前注意事项响应不完整', emptyResult, response.traceId);
}

export async function acknowledgeAttentionNotices(
  orderId: string,
  payload: AttentionNoticeAckRequest,
  signal?: AbortSignal
): Promise<ApiResponse<AttentionNoticeResult>> {
  const noticeIds = Array.from(new Set(payload.noticeIds.map((item) => item.trim()).filter(Boolean)));
  if (!orderId.trim() || noticeIds.length === 0) {
    return failure(422, '请选择需要确认的服务前注意事项', emptyResult, 'frontend-stage-31-validation');
  }
  const response = await request<unknown>({
    method: 'POST',
    url: `${attentionPath(orderId)}/ack`,
    data: { noticeIds },
    signal
  });
  if (response.code !== 0) return { ...response, data: emptyResult };
  const result = normalizedResult(response.data);
  return result
    ? { ...response, data: result }
    : failure(502, '服务前注意事项确认响应不完整', emptyResult, response.traceId);
}
