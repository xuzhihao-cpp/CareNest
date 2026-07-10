<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getFamilyBindings } from '@/api/stageSix';
import { displayLabel } from '@/utils/displayLabels';
import {
  ackElderReport,
  ackFamilyReport,
  decideArchiveSuggestions,
  getHealthInfoReviewTasks,
  getLatestReportAck,
  getStageSixteenEndpointSummary
} from '@/api/stageSixteen';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type {
  HealthInfoReviewTaskRecord,
  ReportAckRecord,
  ReportAckRequest,
  ReportAckResponse,
  StageSixteenScenario
} from '@/types/stageSixteen';
import type { BindingResponse } from '@/types/stageSix';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const reportId = ref('');
const satisfaction = ref(5);
const remark = ref('报告已查看，同意本次服务记录与护理建议。');
const acceptedSuggestionIds = ref('suggestion-bp,suggestion-sleep');
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<ReportAckResponse> | null>(null);
const latestAck = ref<ReportAckRecord | null>(getLatestReportAck(reportId.value));
const reviewTasks = ref<HealthInfoReviewTaskRecord[]>(getHealthInfoReviewTasks(reportId.value));
const endpoints = getStageSixteenEndpointSummary();
const familyBindings = ref<BindingResponse[]>([]);

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
const canFamilyArchiveDecision = computed(() =>
  canFamilyAck.value && familyScopes.value.has('ARCHIVE_EDIT')
);
const canObserve = computed(() => ['ADMIN', 'NURSE'].includes(props.roleCode));

function payload(ackResult: ReportAckRequest['ackResult']): ReportAckRequest {
  return {
    ackResult,
    satisfaction: Number(satisfaction.value),
    remark: remark.value,
    acceptedSuggestionIds: acceptedSuggestionIds.value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  };
}

function refreshDerivedState() {
  latestAck.value = getLatestReportAck(reportId.value);
  reviewTasks.value = getHealthInfoReviewTasks(reportId.value);
}

function applyResponse(response: ApiResponse<ReportAckResponse>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0 && response.data.ackId) {
    message.value = successText;
    error.value = '';
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
  refreshDerivedState();
}

async function submitElderAck(ackResult: ReportAckRequest['ackResult'] = 'ACCEPTED') {
  loading.value = true;
  const response = await ackElderReport(reportId.value, payload(ackResult));
  loading.value = false;
  applyResponse(response, ackResult === 'ACCEPTED' ? '长辈端已确认报告，订单状态同步更新' : '长辈端已提出异议，报告回到待处理');
}

async function submitFamilyAck(ackResult: ReportAckRequest['ackResult'] = 'ACCEPTED') {
  loading.value = true;
  const response = await ackFamilyReport(reportId.value, payload(ackResult));
  loading.value = false;
  applyResponse(response, ackResult === 'ACCEPTED' ? '家属端已确认报告，订单状态同步更新' : '家属端已提出异议，报告回到待处理');
}

async function submitArchiveDecision() {
  loading.value = true;
  const response = await decideArchiveSuggestions(reportId.value, payload('ACCEPTED'));
  loading.value = false;
  applyResponse(response, '家属端已确认档案建议，生成 health_info_review_task 待审核任务');
}

async function loadScenario(scenario: StageSixteenScenario) {
  loading.value = true;
  const response = canFamilyAck.value
    ? await ackFamilyReport(reportId.value, payload('ACCEPTED'), scenario)
    : await ackElderReport(reportId.value, payload('ACCEPTED'), scenario);
  loading.value = false;
  applyResponse(response, '');
}

function handleReportIdBlur() {
  refreshDerivedState();
}

onMounted(async () => {
  if (props.roleCode !== 'FAMILY') {
    return;
  }
  const response = await getFamilyBindings();
  familyBindings.value = response.code === 0 ? response.data : [];
});
</script>

<template>
  <view class="stage-sixteen-panel glass-panel" aria-label="阶段16报告确认">
    <view class="section-title">
      <text>⑯</text>
      <text>长辈/家属确认 MVP</text>
    </view>

    <view class="stage-sixteen-summary">
      <view>
        <text class="section-mini">ack / report / role</text>
        <text class="permission-main">{{ latestAck?.ackId ?? '-' }} / {{ reportId }} / {{ props.roleCode }}</text>
        <text class="auth-meta">确认后报告状态、订单状态同步更新</text>
      </view>
      <view>
        <text class="section-mini">reportStatus / orderStatus</text>
        <text class="permission-main">{{ displayLabel(latestAck?.reportStatus ?? 'WAIT_CONFIRM') }} / {{ displayLabel(latestAck?.orderStatus ?? 'WAIT_CONFIRM') }}</text>
        <text class="auth-meta">{{ lastTraceId || 'mock-16' }}</text>
      </view>
    </view>

    <view class="stage-sixteen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="report-toolbar">
      <label class="field">
        <text>报告 reportId</text>
        <input v-model="reportId" class="input" placeholder="report-001" @blur="handleReportIdBlur" />
      </label>
      <label class="field">
        <text>满意度 satisfaction</text>
        <input v-model="satisfaction" class="input" type="number" />
      </label>
      <label class="field">
        <text>档案建议 acceptedSuggestionIds</text>
        <input v-model="acceptedSuggestionIds" class="input" placeholder="suggestion-bp,suggestion-sleep" />
      </label>
      <label class="field">
        <text>备注 remark</text>
        <input v-model="remark" class="input" placeholder="确认备注" />
      </label>
    </view>

    <view class="binding-actions">
      <button v-if="canElderAck" class="hero-action" type="button" :disabled="loading || !reportId" @click="submitElderAck('ACCEPTED')">
        <text>长辈确认报告</text>
      </button>
      <button v-if="canElderAck" class="ghost-action" type="button" :disabled="loading || !reportId" @click="submitElderAck('REJECTED')">
        <text>长辈提出异议</text>
      </button>
      <button v-if="canFamilyAck" class="hero-action" type="button" :disabled="loading || !reportId" @click="submitFamilyAck('ACCEPTED')">
        <text>家属确认报告</text>
      </button>
      <button v-if="canFamilyAck" class="ghost-action" type="button" :disabled="loading || !reportId" @click="submitFamilyAck('REJECTED')">
        <text>家属提出异议</text>
      </button>
      <button v-if="canFamilyArchiveDecision" class="ghost-action" type="button" :disabled="loading || !reportId" @click="submitArchiveDecision">
        <text>档案建议确认</text>
      </button>
      <text v-if="props.roleCode === 'FAMILY' && !canFamilyAck" class="field-error">当前授权不包含“确认服务报告”</text>
      <text v-else-if="props.roleCode === 'FAMILY' && !canFamilyArchiveDecision" class="field-error">确认档案建议还需要“编辑健康归档”授权</text>
      <button class="ghost-action test-action" type="button" :disabled="loading" @click="loadScenario('empty')">
        <text>空数据 mock</text>
      </button>
      <button class="ghost-action test-action" type="button" :disabled="loading" @click="loadScenario('error')">
        <text>错误 mock</text>
      </button>
    </view>

    <view v-if="canObserve" class="empty-state">
      <text class="empty-icon">↻</text>
      <view>
        <text class="empty-title">阶段16状态观察</text>
        <text class="empty-desc">管理端/护理端不提交确认，只观察确认后报告状态与订单状态是否同步。</text>
      </view>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view v-if="latestAck" class="service-report-workbench">
      <view class="contract-response">
        <text class="section-mini">care_report_ack</text>
        <text class="permission-main">{{ displayLabel(latestAck.ackResult) }} / 满意度 {{ latestAck.satisfaction }}</text>
        <text class="auth-meta">{{ latestAck.operatorRole }} {{ latestAck.operatorId }} · {{ latestAck.createdAt }}</text>
        <text>{{ latestAck.remark }}</text>
      </view>

      <view class="contract-response">
        <text class="section-mini">订单状态同步</text>
        <text>{{ latestAck.orderId }} · {{ displayLabel(latestAck.reportStatus) }} → {{ displayLabel(latestAck.orderStatus) }}</text>
      </view>
    </view>

    <view class="report-section">
      <text class="section-mini">health_info_review_task</text>
      <view v-if="reviewTasks.length === 0" class="status-log-row">
        <text class="flow-label">暂无档案建议待审核任务</text>
      </view>
      <view v-for="item in reviewTasks" :key="item.taskId" class="status-log-row">
        <text class="flow-label">{{ item.taskId }} / {{ item.suggestionId }} / {{ displayLabel(item.status) }}</text>
        <text class="flow-time">{{ item.fieldName }}：{{ item.newValue }}</text>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次报告确认响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.ackId || '-' }} · {{ lastResponse.data.ackResult }} · {{ lastResponse.data.reportStatus }}
      </text>
    </view>
  </view>
</template>
