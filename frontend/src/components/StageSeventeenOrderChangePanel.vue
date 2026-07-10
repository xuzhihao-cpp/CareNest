<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  cancelAdminOrder,
  cancelFamilyOrder,
  getStageSeventeenEndpointSummary,
  resetStageSeventeenMockRecords,
  rescheduleFamilyOrder
} from '@/api/stageSeventeen';
import { getFamilyOrders } from '@/api/stageTen';
import { getAdminOrders } from '@/api/stageEleven';
import type { ApiResponse } from '@/types/api';
import type { AdminOrderRecord } from '@/types/stageEleven';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { FamilyOrderResponse } from '@/types/stageTen';
import type {
  OrderChangeRequest,
  OrderChangeResponse,
  StageSeventeenScenario
} from '@/types/stageSeventeen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const orderId = ref('order_001');
const reason = ref('家属临时调整时间，保留状态日志。');
const newScheduledStart = ref('2026-07-10T14:00');
const familyOrders = ref<FamilyOrderResponse[]>([]);
const adminOrders = ref<AdminOrderRecord[]>([]);
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<OrderChangeResponse> | null>(null);
const endpoints = getStageSeventeenEndpointSummary();

const canFamilyChange = computed(() => props.roleCode === 'FAMILY' && props.authUser?.roles.includes('FAMILY'));
const canAdminChange = computed(() => props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'));
const canUsePanel = computed(() => canFamilyChange.value || canAdminChange.value);
const latestFamilyOrder = computed(() => familyOrders.value.find((item) => item.orderId === orderId.value) ?? familyOrders.value[0] ?? null);
const latestAdminOrder = computed(() => adminOrders.value.find((item) => item.orderId === orderId.value) ?? adminOrders.value[0] ?? null);

function payload(): OrderChangeRequest {
  return {
    reason: reason.value,
    newScheduledStart: newScheduledStart.value
  };
}

async function refreshOrders() {
  if (canFamilyChange.value) {
    const response = await getFamilyOrders('normal');
    familyOrders.value = response.code === 0 ? response.data.records : [];
  }
  if (canAdminChange.value) {
    const response = await getAdminOrders({ page: 1, size: 20, orderStatus: '' }, 'normal');
    adminOrders.value = response.code === 0 ? response.data.records : [];
  }
}

function applyResponse(response: ApiResponse<OrderChangeResponse>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = successText;
    error.value = '';
    orderId.value = response.data.orderId || orderId.value;
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function handleFamilyReschedule(scenario: StageSeventeenScenario = 'normal') {
  loading.value = true;
  const response = await rescheduleFamilyOrder(orderId.value, payload(), scenario);
  loading.value = false;
  applyResponse(response, '家属端已改期，家属端和管理端计划时间同步更新');
  await refreshOrders();
}

async function handleFamilyCancel(scenario: StageSeventeenScenario = 'normal') {
  loading.value = true;
  const response = await cancelFamilyOrder(orderId.value, payload(), scenario);
  loading.value = false;
  applyResponse(response, '家属端已取消订单，管理端同步显示 CANCELED');
  await refreshOrders();
}

async function handleAdminCancel(scenario: StageSeventeenScenario = 'normal') {
  loading.value = true;
  const response = await cancelAdminOrder(orderId.value, payload(), scenario);
  loading.value = false;
  applyResponse(response, '管理端已取消订单，家属端同步显示 CANCELED');
  await refreshOrders();
}

async function resetMock() {
  resetStageSeventeenMockRecords();
  lastResponse.value = null;
  message.value = '阶段17演示订单已重置为 WAIT_DISPATCH';
  error.value = '';
  await refreshOrders();
}

async function loadScenario(scenario: StageSeventeenScenario) {
  if (canAdminChange.value) {
    await handleAdminCancel(scenario);
    return;
  }
  await handleFamilyCancel(scenario);
}

onMounted(() => {
  refreshOrders();
});
</script>

<template>
  <view v-if="canUsePanel" class="stage-seventeen-panel glass-panel" aria-label="阶段17订单取消与改期">
    <view class="section-title">
      <text>⑰</text>
      <text>订单取消与改期 MVP</text>
    </view>

    <view class="stage-seventeen-summary">
      <view>
        <text class="section-mini">order / role / status</text>
        <text class="permission-main">
          {{ orderId }} / {{ props.roleCode }} /
          {{ latestFamilyOrder?.orderStatus || latestAdminOrder?.orderStatus || 'WAIT_DISPATCH' }}
        </text>
        <text class="auth-meta">取消/改期后家属端、管理端状态一致</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-17' }}</text>
        <text class="auth-meta">家属校验 ACTIVE 绑定和 ORDER_CREATE scope</text>
      </view>
    </view>

    <view class="stage-seventeen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="report-toolbar">
      <label class="field">
        <text>订单 orderId</text>
        <input v-model="orderId" class="input" placeholder="order-001" />
      </label>
      <label class="field">
        <text>新时间 newScheduledStart</text>
        <input v-model="newScheduledStart" class="input" placeholder="2026-07-10T14:00" />
      </label>
      <label class="field">
        <text>原因 reason</text>
        <input v-model="reason" class="input" placeholder="取消或改期原因" />
      </label>
    </view>

    <view class="binding-actions">
      <button v-if="canFamilyChange" class="hero-action" type="button" :disabled="loading" @click="handleFamilyReschedule()">
        <text>家属改期</text>
      </button>
      <button v-if="canFamilyChange" class="ghost-action" type="button" :disabled="loading" @click="handleFamilyCancel()">
        <text>家属取消</text>
      </button>
      <button v-if="canAdminChange" class="hero-action" type="button" :disabled="loading" @click="handleAdminCancel()">
        <text>管理端取消</text>
      </button>
      <button class="ghost-action test-action" type="button" :disabled="loading" @click="resetMock">
        <text>重置阶段17 mock</text>
      </button>
      <button class="ghost-action test-action" type="button" :disabled="loading" @click="loadScenario('empty')">
        <text>空数据 mock</text>
      </button>
      <button class="ghost-action test-action" type="button" :disabled="loading" @click="loadScenario('error')">
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
        <text class="section-mini">家属端订单快照</text>
        <text v-if="latestFamilyOrder" class="permission-main">
          {{ latestFamilyOrder.orderNo }} / {{ latestFamilyOrder.orderStatus }}
        </text>
        <text v-if="latestFamilyOrder" class="auth-meta">{{ latestFamilyOrder.orderId }}</text>
        <text v-else class="auth-meta">当前角色不可读取或暂无家属端订单快照</text>
      </view>

      <view class="contract-response">
        <text class="section-mini">管理端订单快照</text>
        <text v-if="latestAdminOrder" class="permission-main">
          {{ latestAdminOrder.orderNo }} / {{ latestAdminOrder.orderStatus }}
        </text>
        <text v-if="latestAdminOrder" class="auth-meta">
          {{ latestAdminOrder.orderId }} / {{ latestAdminOrder.scheduledStart }}
        </text>
        <text v-else class="auth-meta">当前角色不可读取或暂无管理端订单快照</text>
      </view>
    </view>

    <view v-if="latestAdminOrder" class="report-section">
      <text class="section-mini">order_status_log</text>
      <view v-for="log in latestAdminOrder.statusLogs" :key="log.statusLogId" class="status-log-row">
        <text class="flow-label">{{ log.fromStatus || 'INIT' }} → {{ log.toStatus }}</text>
        <text class="flow-time">{{ log.changedBy }} / {{ log.changeReason }}</text>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次取消/改期响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.orderId }} / {{ lastResponse.data.orderStatus }} / {{ lastResponse.data.scheduledStart }}
      </text>
    </view>
  </view>
</template>
