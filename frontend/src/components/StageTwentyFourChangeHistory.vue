<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { getHealthArchiveChangeLogs } from '@/api/stageTwentyFour';
import type { ArchiveVersion } from '@/types/stageNineteen';
import type { HealthArchiveChangeLogRecord } from '@/types/stageTwentyFour';
import { HEALTH_REVIEW_SOURCE_LABELS } from '@/utils/stageTwentyThreeRules';
import {
  changeLogBusinessTitle,
  formatArchiveChangeValue
} from '@/utils/stageTwentyFourRules';

const props = defineProps<{
  elderId: string;
  refreshKey?: ArchiveVersion;
}>();

const records = ref<HealthArchiveChangeLogRecord[]>([]);
const loading = ref(false);
const error = ref('');
let requestSequence = 0;

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function businessError(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号无权查看这位长辈的档案变更记录。';
  if (code === 404) return '健康档案变更记录服务暂不可用。';
  if (code === 502) return '档案变更记录响应不完整，请联系平台维护人员。';
  return '档案变更记录暂时无法读取，请稍后重试。';
}

async function loadHistory() {
  const elderId = props.elderId;
  const sequence = ++requestSequence;
  records.value = [];
  error.value = '';
  if (!elderId) return;
  loading.value = true;
  const response = await getHealthArchiveChangeLogs(elderId);
  if (sequence !== requestSequence || elderId !== props.elderId) return;
  loading.value = false;
  if (response.code !== 0) {
    error.value = businessError(response.code);
    return;
  }
  records.value = [...response.data.records].sort((left, right) =>
    right.changedAt.localeCompare(left.changedAt)
  );
}

watch(() => [props.elderId, props.refreshKey] as const, loadHistory);
onMounted(loadHistory);
</script>

<template>
  <section class="change-history" aria-label="健康档案变更记录">
    <view class="history-heading">
      <view><text class="history-title">档案变更记录</text><text class="history-help">查看健康档案何时调整以及调整了哪些内容。</text></view>
      <button type="button" :disabled="loading" @click="loadHistory">刷新</button>
    </view>

    <view v-if="loading" class="history-state">正在读取变更记录...</view>
    <view v-else-if="error" class="history-state error-state"><text>{{ error }}</text><button type="button" @click="loadHistory">重新读取</button></view>
    <view v-else-if="records.length === 0" class="history-state">暂无健康档案变更记录。</view>
    <view v-else class="history-list">
      <article v-for="record in records" :key="record.changeLogId" class="history-entry">
        <view class="entry-top">
          <view><strong>{{ changeLogBusinessTitle(record) }}</strong><text>{{ formatDateTime(record.changedAt) }}</text></view>
        </view>
        <view class="change-values">
          <view><text>调整前</text><p>{{ formatArchiveChangeValue(record.fieldName, record.beforeValue) }}</p></view>
          <view class="after"><text>调整后</text><p>{{ formatArchiveChangeValue(record.fieldName, record.afterValue) }}</p></view>
        </view>
        <view v-if="record.sourceType || record.sourceSummary" class="entry-meta">
          <text>资料来源</text>
          <p>{{ record.sourceType ? HEALTH_REVIEW_SOURCE_LABELS[record.sourceType] : '健康档案维护' }}<template v-if="record.sourceSummary"> · {{ record.sourceSummary }}</template></p>
        </view>
        <view v-if="record.comment" class="entry-meta"><text>调整说明</text><p>{{ record.comment }}</p></view>
      </article>
    </view>
  </section>
</template>

<style scoped>
.change-history { display:grid; gap:18rpx; margin-top:12rpx; padding-top:24rpx; border-top:1rpx solid #dce7e4; color:#17312e; }.history-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:18rpx; }.history-heading>view { display:grid; gap:6rpx; }.history-title { font-size:31rpx; font-weight:800; }.history-help { color:#647872; font-size:23rpx; line-height:1.5; }.history-heading button,.history-state button { min-height:66rpx; margin:0; padding:0 20rpx; border:1rpx solid #bfd4cf; border-radius:4rpx; background:#fff; color:#176d65; font-size:23rpx; }.history-state { display:grid; gap:14rpx; padding:28rpx 22rpx; border:1rpx dashed #c4d4d0; background:#fff; color:#687b76; font-size:24rpx; line-height:1.55; }.history-state.error-state { border-color:#edb8b2; background:#fff2f0; color:#a53d35; }.history-state button { width:max-content; }.history-list { display:grid; gap:16rpx; }.history-entry { display:grid; gap:15rpx; padding:22rpx; border:1rpx solid #d7e3e0; background:#fff; }.entry-top { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }.entry-top>view { display:grid; gap:5rpx; min-width:0; }.entry-top strong { font-size:27rpx; }.entry-top text { color:#758680; font-size:21rpx; }.change-values { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:10rpx; }.change-values>view { padding:15rpx; border-left:5rpx solid #c8d5d2; background:#f7f9f8; }.change-values>view.after { border-left-color:#168c81; background:#eaf7f4; }.change-values text,.entry-meta text { display:block; margin-bottom:6rpx; color:#647a75; font-size:20rpx; font-weight:700; }.change-values p,.entry-meta p { margin:0; color:#29443f; font-size:23rpx; line-height:1.6; white-space:pre-wrap; overflow-wrap:anywhere; }.entry-meta { padding-top:12rpx; border-top:1rpx solid #edf2f1; }
@media (max-width:420px) { .history-heading { align-items:stretch; flex-direction:column; }.history-heading button { align-self:flex-end; }.change-values { grid-template-columns:1fr; } }
</style>
