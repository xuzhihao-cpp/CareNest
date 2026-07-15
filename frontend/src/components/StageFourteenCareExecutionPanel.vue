<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  createServiceRecord,
  createVitalSignRecord,
  getOrderServiceRecords,
  getStageFourteenEndpointSummary
} from '@/api/stageFourteen';
import { getNurseTasks } from '@/api/stageThirteen';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { CareExecutionPageResult, CareExecutionRecord, CareExecutionResponse } from '@/types/stageFourteen';
import type { NurseTaskDetailRecord } from '@/types/stageThirteen';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const serviceForm = ref({
  startTime: '2026-07-10T09:00',
  endTime: '2026-07-10T10:00',
  content: '完成基础护理、环境整理和用药提醒。',
  nursingAdvice: '建议继续观察血压和睡眠。',
  abnormalFlag: false
});
const vitalForm = ref({
  startTime: '2026-07-10T09:35',
  endTime: '2026-07-10T09:45',
  content: '血压 128/78，心率 72，体温 36.5。',
  nursingAdvice: '生命体征平稳，按计划继续观察。',
  abnormalFlag: false
});
const tasks = ref<NurseTaskDetailRecord[]>([]);
const selectedOrderId = ref('');
const records = ref<CareExecutionRecord[]>([]);
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastActionResponse = ref<ApiResponse<CareExecutionResponse> | null>(null);
const lastPageResponse = ref<ApiResponse<CareExecutionPageResult> | null>(null);
const endpoints = getStageFourteenEndpointSummary();

const canSubmit = computed(() => props.roleCode === 'NURSE' && props.authUser?.roles.includes('NURSE'));
const canRead = computed(
  () =>
    (props.roleCode === 'NURSE' && props.authUser?.roles.includes('NURSE')) ||
    (props.roleCode === 'ADMIN' && props.authUser?.roles.includes('ADMIN'))
);
const selectedTask = computed(() => tasks.value.find((item) => item.orderId === selectedOrderId.value) ?? null);
const serviceRecordCount = computed(() => records.value.filter((item) => item.recordType === 'SERVICE_RECORD').length);
const vitalSignCount = computed(() => records.value.filter((item) => item.recordType === 'VITAL_SIGN').length);

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    SERVING: '服务中',
    WAIT_REPORT: '待报告',
    WAIT_CONFIRM: '待确认'
  };
  return labels[value] ?? value;
}

function statusClass(value: string) {
  if (value === 'WAIT_CONFIRM') {
    return 'tag-amber';
  }
  if (value === 'WAIT_REPORT') {
    return 'tag-blue';
  }
  return 'tag-teal';
}

async function loadTasks() {
  if (!canRead.value) {
    return;
  }
  const response = await getNurseTasks({ status: '', page: 1, size: 10 }, 'normal');
  if (response.code === 0) {
    tasks.value = response.data.records.filter((item) =>
      ['SERVING', 'WAIT_REPORT', 'WAIT_CONFIRM'].includes(item.taskStatus)
    );
    selectedOrderId.value = tasks.value[0]?.orderId ?? '';
    if (selectedOrderId.value) {
      await loadRecords('normal');
    }
  } else {
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadRecords(scenario: 'normal' | 'empty' | 'error' = 'normal') {
  if (!selectedOrderId.value) {
    records.value = [];
    return;
  }
  const response = await getOrderServiceRecords(selectedOrderId.value, scenario);
  lastPageResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    records.value = response.data.records;
    error.value = '';
    message.value = scenario === 'empty' ? '当前暂无服务记录' : '护理执行记录已读取';
  } else {
    records.value = [];
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function submitServiceRecord() {
  if (!selectedOrderId.value) {
    error.value = '请选择服务中任务';
    return;
  }
  loading.value = true;
  const response = await createServiceRecord(selectedOrderId.value, serviceForm.value);
  loading.value = false;
  lastActionResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = `护理执行记录已提交，订单进入${statusLabel(response.data.orderStatus)}`;
    error.value = '';
    await loadTasks();
    selectedOrderId.value = response.data.orderId;
    await loadRecords('normal');
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function submitVitalSign() {
  if (!selectedOrderId.value) {
    error.value = '请选择服务中任务';
    return;
  }
  loading.value = true;
  const response = await createVitalSignRecord(selectedOrderId.value, vitalForm.value);
  loading.value = false;
  lastActionResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = `生命体征记录已提交，订单进入${statusLabel(response.data.orderStatus)}`;
    error.value = '';
    await loadTasks();
    selectedOrderId.value = response.data.orderId;
    await loadRecords('normal');
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

function chooseTask(orderId: string) {
  selectedOrderId.value = orderId;
  loadRecords('normal');
}

onMounted(() => {
  loadTasks();
});
</script>

<template>
  <view class="stage-fourteen-panel glass-panel" aria-label="阶段14护理执行记录">
    <view class="section-title">
      <text>⑭</text>
      <text>护理执行记录</text>
    </view>

    <view class="stage-fourteen-summary">
      <view>
        <text class="section-mini">records / vital / status</text>
        <text class="permission-main">
          {{ serviceRecordCount }} / {{ vitalSignCount }} / {{ selectedTask ? statusLabel(selectedTask.taskStatus) : '-' }}
        </text>
        <text class="auth-meta">提交后进入 WAIT_REPORT 或 WAIT_CONFIRM</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || '暂无追踪信息' }}</text>
        <text class="auth-meta">只做服务记录和生命体征，不生成报告</text>
      </view>
    </view>

    <view class="stage-fourteen-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view class="care-execution-workbench">
      <view class="care-task-list">
        <view class="contract-response">
          <text class="section-mini">可提交执行记录的任务</text>
          <text class="permission-main">{{ tasks.length }} 单</text>
          <text class="auth-meta">来自阶段13任务列表</text>
        </view>
        <button
          v-for="task in tasks"
          :key="task.taskId"
          class="nurse-task-row"
          :class="{ active: selectedOrderId === task.orderId }"
          type="button"
          @click="chooseTask(task.orderId)"
        >
          <view>
            <text class="flow-label">{{ task.orderNo }} · {{ task.taskId }}</text>
            <text class="flow-time">{{ task.orderId }} · {{ task.serviceId }} · {{ task.scheduledStart }}</text>
          </view>
          <text class="tag" :class="statusClass(task.taskStatus)">{{ statusLabel(task.taskStatus) }}</text>
        </button>
      </view>

      <view class="care-record-form">
        <view class="contract-response">
          <text class="section-mini">服务记录</text>
          <text class="permission-main">{{ selectedOrderId || '请选择任务' }}</text>
          <text class="auth-meta">记录本次服务过程和护理建议</text>
        </view>
        <label class="field">
          <text>开始时间</text>
          <input v-model="serviceForm.startTime" class="input" />
        </label>
        <label class="field">
          <text>结束时间</text>
          <input v-model="serviceForm.endTime" class="input" />
        </label>
        <label class="field">
          <text>服务内容</text>
          <input v-model="serviceForm.content" class="input" />
        </label>
        <label class="field">
          <text>护理建议</text>
          <input v-model="serviceForm.nursingAdvice" class="input" />
        </label>
        <view class="binding-options">
          <text class="section-mini">是否发现异常</text>
          <view class="segmented-row">
            <button
              class="choice-button"
              :class="{ active: !serviceForm.abnormalFlag }"
              type="button"
              @click="serviceForm.abnormalFlag = false"
            >
              <text>正常 → 待报告</text>
            </button>
            <button
              class="choice-button"
              :class="{ active: serviceForm.abnormalFlag }"
              type="button"
              @click="serviceForm.abnormalFlag = true"
            >
              <text>异常 → 待确认</text>
            </button>
          </view>
        </view>
        <button v-if="canSubmit" class="hero-action" type="button" :disabled="loading" @click="submitServiceRecord">
          <text>提交护理记录</text>
        </button>
      </view>

      <view class="care-record-form">
        <view class="contract-response">
          <text class="section-mini">生命体征记录</text>
          <text class="permission-main">生命体征</text>
          <text class="auth-meta">记录本次测量结果和后续建议</text>
        </view>
        <label class="field">
          <text>开始时间</text>
          <input v-model="vitalForm.startTime" class="input" />
        </label>
        <label class="field">
          <text>结束时间</text>
          <input v-model="vitalForm.endTime" class="input" />
        </label>
        <label class="field">
          <text>生命体征</text>
          <input v-model="vitalForm.content" class="input" />
        </label>
        <label class="field">
          <text>护理建议</text>
          <input v-model="vitalForm.nursingAdvice" class="input" />
        </label>
        <view class="binding-options">
          <text class="section-mini">是否发现异常</text>
          <view class="segmented-row">
            <button
              class="choice-button"
              :class="{ active: !vitalForm.abnormalFlag }"
              type="button"
              @click="vitalForm.abnormalFlag = false"
            >
              <text>正常 → 待报告</text>
            </button>
            <button
              class="choice-button"
              :class="{ active: vitalForm.abnormalFlag }"
              type="button"
              @click="vitalForm.abnormalFlag = true"
            >
              <text>异常 → 待确认</text>
            </button>
          </view>
        </view>
        <button v-if="canSubmit" class="ghost-action" type="button" :disabled="loading" @click="submitVitalSign">
          <text>提交生命体征</text>
        </button>
      </view>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view class="binding-actions">
      <button class="ghost-action" type="button" @click="loadRecords('normal')">
        <text>读取记录</text>
      </button>
    </view>

    <view v-if="records.length === 0 && !error" class="empty-state">
      <text class="empty-icon">∅</text>
      <view>
        <text class="empty-title">暂无护理执行记录</text>
        <text class="empty-desc">完成服务后，可在上方填写服务记录或生命体征。</text>
      </view>
    </view>

    <view v-else class="care-record-list">
      <view v-for="record in records" :key="record.recordId" class="nurse-task-row">
        <view>
          <text class="flow-label">{{ record.recordId }} · {{ record.recordType }}</text>
          <text class="flow-time">{{ record.startTime }} - {{ record.endTime }}</text>
          <text class="flow-time">{{ record.content }}</text>
          <text class="flow-time">{{ record.nursingAdvice }}</text>
        </view>
        <view class="order-row-side">
          <text class="tag" :class="statusClass(record.orderStatus)">{{ statusLabel(record.orderStatus) }}</text>
          <text class="tag" :class="record.abnormalFlag ? 'tag-coral' : 'tag-teal'">
            {{ record.abnormalFlag ? '异常' : '正常' }}
          </text>
        </view>
      </view>
    </view>

    <view class="contract-response">
      <text class="section-mini">最近一次阶段14响应 DTO</text>
      <text v-if="lastActionResponse">
        {{ lastActionResponse.code }} / {{ lastActionResponse.message }} / {{ lastActionResponse.traceId }}
      </text>
      <text v-if="lastActionResponse && lastActionResponse.code === 0">
        {{ lastActionResponse.data.recordId }} · {{ lastActionResponse.data.orderId }} ·
        {{ lastActionResponse.data.orderStatus }}
      </text>
      <text v-else-if="lastPageResponse">
        {{ lastPageResponse.code }} / {{ lastPageResponse.message }} / {{ lastPageResponse.traceId }}
      </text>
      <text v-if="lastPageResponse && lastPageResponse.code === 0">
        records {{ lastPageResponse.data.records.length }} · total {{ lastPageResponse.data.total }} · page
        {{ lastPageResponse.data.page }}
      </text>
    </view>
  </view>
</template>
