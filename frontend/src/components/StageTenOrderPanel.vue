<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { getFamilyElders } from '@/api/stageSeven';
import { getServiceItems } from '@/api/stageEight';
import { getServiceAddresses } from '@/api/stageNine';
import { displayLabel } from '@/utils/displayLabels';
import { createAsyncActionLock, createLatestRequestGate } from '@/utils/latestRequestGate';
import {
  createFamilyOrder,
  getFamilyOrders,
  getOrderDetail
} from '@/api/stageTen';
import StageTwentyNineRecommendationPanel from '@/components/StageTwentyNineRecommendationPanel.vue';
import StageThirtyFamilyPreferencePanel from '@/components/StageThirtyFamilyPreferencePanel.vue';
import StageThirtyPreferenceBadge from '@/components/StageThirtyPreferenceBadge.vue';
import { getPreferredNurseBindings, getPreferredNursePermissions } from '@/api/stageThirty';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { ElderProfileResponse } from '@/types/stageSeven';
import type { BindingResponse } from '@/types/stageSix';
import type { ServiceItemResponse } from '@/types/stageEight';
import type { ServiceAddressResponse } from '@/types/stageNine';
import type { NurseRecommendationRecord } from '@/types/stageTwentyNine';
import {
  currentPreferredRecommendation,
  hasActiveOrderBinding,
  preferredNurseAccessMessage
} from '@/utils/stageThirtyRules';
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
  remark: ''
});
const loading = ref(false);
const submitting = ref(false);
const message = ref('');
const error = ref('');
const selectedOrderDetail = ref<FamilyOrderResponse | null>(null);
const selectedRecommendedNurse = ref<NurseRecommendationRecord | null>(null);
const preferenceRefreshKey = ref(0);
const preferencePermissions = ref<string[]>([]);
const familyBindings = ref<BindingResponse[]>([]);
const preferencePermissionsLoading = ref(true);
const authorizationRequestError = ref('');
const addressRequestGate = createLatestRequestGate<string>();
const permissionRequestGate = createLatestRequestGate<string>();
const detailRequestGate = createLatestRequestGate<string>();
const ordersRequestGate = createLatestRequestGate<string>();
const submitLock = createAsyncActionLock();

const canUsePanel = computed(() => props.roleCode === 'FAMILY' && props.authUser?.roles.includes('FAMILY'));
const selectedService = computed(() => services.value.find((item) => item.serviceId === form.value.serviceId) ?? null);
const selectedAddress = computed(() => addresses.value.find((item) => item.addressId === form.value.addressId) ?? null);
const bindingAuthorized = computed(() => hasActiveOrderBinding(form.value.elderId, familyBindings.value));
const canSubmit = computed(() => Boolean(
  form.value.elderId && form.value.serviceId && form.value.addressId && form.value.scheduledStart
  && bindingAuthorized.value
));
const canSelectNewPreference = computed(() => !preferencePermissionsLoading.value
  && bindingAuthorized.value
  && preferencePermissions.value.includes('NURSE_PREFERENCE_SELECT'));
const preferencePermissionError = computed(() => {
  if (preferencePermissionsLoading.value) return '';
  return preferredNurseAccessMessage(
    form.value.elderId,
    preferencePermissions.value,
    familyBindings.value,
    authorizationRequestError.value
  );
});

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

function clearPreferredNurse() {
  form.value.preferredNurseId = '';
  selectedRecommendedNurse.value = null;
}

function selectPreferredNurse(record: NurseRecommendationRecord) {
  if (!record.available || !canSelectNewPreference.value) return;
  form.value.preferredNurseId = record.nurseId;
  selectedRecommendedNurse.value = record;
}

async function loadPreferencePermissions() {
  const ticket = permissionRequestGate.begin('NURSE_PREFERENCE_SELECT');
  preferencePermissionsLoading.value = true;
  authorizationRequestError.value = '';
  const [permissionResponse, bindingResponse] = await Promise.all([
    getPreferredNursePermissions(),
    getPreferredNurseBindings()
  ]);
  if (!permissionRequestGate.isCurrent(ticket, 'NURSE_PREFERENCE_SELECT')) return;
  preferencePermissionsLoading.value = false;
  preferencePermissions.value = permissionResponse.code === 0 ? permissionResponse.data : [];
  familyBindings.value = bindingResponse.code === 0 ? bindingResponse.data : [];
  if (permissionResponse.code !== 0) {
    authorizationRequestError.value = '偏好护理权限暂时无法确认，本次仍可不选择偏好并提交预约。';
  } else if (bindingResponse.code !== 0) {
    authorizationRequestError.value = '长辈绑定授权暂时无法读取，请稍后刷新后再预约。';
  }
  if (!canSelectNewPreference.value) clearPreferredNurse();
}

function reconcilePreferredNurse(items: NurseRecommendationRecord[]) {
  if (!form.value.preferredNurseId) return;
  const current = currentPreferredRecommendation(form.value.preferredNurseId, items);
  if (!current) {
    clearPreferredNurse();
    return;
  }
  selectedRecommendedNurse.value = current;
}

function selectService(serviceId: string) {
  form.value.serviceId = serviceId;
  clearPreferredNurse();
}

function selectAddress(addressId: string) {
  form.value.addressId = addressId;
  clearPreferredNurse();
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
  const elderId = form.value.elderId;
  const requestTicket = addressRequestGate.begin(elderId);
  if (!elderId) {
    addresses.value = [];
    form.value.addressId = '';
    return;
  }
  const response = await getServiceAddresses(elderId);
  if (!addressRequestGate.isCurrent(requestTicket, form.value.elderId)) {
    return;
  }
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
  addresses.value = [];
  form.value.addressId = '';
  clearPreferredNurse();
  await loadAddresses();
}

async function loadOrders(scenario: OrderScenario = 'normal', keepMutationState = false): Promise<boolean> {
  const requestKey = `${scenario}:${keepMutationState ? 'preserve' : 'replace'}`;
  const ticket = ordersRequestGate.begin(requestKey);
  loading.value = true;
  const response = await getFamilyOrders(scenario);
  if (!ordersRequestGate.isCurrent(ticket, requestKey)) return false;
  loading.value = false;
  if (response.code === 0) {
    orders.value = response.data.records;
    error.value = '';
    if (!keepMutationState) {
      message.value =
        scenario === 'empty' ? '当前暂无符合条件的订单' : scenario === 'normal' ? '预约订单列表已加载' : message.value;
    }
  } else {
    orders.value = [];
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
  return true;
}

async function submitOrder() {
  if (submitting.value || submitLock.isLocked()) return;
  if (!canSubmit.value) {
    error.value = bindingAuthorized.value
      ? '请选择长辈、在架服务和服务地址，并填写预约时间'
      : '当前长辈没有已生效的代下单授权，不能提交预约';
    return;
  }
  await submitLock.run(async () => {
    submitting.value = true;
    try {
      const response = await createFamilyOrder({ ...form.value });
      if (response.code === 0) {
        message.value = '预约订单已提交，等待派单';
        error.value = '';
        selectedOrderDetail.value = null;
        await loadOrders('normal', true);
        await viewOrderDetail(response.data.orderId, true);
      } else {
        message.value = '';
        error.value = `${response.code} ${response.message}`;
      }
    } finally {
      submitting.value = false;
    }
  });
}

async function viewOrderDetail(orderId: string, silent = false) {
  const ticket = detailRequestGate.begin(orderId);
  const response = await getOrderDetail(orderId);
  if (!detailRequestGate.isCurrent(ticket, orderId)) return;
  if (response.code === 0) {
    selectedOrderDetail.value = response.data;
    if (!silent) message.value = '';
    error.value = '';
  } else {
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function refreshOrderPresentation() {
  const applied = await loadOrders('normal', true);
  if (!applied) return;
  const detailOrderId = selectedOrderDetail.value?.orderId ?? orders.value[0]?.orderId;
  if (detailOrderId) {
    await viewOrderDetail(detailOrderId, true);
  }
}

function handleOrdersUpdated() {
  void refreshOrderPresentation();
}

function closeOrderDetail() {
  detailRequestGate.invalidate();
  selectedOrderDetail.value = null;
}

async function handlePreferenceUpdated() {
  preferenceRefreshKey.value += 1;
  if (selectedOrderDetail.value?.orderId) {
    await viewOrderDetail(selectedOrderDetail.value.orderId, true);
  }
  await loadOrders('normal', true);
}


onMounted(async () => {
  if (!canUsePanel.value) {
    return;
  }
  await Promise.all([loadPrerequisites(), loadPreferencePermissions()]);
  await refreshOrderPresentation();
  uni.$on('carenest-orders-updated', handleOrdersUpdated);
});

onUnmounted(() => {
  addressRequestGate.invalidate();
  permissionRequestGate.invalidate();
  detailRequestGate.invalidate();
  ordersRequestGate.invalidate();
  uni.$off('carenest-orders-updated', handleOrdersUpdated);
});
</script>

<template>
  <view class="stage-ten-panel glass-panel" aria-label="预约服务">
    <view class="section-title">
      <text>预约服务</text>
    </view>

    <view class="order-workbench">
      <view class="order-picker">
        <view class="binding-options">
          <text class="section-mini">服务对象</text>
          <view class="segmented-row">
            <button
              v-for="elder in elders"
              :key="elder.elderId"
              class="choice-button"
              :class="{ active: form.elderId === elder.elderId }"
              type="button"
              @click="selectElder(elder.elderId)"
            >
              <text>{{ elder.name || '未命名长辈' }}</text>
            </button>
          </view>
        </view>

        <view class="binding-options">
          <text class="section-mini">选择服务</text>
          <view class="order-option-list">
            <button
              v-for="service in services"
              :key="service.serviceId"
              class="order-option"
              :class="{ active: form.serviceId === service.serviceId }"
              type="button"
              @click="selectService(service.serviceId)"
            >
              <view>
                <text class="flow-label">{{ service.serviceName }}</text>
                <text class="flow-time">¥{{ service.price }} · {{ service.durationMinutes }} 分钟</text>
              </view>
              <text class="tag tag-teal">{{ displayLabel(service.status) }}</text>
            </button>
          </view>
        </view>

        <view class="binding-options">
          <text class="section-mini">服务地址</text>
          <view class="order-option-list">
            <button
              v-for="address in addresses"
              :key="address.addressId"
              class="order-option"
              :class="{ active: form.addressId === address.addressId }"
              type="button"
              @click="selectAddress(address.addressId)"
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
          <text>预约时间</text>
          <input v-model="form.scheduledStart" class="input" type="datetime-local" @change="clearPreferredNurse" />
        </label>
        <StageTwentyNineRecommendationPanel
          mode="conditions"
          :elder-id="form.elderId"
          :service-id="form.serviceId"
          :address-id="form.addressId"
          :scheduled-start="form.scheduledStart"
          :selectable="canSelectNewPreference"
          :selected-nurse-id="form.preferredNurseId"
          @invalidated="clearPreferredNurse"
          @selected="selectPreferredNurse"
          @recommendations-loaded="reconcilePreferredNurse"
        />
        <view v-if="preferencePermissionsLoading" class="permission-hint">正在确认偏好护理权限...</view>
        <view v-else-if="preferencePermissionError" class="permission-hint warning">{{ preferencePermissionError }}</view>
        <label class="field">
          <text>服务备注</text>
          <input v-model="form.remark" class="input" placeholder="可填写上门注意事项" />
        </label>

        <view class="order-preview">
          <text class="section-mini">下单预览</text>
          <text class="permission-main">预约服务：{{ selectedService?.serviceName ?? '请选择服务' }}</text>
          <text class="auth-meta">服务地址：{{ selectedAddress?.fullAddress ?? '请选择默认地址' }}</text>
          <text class="auth-meta">预约时间：{{ formatAppointmentTime(form.scheduledStart) }}</text>
          <text class="auth-meta">偏好护理：{{ selectedRecommendedNurse?.nurseName ?? '未选择' }}</text>
          <text v-if="selectedRecommendedNurse" class="auth-meta">偏好仅供派单参考，不代表最终护理安排。</text>
        </view>

        <view class="binding-actions">
          <button class="hero-action" type="button" :disabled="loading || submitting || !canSubmit" @click="submitOrder">
            <text>{{ submitting ? '正在提交...' : '提交预约' }}</text>
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
        <text class="empty-desc">还没有预约订单，选择服务和地址后即可提交预约。</text>
      </view>
    </view>

    <view v-else class="order-list">
      <view v-for="order in orders" :key="order.orderId" class="order-row">
        <view>
          <text class="flow-label">{{ orderServiceName(order) }}</text>
          <text class="flow-time">预约时间：{{ formatAppointmentTime(order.scheduledStart || '') }}</text>
          <StageThirtyPreferenceBadge
            :order-id="order.orderId"
            :refresh-key="preferenceRefreshKey"
            :preferred-nurse-name="order.preferredNurseName"
            :preferred-nurse-reason="order.preferredNurseReason"
          />
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
      <StageThirtyFamilyPreferencePanel
        :order-id="selectedOrderDetail.orderId"
        :order-status="selectedOrderDetail.orderStatus"
        :elder-id="selectedOrderDetail.elderId"
        :preferred-nurse-name="selectedOrderDetail.preferredNurseName"
        :preferred-nurse-reason="selectedOrderDetail.preferredNurseReason"
        @updated="handlePreferenceUpdated"
      />
      <button class="ghost-action" type="button" @click="closeOrderDetail">
        <text>收起详情</text>
      </button>
    </view>

  </view>
</template>

<style scoped>
.permission-hint { padding:14rpx 16rpx; border-left:5rpx solid #8ebbb2; background:#eef7f4; color:#56716b; font-size:21rpx; line-height:1.5; }.permission-hint.warning { border-left-color:#d2a14a; background:#fff8e8; color:#785716; }
</style>
