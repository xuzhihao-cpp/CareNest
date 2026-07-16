<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { getOrderRecommendations } from '@/api/stageTwentyNine';
import { getPreferredNurse } from '@/api/stageThirty';
import type { NurseRecommendationRecord } from '@/types/stageTwentyNine';
import type { PreferredNurseResponse } from '@/types/stageThirty';
import { createLatestRequestGate } from '@/utils/latestRequestGate';
import { preferredNurseReadModelPresentation, resolvePreferredNurse } from '@/utils/stageThirtyRules';

const props = withDefaults(defineProps<{
  orderId: string;
  refreshKey?: number;
  preferredNurseName?: string;
  preferredNurseReason?: string;
}>(), {
  refreshKey: 0
});

const preference = ref<PreferredNurseResponse | null>(null);
const recommendations = ref<NurseRecommendationRecord[]>([]);
const unavailable = ref(false);
const requestGate = createLatestRequestGate<string>();
const resolution = computed(() => resolvePreferredNurse(preference.value, recommendations.value));
const readModelPresentation = computed(() => preferredNurseReadModelPresentation(
  props.orderId,
  props.preferredNurseName,
  props.preferredNurseReason
));
const presentation = computed(() => readModelPresentation.value ?? resolution.value.presentation);

async function loadBadge() {
  const orderId = props.orderId.trim();
  const requestKey = `${orderId}:${props.refreshKey}`;
  const ticket = requestGate.begin(requestKey);
  preference.value = null;
  recommendations.value = [];
  unavailable.value = false;
  if (!orderId) return;
  if (readModelPresentation.value) return;
  const preferenceResponse = await getPreferredNurse(orderId);
  if (!requestGate.isCurrent(ticket, `${props.orderId.trim()}:${props.refreshKey}`)) return;
  if (preferenceResponse.code === 404) return;
  if (preferenceResponse.code !== 0) {
    unavailable.value = true;
    return;
  }
  preference.value = preferenceResponse.data;
  const recommendationResponse = await getOrderRecommendations(orderId);
  if (!requestGate.isCurrent(ticket, `${props.orderId.trim()}:${props.refreshKey}`)) return;
  if (recommendationResponse.code !== 0) {
    unavailable.value = true;
    return;
  }
  recommendations.value = recommendationResponse.data.nurses;
}

watch(() => [props.orderId, props.refreshKey, props.preferredNurseName, props.preferredNurseReason], loadBadge, { immediate: true });
onBeforeUnmount(() => requestGate.invalidate());
</script>

<template>
  <view v-if="presentation" class="preference-badge">
    <text class="preference-name">偏好护理：{{ presentation.nurseName }}</text>
    <text class="preference-reason">推荐依据：{{ presentation.recommendReason }}</text>
  </view>
  <text v-else-if="preference && resolution.unresolved" class="preference-badge muted">偏好护理信息待同步</text>
  <text v-else-if="unavailable" class="preference-badge muted">偏好信息暂不可用</text>
</template>

<style scoped>
.preference-badge { display:grid; gap:3rpx; margin-top:7rpx; color:#0d756a; font-size:21rpx; }.preference-name,.preference-reason { display:block; }.preference-name { font-weight:700; }.preference-reason { color:#647b76; font-size:19rpx; line-height:1.45; font-weight:500; }.preference-badge.muted { color:#788783; font-weight:500; }
</style>
