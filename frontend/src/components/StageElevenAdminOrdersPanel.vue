<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { displayLabel } from '@/utils/displayLabels';
import {
  getAdminOrderDetail,
  getAdminOrders,
  getStageElevenEndpointSummary
} from '@/api/stageEleven';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type {
  AdminOrderPageResult,
  AdminOrderQuery,
  AdminOrderRecord,
  AdminOrderScenario,
  AdminOrderStatus
} from '@/types/stageEleven';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const statusOptions: Array<{ value: AdminOrderStatus | ''; label: string }> = [
  { value: 'WAIT_DISPATCH', label: '待派单' },
  { value: '', label: '全部' },
  { value: 'DISPATCHED', label: '已派单' },
  { value: 'SERVING', label: '服务中' },
  { value: 'WAIT_REPORT', label: '待报告' },
  { value: 'WAIT_CONFIRM', label: '待确认' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELED', label: '已取消' }
];

const query = ref<AdminOrderQuery>({
  page: 1,
  size: 10,
  orderStatus: 'WAIT_DISPATCH',
  keyword: '',
  dateFrom: '',
  dateTo: ''
});
const records = ref<AdminOrderRecord[]>([]);
const selectedDetail = ref<AdminOrderRecord | null>(null);
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<AdminOrderPageResult> | null>(null);
const endpoints = getStageElevenEndpointSummary();

const canUsePanel = computed(() => props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'));
const waitDispatchCount = computed(() => records.value.filter((item) => item.orderStatus === 'WAIT_DISPATCH').length);

function statusLabel(value: AdminOrderStatus) {
  return statusOptions.find((item) => item.value === value)?.label ?? value;
}

function statusClass(value: AdminOrderStatus) {
  if (value === 'WAIT_DISPATCH') {
    return 'tag-amber';
  }
  if (value === 'COMPLETED') {
    return 'tag-teal';
  }
  if (value === 'WAIT_REPORT') {
    return 'tag-blue';
  }
  if (value === 'WAIT_CONFIRM') {
    return 'tag-amber';
  }
  if (value === 'CANCELED') {
    return 'tag-coral';
  }
  return 'tag-blue';
}

function applyPageResponse(response: ApiResponse<AdminOrderPageResult>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    records.value = response.data.records;
    error.value = '';
    message.value = successText;
    selectedDetail.value = response.data.records[0] ?? null;
  } else {
    records.value = [];
    selectedDetail.value = null;
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadOrders(scenario: AdminOrderScenario = 'normal') {
  if (!canUsePanel.value) {
    return;
  }
  loading.value = true;
  const response = await getAdminOrders(query.value, scenario);
  loading.value = false;
  applyPageResponse(
    response,
    scenario === 'empty' ? '当前筛选条件下暂无订单' : ''
  );
}

async function loadDetail(orderId: string) {
  const response = await getAdminOrderDetail(orderId);
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    selectedDetail.value = response.data.records[0] ?? null;
    message.value = '';
    error.value = '';
  } else {
    selectedDetail.value = null;
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

function setStatus(value: AdminOrderStatus | '') {
  query.value.orderStatus = value;
  loadOrders();
}

function clearFilters() {
  query.value = {
    page: 1,
    size: 10,
    orderStatus: '',
    keyword: '',
    dateFrom: '',
    dateTo: ''
  };
  loadOrders();
}

onMounted(() => {
  loadOrders();
});
</script>

<template>
  <view class="stage-eleven-panel glass-panel" aria-label="阶段11管理端订单列表">
    <view class="section-title">
      <text>订单查询</text>
    </view>

    <view class="stage-eleven-summary">
      <view>
        <text class="section-mini">records / WAIT_DISPATCH / page</text>
        <text class="permission-main">{{ records.length }} / {{ waitDispatchCount }} / {{ query.page }}</text>
        <text class="auth-meta">管理端只筛选查看，不直接改状态</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || '暂无追踪信息' }}</text>
        <text class="auth-meta">按状态筛选刚创建的订单</text>
      </view>
    </view>

    <view class="stage-eleven-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="admin-order-filters">
      <view class="binding-options">
        <text class="section-mini">订单状态</text>
        <view class="segmented-row">
          <button
            v-for="item in statusOptions"
            :key="item.label"
            class="choice-button"
            :class="{ active: query.orderStatus === item.value }"
            type="button"
            @click="setStatus(item.value)"
          >
            <text>{{ item.label }}</text>
          </button>
        </view>
      </view>

      <label class="field">
        <text>关键词</text>
        <input v-model="query.keyword" class="input" placeholder="服务名称、联系人或订单编号" />
      </label>
      <label class="field">
        <text>开始日期</text>
        <input v-model="query.dateFrom" class="input" type="date" />
      </label>
      <label class="field">
        <text>结束日期</text>
        <input v-model="query.dateTo" class="input" type="date" />
      </label>
      <view class="binding-actions">
        <button class="hero-action" type="button" :disabled="loading" @click="loadOrders('normal')">
          <text>筛选订单</text>
        </button>
        <button class="ghost-action" type="button" @click="clearFilters">
          <text>清空筛选</text>
        </button>
      </view>
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
        <text class="empty-title">暂无符合条件的订单</text>
        <text class="empty-desc">可调整订单状态、关键词或日期范围后重新查询。</text>
      </view>
    </view>

    <view v-else class="admin-order-workbench">
      <view class="admin-order-table">
        <view v-for="record in records" :key="record.orderId" class="admin-order-row">
          <view>
            <text class="flow-label">{{ record.serviceName || '护理服务' }}</text>
            <text class="flow-time">
              预约时间：{{ record.scheduledStart }}{{ record.contactName ? ` · 联系人：${record.contactName}` : '' }}
            </text>
          </view>
          <view class="order-row-side">
            <text class="tag" :class="statusClass(record.orderStatus)">{{ statusLabel(record.orderStatus) }}</text>
            <button class="ghost-action" type="button" @click="loadDetail(record.orderId)">
              <text>查看详情</text>
            </button>
          </view>
        </view>
      </view>

      <view class="admin-order-detail">
        <view class="contract-response">
          <text class="section-mini">订单详情</text>
          <text v-if="selectedDetail" class="permission-main">
            {{ selectedDetail.serviceName || '护理服务' }} · {{ displayLabel(selectedDetail.orderStatus) }}
          </text>
          <text v-if="selectedDetail" class="auth-meta">
            预约时间：{{ selectedDetail.scheduledStart }}{{ selectedDetail.contactName ? ` · 联系人：${selectedDetail.contactName}` : '' }}
          </text>
          <text v-else class="auth-meta">请选择订单查看详情</text>
        </view>
        <view v-if="selectedDetail" class="status-log-list">
          <view v-for="log in selectedDetail.statusLogs" :key="log.statusLogId" class="status-log-row">
            <text class="flow-label">{{ displayLabel(log.fromStatus || 'INIT') }} → {{ displayLabel(log.toStatus) }}</text>
            <text class="flow-time">{{ log.changedBy }} · {{ log.changeReason }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次管理端订单响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        records {{ lastResponse.data.records.length }} · total {{ lastResponse.data.total }} · page
        {{ lastResponse.data.page }} · size {{ lastResponse.data.size }}
      </text>
    </view>
  </view>
</template>
