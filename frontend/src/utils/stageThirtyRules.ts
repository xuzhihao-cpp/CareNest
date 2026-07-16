import type { AdminOrderStatus } from '@/types/stageEleven';
import type { BindingResponse } from '@/types/stageSix';
import type { NurseRecommendationRecord } from '@/types/stageTwentyNine';
import type {
  AdminPreferenceSource,
  PreferenceResolution,
  PreferredNursePresentation,
  PreferredNurseResponse
} from '@/types/stageThirty';

export function preferredNurseReadModelPresentation(
  orderId: string,
  nurseName?: string,
  recommendReason?: string
): PreferredNursePresentation | null {
  const normalizedName = nurseName?.trim() ?? '';
  const normalizedReason = recommendReason?.trim() ?? '';
  if (!normalizedName || !normalizedReason) return null;
  return {
    orderId: orderId.trim(),
    preferredNurseId: '',
    nurseName: normalizedName,
    recommendReason: normalizedReason
  };
}

export function canEditPreferredNurse(status: AdminOrderStatus) {
  return status === 'WAIT_DISPATCH';
}

export function hasActiveOrderBinding(elderId: string, bindings: BindingResponse[]) {
  const normalizedElderId = elderId.trim();
  return Boolean(normalizedElderId) && bindings.some((binding) => binding.elderId === normalizedElderId
    && binding.bindingStatus === 'ACTIVE'
    && binding.scopeCodes.includes('ORDER_CREATE'));
}

export function preferredNurseAccessMessage(
  elderId: string,
  permissions: string[],
  bindings: BindingResponse[],
  requestError = ''
) {
  if (requestError) return requestError;
  if (!permissions.includes('NURSE_PREFERENCE_SELECT')) {
    return '当前账号没有选择偏好护理的权限，本次仍可直接提交预约。';
  }
  if (!elderId.trim()) return '';
  return hasActiveOrderBinding(elderId, bindings)
    ? ''
    : '当前长辈没有已生效的代下单授权，暂时不能预约服务。';
}

export function canSelectPreferredNurse(
  status: AdminOrderStatus,
  permissions: string[],
  bindingAuthorized: boolean
) {
  return canEditPreferredNurse(status)
    && bindingAuthorized
    && permissions.includes('NURSE_PREFERENCE_SELECT');
}

export function currentPreferredRecommendation(
  preferredNurseId: string,
  recommendations: NurseRecommendationRecord[]
) {
  const normalizedId = preferredNurseId.trim();
  if (!normalizedId) return null;
  return recommendations.find((item) => item.nurseId === normalizedId && item.available) ?? null;
}

export function resolvePreferredNurse(
  preference: PreferredNurseResponse | null,
  recommendations: NurseRecommendationRecord[]
): PreferenceResolution {
  if (!preference) return { presentation: null, unresolved: false };
  const nurse = recommendations.find((item) => item.nurseId === preference.preferredNurseId);
  if (!nurse) return { presentation: null, unresolved: true };
  return {
    presentation: {
      ...preference,
      nurseName: nurse.nurseName
    },
    unresolved: false
  };
}

export function resolveAdminPreferredNurse(
  order: AdminPreferenceSource,
  recommendations: NurseRecommendationRecord[]
): PreferenceResolution {
  const preferredNurseId = order.preferredNurseId?.trim() ?? '';
  const providedName = order.preferredNurseName?.trim() ?? '';
  const providedReason = order.preferredNurseReason?.trim() ?? '';
  if (providedName && providedReason) {
    return {
      presentation: {
        orderId: order.orderId,
        preferredNurseId,
        nurseName: providedName,
        recommendReason: providedReason
      },
      unresolved: false
    };
  }
  if (!preferredNurseId) {
    return { presentation: null, unresolved: Boolean(providedName || providedReason) };
  }
  const matched = recommendations.find((item) => item.nurseId === preferredNurseId);
  const nurseName = providedName || matched?.nurseName || '';
  const recommendReason = providedReason || matched?.recommendReason || '';
  if (!nurseName || !recommendReason) return { presentation: null, unresolved: true };
  return {
    presentation: {
      orderId: order.orderId,
      preferredNurseId,
      nurseName,
      recommendReason
    },
    unresolved: false
  };
}

export function preferredNurseErrorMessage(code: number, action: 'read' | 'save') {
  if (code === 401) return '登录状态已失效，请重新登录后再试。';
  if (code === 403) return action === 'save'
    ? '当前账号无权修改这笔订单的偏好护理。'
    : '当前账号无权查看这笔订单的偏好护理。';
  if (code === 404) return action === 'read' ? '' : '订单或推荐记录不存在，请刷新后重试。';
  if (code === 409) return '订单状态已经变化，当前不能再修改偏好护理。';
  if (code === 422) return '该护理已不在当前有效推荐中，请重新获取推荐后选择。';
  return action === 'save'
    ? '偏好护理暂时无法保存，请稍后重试。'
    : '偏好护理信息暂时无法读取，请稍后重试。';
}
