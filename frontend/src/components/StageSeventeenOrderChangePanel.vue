<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { displayLabel } from '@/utils/displayLabels';
import {
  cancelAdminOrder,
  cancelFamilyOrder,
  getStageSeventeenEndpointSummary,
  rescheduleFamilyOrder
} from '@/api/stageSeventeen';
import { getFamilyOrders } from '@/api/stageTen';
import { getAdminOrders } from '@/api/stageEleven';
import { getFamilyBindings } from '@/api/stageSix';
import type { ApiResponse } from '@/types/api';
import type { AdminOrderRecord } from '@/types/stageEleven';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { FamilyOrderResponse } from '@/types/stageTen';
import type { BindingResponse } from '@/types/stageSix';
import type {
  OrderChangeRequest,
  OrderChangeResponse,
  StageSeventeenScenario
} from '@/types/stageSeventeen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

function padTime(value: number) {
  return String(value).padStart(2, '0');
}

function localDate(value = new Date()) {
  return `${value.getFullYear()}-${padTime(value.getMonth() + 1)}-${padTime(value.getDate())}`;
}

function initialRescheduleTime() {
  const date = new Date(Date.now() + 2 * 60 * 60 * 1000);
  date.setMinutes(0, 0, 0);
  return `${localDate(date)}T${padTime(date.getHours())}:${padTime(date.getMinutes())}`;
}

const orderId = ref('');
const reason = ref('家属临时调整时间，保留状态日志。');
const newScheduledStart = ref(initialRescheduleTime());
const familyOrders = ref<FamilyOrderResponse[]>([]);
const adminOrders = ref<AdminOrderRecord[]>([]);
const familyBindings = ref<BindingResponse[]>([]);
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
const selectableOrders = computed(() => {
  const source = canFamilyChange.value ? familyOrders.value : adminOrders.value;
  return source.filter((item) => ['WAIT_DISPATCH', 'DISPATCHED', 'ACCEPTED', 'ON_THE_WAY'].includes(item.orderStatus));
});
const rescheduleDate = computed(() => newScheduledStart.value.slice(0, 10));
const rescheduleTime = computed(() => newScheduledStart.value.slice(11, 16));
const rescheduleTimeValid = computed(() => new Date(newScheduledStart.value).getTime() > Date.now());
const canFamilyChangeSelectedOrder = computed(() => {
  if (!canFamilyChange.value || !latestFamilyOrder.value?.elderId) {
    return false;
  }
  return familyBindings.value.some((binding) =>
    binding.elderId === latestFamilyOrder.value?.elderId
    && binding.bindingStatus === 'ACTIVE'
    && binding.scopeCodes.includes('ORDER_CREATE')
  );
});

function payload(): OrderChangeRequest {
  return {
    reason: reason.value,
    newScheduledStart: newScheduledStart.value
  };
}

function scheduledTime(order: FamilyOrderResponse | AdminOrderRecord) {
  return 'scheduledStart' in order ? order.scheduledStart : '预约时间待确认';
}

function selectRescheduleDate(event: { detail: { value: string } }) {
  newScheduledStart.value = `${event.detail.value}T${rescheduleTime.value || '09:00'}`;
}

function selectRescheduleTime(event: { detail: { value: string } }) {
  newScheduledStart.value = `${rescheduleDate.value || localDate()}T${event.detail.value}`;
}

function orderServiceName(order: FamilyOrderResponse | AdminOrderRecord) {
  return order.serviceName || '护理服务';
}

function selectOrder(order: FamilyOrderResponse | AdminOrderRecord) {
  orderId.value = order.orderId;
  newScheduledStart.value = order.scheduledStart;
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
  const selected = selectableOrders.value.find((item) => item.orderId === orderId.value)
    ?? selectableOrders.value[0];
  if (selected) {
    selectOrder(selected);
  } else {
    orderId.value = '';
  }
}

async function loadFamilyPermissions() {
  if (!canFamilyChange.value) {
    return;
  }
  const response = await getFamilyBindings();
  familyBindings.value = response.code === 0 ? response.data : [];
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
  if (!rescheduleTimeValid.value) {
    error.value = '新的预约时间须晚于当前时间';
    return;
  }
  loading.value = true;
  const response = await rescheduleFamilyOrder(orderId.value, payload(), scenario);
  loading.value = false;
  applyResponse(response, '预约时间已更新');
  await refreshOrders();
  if (response.code === 0) {
    uni.$emit('carenest-orders-updated', response.data.orderId);
  }
}

async function handleFamilyCancel(scenario: StageSeventeenScenario = 'normal') {
  loading.value = true;
  const response = await cancelFamilyOrder(orderId.value, payload(), scenario);
  loading.value = false;
  applyResponse(response, '订单已取消');
  await refreshOrders();
  if (response.code === 0) {
    uni.$emit('carenest-orders-updated', response.data.orderId);
  }
}

async function handleAdminCancel(scenario: StageSeventeenScenario = 'normal') {
  loading.value = true;
  const response = await cancelAdminOrder(orderId.value, payload(), scenario);
  loading.value = false;
  applyResponse(response, '订单已取消');
  await refreshOrders();
  if (response.code === 0) {
    uni.$emit('carenest-orders-updated', response.data.orderId);
  }
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
  loadFamilyPermissions();
});
</script>

<template>
  <view v-if="canUsePanel" class="stage-seventeen-panel glass-panel" aria-label="订单取消与改期">
    <view class="section-title">
      <text>订单取消与改期</text>
    </view>

    <view class="stage-seventeen-summary">
      <view>
        <text class="section-mini">order / role / status</text>
        <text class="permission-main">
          {{ orderId }} / {{ props.roleCode }} /
          {{ displayLabel(latestFamilyOrder?.orderStatus || latestAdminOrder?.orderStatus || 'WAIT_DISPATCH') }}
        </text>
        <text class="auth-meta">取消/改期后家属端、管理端状态一致</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || '暂无追踪信息' }}</text>
        <text class="auth-meta">家属校验 ACTIVE 绑定和 ORDER_CREATE scope</text>
      </view>
    </view>

    <view class="stage-seventeen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="report-toolbar order-change-form">
      <view class="field order-selector">
        <text>选择需要处理的订单</text>
        <view v-if="selectableOrders.length" class="order-option-list">
          <button
            v-for="order in selectableOrders"
            :key="order.orderId"
            class="order-option"
            :class="{ active: orderId === order.orderId }"
            type="button"
            @click="selectOrder(order)"
          >
            <view>
              <text class="flow-label">{{ orderServiceName(order) }}</text>
              <text class="flow-time">预约时间：{{ scheduledTime(order) }}</text>
            </view>
            <text class="tag tag-amber">{{ displayLabel(order.orderStatus) }}</text>
          </button>
        </view>
        <text v-else class="auth-meta">暂无可取消或改期的订单</text>
      </view>
      <view class="field">
        <text>新的预约时间</text>
        <view class="appointment-picker-row">
          <picker mode="date" :start="localDate()" :value="rescheduleDate" @change="selectRescheduleDate">
            <view class="input date-picker-input">{{ rescheduleDate }}</view>
          </picker>
          <picker mode="time" :value="rescheduleTime" @change="selectRescheduleTime">
            <view class="input date-picker-input">{{ rescheduleTime }}</view>
          </picker>
        </view>
        <text v-if="!rescheduleTimeValid" class="field-error">请选择晚于当前时间的预约时段</text>
      </view>
      <label class="field">
        <text>调整原因</text>
        <input v-model="reason" class="input" placeholder="取消或改期原因" />
      </label>
    </view>

    <view class="binding-actions">
      <button v-if="canFamilyChange" class="hero-action" type="button" :disabled="loading || !orderId || !rescheduleTimeValid || !canFamilyChangeSelectedOrder" @click="handleFamilyReschedule()">
        <text>家属改期</text>
      </button>
      <button v-if="canFamilyChange" class="ghost-action" type="button" :disabled="loading || !orderId || !canFamilyChangeSelectedOrder" @click="handleFamilyCancel()">
        <text>家属取消</text>
      </button>
      <button v-if="canAdminChange" class="hero-action" type="button" :disabled="loading || !orderId" @click="handleAdminCancel()">
        <text>管理端取消</text>
      </button>
      <text v-if="canFamilyChange && !canFamilyChangeSelectedOrder" class="field-error">当前生效绑定不包含“代下单”，请等待长辈确认绑定变更后再操作。</text>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view class="service-report-workbench">
      <view v-if="canFamilyChange" class="contract-response">
        <text class="section-mini">家属端订单快照</text>
        <text v-if="latestFamilyOrder" class="permission-main">
          {{ orderServiceName(latestFamilyOrder) }} · {{ displayLabel(latestFamilyOrder.orderStatus) }}
        </text>
        <text v-if="latestFamilyOrder" class="auth-meta">预约时间：{{ scheduledTime(latestFamilyOrder) }}</text>
        <text v-else class="auth-meta">当前角色不可读取或暂无家属端订单快照</text>
      </view>

      <view v-if="canAdminChange" class="contract-response">
        <text class="section-mini">管理端订单快照</text>
        <text v-if="latestAdminOrder" class="permission-main">
          {{ orderServiceName(latestAdminOrder) }} · {{ displayLabel(latestAdminOrder.orderStatus) }}
        </text>
        <text v-if="latestAdminOrder" class="auth-meta">
          预约时间：{{ latestAdminOrder.scheduledStart }}{{ latestAdminOrder.contactName ? ` · 联系人：${latestAdminOrder.contactName}` : '' }}
        </text>
        <text v-else class="auth-meta">当前角色不可读取或暂无管理端订单快照</text>
      </view>
    </view>

    <view v-if="latestAdminOrder" class="report-section">
      <text class="section-mini">订单状态记录</text>
      <view v-for="log in latestAdminOrder.statusLogs" :key="log.statusLogId" class="status-log-row">
        <text class="flow-label">{{ displayLabel(log.fromStatus || 'INIT') }} → {{ displayLabel(log.toStatus) }}</text>
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
