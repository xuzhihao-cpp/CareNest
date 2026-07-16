<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import StageTwentyNineRecommendationPanel from '@/components/StageTwentyNineRecommendationPanel.vue';
import StageThirtyAdminPreferenceSummary from '@/components/StageThirtyAdminPreferenceSummary.vue';
import StageThirtyOneAttentionPanel from '@/components/StageThirtyOneAttentionPanel.vue';
import { createLatestRequestGate } from '@/utils/latestRequestGate';
import {
  getAdminOrderDetail,
  getAdminOrders
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

const props = withDefaults(defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
  canViewRecommendations: boolean;
  canReviewAttentionNotices?: boolean;
}>(), {
  canReviewAttentionNotices: false
});

const adminStatusOptions: Array<{ value: AdminOrderStatus | ''; label: string }> = [
  { value: 'WAIT_DISPATCH', label: '待派单' },
  { value: '', label: '全部' },
  { value: 'DISPATCHED', label: '已派单' },
  { value: 'ACCEPTED', label: '护理员已接单' },
  { value: 'ON_THE_WAY', label: '正在前往' },
  { value: 'SERVING', label: '服务中' },
  { value: 'WAIT_REPORT', label: '待报告' },
  { value: 'WAIT_CONFIRM', label: '待确认' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELED', label: '已取消' }
];
const reviewStatusOptions: Array<{ value: AdminOrderStatus | ''; label: string }> = [
  { value: '', label: '全部可审阅' },
  ...adminStatusOptions.filter((item) => item.value && !['WAIT_DISPATCH', 'CANCELED'].includes(item.value))
];
const statusOptions = computed(() => props.roleCode === 'CUSTOMER_SERVICE'
  ? reviewStatusOptions
  : adminStatusOptions);

const query = ref<AdminOrderQuery>({
  page: 1,
  size: 10,
  orderStatus: props.roleCode === 'CUSTOMER_SERVICE' ? '' : 'WAIT_DISPATCH',
  keyword: '',
  dateFrom: '',
  dateTo: ''
});
const records = ref<AdminOrderRecord[]>([]);
const selectedDetail = ref<AdminOrderRecord | null>(null);
const selectedOrderId = ref('');
const loading = ref(false);
const message = ref('');
const error = ref('');
const listRequestGate = createLatestRequestGate<string>();
const detailRequestGate = createLatestRequestGate<string>();

const canUsePanel = computed(() => (
  props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN')
) || (
  props.roleCode === 'CUSTOMER_SERVICE'
  && props.authUser?.roles.includes('CUSTOMER_SERVICE')
  && props.canReviewAttentionNotices
));
function statusLabel(value: AdminOrderStatus) {
  return adminStatusOptions.find((item) => item.value === value)?.label ?? '状态待同步';
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

function attentionReviewAvailable(status: AdminOrderStatus) {
  return !['WAIT_DISPATCH', 'CANCELED'].includes(status);
}

function applyPageResponse(response: ApiResponse<AdminOrderPageResult>, successText: string) {
  if (response.code === 0) {
    const visibleRecords = props.roleCode === 'CUSTOMER_SERVICE'
      ? response.data.records.filter((item) => attentionReviewAvailable(item.orderStatus))
      : response.data.records;
    records.value = visibleRecords;
    error.value = '';
    message.value = successText;
    const nextDetail = visibleRecords.find((item) => item.orderId === selectedOrderId.value)
      ?? visibleRecords[0]
      ?? null;
    if (nextDetail?.orderId !== selectedOrderId.value) {
      detailRequestGate.invalidate();
    }
    selectedOrderId.value = nextDetail?.orderId ?? '';
    selectedDetail.value = nextDetail;
  } else {
    records.value = [];
    selectedOrderId.value = '';
    selectedDetail.value = null;
    detailRequestGate.invalidate();
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadOrders(scenario: AdminOrderScenario = 'normal') {
  if (!canUsePanel.value) {
    return;
  }
  const requestKey = JSON.stringify(query.value);
  const requestTicket = listRequestGate.begin(`${scenario}:${requestKey}`);
  loading.value = true;
  const response = await getAdminOrders(query.value, scenario);
  const currentKey = `${scenario}:${JSON.stringify(query.value)}`;
  if (!listRequestGate.isCurrent(requestTicket, currentKey)) {
    return;
  }
  loading.value = false;
  applyPageResponse(
    response,
    scenario === 'empty' ? '当前筛选条件下暂无订单' : ''
  );
}

async function loadDetail(orderId: string) {
  const requestTicket = detailRequestGate.begin(orderId);
  selectedOrderId.value = orderId;
  selectedDetail.value = records.value.find((item) => item.orderId === orderId) ?? null;
  const response = await getAdminOrderDetail(orderId);
  if (!detailRequestGate.isCurrent(requestTicket, selectedOrderId.value)) {
    return;
  }
  if (response.code === 0) {
    const detail = response.data.records.find((item) => item.orderId === orderId) ?? null;
    if (detail) {
      selectedDetail.value = detail;
      message.value = '';
      error.value = '';
    } else {
      selectedDetail.value = null;
      message.value = '';
      error.value = '订单详情响应不完整，请刷新后重试';
    }
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

onBeforeUnmount(() => {
  listRequestGate.invalidate();
  detailRequestGate.invalidate();
});
</script>

<template>
  <view class="stage-eleven-panel glass-panel" aria-label="阶段11管理端订单列表">
    <view class="section-title">
      <text>订单查询</text>
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
            {{ selectedDetail.serviceName || '护理服务' }} · {{ statusLabel(selectedDetail.orderStatus) }}
          </text>
          <text v-if="selectedDetail" class="auth-meta">
            预约时间：{{ selectedDetail.scheduledStart }}{{ selectedDetail.contactName ? ` · 联系人：${selectedDetail.contactName}` : '' }}
          </text>
          <text v-else class="auth-meta">请选择订单查看详情</text>
        </view>
        <view v-if="selectedDetail" class="status-log-list">
          <view v-for="log in selectedDetail.statusLogs" :key="log.statusLogId" class="status-log-row">
            <text class="flow-label">{{ log.fromStatus ? statusLabel(log.fromStatus) : '订单已创建' }} → {{ statusLabel(log.toStatus) }}</text>
            <text class="flow-time">{{ log.changeReason || '订单状态已更新' }}</text>
          </view>
        </view>
        <StageThirtyAdminPreferenceSummary v-if="selectedDetail && canViewRecommendations" :order="selectedDetail" />
        <StageTwentyNineRecommendationPanel
          v-if="selectedDetail && canViewRecommendations"
          mode="order"
          :order-id="selectedDetail.orderId"
        />
        <view v-else-if="selectedDetail" class="recommendation-access-note">当前账号无权查看护理推荐信息。</view>
        <StageThirtyOneAttentionPanel
          v-if="selectedDetail && canReviewAttentionNotices && attentionReviewAvailable(selectedDetail.orderStatus)"
          :key="selectedDetail.orderId"
          :order-id="selectedDetail.orderId"
          :task-status="selectedDetail.orderStatus"
          read-only
        />
        <view
          v-else-if="selectedDetail && attentionReviewAvailable(selectedDetail.orderStatus)"
          class="recommendation-access-note"
        >当前账号无权审阅服务前注意事项。</view>
      </view>
    </view>

  </view>
</template>

<style scoped>
.recommendation-access-note { padding:14px 16px; border-left:4px solid #c98e34; background:#fff8e8; color:#755417; font-size:13px; line-height:1.6; }
.stage-eleven-panel :deep(.attention-panel) { margin-top:18px; padding:20px; border:1px solid #d5e3df; background:#fbfdfc; }
</style>
