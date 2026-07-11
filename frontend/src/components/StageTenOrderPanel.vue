<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { getFamilyElders } from '@/api/stageSeven';
import { getServiceItems } from '@/api/stageEight';
import { getServiceAddresses } from '@/api/stageNine';
import { displayLabel } from '@/utils/displayLabels';
import {
  createFamilyOrder,
  getFamilyOrders,
  getOrderDetail,
  getStageTenEndpointSummary,
  resetStageTenMockRecords
} from '@/api/stageTen';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { ElderProfileResponse } from '@/types/stageSeven';
import type { ServiceItemResponse } from '@/types/stageEight';
import type { ServiceAddressResponse } from '@/types/stageNine';
import type {
  FamilyOrderRequest,
  FamilyOrderResponse,
  OrderScenario
} from '@/types/stageTen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

function nextAppointmentTime() {
  const date = new Date(Date.now() + 2 * 60 * 60 * 1000);
  date.setMinutes(0, 0, 0);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const elders = ref<ElderProfileResponse[]>([]);
const services = ref<ServiceItemResponse[]>([]);
const addresses = ref<ServiceAddressResponse[]>([]);
const orders = ref<FamilyOrderResponse[]>([]);
const form = ref<FamilyOrderRequest>({
  elderId: '',
  serviceId: '',
  addressId: '',
  scheduledStart: nextAppointmentTime(),
  preferredNurseId: '',
  remark: '阶段10预约下单演示'
});
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<FamilyOrderResponse> | null>(null);
const selectedOrderDetail = ref<FamilyOrderResponse | null>(null);
const endpoints = getStageTenEndpointSummary();

const canUsePanel = computed(() => props.roleCode === 'FAMILY' && props.authUser?.roles.includes('FAMILY'));
const selectedService = computed(() => services.value.find((item) => item.serviceId === form.value.serviceId) ?? null);
const selectedAddress = computed(() => addresses.value.find((item) => item.addressId === form.value.addressId) ?? null);
const waitDispatchCount = computed(() => orders.value.filter((item) => item.orderStatus === 'WAIT_DISPATCH').length);
const canSubmit = computed(() => Boolean(
  form.value.elderId && form.value.serviceId && form.value.addressId && form.value.scheduledStart
));

function formatAppointmentTime(value: string) {
  if (!value) {
    return '请选择预约时间';
  }
  const [date, time = ''] = value.replace('T', ' ').split(' ');
  return `${date} ${time.slice(0, 5)}`;
}

function orderServiceName(order: FamilyOrderResponse) {
  return order.serviceName || '护理服务';
}

function orderStatusClass(status: FamilyOrderResponse['orderStatus']) {
  if (status === 'COMPLETED') return 'tag-teal';
  if (status === 'CANCELED') return 'tag-coral';
  return 'tag-amber';
}

function setDefaultAddress() {
  form.value.addressId = addresses.value.find((item) => item.isDefault)?.addressId ?? '';
}

async function loadPrerequisites() {
  const [elderResponse, serviceResponse] = await Promise.all([getFamilyElders(), getServiceItems('normal')]);
  if (elderResponse.code === 0) {
    elders.value = elderResponse.data;
    form.value.elderId = form.value.elderId || elders.value[0]?.elderId || '';
  } else {
    error.value = `${elderResponse.code} ${elderResponse.message}`;
  }
  if (serviceResponse.code === 0) {
    services.value = serviceResponse.data.records;
    form.value.serviceId = form.value.serviceId || services.value[0]?.serviceId || '';
  } else {
    error.value = `${serviceResponse.code} ${serviceResponse.message}`;
  }
  await loadAddresses();
}

async function loadAddresses() {
  if (!form.value.elderId) {
    addresses.value = [];
    form.value.addressId = '';
    return;
  }
  const response = await getServiceAddresses(form.value.elderId);
  if (response.code === 0) {
    addresses.value = response.data;
    setDefaultAddress();
  } else {
    addresses.value = [];
    form.value.addressId = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function selectElder(elderId: string) {
  form.value.elderId = elderId;
  await loadAddresses();
}

async function loadOrders(scenario: OrderScenario = 'normal', keepMutationState = false) {
  loading.value = true;
  const response = await getFamilyOrders(scenario);
  loading.value = false;
  lastTraceId.value = response.traceId;
  if (!keepMutationState) {
    lastResponse.value = null;
  }
  if (response.code === 0) {
    orders.value = response.data.records;
    error.value = '';
    if (!keepMutationState) {
      message.value =
        scenario === 'empty' ? '已切换为空订单 mock' : scenario === 'normal' ? '预约订单列表已加载' : message.value;
    }
  } else {
    orders.value = [];
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function submitOrder() {
  if (!canSubmit.value) {
    error.value = '请选择长辈、在架服务和默认服务地址，并填写预约时间';
    return;
  }
  const response = await createFamilyOrder(form.value);
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = '预约订单已提交，等待派单';
    error.value = '';
    selectedOrderDetail.value = response.data;
    await loadOrders('normal', true);
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function viewOrderDetail(orderId: string, silent = false) {
  const response = await getOrderDetail(orderId);
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    selectedOrderDetail.value = response.data;
    if (!silent) {
      message.value = '订单详情已读取';
    }
    error.value = '';
  } else {
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function refreshOrderPresentation() {
  await loadOrders('normal', true);
  const detailOrderId = selectedOrderDetail.value?.orderId ?? orders.value[0]?.orderId;
  if (detailOrderId) {
    await viewOrderDetail(detailOrderId, true);
  }
}

function handleOrdersUpdated() {
  void refreshOrderPresentation();
}

async function resetNormalMock() {
  resetStageTenMockRecords();
  selectedOrderDetail.value = null;
  await loadOrders('normal');
}

onMounted(async () => {
  if (!canUsePanel.value) {
    return;
  }
  await loadPrerequisites();
  await refreshOrderPresentation();
  uni.$on('carenest-orders-updated', handleOrdersUpdated);
});

onUnmounted(() => {
  uni.$off('carenest-orders-updated', handleOrdersUpdated);
});
</script>

<template>
  <view class="stage-ten-panel glass-panel" aria-label="阶段10预约下单">
    <view class="section-title">
      <text>⑩</text>
      <text>预约下单 MVP</text>
    </view>

    <view class="stage-ten-summary">
      <view>
        <text class="section-mini">orders / WAIT_DISPATCH / selected</text>
        <text class="permission-main">{{ orders.length }} / {{ waitDispatchCount }} / {{ form.serviceId || 'none' }}</text>
        <text class="auth-meta">提交后初始状态固定 WAIT_DISPATCH</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-10' }}</text>
        <text class="auth-meta">校验绑定授权、服务上架和地址归属</text>
      </view>
    </view>

    <view class="stage-ten-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="order-workbench">
      <view class="order-picker">
        <view class="binding-options">
          <text class="section-mini">长辈 elderId</text>
          <view class="segmented-row">
            <button
              v-for="elder in elders"
              :key="elder.elderId"
              class="choice-button"
              :class="{ active: form.elderId === elder.elderId }"
              type="button"
              @click="selectElder(elder.elderId)"
            >
              <text>{{ elder.elderId }}</text>
            </button>
          </view>
        </view>

        <view class="binding-options">
          <text class="section-mini">服务 serviceId</text>
          <view class="order-option-list">
            <button
              v-for="service in services"
              :key="service.serviceId"
              class="order-option"
              :class="{ active: form.serviceId === service.serviceId }"
              type="button"
              @click="form.serviceId = service.serviceId"
            >
              <view>
                <text class="flow-label">{{ service.serviceName }}</text>
                <text class="flow-time">{{ service.serviceId }} · ¥{{ service.price }} · {{ service.durationMinutes }} 分钟</text>
              </view>
              <text class="tag tag-teal">{{ displayLabel(service.status) }}</text>
            </button>
          </view>
        </view>

        <view class="binding-options">
          <text class="section-mini">地址 addressId</text>
          <view class="order-option-list">
            <button
              v-for="address in addresses"
              :key="address.addressId"
              class="order-option"
              :class="{ active: form.addressId === address.addressId }"
              type="button"
              @click="form.addressId = address.addressId"
            >
              <view>
                <text class="flow-label">{{ address.fullAddress }}</text>
                <text class="flow-time">{{ address.isDefault ? '默认服务地址' : '备用服务地址' }}</text>
              </view>
              <text class="tag" :class="address.isDefault ? 'tag-teal' : 'tag-blue'">
                {{ address.isDefault ? '默认地址' : '备用地址' }}
              </text>
            </button>
          </view>
        </view>
      </view>

      <view class="order-form">
        <label class="field">
          <text>预约时间 scheduledStart</text>
          <input v-model="form.scheduledStart" class="input" type="datetime-local" />
        </label>
        <label class="field">
          <text>偏好护理员 preferredNurseId</text>
          <input v-model="form.preferredNurseId" class="input" placeholder="可为空" />
        </label>
        <label class="field">
          <text>备注 remark</text>
          <input v-model="form.remark" class="input" placeholder="阶段10预约下单演示" />
        </label>

        <view class="order-preview">
          <text class="section-mini">下单预览</text>
          <text class="permission-main">预约服务：{{ selectedService?.serviceName ?? '请选择服务' }}</text>
          <text class="auth-meta">服务地址：{{ selectedAddress?.fullAddress ?? '请选择默认地址' }}</text>
          <text class="auth-meta">预约时间：{{ formatAppointmentTime(form.scheduledStart) }}</text>
        </view>

        <view class="binding-actions">
          <button class="hero-action" type="button" :disabled="loading || !canSubmit" @click="submitOrder">
            <text>提交预约</text>
          </button>
        <button class="ghost-action test-action" type="button" @click="resetNormalMock">
            <text>重置 mock</text>
          </button>
        <button class="ghost-action test-action" type="button" @click="loadOrders('empty')">
            <text>空数据 mock</text>
          </button>
        <button class="ghost-action test-action" type="button" @click="loadOrders('error')">
            <text>错误 mock</text>
          </button>
        </view>
      </view>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view v-if="orders.length === 0 && !error" class="empty-state">
      <text class="empty-icon">∅</text>
      <view>
        <text class="empty-title">暂无预约订单</text>
        <text class="empty-desc">空数据 mock 已返回 records: []，提交预约后应出现 WAIT_DISPATCH 订单。</text>
      </view>
    </view>

    <view v-else class="order-list">
      <view v-for="order in orders" :key="order.orderId" class="order-row">
        <view>
          <text class="flow-label">{{ orderServiceName(order) }}</text>
          <text class="flow-time">预约时间：{{ formatAppointmentTime(order.scheduledStart || '') }}</text>
        </view>
        <view class="order-row-side">
          <text class="tag" :class="orderStatusClass(order.orderStatus)">{{ displayLabel(order.orderStatus) }}</text>
          <button class="ghost-action" type="button" @click="viewOrderDetail(order.orderId)">
            <text>查看服务安排</text>
          </button>
        </view>
      </view>
    </view>

    <view v-if="selectedOrderDetail" class="order-detail-panel">
      <view class="order-detail-heading">
        <text class="section-mini">服务安排</text>
        <view class="order-detail-title-row">
          <text class="permission-main">{{ orderServiceName(selectedOrderDetail) }}</text>
          <text class="tag tag-amber">{{ displayLabel(selectedOrderDetail.orderStatus) }}</text>
        </view>
      </view>
      <view class="order-detail-grid">
        <text>预约时间：{{ formatAppointmentTime(selectedOrderDetail.scheduledStart) }}</text>
        <text v-if="selectedOrderDetail.scheduledEnd">预计结束：{{ formatAppointmentTime(selectedOrderDetail.scheduledEnd) }}</text>
        <text v-if="selectedOrderDetail.serviceAddress">服务地址：{{ selectedOrderDetail.serviceAddress }}</text>
        <text v-if="selectedOrderDetail.contactName">联系人：{{ selectedOrderDetail.contactName }}</text>
        <text v-if="selectedOrderDetail.contactPhone">联系电话：{{ selectedOrderDetail.contactPhone }}</text>
        <text v-if="selectedOrderDetail.remark">服务备注：{{ selectedOrderDetail.remark }}</text>
      </view>
      <button class="ghost-action" type="button" @click="selectedOrderDetail = null">
        <text>收起详情</text>
      </button>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次预约响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.orderId }} · {{ lastResponse.data.orderNo }} · {{ lastResponse.data.orderStatus }}
      </text>
    </view>

  </view>
</template>
