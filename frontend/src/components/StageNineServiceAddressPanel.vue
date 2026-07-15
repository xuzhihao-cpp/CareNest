<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getFamilyElders } from '@/api/stageSeven';
import {
  createServiceAddress,
  deleteServiceAddress,
  getServiceAddresses,
  getStageNineEndpointSummary,
  updateServiceAddress
} from '@/api/stageNine';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { ElderProfileResponse } from '@/types/stageSeven';
import type {
  ServiceAddressRequest,
  ServiceAddressResponse,
  ServiceAddressScenario
} from '@/types/stageNine';

type AddressDisplay = ServiceAddressResponse & {
  contactName?: string;
  contactPhone?: string;
  regionCode?: string;
  detailAddress?: string;
};

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

const elders = ref<ElderProfileResponse[]>([]);
const selectedElderId = ref('');
const records = ref<AddressDisplay[]>([]);
const selectedAddressId = ref('');
const bookingAddressId = ref('');
const form = ref<ServiceAddressRequest>({ ...emptyForm });
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<ServiceAddressResponse> | null>(null);
const endpoints = getStageNineEndpointSummary();

function updatePhone(event: InputEvent & { detail?: { value?: string } }) {
  const rawValue = event.detail?.value ?? (event.target as HTMLInputElement | null)?.value ?? '';
  const phone = rawValue.replace(/\D/g, '').slice(0, 11);
  form.value.contactPhone = phone;
}

function normalizeRegionCode() {
  form.value.regionCode = form.value.regionCode.replace(/\D/g, '').slice(0, 6);
}

function displayAddress(record: AddressDisplay) {
  if (record.detailAddress) {
    return record.detailAddress;
  }
  const prefix = record.regionCode ? `${record.regionCode} ` : '';
  return prefix && record.fullAddress.startsWith(prefix)
    ? record.fullAddress.slice(prefix.length)
    : record.fullAddress;
}

function validAddressForm() {
  if (!form.value.contactName.trim() || form.value.contactName.trim().length > 32) {
    error.value = '请输入 1 至 32 个字符的联系人姓名';
    return false;
  }
  if (!/^1\d{10}$/.test(form.value.contactPhone)) {
    error.value = '请输入 11 位手机号码';
    return false;
  }
  if (!/^\d{6}$/.test(form.value.regionCode)) {
    error.value = '区县编码应为 6 位数字';
    return false;
  }
  if (!form.value.detailAddress.trim() || form.value.detailAddress.trim().length > 120) {
    error.value = '请输入 1 至 120 个字符的详细地址';
    return false;
  }
  return true;
}

const canUsePanel = computed(() => props.roleCode === 'FAMILY' && props.authUser?.roles.includes('FAMILY'));
const defaultAddress = computed(() => records.value.find((item) => item.isDefault) ?? null);
const bookingAddress = computed(
  () => records.value.find((item) => item.addressId === bookingAddressId.value) ?? defaultAddress.value
);
const selectedElderName = computed(() => selectedElderId.value || '未选择');

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

function applyAddress(record: AddressDisplay) {
  selectedAddressId.value = record.addressId;
  const parsed = record.regionCode && record.detailAddress
    ? { regionCode: record.regionCode, detailAddress: record.detailAddress }
    : parseFullAddress(record.fullAddress);
  form.value = {
    contactName: record.contactName || emptyForm.contactName,
    contactPhone: record.contactPhone || emptyForm.contactPhone,
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
    elders.value = response.data;
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
    records.value = response.data;
    error.value = '';
    if (!keepMutationState) {
      message.value =
        scenario === 'empty' ? '当前暂无服务地址' : scenario === 'normal' ? '服务地址列表已加载' : message.value;
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
  if (!validAddressForm()) {
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
  if (!validAddressForm()) {
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

async function setDefaultAddress(record: AddressDisplay) {
  const parsed = record.regionCode && record.detailAddress
    ? { regionCode: record.regionCode, detailAddress: record.detailAddress }
    : parseFullAddress(record.fullAddress);
  const response = await updateServiceAddress(record.addressId, {
    contactName: record.contactName || emptyForm.contactName,
    contactPhone: record.contactPhone || emptyForm.contactPhone,
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

async function removeAddress(record: AddressDisplay) {
  const response = await deleteServiceAddress(record.addressId);
  applyResponse(response, '服务地址已删除');
  if (response.code === 0) {
    await loadAddresses('normal', true);
  }
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
      <text>服务地址</text>
    </view>

    <view class="stage-nine-summary">
      <view>
        <text class="section-mini">records / default / elder</text>
        <text class="permission-main">{{ records.length }} / {{ defaultAddress?.addressId ?? 'none' }}</text>
        <text class="auth-meta">{{ selectedElderName }} · 同一长辈仅一个默认地址</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || '暂无追踪信息' }}</text>
        <text class="auth-meta">预约前默认地址选择预览</text>
      </view>
    </view>

    <view class="stage-nine-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="address-workbench">
      <view class="address-list">
        <view class="binding-options">
          <text class="section-mini">选择长辈</text>
          <view class="segmented-row">
            <button
              v-for="elder in elders"
              :key="elder.elderId"
              class="choice-button"
              :class="{ active: selectedElderId === elder.elderId }"
              type="button"
              @click="selectElder(elder.elderId)"
            >
              <text>{{ elder.name || '未命名长辈' }}</text>
            </button>
          </view>
        </view>

        <view
          v-for="record in records"
          :key="record.addressId"
          class="address-row"
          :class="{ active: selectedAddressId === record.addressId }"
          @click="applyAddress(record)"
        >
          <view>
            <text class="flow-label">{{ displayAddress(record) }}</text>
            <text v-if="record.regionCode" class="address-code">区县编码：{{ record.regionCode }}</text>
          </view>
          <view class="address-row-side">
            <text class="tag" :class="record.isDefault ? 'tag-teal' : 'tag-blue'">
              {{ record.isDefault ? '默认地址' : '备用地址' }}
            </text>
            <view class="address-row-actions">
            <button
              v-if="!record.isDefault"
              class="ghost-action"
              type="button"
              @click.stop="setDefaultAddress(record)"
            >
              <text>设为默认</text>
            </button>
            <button class="ghost-action danger-lite" type="button" @click.stop="removeAddress(record)">
              <text>删除地址</text>
            </button>
            </view>
          </view>
        </view>

        <view v-if="false" class="booking-preview">
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
              <text class="booking-choice-label">{{ record.isDefault ? '当前默认地址' : '选择此地址' }}</text>
              <text>{{ record.isDefault ? '默认' : '选择' }} {{ record.addressId }}</text>
            </button>
          </view>
        </view>
      </view>

      <view class="address-form">
        <view class="contact-grid">
          <label class="field">
            <text>联系人</text>
            <input v-model="form.contactName" class="input" maxlength="32" placeholder="联系人姓名" />
          </label>
          <label class="field">
            <text>联系电话</text>
            <input v-model="form.contactPhone" class="input" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="11" placeholder="11 位手机号码" @input="updatePhone" />
          </label>
        </view>

        <label class="field">
          <text>区县编码</text>
          <input v-model="form.regionCode" class="input" inputmode="numeric" maxlength="6" placeholder="6 位区县编码，例如 310101" @input="normalizeRegionCode" />
        </label>

        <label class="field">
          <text>详细地址</text>
          <input v-model="form.detailAddress" class="input" maxlength="120" placeholder="街道、门牌号、楼栋和房间号" />
        </label>

        <view class="binding-options">
          <text class="section-mini">默认地址</text>
          <label class="default-address-toggle">
            <checkbox :checked="form.isDefault" @click="form.isDefault = !form.isDefault" />
            <text>设为默认地址</text>
          </label>
          <view class="segmented-row legacy-default-toggle">
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
        </view>
      </view>
    </view>

    <view v-if="false" class="binding-actions">
      <button
        v-for="record in records"
        :key="`${record.addressId}-default`"
        v-show="!record.isDefault"
        class="ghost-action"
        type="button"
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
        <text class="empty-desc">请先新增服务地址，新增后可设为默认地址用于预约。</text>
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
