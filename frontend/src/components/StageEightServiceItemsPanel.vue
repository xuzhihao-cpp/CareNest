<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  createServiceItem,
  deleteServiceItem,
  getServiceItems,
  getStageEightEndpointSummary,
  updateServiceItem
} from '@/api/stageEight';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type {
  ServiceItemRequest,
  ServiceItemResponse,
  ServiceStatus
} from '@/types/stageEight';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const statusOptions: Array<{ value: ServiceStatus; label: string }> = [
  { value: 'ON_SHELF', label: '已上架' },
  { value: 'OFF_SHELF', label: '已下架' }
];

const categoryOptions = [
  { value: 'BASIC_CARE', label: '基础护理' },
  { value: 'REHABILITATION', label: '康复陪护' },
  { value: 'NIGHT_CARE', label: '夜间照护' },
  { value: 'CUSTOM', label: '自定义' }
];

const form = ref<ServiceItemRequest>({
  serviceName: '认知陪伴',
  category: 'BASIC_CARE',
  price: 159,
  durationMinutes: 45,
  status: 'ON_SHELF'
});
const records = ref<ServiceItemResponse[]>([]);
const selectedServiceId = ref('');
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<ServiceItemResponse> | null>(null);
const endpoints = getStageEightEndpointSummary();

const isAdmin = computed(() => props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'));
const isFamily = computed(() => props.roleCode === 'FAMILY' && props.authUser?.roles.includes('FAMILY'));
const onShelfCount = computed(() => records.value.filter((item) => item.status === 'ON_SHELF').length);
const offShelfCount = computed(() => records.value.filter((item) => item.status === 'OFF_SHELF').length);

function labelStatus(value: ServiceStatus) {
  return statusOptions.find((item) => item.value === value)?.label ?? value;
}

function labelCategory(value: string) {
  return categoryOptions.find((item) => item.value === value)?.label ?? value;
}

function statusClass(value: ServiceStatus) {
  return value === 'ON_SHELF' ? 'tag-teal' : 'tag-amber';
}

function applyRecord(record: ServiceItemResponse) {
  selectedServiceId.value = record.serviceId;
  form.value = {
    serviceName: record.serviceName,
    category: record.category || 'CUSTOM',
    price: record.price,
    durationMinutes: record.durationMinutes,
    status: record.status
  };
}

function applyMutation(response: ApiResponse<ServiceItemResponse>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = successText;
    error.value = '';
    applyRecord(response.data);
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadServices() {
  if (!isAdmin.value && !isFamily.value) {
    return;
  }
  loading.value = true;
  const response = await getServiceItems('normal', isAdmin.value);
  loading.value = false;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    records.value = response.data.records;
    error.value = '';
    message.value = '服务项目列表已加载';
    if (isAdmin.value && records.value.length > 0 && !selectedServiceId.value) {
      applyRecord(records.value[0]);
    }
  } else {
    records.value = [];
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function createService() {
  const response = await createServiceItem(form.value);
  applyMutation(response, '服务项目已新增');
  await loadServices();
}

async function saveSelectedService() {
  if (!selectedServiceId.value) {
    error.value = '请先选择一个服务项目';
    return;
  }
  const response = await updateServiceItem(selectedServiceId.value, form.value);
  applyMutation(response, '服务项目已保存');
  await loadServices();
}

async function toggleServiceStatus(record: ServiceItemResponse) {
  const response = await updateServiceItem(record.serviceId, {
    serviceName: record.serviceName,
    category: record.category,
    price: record.price,
    durationMinutes: record.durationMinutes,
    status: record.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF'
  });
  applyMutation(response, '服务上下架状态已更新');
  await loadServices();
}

async function deleteService(record: ServiceItemResponse) {
  loading.value = true;
  const response = await deleteServiceItem(record.serviceId);
  loading.value = false;
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code !== 0) {
    message.value = '';
    error.value = response.code === 409 ? '该服务已有历史订单，不能删除；可先将其下架。' : response.message;
    return;
  }
  if (selectedServiceId.value === record.serviceId) {
    selectedServiceId.value = '';
  }
  message.value = '服务项目已删除。';
  error.value = '';
  await loadServices();
}

function confirmDeleteService(record: ServiceItemResponse) {
  uni.showModal({
    title: '删除服务项目',
    content: `确定删除“${record.serviceName}”吗？删除后无法恢复。`,
    confirmText: '删除',
    success: (result) => {
      if (result.confirm) {
        void deleteService(record);
      }
    }
  });
}

onMounted(() => {
  loadServices();
});
</script>

<template>
  <view class="stage-eight-panel glass-panel" aria-label="阶段8服务项目">
    <view class="section-title">
      <text>⑧</text>
      <text>服务项目 MVP</text>
    </view>

    <view class="stage-eight-summary">
      <view>
        <text class="section-mini">records / ON_SHELF / OFF_SHELF</text>
        <text class="permission-main">{{ records.length }} / {{ onShelfCount }} / {{ offShelfCount }}</text>
        <text class="auth-meta">全部服务 / 可预约 / 管理维护下架</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-8' }}</text>
        <text class="auth-meta">新增后家属端列表应出现 ON_SHELF 服务</text>
      </view>
    </view>

    <view class="stage-eight-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view v-if="roleCode === 'ADMIN'" class="service-admin-workbench">
      <view class="service-list">
        <view
          v-for="record in records"
          :key="record.serviceId"
          class="service-row"
          :class="{ active: selectedServiceId === record.serviceId }"
          @click="applyRecord(record)"
        >
          <view class="service-row-main">
            <text class="flow-label">{{ record.serviceName }}</text>
            <view class="service-row-meta"><text>分类：{{ labelCategory(record.category) }}</text><text>时长：{{ record.durationMinutes }} 分钟</text><text class="service-row-price">价格：¥{{ record.price }}</text></view>
          </view>
          <view class="service-row-side">
            <text class="tag" :class="statusClass(record.status)">{{ labelStatus(record.status) }}</text>
            <button class="row-action" type="button" :disabled="loading" @click.stop="toggleServiceStatus(record)">
              {{ record.status === 'ON_SHELF' ? '下架' : '上架' }}
            </button>
            <button class="row-action danger-action" type="button" :disabled="loading" @click.stop="confirmDeleteService(record)">
              删除
            </button>
          </view>
        </view>
      </view>

      <view class="service-editor">
        <label class="field">
          <text>服务名称 serviceName</text>
          <input v-model="form.serviceName" class="input" placeholder="服务项目名称" />
        </label>

        <view class="binding-options service-category-options">
          <text class="section-mini">分类 category</text>
          <view class="segmented-row">
            <button
              v-for="item in categoryOptions"
              :key="item.value"
              class="choice-button"
              :class="{ active: form.category === item.value }"
              type="button"
              @click="form.category = item.value"
            >
              <text>{{ item.label }}</text>
            </button>
          </view>
        </view>

        <view class="service-number-grid">
          <label class="field">
            <text>价格 price</text>
            <input v-model.number="form.price" class="input" type="number" placeholder="199" />
          </label>
          <label class="field">
            <text>时长 durationMinutes</text>
            <input v-model.number="form.durationMinutes" class="input" type="number" placeholder="60" />
          </label>
        </view>

        <view class="binding-options service-status-options">
          <text class="section-mini">上下架 status</text>
          <view class="segmented-row">
            <button
              v-for="item in statusOptions"
              :key="item.value"
              class="choice-button"
              :class="{ active: form.status === item.value }"
              type="button"
              @click="form.status = item.value"
            >
              <text>{{ item.label }}</text>
            </button>
          </view>
        </view>

        <view class="binding-actions service-editor-actions">
          <button class="hero-action" type="button" :disabled="loading" @click="createService">
            <text>新增服务</text>
          </button>
          <button class="ghost-action" type="button" :disabled="loading || !selectedServiceId" @click="saveSelectedService">
            <text>保存所选</text>
          </button>
        </view>
      </view>
    </view>

    <view v-if="roleCode === 'FAMILY'" class="service-family-catalog">
      <view v-for="record in records" :key="record.serviceId" class="service-card">
        <view class="service-card-main">
          <text class="flow-label">{{ record.serviceName }}</text>
          <text class="flow-time">
            {{ labelCategory(record.category) }} · {{ record.durationMinutes }} 分钟 · {{ record.serviceId }}
          </text>
        </view>
        <view class="service-price-box">
          <text class="service-price">¥{{ record.price }}</text>
          <text class="tag tag-teal">{{ labelStatus(record.status) }}</text>
        </view>
      </view>
      <view class="binding-actions"><button class="ghost-action" type="button" @click="loadServices"><text>刷新服务列表</text></button></view>
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
        <text class="empty-title">暂无服务项目</text>
        <text class="empty-desc">空数据 mock 已返回 records: []，服务项目表结构仍保持一致。</text>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次服务项目响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.serviceId }} · {{ lastResponse.data.serviceName }} · ¥{{ lastResponse.data.price }} ·
        {{ lastResponse.data.durationMinutes }} 分钟 · {{ labelStatus(lastResponse.data.status) }}
      </text>
    </view>
  </view>
</template>
