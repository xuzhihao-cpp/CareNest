<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import StageTwentyNineRecommendationPanel from '@/components/StageTwentyNineRecommendationPanel.vue';
import {
  getPreferredNurse,
  getPreferredNurseBindings,
  getPreferredNursePermissions,
  updatePreferredNurse
} from '@/api/stageThirty';
import type { AdminOrderStatus } from '@/types/stageEleven';
import type { BindingResponse } from '@/types/stageSix';
import type { NurseRecommendationRecord } from '@/types/stageTwentyNine';
import type { PreferredNurseResponse } from '@/types/stageThirty';
import { createLatestRequestGate } from '@/utils/latestRequestGate';
import {
  canEditPreferredNurse,
  canSelectPreferredNurse,
  hasActiveOrderBinding,
  preferredNurseReadModelPresentation,
  preferredNurseErrorMessage,
  resolvePreferredNurse
} from '@/utils/stageThirtyRules';

const props = defineProps<{
  orderId: string;
  orderStatus: AdminOrderStatus;
  elderId: string;
  preferredNurseName?: string;
  preferredNurseReason?: string;
}>();

const emit = defineEmits<{
  updated: [];
}>();

const preference = ref<PreferredNurseResponse | null>(null);
const recommendations = ref<NurseRecommendationRecord[]>([]);
const reading = ref(false);
const saving = ref(false);
const error = ref('');
const message = ref('');
const recommendationRefreshKey = ref(0);
const permissions = ref<string[]>([]);
const bindings = ref<BindingResponse[]>([]);
const authorizationLoading = ref(true);
const permissionError = ref('');
const readGate = createLatestRequestGate<string>();
const saveGate = createLatestRequestGate<string>();
const permissionGate = createLatestRequestGate<string>();

const statusEditable = computed(() => canEditPreferredNurse(props.orderStatus));
const bindingAuthorized = computed(() => hasActiveOrderBinding(props.elderId, bindings.value));
const editable = computed(() => canSelectPreferredNurse(
  props.orderStatus,
  permissions.value,
  bindingAuthorized.value
));
const readModelPresentation = computed(() => preferredNurseReadModelPresentation(
  props.orderId,
  props.preferredNurseName,
  props.preferredNurseReason
));
const resolution = computed(() => readModelPresentation.value
  ? { presentation: readModelPresentation.value, unresolved: false }
  : resolvePreferredNurse(preference.value, recommendations.value));
const selectedNurseId = computed(() => preference.value?.preferredNurseId ?? '');

async function loadAuthorization() {
  const requestKey = `${props.orderId.trim()}:${props.elderId.trim()}`;
  const ticket = permissionGate.begin(requestKey);
  authorizationLoading.value = true;
  permissionError.value = '';
  const [permissionResponse, bindingResponse] = await Promise.all([
    getPreferredNursePermissions(),
    getPreferredNurseBindings()
  ]);
  if (!permissionGate.isCurrent(ticket, `${props.orderId.trim()}:${props.elderId.trim()}`)) return;
  authorizationLoading.value = false;
  permissions.value = permissionResponse.code === 0 ? permissionResponse.data : [];
  bindings.value = bindingResponse.code === 0 ? bindingResponse.data : [];
  if (permissionResponse.code !== 0) {
    permissionError.value = permissionResponse.code === 403
      ? '当前账号没有选择偏好护理的权限。'
      : '账号权限暂时无法读取，当前不能修改偏好护理。';
  } else if (!permissions.value.includes('NURSE_PREFERENCE_SELECT')) {
    permissionError.value = '当前账号没有选择偏好护理的权限。';
  } else if (bindingResponse.code !== 0) {
    permissionError.value = '长辈绑定授权暂时无法读取，当前不能修改偏好护理。';
  } else if (!bindingAuthorized.value) {
    permissionError.value = '当前长辈没有已生效的代下单授权，不能修改偏好护理。';
  }
}

async function loadPreference() {
  const orderId = props.orderId.trim();
  const ticket = readGate.begin(orderId);
  if (!orderId) {
    preference.value = null;
    return false;
  }
  reading.value = true;
  error.value = '';
  const response = await getPreferredNurse(orderId);
  if (!readGate.isCurrent(ticket, props.orderId.trim())) return false;
  reading.value = false;
  if (response.code === 404) {
    preference.value = null;
    return true;
  }
  if (response.code !== 0) {
    preference.value = null;
    error.value = preferredNurseErrorMessage(response.code, 'read');
    return false;
  }
  preference.value = response.data;
  return true;
}

function handleRecommendationsLoaded(items: NurseRecommendationRecord[]) {
  recommendations.value = items;
}

async function handleSelect(record: NurseRecommendationRecord) {
  if (!editable.value || !record.available || saving.value) return;
  const orderId = props.orderId.trim();
  const ticket = saveGate.begin(orderId);
  saving.value = true;
  error.value = '';
  message.value = '';
  const response = await updatePreferredNurse(orderId, record.nurseId);
  if (!saveGate.isCurrent(ticket, props.orderId.trim())) return;
  if (response.code !== 0) {
    error.value = preferredNurseErrorMessage(response.code, 'save');
    if (response.code === 409) {
      await loadPreference();
      if (!saveGate.isCurrent(ticket, props.orderId.trim())) return;
      emit('updated');
    }
    saving.value = false;
    return;
  }
  const refreshed = await loadPreference();
  if (!saveGate.isCurrent(ticket, props.orderId.trim())) return;
  saving.value = false;
  recommendationRefreshKey.value += 1;
  emit('updated');
  if (refreshed && preference.value?.preferredNurseId === record.nurseId) {
    message.value = `已将${record.nurseName}设为本单偏好护理。`;
  } else if (!error.value) {
    error.value = '偏好护理已提交，但最新结果暂时无法确认，请刷新后查看。';
  }
}

watch(() => [props.orderId, props.orderStatus], () => {
  readGate.invalidate();
  saveGate.invalidate();
  reading.value = false;
  saving.value = false;
  preference.value = null;
  recommendations.value = [];
  message.value = '';
  error.value = '';
  void loadPreference();
}, { immediate: true });

watch(() => [props.orderId, props.elderId], () => {
  void loadAuthorization();
}, { immediate: true });

onBeforeUnmount(() => {
  readGate.invalidate();
  saveGate.invalidate();
  permissionGate.invalidate();
});

</script>

<template>
  <section class="family-preference-panel" aria-label="偏好护理">
    <header>
      <view>
        <text class="preference-title">偏好护理</text>
        <text class="preference-subtitle">偏好用于表达服务意愿，最终护理安排以平台派单为准。</text>
      </view>
      <text v-if="!statusEditable" class="readonly-tag">当前订单不可修改</text>
      <text v-else-if="authorizationLoading" class="readonly-tag">正在确认操作权限</text>
      <text v-else-if="!editable" class="readonly-tag">仅可查看</text>
    </header>

    <view v-if="reading" class="preference-state">正在读取偏好护理...</view>
    <view v-if="message" class="success-banner">{{ message }}</view>
    <view v-if="error" class="error-banner" role="alert">{{ error }}</view>
    <view v-if="permissionError && statusEditable" class="preference-state warning">{{ permissionError }}</view>

    <view v-if="resolution.presentation" class="current-preference">
      <text class="current-label">当前偏好</text>
      <text class="current-name">{{ resolution.presentation.nurseName }}</text>
      <text class="current-reason">{{ resolution.presentation.recommendReason }}</text>
    </view>
    <view v-else-if="preference && resolution.unresolved && !reading" class="preference-state warning">
      当前偏好护理的姓名暂时无法读取，请稍后刷新推荐结果。
    </view>
    <view v-else-if="!preference && !reading && !error" class="preference-state">
      当前订单尚未选择偏好护理。
    </view>

    <StageTwentyNineRecommendationPanel
      :key="`${orderId}:${recommendationRefreshKey}`"
      mode="order"
      :order-id="orderId"
      :selectable="editable && !saving"
      :selected-nurse-id="selectedNurseId"
      @selected="handleSelect"
      @recommendations-loaded="handleRecommendationsLoaded"
    />
  </section>
</template>

<style scoped>
.family-preference-panel { display:grid; gap:14rpx; margin-top:22rpx; padding-top:22rpx; border-top:1rpx solid #dbe6e3; }.family-preference-panel header { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }.preference-title,.preference-subtitle,.current-label,.current-name,.current-reason { display:block; }.preference-title { color:#193d37; font-size:27rpx; font-weight:800; }.preference-subtitle { margin-top:5rpx; color:#71827e; font-size:21rpx; line-height:1.5; }.readonly-tag { padding:7rpx 12rpx; border-radius:999rpx; background:#edf1ef; color:#687873; font-size:20rpx; }.preference-state { padding:16rpx; border-left:5rpx solid #8dbab2; background:#f0f7f5; color:#55706a; font-size:22rpx; line-height:1.55; }.preference-state.warning { border-left-color:#d2a14a; background:#fff8e8; color:#785716; }.current-preference { display:grid; gap:5rpx; padding:18rpx; border:1rpx solid #9ed4ca; border-radius:7rpx; background:#edf9f6; }.current-label { color:#578078; font-size:20rpx; }.current-name { color:#0e6f64; font-size:28rpx; font-weight:800; }.current-reason { color:#526d67; font-size:22rpx; line-height:1.55; }.success-banner,.error-banner { border-radius:6rpx; }
</style>
