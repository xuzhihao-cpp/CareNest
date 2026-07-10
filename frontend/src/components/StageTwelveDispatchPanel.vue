<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getAdminOrders } from '@/api/stageEleven';
import {
  acceptNurseTask,
  dispatchAdminOrder,
  getStageTwelveEndpointSummary,
  getStageTwelveNurseTasks,
  resetStageTwelveMockRecords,
  updateNurseTaskStatus
} from '@/api/stageTwelve';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { AdminOrderPageResult, AdminOrderRecord } from '@/types/stageEleven';
import type {
  DispatchRequest,
  NurseTaskPageResult,
  NurseTaskRecord,
  NurseTaskStatus,
  StageTwelveScenario,
  TaskActionResponse
} from '@/types/stageTwelve';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const nurseOptions = [
  { nurseId: 'nurse-001', nurseName: '护理演示账号' },
  { nurseId: 'nurse-002', nurseName: '李护士' },
  { nurseId: 'nurse-003', nurseName: '王护士' }
];

const dispatchForm = ref<DispatchRequest>({
  nurseId: 'nurse-001',
  dispatchRemark: '阶段12管理端派单',
  targetStatus: 'DISPATCHED'
});
const taskQuery = ref({
  status: '' as NurseTaskStatus | '',
  page: 1,
  size: 10
});
const orders = ref<AdminOrderRecord[]>([]);
const selectedOrderId = ref('');
const tasks = ref<NurseTaskRecord[]>([]);
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastActionResponse = ref<ApiResponse<TaskActionResponse> | null>(null);
const lastTaskResponse = ref<ApiResponse<NurseTaskPageResult> | null>(null);
const lastOrderResponse = ref<ApiResponse<AdminOrderPageResult> | null>(null);
const endpoints = getStageTwelveEndpointSummary();

const canAdminDispatch = computed(() => props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'));
const canNurseOperate = computed(() => props.roleCode === 'NURSE' && props.authUser?.roles.includes('NURSE'));
const dispatchedCount = computed(() => tasks.value.filter((item) => item.taskStatus === 'DISPATCHED').length);
const activeTask = computed(() => tasks.value[0] ?? null);

function statusLabel(value: NurseTaskStatus) {
  const labels: Record<NurseTaskStatus, string> = {
    DISPATCHED: '待接单',
    ACCEPTED: '已接单',
    ON_THE_WAY: '前往中',
    SERVING: '服务中',
    WAIT_REPORT: '待报告',
    WAIT_CONFIRM: '待确认',
    COMPLETED: '已完成'
  };
  return labels[value] ?? value;
}

function statusClass(value: NurseTaskStatus) {
  if (value === 'DISPATCHED') {
    return 'tag-amber';
  }
  if (value === 'SERVING') {
    return 'tag-teal';
  }
  return 'tag-blue';
}

function applyActionResponse(response: ApiResponse<TaskActionResponse>, successText: string) {
  lastActionResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = successText;
    error.value = '';
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadDispatchOrders() {
  if (!canAdminDispatch.value) {
    return;
  }
  const response = await getAdminOrders({ page: 1, size: 10, orderStatus: 'WAIT_DISPATCH' }, 'normal');
  lastOrderResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    orders.value = response.data.records;
    selectedOrderId.value = response.data.records[0]?.orderId ?? '';
    error.value = '';
  } else {
    orders.value = [];
    selectedOrderId.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadTasks(scenario: StageTwelveScenario = 'normal') {
  const response = await getStageTwelveNurseTasks(taskQuery.value, scenario);
  lastTaskResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    tasks.value = response.data.records;
    error.value = '';
    message.value = scenario === 'empty' ? '已切换为空任务 mock' : '护理任务视图已同步';
  } else {
    tasks.value = [];
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function handleDispatch() {
  if (!selectedOrderId.value) {
    error.value = '请选择待派单订单';
    return;
  }
  loading.value = true;
  const response = await dispatchAdminOrder(selectedOrderId.value, dispatchForm.value);
  loading.value = false;
  applyActionResponse(response, '派单成功，护理端任务已生成');
  await Promise.all([loadDispatchOrders(), loadTasks()]);
}

async function handleAccept(task: NurseTaskRecord) {
  loading.value = true;
  const response = await acceptNurseTask(task.taskId, {
    nurseId: task.nurseId,
    dispatchRemark: '护理端接单',
    targetStatus: 'ACCEPTED'
  });
  loading.value = false;
  applyActionResponse(response, '护理端接单成功，订单状态已同步');
  await loadTasks();
}

async function handleStatus(task: NurseTaskRecord, targetStatus: NurseTaskStatus) {
  loading.value = true;
  const response = await updateNurseTaskStatus(task.taskId, {
    nurseId: task.nurseId,
    dispatchRemark: statusLabel(targetStatus),
    targetStatus
  });
  loading.value = false;
  applyActionResponse(response, `任务状态已更新为${statusLabel(targetStatus)}`);
  await loadTasks();
}

function setTaskStatus(value: NurseTaskStatus | '') {
  taskQuery.value.status = value;
  loadTasks();
}

async function resetMock() {
  resetStageTwelveMockRecords();
  message.value = '阶段12 mock 已重置';
  error.value = '';
  await Promise.all([loadDispatchOrders(), loadTasks()]);
}

onMounted(() => {
  if (canAdminDispatch.value) {
    loadDispatchOrders();
    loadTasks();
  }
  if (canNurseOperate.value) {
    loadTasks();
  }
});
</script>

<template>
  <view class="stage-twelve-panel glass-panel" aria-label="阶段12派单与护理任务">
    <view class="section-title">
      <text>⑫</text>
      <text>派单与任务状态 MVP</text>
    </view>

    <view class="stage-twelve-summary">
      <view>
        <text class="section-mini">tasks / DISPATCHED / role</text>
        <text class="permission-main">{{ tasks.length }} / {{ dispatchedCount }} / {{ props.roleCode }}</text>
        <text class="auth-meta">派单后护理端可见任务</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-12' }}</text>
        <text class="auth-meta">订单状态和 nurse_task 同步</text>
      </view>
    </view>

    <view class="stage-twelve-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view v-if="canAdminDispatch" class="dispatch-workbench">
      <view class="dispatch-order-list">
        <view class="contract-response">
          <text class="section-mini">待派单订单 WAIT_DISPATCH</text>
          <text class="permission-main">{{ orders.length }} 单</text>
          <text class="auth-meta">阶段12只派单，不做取消和改期</text>
        </view>
        <button
          v-for="record in orders"
          :key="record.orderId"
          class="admin-order-row"
          :class="{ active: selectedOrderId === record.orderId }"
          type="button"
          @click="selectedOrderId = record.orderId"
        >
          <view>
            <text class="flow-label">{{ record.orderNo }}</text>
            <text class="flow-time">
              {{ record.orderId }} · {{ record.elderId }} · {{ record.serviceId }} · {{ record.scheduledStart }}
            </text>
          </view>
          <text class="tag tag-amber">待派单</text>
        </button>
      </view>

      <view class="dispatch-form">
        <view class="contract-response">
          <text class="section-mini">POST /api/v1/admin/orders/{orderId}/dispatch</text>
          <text class="permission-main">{{ selectedOrderId || '请选择订单' }}</text>
          <text class="auth-meta">targetStatus 固定为 DISPATCHED</text>
        </view>

        <view class="binding-options">
          <text class="section-mini">护理员 nurseId</text>
          <view class="segmented-row">
            <button
              v-for="item in nurseOptions"
              :key="item.nurseId"
              class="choice-button"
              :class="{ active: dispatchForm.nurseId === item.nurseId }"
              type="button"
              @click="dispatchForm.nurseId = item.nurseId"
            >
              <text>{{ item.nurseName }}</text>
            </button>
          </view>
        </view>

        <label class="field">
          <text>派单备注 dispatchRemark</text>
          <input v-model="dispatchForm.dispatchRemark" class="input" placeholder="阶段12管理端派单" />
        </label>

        <view class="binding-actions">
          <button class="hero-action" type="button" :disabled="loading || !selectedOrderId" @click="handleDispatch">
            <text>确认派单</text>
          </button>
          <button class="ghost-action test-action" type="button" @click="resetMock">
            <text>重置 mock</text>
          </button>
        </view>
      </view>
    </view>

    <view v-if="canNurseOperate" class="nurse-task-toolbar">
      <view class="binding-options">
        <text class="section-mini">任务状态 status</text>
        <view class="segmented-row">
          <button class="choice-button" :class="{ active: taskQuery.status === '' }" type="button" @click="setTaskStatus('')">
            <text>全部</text>
          </button>
          <button
            v-for="item in ['DISPATCHED', 'ACCEPTED', 'ON_THE_WAY', 'SERVING']"
            :key="item"
            class="choice-button"
            :class="{ active: taskQuery.status === item }"
            type="button"
            @click="setTaskStatus(item as NurseTaskStatus)"
          >
            <text>{{ statusLabel(item as NurseTaskStatus) }}</text>
          </button>
        </view>
      </view>
      <view class="binding-actions">
        <button class="ghost-action" type="button" @click="loadTasks('normal')">
          <text>刷新任务</text>
        </button>
        <button class="ghost-action test-action" type="button" @click="loadTasks('empty')">
          <text>空数据 mock</text>
        </button>
        <button class="ghost-action test-action" type="button" @click="loadTasks('error')">
          <text>错误 mock</text>
        </button>
      </view>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view v-if="tasks.length === 0 && !error" class="empty-state">
      <text class="empty-icon">∅</text>
      <view>
        <text class="empty-title">暂无护理任务</text>
        <text class="empty-desc">管理端完成派单后，这里会显示同一笔订单生成的 nurse_task。</text>
      </view>
    </view>

    <view v-else class="nurse-task-list">
      <view v-for="task in tasks" :key="task.taskId" class="nurse-task-row">
        <view>
          <text class="flow-label">{{ task.orderNo }} · {{ task.taskId }}</text>
          <text class="flow-time">
            {{ task.nurseName }} · {{ task.elderId }} · {{ task.serviceId }} · {{ task.scheduledStart }}
          </text>
          <text class="flow-time">{{ task.dispatchRemark }}</text>
        </view>
        <view class="order-row-side">
          <text class="tag" :class="statusClass(task.taskStatus)">{{ statusLabel(task.taskStatus) }}</text>
          <button
            v-if="canNurseOperate && task.taskStatus === 'DISPATCHED'"
            class="ghost-action"
            type="button"
            @click="handleAccept(task)"
          >
            <text>接单</text>
          </button>
          <button
            v-if="canNurseOperate && task.taskStatus === 'ACCEPTED'"
            class="ghost-action"
            type="button"
            @click="handleStatus(task, 'ON_THE_WAY')"
          >
            <text>出发</text>
          </button>
          <button
            v-if="canNurseOperate && task.taskStatus === 'ON_THE_WAY'"
            class="ghost-action"
            type="button"
            @click="handleStatus(task, 'SERVING')"
          >
            <text>开始服务</text>
          </button>
        </view>
      </view>
    </view>

    <view class="contract-response">
      <text class="section-mini">最近一次阶段12响应 DTO</text>
      <text v-if="lastActionResponse">
        {{ lastActionResponse.code }} / {{ lastActionResponse.message }} / {{ lastActionResponse.traceId }}
      </text>
      <text v-if="lastActionResponse && lastActionResponse.code === 0">
        {{ lastActionResponse.data.orderId }} · {{ lastActionResponse.data.orderStatus }} ·
        {{ lastActionResponse.data.taskId }}
      </text>
      <text v-else-if="lastTaskResponse">
        {{ lastTaskResponse.code }} / {{ lastTaskResponse.message }} / {{ lastTaskResponse.traceId }}
      </text>
      <text v-if="lastTaskResponse && lastTaskResponse.code === 0">
        records {{ lastTaskResponse.data.records.length }} · total {{ lastTaskResponse.data.total }} · page
        {{ lastTaskResponse.data.page }}
      </text>
      <text v-if="lastOrderResponse && canAdminDispatch" class="auth-meta">
        admin orders {{ lastOrderResponse.data.records.length }} · {{ lastOrderResponse.traceId }}
      </text>
      <text v-if="activeTask" class="auth-meta">
        当前任务：{{ activeTask.orderId }} / {{ activeTask.orderStatus }}
      </text>
    </view>
  </view>
</template>
