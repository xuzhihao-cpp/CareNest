<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { readAuthSession } from '@/api/client';
import { getPreServiceHealthSummary } from '@/api/stageTwentyFive';
import type { PreServiceHealthSummary } from '@/types/stageTwentyFive';
import {
  ALLERGY_SEVERITY_LABELS,
  carePointList,
  DISEASE_STATUS_LABELS,
  elderProfileName,
  elderProfileSummary,
  formatBusinessDate,
  medicalFileTypeLabel,
  MEDICATION_FREQUENCY_LABELS,
  preServiceSummaryError,
  reportTitle
} from '@/utils/stageTwentyFiveRules';

const props = defineProps<{
  orderId: string;
  serviceName: string;
  scheduledStart: string;
  elderName?: string;
}>();

const emit = defineEmits<{ (event: 'close'): void }>();

const summary = ref<PreServiceHealthSummary | null>(null);
const loading = ref(false);
const error = ref('');
const previewError = ref('');
let requestSequence = 0;
let previewRequestSequence = 0;
let previewController: AbortController | null = null;
let pendingPreviewWindow: Window | null = null;

const profileName = computed(() => summary.value
  ? elderProfileName(summary.value.elderProfile, props.elderName || '服务对象')
  : props.elderName || '服务对象');
const profileMeta = computed(() => summary.value ? elderProfileSummary(summary.value.elderProfile) : '');
const carePoints = computed(() => summary.value ? carePointList(summary.value.elderProfile) : []);

function cancelPendingPreview() {
  ++previewRequestSequence;
  previewController?.abort();
  previewController = null;
  if (pendingPreviewWindow && !pendingPreviewWindow.closed) pendingPreviewWindow.close();
  pendingPreviewWindow = null;
}

async function loadSummary() {
  const orderId = props.orderId;
  const sequence = ++requestSequence;
  cancelPendingPreview();
  summary.value = null;
  error.value = '';
  previewError.value = '';
  if (!orderId) return;
  loading.value = true;
  const response = await getPreServiceHealthSummary(orderId);
  if (sequence !== requestSequence || orderId !== props.orderId) return;
  loading.value = false;
  if (response.code !== 0) {
    error.value = preServiceSummaryError(response.code);
    return;
  }
  summary.value = response.data;
}

function closeSummary() {
  ++requestSequence;
  cancelPendingPreview();
  summary.value = null;
  loading.value = false;
  error.value = '';
  previewError.value = '';
  emit('close');
}

async function openAuthorizedPreview(url?: string) {
  if (!url) return;
  cancelPendingPreview();
  previewError.value = '';
  let target: URL;
  try {
    target = new URL(url, window.location.origin);
  } catch {
    previewError.value = '病历预览地址无效，请刷新摘要后重试。';
    return;
  }
  if (!['http:', 'https:'].includes(target.protocol)) {
    previewError.value = '病历预览地址不受支持。';
    return;
  }
  const signatureKeys = [...target.searchParams.keys()].map((key) => key.toLowerCase());
  const isSignedUrl = signatureKeys.some((key) =>
    key.includes('signature') || key.startsWith('x-amz-') || key === 'token'
  );
  if (target.origin !== window.location.origin) {
    if (!isSignedUrl) {
      previewError.value = '病历预览授权已失效，请刷新摘要后重试。';
      return;
    }
    const opened = window.open(target.toString(), '_blank');
    if (opened) opened.opener = null;
    else previewError.value = '浏览器未能打开病历资料，请允许新窗口后重试。';
    return;
  }
  if (isSignedUrl) {
    const opened = window.open(target.toString(), '_blank');
    if (opened) opened.opener = null;
    else previewError.value = '浏览器未能打开病历资料，请允许新窗口后重试。';
    return;
  }

  const opened = window.open('about:blank', '_blank');
  if (!opened) {
    previewError.value = '浏览器未能打开病历资料，请允许新窗口后重试。';
    return;
  }
  opened.opener = null;
  const previewSequence = ++previewRequestSequence;
  const controller = new AbortController();
  previewController = controller;
  pendingPreviewWindow = opened;
  try {
    const session = readAuthSession();
    const response = await fetch(target.toString(), {
      headers: session ? { Authorization: `Bearer ${session.token}` } : {},
      signal: controller.signal
    });
    if (!response.ok) throw new Error('preview failed');
    const objectUrl = URL.createObjectURL(await response.blob());
    if (previewSequence !== previewRequestSequence || controller.signal.aborted) {
      URL.revokeObjectURL(objectUrl);
      opened.close();
      return;
    }
    opened.location.href = objectUrl;
    pendingPreviewWindow = null;
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch {
    if (previewSequence !== previewRequestSequence || controller.signal.aborted) return;
    opened.close();
    previewError.value = '病历资料暂时无法预览，请稍后重试。';
  } finally {
    if (previewSequence === previewRequestSequence) {
      previewController = null;
      pendingPreviewWindow = null;
    }
  }
}

watch(() => props.orderId, loadSummary, { immediate: true });
onBeforeUnmount(() => {
  ++requestSequence;
  cancelPendingPreview();
  summary.value = null;
});
</script>

<template>
  <view class="pre-service-summary">
    <view class="summary-header">
      <button type="button" class="back-command" @click="closeSummary">返回任务</button>
      <button type="button" class="refresh-command" :disabled="loading" @click="loadSummary">刷新</button>
    </view>

    <view class="service-context">
      <text class="context-kicker">服务前健康摘要</text>
      <text class="context-title">{{ profileName }}</text>
      <text v-if="profileMeta" class="context-meta">{{ profileMeta }}</text>
      <text class="context-service">{{ serviceName || '上门护理服务' }}</text>
      <text class="context-time">预约时间：{{ formatBusinessDate(scheduledStart) || '时间待确认' }}</text>
    </view>

    <view v-if="loading" class="summary-state">
      <text class="state-title">正在读取健康摘要</text>
      <text>加载完成前不会显示其他订单的健康信息。</text>
    </view>
    <view v-else-if="error" class="summary-state error-state">
      <text class="state-title">摘要读取失败</text>
      <text>{{ error }}</text>
      <button type="button" @click="loadSummary">重新读取</button>
    </view>

    <template v-else-if="summary">
      <section class="summary-section risk-section" aria-label="重点风险">
        <view class="section-heading"><view><text class="section-index">01</text><text>重点风险</text></view><text>{{ summary.riskTags.length }} 项</text></view>
        <view v-if="summary.riskTags.length" class="risk-list">
          <text v-for="risk in summary.riskTags" :key="risk.tagCode" class="risk-tag">{{ risk.tagName }}</text>
        </view>
        <view v-else class="section-empty">当前归档中暂无重点风险标签，仍请按护理规范完成服务前核对。</view>
      </section>

      <section class="summary-section allergy-section" aria-label="过敏信息">
        <view class="section-heading"><view><text class="section-index">02</text><text>过敏信息</text></view><text>{{ summary.allergies.length }} 项</text></view>
        <view v-if="summary.allergies.length" class="item-list">
          <view v-for="item in summary.allergies" :key="`${item.allergenName}-${item.reaction}`" class="summary-item">
            <view class="item-title"><strong>{{ item.allergenName }}</strong><text :class="{ severe: item.severity === 'SEVERE' }">{{ ALLERGY_SEVERITY_LABELS[item.severity] }}</text></view>
            <text v-if="item.reaction">可能反应：{{ item.reaction }}</text>
            <text v-if="item.remark">补充说明：{{ item.remark }}</text>
          </view>
        </view>
        <view v-else class="section-empty">当前归档中暂无过敏记录。</view>
      </section>

      <section class="summary-section" aria-label="当前用药">
        <view class="section-heading"><view><text class="section-index">03</text><text>当前用药</text></view><text>{{ summary.medications.length }} 项</text></view>
        <view v-if="summary.medications.length" class="item-list">
          <view v-for="item in summary.medications" :key="`${item.medicationName}-${item.startDate}`" class="summary-item">
            <view class="item-title"><strong>{{ item.medicationName }}</strong><text>{{ MEDICATION_FREQUENCY_LABELS[item.frequency] }}</text></view>
            <text>{{ item.dosage || '剂量未记录' }}<template v-if="item.timePoints.length"> · {{ item.timePoints.join('、') }}</template></text>
            <text v-if="item.remark">用药说明：{{ item.remark }}</text>
          </view>
        </view>
        <view v-else class="section-empty">当前归档中暂无用药记录。</view>
      </section>

      <section class="summary-section" aria-label="慢病与照护要点">
        <view class="section-heading"><view><text class="section-index">04</text><text>慢病与照护要点</text></view></view>
        <view v-if="summary.diseases.length" class="item-list compact-list">
          <view v-for="item in summary.diseases" :key="`${item.diseaseName}-${item.diagnosedAt}`" class="summary-item">
            <view class="item-title"><strong>{{ item.diseaseName }}</strong><text>{{ DISEASE_STATUS_LABELS[item.status] }}</text></view>
            <text v-if="item.remark">{{ item.remark }}</text>
          </view>
        </view>
        <view v-else class="section-empty">当前归档中暂无慢性病记录。</view>
        <view v-if="carePoints.length" class="care-points">
          <text v-for="point in carePoints" :key="point">{{ point }}</text>
        </view>
        <view v-else class="section-empty care-empty">当前摘要未提供额外照护要点。</view>
      </section>

      <section class="summary-section" aria-label="审核通过病历">
        <view class="section-heading"><view><text class="section-index">05</text><text>审核通过病历</text></view><text>{{ summary.approvedMedicalFiles.length }} 份</text></view>
        <view v-if="summary.approvedMedicalFiles.length" class="item-list">
          <view v-for="record in summary.approvedMedicalFiles" :key="`${record.title}-${record.occurredAt}`" class="summary-item medical-item">
            <view class="item-title"><strong>{{ record.title }}</strong><text>{{ medicalFileTypeLabel(record) }}</text></view>
            <text v-if="record.occurredAt">资料日期：{{ formatBusinessDate(record.occurredAt).slice(0, 10) }}</text>
            <text v-if="record.summary">{{ record.summary }}</text>
            <button v-if="record.previewUrl" type="button" @click="openAuthorizedPreview(record.previewUrl)">预览资料</button>
          </view>
        </view>
        <view v-else class="section-empty">暂无可供本次服务查看的已审核病历资料。</view>
        <text v-if="previewError" class="preview-error">{{ previewError }}</text>
      </section>

      <section class="summary-section" aria-label="近期服务摘要">
        <view class="section-heading"><view><text class="section-index">06</text><text>近期服务摘要</text></view><text>{{ summary.recentReports.length }} 份</text></view>
        <view v-if="summary.recentReports.length" class="item-list">
          <view v-for="record in summary.recentReports" :key="`${record.serviceName}-${record.generatedAt || record.occurredAt}`" class="summary-item report-item">
            <view class="item-title"><strong>{{ reportTitle(record) }}</strong><text>{{ formatBusinessDate(record.occurredAt || record.generatedAt) }}</text></view>
            <text>{{ record.summary }}</text>
            <text v-if="record.nursingAdvice">护理建议：{{ record.nursingAdvice }}</text>
            <text v-if="record.vitalSigns?.length">生命体征：{{ record.vitalSigns.join('；') }}</text>
          </view>
        </view>
        <view v-else class="section-empty">暂无与本次护理相关的近期服务摘要。</view>
      </section>

      <view class="read-only-note">本页信息仅用于本次护理服务前核对，不能在此修改健康档案。</view>
    </template>
  </view>
</template>

<style scoped>
.pre-service-summary { display:grid; gap:18rpx; min-width:0; color:#17312e; }
.summary-header { display:flex; align-items:center; justify-content:space-between; gap:16rpx; }
.back-command,.refresh-command,.summary-state button,.medical-item button { min-height:68rpx; margin:0; padding:0 22rpx; border:1rpx solid #bfd4cf; border-radius:4rpx; background:#fff; color:#176d65; font-size:24rpx; font-weight:700; }
.service-context { padding:24rpx 4rpx 22rpx; border-bottom:1rpx solid #d9e5e2; }
.context-kicker,.context-title,.context-meta,.context-service,.context-time,.summary-item>text,.state-title { display:block; }
.context-kicker { color:#167d73; font-size:21rpx; font-weight:800; }
.context-title { margin-top:8rpx; font-size:42rpx; font-weight:800; }
.context-meta { margin-top:6rpx; color:#5f746f; font-size:24rpx; }
.context-service { margin-top:18rpx; font-size:29rpx; font-weight:750; }
.context-time { margin-top:7rpx; color:#677a76; font-size:24rpx; }
.summary-state { display:grid; gap:10rpx; padding:28rpx 22rpx; border:1rpx dashed #c5d6d2; background:#fff; color:#637772; font-size:24rpx; line-height:1.55; }
.summary-state .state-title { color:#203a36; font-size:29rpx; font-weight:800; }.summary-state button { justify-self:start; margin-top:6rpx; }.error-state { border-color:#efc1bd; background:#fff7f6; color:#a43b34; }
.summary-section { display:grid; gap:14rpx; padding:24rpx 0 6rpx; border-top:1rpx solid #dce7e4; }
.summary-section:first-of-type { border-top:0; }.section-heading { display:flex; align-items:center; justify-content:space-between; gap:18rpx; }.section-heading>view { display:flex; align-items:center; gap:12rpx; min-width:0; }.section-heading>view>text:last-child { color:#183631; font-size:30rpx; font-weight:800; }.section-heading>text { color:#6d7e7a; font-size:22rpx; }.section-index { color:#17877c; font-size:20rpx; font-weight:800; }
.risk-section { padding:22rpx; border:1rpx solid #e7c987; background:#fffaf0; }.risk-list { display:flex; flex-wrap:wrap; gap:10rpx; }.risk-tag { padding:10rpx 14rpx; border:1rpx solid #e2bd68; border-radius:4rpx; background:#fff; color:#80580b; font-size:24rpx; font-weight:750; }
.allergy-section { padding:22rpx; border:1rpx solid #e8c0bb; background:#fff8f7; }.item-list { display:grid; gap:12rpx; }.summary-item { display:grid; gap:8rpx; padding:18rpx; border:1rpx solid #dce7e4; background:#fff; }.item-title { display:flex; align-items:flex-start; justify-content:space-between; gap:14rpx; }.item-title strong { min-width:0; color:#193833; font-size:27rpx; }.item-title text { flex:none; color:#5e746f; font-size:21rpx; }.item-title text.severe { color:#b13c34; font-weight:800; }.summary-item>text { color:#5d726d; font-size:23rpx; line-height:1.5; }
.section-empty { padding:17rpx; background:#f0f5f4; color:#687b77; font-size:23rpx; line-height:1.5; }.care-empty { margin-top:0; }.care-points { display:grid; gap:9rpx; padding:18rpx; border-left:5rpx solid #16847a; background:#eef8f5; color:#31534d; font-size:24rpx; line-height:1.55; }.care-points text { display:block; }
.medical-item button { justify-self:start; min-height:62rpx; margin-top:4rpx; }.preview-error { padding:14rpx 16rpx; background:#fff0ef; color:#a43b34; font-size:23rpx; }.report-item .item-title text { max-width:210rpx; text-align:right; }.read-only-note { padding:20rpx; border-top:1rpx solid #dce7e4; color:#647873; font-size:23rpx; line-height:1.55; text-align:center; }
@media (max-width: 390px) { .item-title { align-items:flex-start; }.report-item .item-title { display:grid; }.report-item .item-title text { max-width:none; text-align:left; } }
</style>
