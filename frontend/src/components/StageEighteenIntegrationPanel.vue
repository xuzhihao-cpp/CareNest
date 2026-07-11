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
  DemoDataStatusResponse,
  HealthStatusResponse,
  StageEighteenFlowStep,
  StageEighteenScenario
} from '@/types/stageEighteen';

const props = defineProps<{ roleCode: RoleCode; authUser: AuthUser | null }>();

const healthResponse = ref<ApiResponse<HealthStatusResponse> | null>(null);
const adminStatusResponse = ref<ApiResponse<DemoDataStatusResponse> | null>(null);
const flowSteps = ref<StageEighteenFlowStep[]>(getStageEighteenFlowSteps());
const loading = ref(false);
const message = ref('');
const error = ref('');
const endpoints = getStageEighteenEndpointSummary();

const canReadAdminStatus = computed(() => props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'));
const readyCount = computed(() => flowSteps.value.filter((item) => item.status === 'READY').length);
const scenarioCount = computed(() => adminStatusResponse.value?.data.scenarioCount ?? 0);
const modeLabel = computed(() => (isMockEnabled() ? 'contract mock' : 'real api'));

function statusClass(status: StageEighteenFlowStep['status']) {
  if (status === 'READY') return 'tag-teal';
  if (status === 'MOCK_ONLY') return 'tag-amber';
  return 'tag-coral';
}

function applyResponses(
  health: ApiResponse<HealthStatusResponse>,
  adminStatus: ApiResponse<DemoDataStatusResponse> | null
) {
  healthResponse.value = health;
  adminStatusResponse.value = adminStatus;
  if (health.code === 0 && (!adminStatus || adminStatus.code === 0 || adminStatus.code === 403)) {
    message.value = 'Integration status refreshed';
    error.value = '';
  } else {
    message.value = '';
    error.value = `${health.code} ${health.message}`;
  }
}

async function refreshStatus(scenario: StageEighteenScenario = 'normal') {
  loading.value = true;
  const health = await getIntegrationHealth(scenario);
  const adminStatus = canReadAdminStatus.value
    ? await getAdminDemoDataStatus(scenario)
    : null;
  loading.value = false;
  applyResponses(health, adminStatus);
}

onMounted(() => refreshStatus());
</script>

<template>
  <view class="stage-eighteen-panel glass-panel" aria-label="Stage 18 integration">
    <view class="section-title">
      <text>Stage 18</text>
      <text>MVP integration status</text>
    </view>

    <view class="stage-eighteen-summary">
      <view>
        <text class="section-mini">health / database / version</text>
        <text class="permission-main">
          {{ healthResponse?.data.status ?? '-' }} /
          {{ healthResponse?.data.dbConnected ? 'connected' : 'disconnected' }} /
          {{ healthResponse?.data.version ?? '-' }}
        </text>
        <text class="auth-meta">{{ healthResponse?.data.appName ?? 'carenest-user' }}</text>
      </view>
      <view>
        <text class="section-mini">demo accounts / scenarios / mode</text>
        <text class="permission-main">
          {{ adminStatusResponse?.data.accounts.length ?? 0 }} /
          {{ scenarioCount }} /
          {{ modeLabel }}
        </text>
        <text class="auth-meta">{{ healthResponse?.traceId ?? 'mock-18-health' }}</text>
      </view>
    </view>

    <view class="stage-eighteen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="binding-actions">
      <button class="hero-action" type="button" :disabled="loading" @click="refreshStatus('normal')">
        <text>Refresh</text>
      </button>
      <button class="ghost-action test-action" type="button" :disabled="loading" @click="refreshStatus('empty')">
        <text>Empty mock</text>
      </button>
      <button class="ghost-action test-action" type="button" :disabled="loading" @click="refreshStatus('error')">
        <text>Error mock</text>
      </button>
    </view>

    <view v-if="message" class="success-banner"><text>{{ message }}</text></view>
    <view v-if="error" class="error-banner" role="alert"><text>{{ error }}</text></view>

    <view class="report-section">
      <text class="section-mini">Implemented stages {{ readyCount }} / {{ flowSteps.length }}</text>
      <view v-for="step in flowSteps" :key="step.stepId" class="status-log-row">
        <text class="flow-label">Stage {{ step.sourceStage }}: {{ step.label }}</text>
        <text class="tag" :class="statusClass(step.status)">{{ step.status }}</text>
        <text class="flow-time">{{ step.ownerRole }}</text>
      </view>
    </view>

    <view v-if="healthResponse" class="contract-response">
      <text class="section-mini">GET /api/v1/health response</text>
      <text>{{ healthResponse.code }} / {{ healthResponse.message }} / {{ healthResponse.traceId }}</text>
      <text>
        {{ healthResponse.data.status }} / {{ healthResponse.data.dbConnected }} /
        {{ healthResponse.data.serverTime }}
      </text>
    </view>

    <view v-if="adminStatusResponse" class="contract-response">
      <text class="section-mini">GET /api/v1/admin/demo-data/status response</text>
      <text>{{ adminStatusResponse.code }} / {{ adminStatusResponse.message }} / {{ adminStatusResponse.traceId }}</text>
      <text>
        ready {{ adminStatusResponse.data.ready }} / accounts {{ adminStatusResponse.data.accounts.join(',') }} /
        scenarios {{ adminStatusResponse.data.scenarioCount }}
      </text>
    </view>
  </view>
</template>
