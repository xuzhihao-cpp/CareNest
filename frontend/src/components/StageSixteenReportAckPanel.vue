<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getServiceReport } from '@/api/stageFifteen';
import { getFamilyBindings } from '@/api/stageSix';
import {
  ackElderReport,
  ackFamilyReport,
  decideArchiveSuggestions,
  getHealthInfoReviewTasks,
  getLatestReportAck,
  getPendingReports
} from '@/api/stageSixteen';
import { displayLabel } from '@/utils/displayLabels';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { BindingResponse } from '@/types/stageSix';
import type { ServiceReportResponse } from '@/types/stageFifteen';
import type {
  HealthInfoReviewTaskRecord,
  PendingReportRecord,
  ReportAckRecord,
  ReportAckRequest,
  ReportAckResponse
} from '@/types/stageSixteen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const reportId = ref('');
const pendingReports = ref<PendingReportRecord[]>([]);
const reportContent = ref<ServiceReportResponse | null>(null);
const satisfaction = ref(80);
const remark = ref('已查看本次服务报告，同意服务记录与护理建议。');
const loading = ref(false);
const message = ref('');
const error = ref('');
const familyBindings = ref<BindingResponse[]>([]);
const latestAck = ref<ReportAckRecord | null>(null);
const reviewTasks = ref<HealthInfoReviewTaskRecord[]>([]);

const canElderAck = computed(() => props.roleCode === 'ELDER' && props.authUser?.roles.includes('ELDER'));
const familyScopes = computed(() => new Set(
  familyBindings.value
    .filter((binding) => binding.bindingStatus === 'ACTIVE')
    .flatMap((binding) => binding.scopeCodes)
));
const canFamilyAck = computed(() =>
  props.roleCode === 'FAMILY'
  && props.authUser?.roles.includes('FAMILY')
  && familyScopes.value.has('REPORT_CONFIRM')
);
const canFamilyArchiveDecision = computed(() => canFamilyAck.value && familyScopes.value.has('ARCHIVE_EDIT'));
const selectedPendingReport = computed(() =>
  pendingReports.value.find((item) => item.reportId === reportId.value) ?? null
);

function updateSatisfaction(event: { detail: { value: number } }) {
  satisfaction.value = Number(event.detail.value);
}

function requestPayload(ackResult: ReportAckRequest['ackResult']): ReportAckRequest {
  return {
    ackResult,
    satisfaction: Number(satisfaction.value),
    remark: remark.value.trim(),
    acceptedSuggestionIds: []
  };
}

function refreshDerivedState() {
  latestAck.value = getLatestReportAck(reportId.value);
  reviewTasks.value = getHealthInfoReviewTasks(reportId.value);
}

async function loadReportContent(orderId: string) {
  reportContent.value = null;
  const response = await getServiceReport(orderId);
  if (response.code === 0) {
    reportContent.value = response.data;
  }
}

async function loadPendingReports() {
  if (!canElderAck.value && props.roleCode !== 'FAMILY') return;
  const response = await getPendingReports(canElderAck.value ? 'ELDER' : 'FAMILY');
  if (response.code !== 0) {
    error.value = '暂时无法读取待确认的服务报告，请稍后重试。';
    return;
  }

  pendingReports.value = response.data;
  if (!reportId.value || !response.data.some((item) => item.reportId === reportId.value)) {
    reportId.value = response.data[0]?.reportId ?? '';
  }
  refreshDerivedState();
  const selected = response.data.find((item) => item.reportId === reportId.value);
  if (selected) {
    await loadReportContent(selected.orderId);
  } else {
    reportContent.value = null;
  }
}

function selectPendingReport(item: PendingReportRecord) {
  reportId.value = item.reportId;
  refreshDerivedState();
  void loadReportContent(item.orderId);
}

async function submitAcknowledgement(ackResult: ReportAckRequest['ackResult']) {
  if (!reportId.value) return;
  loading.value = true;
  error.value = '';
  const response = canElderAck.value
    ? await ackElderReport(reportId.value, requestPayload(ackResult))
    : await ackFamilyReport(reportId.value, requestPayload(ackResult));
  loading.value = false;

  if (response.code !== 0 || !response.data.ackId) {
    error.value = '提交未成功，请检查授权范围或稍后重试。';
    return;
  }

  latestAck.value = getLatestReportAck(reportId.value);
  reviewTasks.value = getHealthInfoReviewTasks(reportId.value);
  message.value = ackResult === 'ACCEPTED' ? '已确认本次服务报告。' : '已提交异议，平台将跟进处理。';
  await loadPendingReports();
}

async function submitArchiveDecision() {
  if (!reportId.value) return;
  loading.value = true;
  const response: ApiResponse<ReportAckResponse> = await decideArchiveSuggestions(
    reportId.value,
    requestPayload('ACCEPTED')
  );
  loading.value = false;
  if (response.code === 0) {
    message.value = '档案建议已提交审核。';
    refreshDerivedState();
  } else {
    error.value = '档案建议提交未成功，请稍后重试。';
  }
}

onMounted(async () => {
  if (props.roleCode === 'FAMILY') {
    const response = await getFamilyBindings();
    familyBindings.value = response.code === 0 ? response.data : [];
  }
  await loadPendingReports();
});
</script>

<template>
  <view v-if="canElderAck || props.roleCode === 'FAMILY'" class="stage-sixteen-panel glass-panel" aria-label="服务报告确认">
    <view class="section-title">
      <text>服务报告确认</text>
    </view>

    <view class="report-toolbar">
      <view class="field">
        <text>待确认的服务报告</text>
        <view v-if="pendingReports.length" class="order-option-list">
          <button
            v-for="item in pendingReports"
            :key="item.reportId"
            class="order-option"
            :class="{ active: reportId === item.reportId }"
            type="button"
            @click="selectPendingReport(item)"
          >
            <view>
              <text class="flow-label">{{ item.elderName }}的服务报告</text>
              <text class="flow-time">请选择后查看本次服务内容</text>
            </view>
            <text class="tag tag-amber">待确认</text>
          </button>
        </view>
        <text v-else class="auth-meta">暂无待确认的服务报告。</text>
      </view>
    </view>

    <view v-if="selectedPendingReport && reportContent" class="service-report-workbench">
      <view class="contract-response">
        <text class="section-mini">本次服务情况</text>
        <text class="permission-main">{{ reportContent.summary || '本次服务已完成。' }}</text>
      </view>
      <view v-if="reportContent.serviceRecords.length" class="contract-response">
        <text class="section-mini">服务记录</text>
        <text v-for="item in reportContent.serviceRecords" :key="item" class="report-copy">{{ item }}</text>
      </view>
      <view v-if="reportContent.vitalSigns.length" class="contract-response">
        <text class="section-mini">生命体征</text>
        <text class="report-copy">{{ reportContent.vitalSigns.join('、') }}</text>
      </view>
      <view class="contract-response">
        <text class="section-mini">护理建议</text>
        <text class="report-copy">{{ reportContent.nursingAdvice || '暂无护理建议。' }}</text>
      </view>
    </view>

    <view v-if="selectedPendingReport" class="report-toolbar">
      <view class="field satisfaction-field">
        <view class="satisfaction-heading"><text>满意度</text><text>{{ satisfaction }} 分</text></view>
        <slider :value="satisfaction" :min="0" :max="100" :step="1" activeColor="#0f8f8a" backgroundColor="#dbe9e6" block-color="#0f8f8a" @changing="updateSatisfaction" @change="updateSatisfaction" />
        <view class="satisfaction-scale"><text>0</text><text>一般</text><text>100</text></view>
      </view>
      <label class="field">
        <text>确认说明</text>
        <input v-model="remark" class="input" maxlength="200" placeholder="可补充本次服务的反馈" />
      </label>
    </view>

    <view v-if="selectedPendingReport" class="binding-actions">
      <button
        v-if="canElderAck || canFamilyAck"
        class="hero-action"
        type="button"
        :disabled="loading"
        @click="submitAcknowledgement('ACCEPTED')"
      >确认报告</button>
      <button
        v-if="canElderAck || canFamilyAck"
        class="ghost-action"
        type="button"
        :disabled="loading"
        @click="submitAcknowledgement('REJECTED')"
      >提出异议</button>
      <button
        v-if="canFamilyArchiveDecision && reviewTasks.length"
        class="ghost-action"
        type="button"
        :disabled="loading"
        @click="submitArchiveDecision"
      >处理档案建议</button>
      <text v-if="props.roleCode === 'FAMILY' && !canFamilyAck" class="field-error">当前绑定不包含服务报告确认权限。</text>
    </view>

    <view v-if="reviewTasks.length" class="report-section">
      <text class="section-mini">待审核的档案建议</text>
      <view v-for="item in reviewTasks" :key="item.taskId" class="status-log-row">
        <text class="flow-label">{{ item.fieldName }}：{{ item.newValue }}</text>
        <text class="flow-time">{{ displayLabel(item.status) }}</text>
      </view>
    </view>

    <view v-if="message" class="success-banner"><text>{{ message }}</text></view>
    <view v-if="error" class="error-banner" role="alert"><text>{{ error }}</text></view>
  </view>
</template>

<style scoped>
.satisfaction-field { gap: 10px; }
.satisfaction-heading, .satisfaction-scale { display: flex; align-items: center; justify-content: space-between; }
.satisfaction-heading text:last-child { color: #0f766e; font-weight: 700; }
.satisfaction-scale { color: #748580; font-size: 13px; }
</style>
