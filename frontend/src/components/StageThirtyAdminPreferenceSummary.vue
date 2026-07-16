<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { getOrderRecommendations } from '@/api/stageTwentyNine';
import type { AdminOrderRecord } from '@/types/stageEleven';
import type { NurseRecommendationRecord } from '@/types/stageTwentyNine';
import { createLatestRequestGate } from '@/utils/latestRequestGate';
import { resolveAdminPreferredNurse } from '@/utils/stageThirtyRules';

const props = defineProps<{
  order: AdminOrderRecord;
}>();

const recommendations = ref<NurseRecommendationRecord[]>([]);
const loading = ref(false);
const error = ref('');
const requestGate = createLatestRequestGate<string>();
const resolution = computed(() => resolveAdminPreferredNurse(props.order, recommendations.value));
const hasPreferenceReference = computed(() => Boolean(
  props.order.preferredNurseId?.trim()
  || props.order.preferredNurseName?.trim()
  || props.order.preferredNurseReason?.trim()
));

async function loadPreferenceContext() {
  const orderId = props.order.orderId;
  const ticket = requestGate.begin(orderId);
  recommendations.value = [];
  error.value = '';
  loading.value = false;
  if (!props.order.preferredNurseId?.trim() || (
    props.order.preferredNurseName?.trim() && props.order.preferredNurseReason?.trim()
  )) return;
  loading.value = true;
  const response = await getOrderRecommendations(orderId);
  if (!requestGate.isCurrent(ticket, props.order.orderId)) return;
  loading.value = false;
  if (response.code !== 0) {
    error.value = '家属偏好护理的推荐依据暂时无法读取。';
    return;
  }
  recommendations.value = response.data.nurses;
}

watch(() => [
  props.order.orderId,
  props.order.preferredNurseId,
  props.order.preferredNurseName,
  props.order.preferredNurseReason
], loadPreferenceContext, { immediate: true });

onBeforeUnmount(() => requestGate.invalidate());
</script>

<template>
  <section class="admin-preference-summary" aria-label="家属偏好护理">
    <view class="summary-heading">
      <view><text class="summary-title">家属偏好护理</text><text class="summary-subtitle">偏好仅供派单参考，管理员仍需按实际情况选择合格护理员。</text></view>
      <text class="reference-tag">派单参考</text>
    </view>
    <view v-if="loading" class="summary-state">正在读取偏好信息...</view>
    <view v-else-if="error" class="summary-state warning">{{ error }}</view>
    <view v-else-if="resolution.presentation" class="preference-card">
      <text class="preference-name">{{ resolution.presentation.nurseName }}</text>
      <text class="preference-reason">{{ resolution.presentation.recommendReason }}</text>
    </view>
    <view v-else-if="hasPreferenceReference && resolution.unresolved" class="summary-state warning">
      订单包含偏好护理，但当前详情不足以展示姓名和推荐依据。
    </view>
    <view v-else class="summary-state">暂未读取到家属偏好护理。</view>
  </section>
</template>

<style scoped>
.admin-preference-summary { display:grid; gap:12px; padding:16px; border:1px solid #c8ded9; border-radius:7px; background:#f8fbfa; }.summary-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; }.summary-title,.summary-subtitle,.preference-name,.preference-reason { display:block; }.summary-title { color:#183e37; font-size:15px; font-weight:800; }.summary-subtitle { margin-top:4px; color:#71827e; font-size:12px; line-height:1.5; }.reference-tag { flex:none; padding:5px 9px; border-radius:999px; background:#e4f5f1; color:#087569; font-size:11px; font-weight:700; }.summary-state { padding:12px; border-left:4px solid #91b9b1; background:#eef6f4; color:#58716c; font-size:12px; line-height:1.55; }.summary-state.warning { border-left-color:#d2a14a; background:#fff8e8; color:#785716; }.preference-card { padding:13px; border-left:4px solid #15998b; background:#edf9f6; }.preference-name { color:#0d7065; font-size:16px; font-weight:800; }.preference-reason { margin-top:6px; color:#526d67; font-size:12px; line-height:1.6; }
</style>
