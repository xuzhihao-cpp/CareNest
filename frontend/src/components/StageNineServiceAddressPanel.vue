<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getFamilyElders } from '@/api/stageSeven';
import {
  createServiceAddress,
  deleteServiceAddress,
  getServiceAddresses,
  getStageNineEndpointSummary,
  resetStageNineMockRecords,
  updateServiceAddress
} from '@/api/stageNine';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { ElderProfileDetail } from '@/types/stageSeven';
import type {
  ServiceAddressRequest,
  ServiceAddressResponse,
  ServiceAddressScenario
} from '@/types/stageNine';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const emptyForm: ServiceAddressRequest = {
  contactName: '张小明',
  contactPhone: '13800000002',
  regionCode: '310101',
  detailAddress: '人民路100号1单元201',
  isDefault: true
};

const elders = ref<ElderProfileDetail[]>([]);
const selectedElderId = ref('');
const records = ref<ServiceAddressResponse[]>([]);
const selectedAddressId = ref('');
const bookingAddressId = ref('');
const form = ref<ServiceAddressRequest>({ ...emptyForm });
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<ServiceAddressResponse> | null>(null);
const endpoints = getStageNineEndpointSummary();

const canUsePanel = computed(() => props.roleCode === 'FAMILY' && props.authUser?.roles.includes('FAMILY'));
const defaultAddress = computed(() => records.value.find((item) => item.isDefault) ?? null);
const bookingAddress = computed(
  () => records.value.find((item) => item.addressId === bookingAddressId.value) ?? defaultAddress.value
);
const selectedElderName = computed(() => elders.value.find((item) => item.elderId === selectedElderId.value)?.name ?? '未选择');

function parseFullAddress(fullAddress: string) {
  const [regionCode, ...detailParts] = fullAddress.split(' ');
  return {
    regionCode: regionCode || '',
    detailAddress: detailParts.join(' ') || fullAddress
  };
}

function resetForm() {
  form.value = { ...emptyForm };
  selectedAddressId.value = '';
}

function applyAddress(record: ServiceAddressResponse) {
  selectedAddressId.value = record.addressId;
  const parsed = parseFullAddress(record.fullAddress);
  form.value = {
    contactName: form.value.contactName || emptyForm.contactName,
    contactPhone: form.value.contactPhone || emptyForm.contactPhone,
    regionCode: parsed.regionCode,
    detailAddress: parsed.detailAddress,
    isDefault: record.isDefault
  };
}

function syncBookingAddress() {
  bookingAddressId.value = defaultAddress.value?.addressId ?? records.value[0]?.addressId ?? '';
}

function applyResponse(response: ApiResponse<ServiceAddressResponse>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = successText;
    error.value = '';
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadElders() {
  const response = await getFamilyElders();
  if (response.code === 0) {
    elders.value = response.data.records;
    selectedElderId.value = elders.value[0]?.elderId ?? '';
  } else {
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadAddresses(scenario: ServiceAddressScenario = 'normal', keepMutationState = false) {
  if (!canUsePanel.value || !selectedElderId.value) {
    return;
  }
  loading.value = true;
  const response = await getServiceAddresses(selectedElderId.value, scenario);
  loading.value = false;
  lastTraceId.value = response.traceId;
  if (!keepMutationState) {
    lastResponse.value = null;
  }
  if (response.code === 0) {
    records.value = response.data.records;
    error.value = '';
    if (!keepMutationState) {
      message.value =
        scenario === 'empty' ? '已切换为空地址 mock' : scenario === 'normal' ? '服务地址列表已加载' : message.value;
    }
    syncBookingAddress();
    if (records.value.length > 0) {
      applyAddress(defaultAddress.value ?? records.value[0]);
    } else {
      resetForm();
    }
  } else {
    records.value = [];
    selectedAddressId.value = '';
    bookingAddressId.value = '';
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function selectElder(elderId: string) {
  selectedElderId.value = elderId;
  resetForm();
  await loadAddresses();
}

async function createAddress() {
  if (!selectedElderId.value) {
    error.value = '请先选择长辈';
    return;
  }
  const response = await createServiceAddress(selectedElderId.value, form.value);
  applyResponse(response, '服务地址已新增');
  if (response.code === 0) {
    await loadAddresses('normal', true);
    selectedAddressId.value = response.data.addressId;
    bookingAddressId.value = response.data.isDefault ? response.data.addressId : bookingAddressId.value;
  }
}

async function saveAddress() {
  if (!selectedAddressId.value) {
    error.value = '请先选择一个地址';
    return;
  }
  const response = await updateServiceAddress(selectedAddressId.value, form.value);
  applyResponse(response, '服务地址已保存');
  if (response.code === 0) {
    await loadAddresses('normal', true);
    selectedAddressId.value = response.data.addressId;
    bookingAddressId.value = response.data.isDefault ? response.data.addressId : bookingAddressId.value;
  }
}

async function setDefaultAddress(record: ServiceAddressResponse) {
  const parsed = parseFullAddress(record.fullAddress);
  const response = await updateServiceAddress(record.addressId, {
    contactName: form.value.contactName || emptyForm.contactName,
    contactPhone: form.value.contactPhone || emptyForm.contactPhone,
    regionCode: parsed.regionCode,
    detailAddress: parsed.detailAddress,
    isDefault: true
  });
  applyResponse(response, '默认地址已更新');
  if (response.code === 0) {
    await loadAddresses('normal', true);
    bookingAddressId.value = response.data.addressId;
  }
}

async function removeAddress(record: ServiceAddressResponse) {
  const response = await deleteServiceAddress(record.addressId);
  applyResponse(response, '服务地址已删除');
  if (response.code === 0) {
    await loadAddresses('normal', true);
  }
}

async function resetNormalMock() {
  resetStageNineMockRecords();
  resetForm();
  await loadAddresses('normal');
}

onMounted(async () => {
  if (!canUsePanel.value) {
    return;
  }
  await loadElders();
  await loadAddresses();
});
</script>

<template>
  <view class="stage-nine-panel glass-panel" aria-label="阶段9服务地址">
    <view class="section-title">
      <text>⑨</text>
      <text>服务地址 MVP</text>
    </view>

    <view class="stage-nine-summary">
      <view>
        <text class="section-mini">records / default / elder</text>
        <text class="permission-main">{{ records.length }} / {{ defaultAddress?.addressId ?? 'none' }}</text>
        <text class="auth-meta">{{ selectedElderName }} · 同一长辈仅一个默认地址</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-9' }}</text>
        <text class="auth-meta">预约前默认地址选择预览</text>
      </view>
    </view>

    <view class="stage-nine-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="address-workbench">
      <view class="address-list">
        <view class="binding-options">
          <text class="section-mini">选择长辈 elderId</text>
          <view class="segmented-row">
            <button
              v-for="elder in elders"
              :key="elder.elderId"
              class="choice-button"
              :class="{ active: selectedElderId === elder.elderId }"
              type="button"
              @click="selectElder(elder.elderId)"
            >
              <text>{{ elder.name }}</text>
            </button>
          </view>
        </view>

        <button
          v-for="record in records"
          :key="record.addressId"
          class="address-row"
          :class="{ active: selectedAddressId === record.addressId }"
          type="button"
          @click="applyAddress(record)"
        >
          <view>
            <text class="flow-label">{{ record.fullAddress }}</text>
            <text class="flow-time">{{ record.addressId }}</text>
          </view>
          <text class="tag" :class="record.isDefault ? 'tag-teal' : 'tag-blue'">
            {{ record.isDefault ? '默认地址' : '备用地址' }}
          </text>
        </button>

        <view class="booking-preview">
          <text class="section-mini">预约页地址选择预览</text>
          <text class="permission-main">{{ bookingAddress?.fullAddress ?? '暂无可选地址' }}</text>
          <view class="segmented-row">
            <button
              v-for="record in records"
              :key="`${record.addressId}-booking`"
              class="choice-button scope"
              :class="{ active: bookingAddress?.addressId === record.addressId }"
              type="button"
              @click="bookingAddressId = record.addressId"
            >
              <text>{{ record.isDefault ? '默认' : '选择' }} {{ record.addressId }}</text>
            </button>
          </view>
        </view>
      </view>

      <view class="address-form">
        <view class="contact-grid">
          <label class="field">
            <text>联系人 contactName</text>
            <input v-model="form.contactName" class="input" placeholder="联系人姓名" />
          </label>
          <label class="field">
            <text>联系电话 contactPhone</text>
            <input v-model="form.contactPhone" class="input" placeholder="手机号" />
          </label>
        </view>

        <label class="field">
          <text>区县编码 regionCode</text>
          <input v-model="form.regionCode" class="input" placeholder="310101" />
        </label>

        <label class="field">
          <text>详细地址 detailAddress</text>
          <input v-model="form.detailAddress" class="input" placeholder="人民路100号1单元201" />
        </label>

        <view class="binding-options">
          <text class="section-mini">默认地址 isDefault</text>
          <view class="segmented-row">
            <button class="choice-button" :class="{ active: form.isDefault }" type="button" @click="form.isDefault = true">
              <text>设为默认</text>
            </button>
            <button class="choice-button" :class="{ active: !form.isDefault }" type="button" @click="form.isDefault = false">
              <text>普通地址</text>
            </button>
          </view>
        </view>

        <view class="binding-actions">
          <button class="hero-action" type="button" :disabled="loading" @click="createAddress">
            <text>新增地址</text>
          </button>
          <button class="ghost-action" type="button" :disabled="loading || !selectedAddressId" @click="saveAddress">
            <text>保存所选</text>
          </button>
          <button class="ghost-action" type="button" @click="resetNormalMock">
            <text>重置 mock</text>
          </button>
          <button class="ghost-action" type="button" @click="loadAddresses('empty')">
            <text>空数据 mock</text>
          </button>
          <button class="ghost-action" type="button" @click="loadAddresses('error')">
            <text>错误 mock</text>
          </button>
        </view>
      </view>
    </view>

    <view v-if="records.length > 0" class="binding-actions">
      <button
        v-for="record in records"
        :key="`${record.addressId}-default`"
        class="ghost-action"
        type="button"
        :disabled="record.isDefault"
        @click="setDefaultAddress(record)"
      >
        <text>{{ record.addressId }} 设为默认</text>
      </button>
      <button
        v-for="record in records"
        :key="`${record.addressId}-delete`"
        class="ghost-action danger-lite"
        type="button"
        @click="removeAddress(record)"
      >
        <text>删除 {{ record.addressId }}</text>
      </button>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view v-if="records.length === 0 && !error" class="empty-state">
      <text class="empty-icon">∅</text>
      <view>
        <text class="empty-title">暂无服务地址</text>
        <text class="empty-desc">空数据 mock 已返回 records: []，新增地址后可作为预约默认地址。</text>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次服务地址响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.addressId }} · {{ lastResponse.data.fullAddress }} ·
        {{ lastResponse.data.isDefault ? '默认' : '非默认' }}
      </text>
    </view>
  </view>
</template>
