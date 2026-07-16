<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import {
  acknowledgeAttentionNotices,
  getAttentionNotices,
  getAttentionPermissions
} from '@/api/stageThirtyOne';
import type { AttentionNoticeRecord } from '@/types/stageThirtyOne';
import { createLatestRequestGate } from '@/utils/latestRequestGate';
import {
  ATTENTION_SOURCE_LABELS,
  attentionNoticeErrorMessage,
  canStartServiceAfterAttention,
  formatShanghaiDateTime,
  groupAttentionNotices,
  pendingRequiredNotices,
  selectedPendingNoticeIds
} from '@/utils/stageThirtyOneRules';

const props = withDefaults(defineProps<{
  orderId: string;
  taskStatus: string;
  readOnly?: boolean;
  starting?: boolean;
  refreshKey?: number;
}>(), {
  readOnly: false,
  starting: false,
  refreshKey: 0
});

const emit = defineEmits<{
  startService: [];
  stateConflict: [];
}>();

const items = ref<AttentionNoticeRecord[]>([]);
const selectedIds = ref<string[]>([]);
const permissions = ref<string[]>([]);
const loading = ref(false);
const loaded = ref(false);
const submitting = ref(false);
const error = ref('');
const permissionError = ref('');
const message = ref('');
const readGate = createLatestRequestGate<string>();
const ackGate = createLatestRequestGate<string>();
let readController: AbortController | null = null;
let ackController: AbortController | null = null;

const groups = computed(() => groupAttentionNotices(items.value));
const pendingRequired = computed(() => pendingRequiredNotices(items.value));
const canAcknowledge = computed(() => !props.readOnly
  && permissions.value.includes('NURSE_ATTENTION_ACK')
  && !permissionError.value);
const selectedPendingIds = computed(() => selectedPendingNoticeIds(items.value, selectedIds.value));
const canStartService = computed(() => canStartServiceAfterAttention({
  loaded: loaded.value,
  hasReadError: Boolean(error.value),
  taskStatus: props.taskStatus,
  items: items.value
}));

function contextKey() {
  return `${props.orderId.trim()}:${props.refreshKey}:${props.readOnly ? 'review' : 'nurse'}`;
}

function cancelReadRequest() {
  readController?.abort();
  readController = null;
}

function cancelAckRequest() {
  ackController?.abort();
  ackController = null;
}

async function loadAttention(preserveMessage = false) {
  cancelReadRequest();
  const orderId = props.orderId.trim();
  const key = contextKey();
  const ticket = readGate.begin(key);
  ackGate.invalidate();
  items.value = [];
  selectedIds.value = [];
  loaded.value = false;
  loading.value = Boolean(orderId);
  submitting.value = false;
  error.value = '';
  permissionError.value = '';
  if (!preserveMessage) message.value = '';
  if (!orderId) {
    loading.value = false;
    return;
  }
  const controller = new AbortController();
  readController = controller;

  const [noticeResponse, permissionResponse] = await Promise.all([
    getAttentionNotices(orderId, controller.signal),
    props.readOnly ? Promise.resolve(null) : getAttentionPermissions(controller.signal)
  ]);
  if (readController === controller) readController = null;
  if (controller.signal.aborted) return;
  if (!readGate.isCurrent(ticket, contextKey())) return;
  loading.value = false;
  if (noticeResponse.code !== 0) {
    error.value = attentionNoticeErrorMessage(noticeResponse.code, 'read');
    return;
  }
  items.value = noticeResponse.data.items;
  loaded.value = true;
  if (!props.readOnly) {
    permissions.value = permissionResponse?.code === 0 ? permissionResponse.data : [];
    if (permissionResponse?.code !== 0) {
      permissionError.value = permissionResponse?.code === 403
        ? '当前账号没有确认服务前注意事项的权限。'
        : '账号权限暂时无法读取，当前不能确认服务前注意事项。';
    } else if (!permissions.value.includes('NURSE_ATTENTION_ACK')) {
      permissionError.value = '当前账号没有确认服务前注意事项的权限。';
    }
  }
}

function updateSelection(event: { detail: { value: string[] } }) {
  selectedIds.value = selectedPendingNoticeIds(items.value, event.detail.value);
  error.value = '';
}

async function acknowledgeSelected() {
  if (!canAcknowledge.value || submitting.value) return;
  const noticeIds = selectedPendingIds.value;
  if (!noticeIds.length) {
    error.value = '请先勾选需要确认的事项。';
    return;
  }
  const orderId = props.orderId.trim();
  const ticket = ackGate.begin(orderId);
  cancelAckRequest();
  const controller = new AbortController();
  ackController = controller;
  submitting.value = true;
  error.value = '';
  message.value = '';
  const response = await acknowledgeAttentionNotices(orderId, { noticeIds }, controller.signal);
  if (ackController === controller) ackController = null;
  if (controller.signal.aborted) return;
  if (!ackGate.isCurrent(ticket, props.orderId.trim())) return;
  if (response.code !== 0) {
    submitting.value = false;
    error.value = attentionNoticeErrorMessage(response.code, 'ack');
    if (response.code === 409) {
      error.value = '';
      message.value = '任务状态已经变化，已重新读取最新任务和注意事项。';
      emit('stateConflict');
      await loadAttention(true);
    }
    return;
  }
  message.value = '所选注意事项已确认。';
  await loadAttention(true);
  if (!ackGate.isCurrent(ticket, props.orderId.trim())) return;
  submitting.value = false;
}

watch(() => [props.orderId, props.refreshKey, props.readOnly], () => {
  cancelReadRequest();
  cancelAckRequest();
  readGate.invalidate();
  ackGate.invalidate();
  void loadAttention();
}, { immediate: true });

onBeforeUnmount(() => {
  cancelReadRequest();
  cancelAckRequest();
  readGate.invalidate();
  ackGate.invalidate();
});
</script>

<template>
  <section class="attention-panel" aria-label="服务前注意事项">
    <header class="attention-heading">
      <view>
        <text class="attention-index">07</text>
        <text class="attention-title">服务前注意事项</text>
        <text class="attention-subtitle">请在开始服务前核对本次护理需要留意的内容。</text>
      </view>
      <button type="button" :disabled="loading || submitting" @click="loadAttention()">刷新</button>
    </header>

    <view v-if="loading" class="attention-state">正在读取本次服务注意事项...</view>
    <view v-else-if="error" class="attention-state error-state" role="alert">
      <text>{{ error }}</text>
      <button type="button" @click="loadAttention()">重新读取</button>
    </view>
    <view v-if="message" class="attention-state success-state">{{ message }}</view>
    <view v-if="permissionError && !readOnly" class="attention-state warning-state">{{ permissionError }}</view>

    <view v-if="loaded && items.length === 0" class="attention-state empty-state">
      当前服务没有需要额外确认的注意事项，请继续按照护理规范完成服务前核对。
    </view>

    <checkbox-group v-if="loaded && items.length" class="attention-groups" @change="updateSelection">
      <section v-for="group in groups" :key="group.level" class="attention-group" :class="`level-${group.level.toLowerCase()}`">
        <view class="group-heading">
          <text>{{ group.label }}</text>
          <text>{{ group.items.length }} 项</text>
        </view>
        <label v-for="item in group.items" :key="item.noticeId" class="notice-row">
          <checkbox
            v-if="item.requiredAck && !readOnly"
            :value="item.noticeId"
            :checked="item.acknowledged || selectedIds.includes(item.noticeId)"
            :disabled="item.acknowledged || !canAcknowledge || submitting"
            color="#0f766e"
          />
          <view class="notice-content">
            <view class="notice-meta">
              <text>{{ ATTENTION_SOURCE_LABELS[item.source] }}</text>
              <text v-if="item.requiredAck" class="required-tag">必须确认</text>
              <text v-else class="readonly-tag">阅读提示</text>
              <text v-if="item.acknowledged" class="acknowledged-tag">已确认</text>
            </view>
            <text class="notice-text">{{ item.content }}</text>
            <text v-if="item.acknowledgedAt" class="acknowledged-time">确认时间：{{ formatShanghaiDateTime(item.acknowledgedAt) }}</text>
          </view>
        </label>
      </section>
    </checkbox-group>

    <view v-if="loaded && !readOnly && pendingRequired.length" class="acknowledge-bar">
      <view>
        <text>还需确认 {{ pendingRequired.length }} 项</text>
        <text>勾选已经核对的事项后统一确认。</text>
      </view>
      <button type="button" :disabled="!selectedPendingIds.length || submitting || !canAcknowledge" @click="acknowledgeSelected">
        {{ submitting ? '正在确认' : `确认已勾选（${selectedPendingIds.length}）` }}
      </button>
    </view>

    <view v-else-if="loaded && !readOnly && items.length" class="attention-state success-state">
      所有必须确认的事项均已完成核对。
    </view>

    <view v-if="!readOnly && taskStatus === 'ON_THE_WAY'" class="start-service-bar">
      <text v-if="pendingRequired.length">完成剩余 {{ pendingRequired.length }} 项确认后才能开始服务。</text>
      <text v-else-if="!canStartService">注意事项读取完成后才能开始服务。</text>
      <text v-else>服务前核对已完成，可以开始本次服务。</text>
      <button type="button" :disabled="!canStartService || starting" @click="emit('startService')">
        {{ starting ? '正在开始服务' : '开始服务' }}
      </button>
    </view>
  </section>
</template>

<style scoped>
.attention-panel { display:grid; gap:16rpx; padding:24rpx 0 8rpx; border-top:1rpx solid #dce7e4; color:#17312e; }.attention-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }.attention-heading>view { min-width:0; }.attention-index,.attention-title,.attention-subtitle,.notice-text,.acknowledged-time,.acknowledge-bar text,.start-service-bar>text { display:block; }.attention-index { color:#17877c; font-size:20rpx; font-weight:800; }.attention-title { margin-top:5rpx; color:#183631; font-size:30rpx; font-weight:800; }.attention-subtitle { margin-top:6rpx; color:#677b76; font-size:23rpx; line-height:1.5; }.attention-heading>button,.attention-state button,.acknowledge-bar button,.start-service-bar button { display:inline-flex; align-items:center; justify-content:center; box-sizing:border-box; min-height:78rpx; margin:0; padding:0 20rpx; border:1rpx solid #b9d3cd; border-radius:5rpx; background:#fff; color:#126f65; font-size:23rpx; font-weight:700; line-height:1.2; }.attention-heading>button { flex:none; min-width:110rpx; }.attention-state { padding:18rpx; border-left:5rpx solid #8bbab1; background:#eff7f5; color:#536e68; font-size:23rpx; line-height:1.55; }.attention-state.error-state { display:flex; align-items:center; justify-content:space-between; gap:14rpx; border-left-color:#dc766d; background:#fff2f0; color:#a33a32; }.attention-state.warning-state { border-left-color:#d1a14a; background:#fff8e9; color:#785716; }.attention-state.success-state { border-left-color:#16877b; background:#edf8f5; color:#176d64; }.attention-state.empty-state { border-left-color:#9aada8; background:#f4f7f6; color:#647773; }.attention-groups { display:grid; gap:14rpx; }.attention-group { display:grid; gap:10rpx; padding:18rpx; border:1rpx solid #d9e5e2; background:#fff; }.attention-group.level-critical { border-color:#e5aaa4; background:#fff7f6; }.attention-group.level-warning { border-color:#e6c77f; background:#fffaf0; }.attention-group.level-info { border-color:#bedbd5; background:#f5fbf9; }.group-heading { display:flex; align-items:center; justify-content:space-between; gap:12rpx; padding-bottom:10rpx; border-bottom:1rpx solid rgba(99,124,117,.16); }.group-heading text:first-child { font-size:27rpx; font-weight:800; }.group-heading text:last-child { color:#70817d; font-size:21rpx; }.notice-row { display:flex; align-items:flex-start; gap:12rpx; padding:14rpx 0; border-bottom:1rpx solid rgba(99,124,117,.12); }.notice-row:last-child { border-bottom:0; }.notice-row checkbox { flex:none; margin-top:3rpx; }.notice-content { min-width:0; flex:1; }.notice-meta { display:flex; flex-wrap:wrap; align-items:center; gap:8rpx; }.notice-meta>text { padding:5rpx 9rpx; border-radius:4rpx; background:#e9f2f0; color:#486a64; font-size:19rpx; font-weight:700; }.notice-meta .required-tag { background:#fff0d5; color:#875d0c; }.notice-meta .readonly-tag { background:#edf1f0; color:#63736f; }.notice-meta .acknowledged-tag { background:#dff4ee; color:#0f7065; }.notice-text { margin-top:10rpx; color:#243f3a; font-size:25rpx; line-height:1.65; overflow-wrap:anywhere; }.acknowledged-time { margin-top:8rpx; color:#70817d; font-size:21rpx; }.acknowledge-bar,.start-service-bar { display:grid; gap:13rpx; padding:18rpx; border:1rpx solid #bfd8d3; background:#f2faf8; }.acknowledge-bar>view { display:grid; gap:5rpx; }.acknowledge-bar text:first-child { color:#1d5149; font-size:26rpx; font-weight:800; }.acknowledge-bar text:last-child { color:#657b76; font-size:22rpx; }.acknowledge-bar button,.start-service-bar button { width:100%; min-height:84rpx; background:#0f766e; color:#fff; }.start-service-bar>text { color:#58726c; font-size:23rpx; line-height:1.5; text-align:center; }.attention-panel button[disabled] { opacity:.48; }
</style>
