<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  getNurseTaskDetail,
  getNurseTasks,
  getStageThirteenEndpointSummary
} from '@/api/stageThirteen';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { NurseTaskStatus } from '@/types/stageTwelve';
import type {
  NurseTaskDetailRecord,
  StageThirteenScenario,
  StageThirteenTaskPageResult,
  StageThirteenTaskQuery
} from '@/types/stageThirteen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const statusOptions: Array<{ value: NurseTaskStatus | ''; label: string }> = [
  { value: '', label: '全部' },
  { value: 'DISPATCHED', label: '待接单' },
  { value: 'ACCEPTED', label: '已接单' },
  { value: 'ON_THE_WAY', label: '前往中' },
  { value: 'SERVING', label: '服务中' },
  { value: 'WAIT_REPORT', label: '待报告' },
  { value: 'WAIT_CONFIRM', label: '待确认' }
];

const query = ref<StageThirteenTaskQuery>({
  status: '',
  page: 1,
  size: 10
});
const records = ref<NurseTaskDetailRecord[]>([]);
const selectedDetail = ref<NurseTaskDetailRecord | null>(null);
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<StageThirteenTaskPageResult> | null>(null);
const endpoints = getStageThirteenEndpointSummary();

const canUsePanel = computed(
  () =>
    (props.roleCode === 'NURSE' && props.authUser?.roles.includes('NURSE')) ||
    (props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'))
);
const consistentCount = computed(() => records.value.filter((item) => item.statusConsistent).length);

function statusLabel(value: string) {
  return statusOptions.find((item) => item.value === value)?.label ?? value;
}

function statusClass(value: string) {
  if (value === 'DISPATCHED') {
    return 'tag-amber';
  }
  if (value === 'SERVING' || value === 'WAIT_REPORT') {
    return 'tag-teal';
  }
  if (value === 'WAIT_CONFIRM') {
    return 'tag-amber';
  }
  return 'tag-blue';
}

function applyResponse(response: ApiResponse<StageThirteenTaskPageResult>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    records.value = response.data.records;
    selectedDetail.value = response.data.records[0] ?? null;
    message.value = successText;
    error.value = '';
  } else {
    records.value = [];
    selectedDetail.value = null;
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadTasks(scenario: StageThirteenScenario = 'normal') {
  if (!canUsePanel.value) {
    return;
  }
  loading.value = true;
  const response = await getNurseTasks(query.value, scenario);
  loading.value = false;
  applyResponse(
    response,
    scenario === 'empty' ? '已切换为空任务列表 mock' : scenario === 'normal' ? '护理任务列表已加载' : ''
  );
}

async function loadDetail(taskId: string) {
  const response = await getNurseTaskDetail(taskId);
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    selectedDetail.value = response.data.records[0] ?? null;
    message.value = '护理任务详情已读取';
    error.value = '';
  } else {
    selectedDetail.value = null;
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

function setStatus(value: NurseTaskStatus | '') {
  query.value.status = value;
  loadTasks();
}

onMounted(() => {
  loadTasks();
});
</script>

<template>
  <view class="stage-thirteen-panel glass-panel" aria-label="阶段13护理任务列表">
    <view class="section-title">
      <text>⑬</text>
      <text>护理任务列表 MVP</text>
    </view>

    <view class="stage-thirteen-summary">
      <view>
        <text class="section-mini">records / consistent / page</text>
        <text class="permission-main">{{ records.length }} / {{ consistentCount }} / {{ query.page }}</text>
        <text class="auth-meta">任务状态和订单状态一致</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-13' }}</text>
        <text class="auth-meta">正式 GET 护理任务列表和详情</text>
      </view>
    </view>

    <view class="stage-thirteen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="nurse-task-toolbar">
      <view class="binding-options">
        <text class="section-mini">状态 status</text>
        <view class="segmented-row">
          <button
            v-for="item in statusOptions"
            :key="item.label"
            class="choice-button"
            :class="{ active: query.status === item.value }"
            type="button"
            @click="setStatus(item.value)"
          >
            <text>{{ item.label }}</text>
          </button>
        </view>
      </view>
      <view class="binding-actions">
        <button class="hero-action" type="button" :disabled="loading" @click="loadTasks('normal')">
          <text>读取任务</text>
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

    <view v-if="records.length === 0 && !error" class="empty-state">
      <text class="empty-icon">∅</text>
      <view>
        <text class="empty-title">暂无护理任务</text>
        <text class="empty-desc">空数据 mock 已返回 records: []，列表和详情契约仍保持 PageResult。</text>
      </view>
    </view>

    <view v-else class="stage-thirteen-workbench">
      <view class="nurse-task-list">
        <view v-for="task in records" :key="task.taskId" class="nurse-task-row">
          <view>
            <text class="flow-label">{{ task.orderNo }} · {{ task.taskId }}</text>
            <text class="flow-time">
              {{ task.nurseName }} · {{ task.elderId }} · {{ task.serviceId }} · {{ task.scheduledStart }}
            </text>
            <text class="flow-time">order {{ task.orderStatus }} / task {{ task.taskStatus }}</text>
          </view>
          <view class="order-row-side">
            <text class="tag" :class="statusClass(task.taskStatus)">{{ statusLabel(task.taskStatus) }}</text>
            <text class="tag" :class="task.statusConsistent ? 'tag-teal' : 'tag-coral'">
              {{ task.statusConsistent ? '状态一致' : '状态不一致' }}
            </text>
            <button class="ghost-action" type="button" @click="loadDetail(task.taskId)">
              <text>查看详情</text>
            </button>
          </view>
        </view>
      </view>

      <view class="nurse-task-detail">
        <view class="contract-response">
          <text class="section-mini">GET /api/v1/nurse/tasks/{taskId}</text>
          <text v-if="selectedDetail" class="permission-main">
            {{ selectedDetail.taskId }} · {{ selectedDetail.taskStatus }}
          </text>
          <text v-if="selectedDetail" class="auth-meta">
            订单 {{ selectedDetail.orderId }} · {{ selectedDetail.orderSnapshotStatus }}
          </text>
          <text v-else class="auth-meta">请选择任务查看详情</text>
        </view>

        <view v-if="selectedDetail" class="status-log-list">
          <view class="status-log-row">
            <text class="flow-label">一致性校验</text>
            <text class="flow-time">
              taskStatus {{ selectedDetail.taskStatus }} / orderStatus {{ selectedDetail.orderSnapshotStatus }}
            </text>
          </view>
          <view v-for="item in selectedDetail.statusTimeline" :key="`${item.status}-${item.at}`" class="status-log-row">
            <text class="flow-label">{{ item.status }} · {{ item.label }}</text>
            <text class="flow-time">{{ item.at }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次护理任务响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        records {{ lastResponse.data.records.length }} · total {{ lastResponse.data.total }} · page
        {{ lastResponse.data.page }} · size {{ lastResponse.data.size }}
      </text>
    </view>
  </view>
</template>
