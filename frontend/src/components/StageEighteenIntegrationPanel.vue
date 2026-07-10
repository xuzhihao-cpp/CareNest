<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { isMockEnabled } from '@/api/client';
import {
  getAdminDemoDataStatus,
  getIntegrationHealth,
  getStageEighteenEndpointSummary,
  getStageEighteenFlowSteps
} from '@/api/stageEighteen';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type {
  StageEighteenFlowStep,
  StageEighteenScenario,
  StageEighteenStatusResponse
} from '@/types/stageEighteen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const healthResponse = ref<ApiResponse<StageEighteenStatusResponse> | null>(null);
const adminStatusResponse = ref<ApiResponse<StageEighteenStatusResponse> | null>(null);
const flowSteps = ref<StageEighteenFlowStep[]>(getStageEighteenFlowSteps());
const loading = ref(false);
const message = ref('');
const error = ref('');
const endpoints = getStageEighteenEndpointSummary();

const canReadAdminStatus = computed(() => props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'));
const readyCount = computed(() => flowSteps.value.filter((item) => item.status === 'READY').length);
const scenarioCount = computed(() => healthResponse.value?.data.scenarioCount ?? flowSteps.value.length);
const modeLabel = computed(() => (isMockEnabled() ? 'contract mock' : 'real api'));

function statusClass(status: StageEighteenFlowStep['status']) {
  if (status === 'READY') {
    return 'tag-teal';
  }
  if (status === 'MOCK_ONLY') {
    return 'tag-amber';
  }
  return 'tag-coral';
}

function applyResponses(
  health: ApiResponse<StageEighteenStatusResponse>,
  adminStatus: ApiResponse<StageEighteenStatusResponse> | null
) {
  healthResponse.value = health;
  adminStatusResponse.value = adminStatus;
  if (health.code === 0 && (!adminStatus || adminStatus.code === 0 || adminStatus.code === 403)) {
    message.value = '阶段18联调状态已刷新';
    error.value = '';
  } else {
    message.value = '';
    error.value = `${health.code} ${health.message}`;
  }
}

async function refreshStatus(scenario: StageEighteenScenario = 'normal') {
  loading.value = true;
  const health = await getIntegrationHealth(scenario);
  const adminStatus = canReadAdminStatus.value || props.roleCode !== 'ADMIN'
    ? await getAdminDemoDataStatus(scenario)
    : null;
  loading.value = false;
  applyResponses(health, adminStatus);
}

onMounted(() => {
  refreshStatus();
});
</script>

<template>
  <view class="stage-eighteen-panel glass-panel" aria-label="阶段18 MVP全流程联调">
    <view class="section-title">
      <text>⑱</text>
      <text>MVP 全流程联调</text>
    </view>

    <view class="stage-eighteen-summary">
      <view>
        <text class="section-mini">ready / accounts / scenarios</text>
        <text class="permission-main">
          {{ healthResponse?.data.ready ? 'ready' : 'not ready' }} /
          {{ healthResponse?.data.accounts ?? 0 }} /
          {{ scenarioCount }}
        </text>
        <text class="auth-meta">从登录到确认服务可连续演示，中途不改数据库</text>
      </view>
      <view>
        <text class="section-mini">mode / traceId</text>
        <text class="permission-main">{{ modeLabel }} / {{ healthResponse?.traceId || 'mock-18' }}</text>
        <text class="auth-meta">支线若仍走 mock，页面显式标记 contract mock</text>
      </view>
    </view>

    <view class="stage-eighteen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="binding-actions">
      <button class="hero-action" type="button" :disabled="loading" @click="refreshStatus('normal')">
        <text>刷新联调状态</text>
      </button>
      <button class="ghost-action" type="button" :disabled="loading" @click="refreshStatus('empty')">
        <text>空数据 mock</text>
      </button>
      <button class="ghost-action" type="button" :disabled="loading" @click="refreshStatus('error')">
        <text>错误 mock</text>
      </button>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view class="service-report-workbench">
      <view class="contract-response">
        <text class="section-mini">GET /api/v1/health</text>
        <text class="permission-main">
          {{ healthResponse?.code ?? '-' }} /
          {{ healthResponse?.data.ready ? 'ready' : 'not ready' }}
        </text>
        <text class="auth-meta">
          accounts {{ healthResponse?.data.accounts ?? 0 }} /
          scenarioCount {{ healthResponse?.data.scenarioCount ?? 0 }}
        </text>
      </view>

      <view class="contract-response">
        <text class="section-mini">GET /api/v1/admin/demo-data/status</text>
        <text class="permission-main">
          {{ adminStatusResponse?.code ?? (canReadAdminStatus ? '-' : '403') }} /
          {{ adminStatusResponse?.data.ready ? 'ready' : 'protected' }}
        </text>
        <text class="auth-meta">
          {{ adminStatusResponse?.traceId || (canReadAdminStatus ? '待刷新' : '非管理端按权限拒绝') }}
        </text>
      </view>
    </view>

    <view class="report-section">
      <text class="section-mini">主流程连续演示节点 {{ readyCount }} / {{ flowSteps.length }}</text>
      <view v-for="step in flowSteps" :key="step.stepId" class="status-log-row">
        <text class="flow-label">{{ step.sourceStage }} · {{ step.label }}</text>
        <text class="tag" :class="statusClass(step.status)">{{ step.status }}</text>
        <text class="flow-time">{{ step.ownerRole }}</text>
      </view>
    </view>

    <view v-if="healthResponse" class="contract-response">
      <text class="section-mini">最近一次阶段18响应 DTO</text>
      <text>{{ healthResponse.code }} / {{ healthResponse.message }} / {{ healthResponse.traceId }}</text>
      <text>
        ready {{ healthResponse.data.ready }} / accounts {{ healthResponse.data.accounts }} / scenarioCount
        {{ healthResponse.data.scenarioCount }}
      </text>
    </view>
  </view>
</template>
