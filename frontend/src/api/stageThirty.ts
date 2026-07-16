import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { BindingListResult } from '@/types/stageSix';
import type { PreferredNurseResponse } from '@/types/stageThirty';
import { recommendationReasonIsBusinessReadable } from '@/utils/stageTwentyNineRules';

const emptyPreference: PreferredNurseResponse = {
  orderId: '',
  preferredNurseId: '',
  recommendReason: ''
};

function preferencePath(orderId: string) {
  return `/family/orders/${encodeURIComponent(orderId)}/preferred-nurse`;
}

function preferenceViewPath(orderId: string) {
  return `/family/orders/${encodeURIComponent(orderId)}/recommendation-view`;
}

function validPreference(value: unknown): value is PreferredNurseResponse {
  if (!value || typeof value !== 'object') return false;
  const item = value as Record<string, unknown>;
  return typeof item.orderId === 'string'
    && item.orderId.trim().length > 0
    && typeof item.preferredNurseId === 'string'
    && item.preferredNurseId.trim().length > 0
    && typeof item.recommendReason === 'string'
    && recommendationReasonIsBusinessReadable(item.recommendReason.trim());
}

function normalizedOrderId(orderId: string) {
  return orderId.trim();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

export async function getPreferredNursePermissions(): Promise<ApiResponse<string[]>> {
  const response = await request<unknown>({ method: 'GET', url: '/auth/permissions' });
  if (response.code !== 0) return { ...response, data: [] };
  if (!isRecord(response.data)) return failure(502, '账号权限响应不完整', [], response.traceId);
  const values = Array.isArray(response.data.permissions)
    ? response.data.permissions
    : Array.isArray(response.data.permissionCodes) ? response.data.permissionCodes : null;
  if (!values || values.some((item) => typeof item !== 'string')) {
    return failure(502, '账号权限响应不完整', [], response.traceId);
  }
  return {
    ...response,
    data: Array.from(new Set((values as string[]).map((item) => item.trim()).filter(Boolean)))
  };
}

export async function getPreferredNurseBindings(): Promise<ApiResponse<BindingListResult>> {
  const response = await request<unknown>({ method: 'GET', url: '/family/bindings' });
  if (response.code !== 0) return { ...response, data: [] };
  if (!Array.isArray(response.data)) {
    return failure(502, '绑定授权响应不完整', [], response.traceId);
  }
  const valid = response.data.every((item) => {
    if (!isRecord(item)) return false;
    return typeof item.bindingId === 'string'
      && typeof item.elderId === 'string'
      && typeof item.bindingStatus === 'string'
      && Array.isArray(item.scopeCodes)
      && item.scopeCodes.every((scope) => typeof scope === 'string');
  });
  return valid
    ? { ...response, data: response.data as BindingListResult }
    : failure(502, '绑定授权响应不完整', [], response.traceId);
}

export async function updatePreferredNurse(
  orderId: string,
  preferredNurseId: string
): Promise<ApiResponse<PreferredNurseResponse>> {
  const normalizedId = normalizedOrderId(orderId);
  const normalizedNurseId = preferredNurseId.trim();
  if (!normalizedId || !normalizedNurseId) {
    return failure(422, '请选择有效的推荐护理', emptyPreference, 'frontend-stage-30-validation');
  }
  const response = await request<PreferredNurseResponse>({
    method: 'PUT',
    url: preferencePath(normalizedId),
    data: { preferredNurseId: normalizedNurseId }
  });
  if (response.code !== 0) return { ...response, data: emptyPreference };
  return validPreference(response.data)
    ? response
    : failure(502, '偏好护理响应不完整', emptyPreference, response.traceId);
}

export async function getPreferredNurse(
  orderId: string
): Promise<ApiResponse<PreferredNurseResponse>> {
  const normalizedId = normalizedOrderId(orderId);
  if (!normalizedId) {
    return failure(422, '订单信息不完整', emptyPreference, 'frontend-stage-30-validation');
  }
  const response = await request<PreferredNurseResponse>({
    method: 'GET',
    url: preferenceViewPath(normalizedId)
  });
  if (response.code !== 0) return { ...response, data: emptyPreference };
  return validPreference(response.data)
    ? response
    : failure(502, '偏好护理响应不完整', emptyPreference, response.traceId);
}
