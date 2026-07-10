<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  generateServiceReport,
  getServiceReport,
  getStageFifteenEndpointSummary
} from '@/api/stageFifteen';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { ServiceReportResponse, StageFifteenScenario } from '@/types/stageFifteen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const orderId = ref('order-002');
const report = ref<ServiceReportResponse | null>(null);
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<ServiceReportResponse> | null>(null);
const endpoints = getStageFifteenEndpointSummary();

const canGenerate = computed(
  () =>
    (props.roleCode === 'NURSE' && props.authUser?.roles.includes('NURSE')) ||
    (props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'))
);
const canView = computed(() => !!props.authUser && props.authUser.roles.includes(props.roleCode));
const reportReady = computed(() => !!report.value?.reportId);

function applyResponse(response: ApiResponse<ServiceReportResponse>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0 && response.data.reportId) {
    report.value = response.data;
    message.value = successText;
    error.value = '';
  } else if (response.code === 0) {
    report.value = null;
    message.value = '';
    error.value = '报告暂无数据';
  } else {
    report.value = null;
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function handleGenerate() {
  loading.value = true;
  const response = await generateServiceReport(orderId.value);
  loading.value = false;
  applyResponse(response, '服务报告已生成，四端可查看同一报告');
}

async function loadReport(scenario: StageFifteenScenario = 'normal') {
  if (!canView.value) {
    return;
  }
  loading.value = true;
  const response = await getServiceReport(orderId.value, scenario);
  loading.value = false;
  applyResponse(response, scenario === 'normal' ? '服务报告已读取' : '');
}

onMounted(() => {
  loadReport('normal');
});
</script>

<template>
  <view class="stage-fifteen-panel glass-panel" aria-label="阶段15服务报告">
    <view class="section-title">
      <text>⑮</text>
      <text>服务报告 MVP</text>
    </view>

    <view class="stage-fifteen-summary">
      <view>
        <text class="section-mini">report / role / order</text>
        <text class="permission-main">{{ reportReady ? report?.reportId : '-' }} / {{ props.roleCode }} / {{ orderId }}</text>
        <text class="auth-meta">家属端、长辈端查看同一报告</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-15' }}</text>
        <text class="auth-meta">只生成和查看报告，不做阶段16确认</text>
      </view>
    </view>

    <view class="stage-fifteen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="report-toolbar">
      <label class="field">
        <text>订单 orderId</text>
        <input v-model="orderId" class="input" placeholder="order-002" />
      </label>
      <view class="binding-actions">
        <button v-if="canGenerate" class="hero-action" type="button" :disabled="loading" @click="handleGenerate">
          <text>生成报告</text>
        </button>
        <button class="ghost-action" type="button" :disabled="loading" @click="loadReport('normal')">
          <text>读取报告</text>
        </button>
        <button class="ghost-action test-action" type="button" @click="loadReport('empty')">
          <text>空数据 mock</text>
        </button>
        <button class="ghost-action test-action" type="button" @click="loadReport('error')">
          <text>错误 mock</text>
        </button>
      </view>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view v-if="!reportReady && !error" class="empty-state">
      <text class="empty-icon">∅</text>
      <view>
        <text class="empty-title">暂无服务报告</text>
        <text class="empty-desc">护理端或管理端生成后，家属端和长辈端会读取同一份报告。</text>
      </view>
    </view>

    <view v-else-if="report" class="service-report-workbench">
      <view class="contract-response">
        <text class="section-mini">报告摘要 summary</text>
        <text class="permission-main">{{ report.summary }}</text>
        <text class="auth-meta">{{ report.reportId }} · {{ report.orderId }}</text>
      </view>

      <view class="report-section">
        <text class="section-mini">服务记录 serviceRecords</text>
        <view v-for="item in report.serviceRecords" :key="item" class="status-log-row">
          <text class="flow-label">{{ item }}</text>
        </view>
      </view>

      <view class="report-section">
        <text class="section-mini">生命体征 vitalSigns</text>
        <view v-for="item in report.vitalSigns" :key="item" class="status-log-row">
          <text class="flow-label">{{ item }}</text>
        </view>
      </view>

      <view class="contract-response">
        <text class="section-mini">护理建议 nursingAdvice</text>
        <text>{{ report.nursingAdvice }}</text>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次服务报告响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.reportId || '-' }} · {{ lastResponse.data.summary || '无报告' }}
      </text>
    </view>
  </view>
</template>
