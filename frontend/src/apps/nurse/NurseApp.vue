<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import { generateServiceReport } from '@/api/stageFifteen';
import { createServiceRecord, getOrderServiceRecords } from '@/api/stageFourteen';
import { getNurseTasks } from '@/api/stageThirteen';
import { acceptNurseTask, updateNurseTaskStatus } from '@/api/stageTwelve';
import StageTwentyThreeSuggestionPanel from '@/components/StageTwentyThreeSuggestionPanel.vue';
import StageTwentyFivePreServiceSummary from '@/components/StageTwentyFivePreServiceSummary.vue';
import StageTwentySixQualificationPanel from '@/components/StageTwentySixQualificationPanel.vue';
import StageFiftyRecommendedArticles from '@/components/StageFiftyRecommendedArticles.vue';
import { getRecommendedTrainingArticles } from '@/api/stageFortyNineToFiftyFive';
import type { AuthUser } from '@/types/stageTwo';
import type { NurseTaskDetailRecord } from '@/types/stageThirteen';
import type { NurseTaskStatus } from '@/types/stageTwelve';
import type { CareExecutionRecord } from '@/types/stageFourteen';

const user = ref<AuthUser | null>(null);
const tasks = ref<NurseTaskDetailRecord[]>([]);
const serviceRecords = ref<CareExecutionRecord[]>([]);
const recordsLoading = ref(false);
const recentRecordId = ref('');
const selectedTaskId = ref('');
const summaryTaskId = ref('');
const attentionRefreshKey = ref(0);
const activeTab = ref<'tasks' | 'records' | 'suggestions' | 'qualification' | 'health-summary' | 'articles'>('tasks');
const articleTaskId = ref('');
const isEditingRecord = ref(false);
const suggestionOrderId = ref('');
const loading = ref(false);
const notice = ref('');
const error = ref('');
const recordForm = ref({
  startTime: '',
  endTime: '',
  content: '',
  nursingAdvice: '',
  abnormalFlag: false
});

const selectedTask = computed(() => tasks.value.find((task) => task.taskId === selectedTaskId.value) ?? tasks.value[0] ?? null);
const summaryTask = computed(() => tasks.value.find((task) => task.taskId === summaryTaskId.value) ?? null);
const pendingCount = computed(() => tasks.value.filter((task) => task.taskStatus === 'DISPATCHED').length);
const activeCount = computed(() => tasks.value.filter((task) => ['ACCEPTED', 'ON_THE_WAY', 'SERVING'].includes(task.taskStatus)).length);
const recordedOrderIds = computed(() => new Set(serviceRecords.value.map((record) => record.orderId)));
const servingTasks = computed(() => tasks.value.filter((task) => task.taskStatus === 'SERVING'));
const activeServiceTasks = computed(() => tasks.value.filter((task) =>
  ['DISPATCHED', 'ACCEPTED', 'ON_THE_WAY', 'SERVING'].includes(task.taskStatus)
));
const activeTaskGroups = computed(() => [
  { status: 'DISPATCHED', label: '待接单', tasks: tasks.value.filter((task) => task.taskStatus === 'DISPATCHED') },
  { status: 'ACCEPTED', label: '已接单', tasks: tasks.value.filter((task) => task.taskStatus === 'ACCEPTED') },
  { status: 'ON_THE_WAY', label: '前往中', tasks: tasks.value.filter((task) => task.taskStatus === 'ON_THE_WAY') },
  { status: 'SERVING', label: '服务中', tasks: tasks.value.filter((task) => task.taskStatus === 'SERVING') }
]);
const preServiceSummaryStatuses: NurseTaskStatus[] = ['DISPATCHED', 'ACCEPTED', 'ON_THE_WAY'];
const pendingRecordTasks = computed(() => tasks.value.filter((task) =>
  task.taskStatus === 'COMPLETED'
  && task.orderStatus === 'WAIT_REPORT'
  && !recordedOrderIds.value.has(task.orderId)
));
const waitingConfirmTasks = computed(() => tasks.value.filter((task) => task.orderStatus === 'WAIT_CONFIRM'));
const completedTasks = computed(() => tasks.value.filter((task) => task.orderStatus === 'COMPLETED'));
const canceledTasks = computed(() => tasks.value.filter((task) => task.orderStatus === 'CANCELED'));
const completedCount = computed(() => completedTasks.value.length);
const recordableTask = computed(() => pendingRecordTasks.value.find((task) => task.taskId === selectedTaskId.value) ?? null);

type RecordTimeField = 'startTime' | 'endTime';

function padTime(value: number) {
  return String(value).padStart(2, '0');
}

function localDate(value = new Date()) {
  return `${value.getFullYear()}-${padTime(value.getMonth() + 1)}-${padTime(value.getDate())}`;
}

function localDateTime(value = new Date()) {
  return `${localDate(value)}T${padTime(value.getHours())}:${padTime(value.getMinutes())}`;
}

function oneHourAfter(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return localDateTime(new Date(Date.now() + 60 * 60 * 1000));
  }
  date.setHours(date.getHours() + 1);
  return localDateTime(date);
}

function resetRecordForm(task: NurseTaskDetailRecord) {
  const startTime = task.scheduledStart?.slice(0, 16) || localDateTime();
  recordForm.value = {
    startTime,
    endTime: oneHourAfter(startTime),
    content: '',
    nursingAdvice: '',
    abnormalFlag: false
  };
}

function recordDate(value: string) {
  return value.slice(0, 10) || localDate();
}

function recordTime(value: string) {
  return value.slice(11, 16) || '09:00';
}

function selectRecordDate(field: RecordTimeField, event: { detail: { value: string } }) {
  recordForm.value[field] = `${event.detail.value}T${recordTime(recordForm.value[field])}`;
  error.value = '';
}

function selectRecordTime(field: RecordTimeField, event: { detail: { value: string } }) {
  recordForm.value[field] = `${recordDate(recordForm.value[field])}T${event.detail.value}`;
  error.value = '';
}

const statusText: Record<string, string> = {
  DISPATCHED: '待接单',
  ACCEPTED: '已接单',
  ON_THE_WAY: '前往中',
  SERVING: '服务中',
  WAIT_REPORT: '待提交报告',
  WAIT_CONFIRM: '等待确认',
  COMPLETED: '已完成',
  CANCELED: '已取消'
};

function label(value: string) {
  return statusText[value] ?? value;
}

function taskTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

async function loadTasks() {
  loading.value = true;
  error.value = '';
  const [userResponse, taskResponse] = await Promise.all([
    getCurrentUser(),
    getNurseTasks({ page: 1, size: 30, status: '' })
  ]);
  loading.value = false;
  if (userResponse.code === 0) {
    user.value = userResponse.data;
  }
  if (taskResponse.code !== 0) {
    error.value = `${taskResponse.code} ${taskResponse.message}`;
    return;
  }
  tasks.value = taskResponse.data.records;
  if (summaryTaskId.value && !tasks.value.some((task) => task.taskId === summaryTaskId.value)) {
    summaryTaskId.value = '';
    activeTab.value = 'tasks';
  }
  if (!selectedTaskId.value || !tasks.value.some((task) => task.taskId === selectedTaskId.value)) {
    selectedTaskId.value = activeServiceTasks.value[0]?.taskId ?? pendingRecordTasks.value[0]?.taskId ?? completedTasks.value[0]?.taskId ?? '';
  }
  await loadServiceRecords();
}

async function loadServiceRecords() {
  recordsLoading.value = true;
  const responses = await Promise.all(tasks.value.map((task) => getOrderServiceRecords(task.orderId)));
  serviceRecords.value = responses.flatMap((response, index) => response.code === 0
    ? response.data.records.map((record) => ({ ...record, taskId: tasks.value[index].taskId, nurseId: tasks.value[index].nurseId }))
    : []);
  recordsLoading.value = false;
}

function openRecords() {
  summaryTaskId.value = '';
  activeTab.value = 'records';
  isEditingRecord.value = false;
}

function openRecordEditor(task = pendingRecordTasks.value[0]) {
  if (!task) {
    error.value = '当前没有待填写服务记录的已完成任务';
    return;
  }
  selectedTaskId.value = task.taskId;
  summaryTaskId.value = '';
  resetRecordForm(task);
  error.value = '';
  activeTab.value = 'records';
  isEditingRecord.value = true;
}

function openRecordsForTask(task: NurseTaskDetailRecord) {
  selectedTaskId.value = task.taskId;
  summaryTaskId.value = '';
  activeTab.value = 'records';
  isEditingRecord.value = false;
}

function openSuggestions(task?: NurseTaskDetailRecord) {
  summaryTaskId.value = '';
  suggestionOrderId.value = task?.orderId ?? selectedTask.value?.orderId ?? suggestionOrderId.value;
  activeTab.value = 'suggestions';
  isEditingRecord.value = false;
  error.value = '';
}

function openHealthSummary(task: NurseTaskDetailRecord) {
  if (!preServiceSummaryStatuses.includes(task.taskStatus)) {
    error.value = '健康摘要仅供服务开始前核对。';
    return;
  }
  selectedTaskId.value = task.taskId;
  summaryTaskId.value = task.taskId;
  error.value = '';
  notice.value = '';
  isEditingRecord.value = false;
  activeTab.value = 'health-summary';
}

function openArticles(task: NurseTaskDetailRecord) {
  selectedTaskId.value = task.taskId;
  articleTaskId.value = task.taskId;
  activeTab.value = 'articles';
  error.value = '';
}

function closeHealthSummary() {
  summaryTaskId.value = '';
  activeTab.value = 'tasks';
}

async function generateReportForTask(task: NurseTaskDetailRecord) {
  selectedTaskId.value = task.taskId;
  await submitReport();
}

function taskForRecord(record: CareExecutionRecord) {
  return tasks.value.find((task) => task.orderId === record.orderId);
}

function nextTaskAction(task: NurseTaskDetailRecord) {
  if (task.taskStatus === 'DISPATCHED') return { label: '接单', targetStatus: 'ACCEPTED' as NurseTaskStatus };
  if (task.taskStatus === 'ACCEPTED') return { label: '开始前往', targetStatus: 'ON_THE_WAY' as NurseTaskStatus };
  if (task.taskStatus === 'ON_THE_WAY') return { label: '服务前核对并开始', targetStatus: 'SERVING' as NurseTaskStatus };
  return { label: '结束服务', targetStatus: 'COMPLETED' as NurseTaskStatus };
}

async function transitionTask(task: NurseTaskDetailRecord, targetStatus: NurseTaskStatus) {
  loading.value = true;
  error.value = '';
  const response = targetStatus === 'ACCEPTED'
    ? await acceptNurseTask(task.taskId, { nurseId: task.nurseId, dispatchRemark: '护理员已接单', targetStatus })
    : await updateNurseTaskStatus(task.taskId, { nurseId: task.nurseId, dispatchRemark: '', targetStatus });
  loading.value = false;
  if (response.code !== 0) {
    if (targetStatus === 'SERVING' && (response.code === 409 || response.code === 422)) {
      error.value = '服务前核对状态已经变化，请根据最新注意事项重新确认。';
      attentionRefreshKey.value += 1;
      await loadTasks();
    } else {
      error.value = response.message || '任务状态暂时无法更新，请稍后重试。';
    }
    return false;
  }
  notice.value = `任务已更新为${label(response.data.orderStatus)}`;
  await loadTasks();
  return true;
}

async function handlePrimaryTaskAction(task: NurseTaskDetailRecord) {
  selectedTaskId.value = task.taskId;
  const action = nextTaskAction(task);
  if (action.targetStatus === 'SERVING') {
    const reading = await getRecommendedTrainingArticles(task.orderId);
    if (reading.code !== 0) {
      error.value = reading.message || '服务前学习资料暂时无法核对。';
      return;
    }
    if (reading.data.some((item) => item.requiredRead && item.readStatus === 'UNREAD')) {
      error.value = '请先完成本次服务的必读学习资料。';
      openArticles(task);
      return;
    }
    openHealthSummary(task);
    return;
  }
  await transitionTask(task, action.targetStatus);
}

async function startServiceAfterAttention() {
  const task = summaryTask.value;
  if (!task || task.taskStatus !== 'ON_THE_WAY') {
    error.value = '当前任务状态已变化，请返回任务列表刷新后重试。';
    attentionRefreshKey.value += 1;
    return;
  }
  const succeeded = await transitionTask(task, 'SERVING');
  if (succeeded) closeHealthSummary();
}

async function refreshAfterAttentionConflict() {
  attentionRefreshKey.value += 1;
  await loadTasks();
}

async function submitRecord() {
  if (!selectedTask.value) return;
  if (!recordForm.value.content.trim() || !recordForm.value.nursingAdvice.trim()) {
    error.value = '请填写本次服务记录和护理建议。';
    return;
  }
  if (!recordForm.value.endTime || new Date(recordForm.value.endTime).getTime() <= new Date(recordForm.value.startTime).getTime()) {
    error.value = '结束时间须晚于开始时间';
    return;
  }
  loading.value = true;
  error.value = '';
  const response = await createServiceRecord(selectedTask.value.orderId, recordForm.value);
  loading.value = false;
  if (response.code !== 0) {
    error.value = `${response.code} ${response.message}`;
    return;
  }
  recentRecordId.value = response.data.recordId;
  suggestionOrderId.value = selectedTask.value.orderId;
  notice.value = '服务记录已保存。可根据本次记录提出健康档案更新建议。';
  activeTab.value = 'records';
  isEditingRecord.value = false;
  await loadTasks();
}

async function submitReport() {
  if (!selectedTask.value) return;
  loading.value = true;
  error.value = '';
  const response = await generateServiceReport(selectedTask.value.orderId);
  loading.value = false;
  if (response.code !== 0) {
    error.value = `${response.code} ${response.message}`;
    return;
  }
  suggestionOrderId.value = selectedTask.value.orderId;
  notice.value = '服务报告已生成，等待长辈或家属确认。可根据报告提出健康档案更新建议。';
  await loadTasks();
}

async function signOut() {
  await logout();
  uni.redirectTo({ url: '/pages/login/index' });
}

function changeAbnormal(event: Event) {
  recordForm.value.abnormalFlag = (event as unknown as { detail: { value: boolean } }).detail.value;
}

onMounted(loadTasks);
</script>

<template>
  <view class="nurse-app">
    <view class="nurse-header">
      <view>
        <text class="header-kicker">护理工作台</text>
        <text class="header-title">{{ user?.displayName || '护理工作台' }}</text>
        <text class="header-date">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }) }}</text>
      </view>
      <button v-if="activeTab !== 'health-summary' && activeTab !== 'qualification'" class="refresh-button" type="button" :disabled="loading" @click="loadTasks">刷新</button>
    </view>

    <view v-if="activeTab !== 'health-summary' && activeTab !== 'qualification'" class="summary-strip">
      <view class="summary-item"><text>{{ activeServiceTasks.length }}</text><text>服务中</text></view>
      <view class="summary-item"><text>{{ pendingRecordTasks.length }}</text><text>待填写</text></view>
      <view class="summary-item"><text>{{ completedCount }}</text><text>已完成</text></view>
    </view>

    <view v-if="notice" class="notice success">{{ notice }}</view>
    <view v-if="error" class="notice error">{{ error }}</view>

    <view v-if="activeTab !== 'health-summary'" class="tabbar">
      <button :class="{ active: activeTab === 'tasks' }" type="button" @click="activeTab = 'tasks'">我的任务</button>
      <button :class="{ active: activeTab === 'records' }" type="button" @click="openRecords">服务记录</button>
      <button :class="{ active: activeTab === 'suggestions' }" type="button" @click="openSuggestions()">档案建议</button>
      <button :class="{ active: activeTab === 'qualification' }" type="button" @click="activeTab = 'qualification'">准入资格</button>
      <button :class="{ active: activeTab === 'articles' }" type="button" @click="selectedTask && openArticles(selectedTask)">学习资料</button>
    </view>

    <view v-if="activeTab === 'tasks'" class="task-board">
      <view v-for="group in activeTaskGroups" :key="group.status" class="task-section">
        <view class="task-section-heading"><text>{{ group.label }}</text><text>{{ group.tasks.length }} 项</text></view>
        <view v-if="group.tasks.length === 0" class="record-empty">暂无{{ group.label }}的任务</view>
        <view v-for="task in group.tasks" :key="task.taskId" class="task-card">
          <view class="task-card-top"><text class="task-service">{{ task.serviceName || '上门护理服务' }}</text><text class="status-chip" :class="`status-${task.taskStatus.toLowerCase()}`">{{ label(task.taskStatus) }}</text></view>
          <text class="task-person">服务对象 {{ task.elderName || '长辈信息待同步' }}</text><text class="task-time">{{ taskTime(task.scheduledStart) }}</text>
          <view class="task-card-actions">
            <button v-if="preServiceSummaryStatuses.includes(task.taskStatus)" class="secondary-action" type="button" @click="openArticles(task)">学习资料</button>
            <button v-if="preServiceSummaryStatuses.includes(task.taskStatus) && task.taskStatus !== 'ON_THE_WAY'" class="secondary-action health-summary-entry" type="button" :disabled="loading" @click="openHealthSummary(task)">查看健康摘要</button>
            <button class="primary-action" type="button" :disabled="loading" @click="handlePrimaryTaskAction(task)">{{ nextTaskAction(task).label }}</button>
          </view>
        </view>
      </view>
      <view class="task-section">
        <view class="task-section-heading"><text>待填写记录</text><text>{{ pendingRecordTasks.length }} 项</text></view>
        <view v-if="pendingRecordTasks.length === 0" class="record-empty">暂无待填写记录的任务</view>
        <view v-for="task in pendingRecordTasks" :key="task.taskId" class="task-card">
          <view class="task-card-top"><text class="task-service">{{ task.serviceName || '上门护理服务' }}</text><text class="status-chip status-wait_report">待填写记录</text></view>
          <text class="task-person">服务对象 {{ task.elderName || '长辈信息待同步' }}</text><text class="task-time">{{ taskTime(task.scheduledStart) }}</text>
          <button class="primary-action record-entry-action" type="button" :disabled="loading" @click="openRecordEditor(task)">填写服务记录</button>
        </view>
      </view>
      <view class="task-section">
        <view class="task-section-heading"><text>等待确认</text><text>{{ waitingConfirmTasks.length }} 项</text></view>
        <view v-if="waitingConfirmTasks.length === 0" class="record-empty">暂无等待长辈或家属确认的报告</view>
        <view v-for="task in waitingConfirmTasks" :key="task.taskId" class="task-card"><view class="task-card-top"><text class="task-service">{{ task.serviceName || '上门护理服务' }}</text><text class="status-chip">等待确认</text></view><text class="task-time">{{ taskTime(task.scheduledStart) }}</text><button class="secondary-action suggestion-entry" type="button" @click="openSuggestions(task)">建议更新健康档案</button></view>
      </view>
      <view class="task-section">
        <view class="task-section-heading"><text>已完成</text><text>{{ completedTasks.length }} 项</text></view>
        <view v-if="completedTasks.length === 0" class="record-empty">暂无已完成任务</view>
        <view v-for="task in completedTasks" :key="task.taskId" class="task-card">
          <view class="task-card-top"><text class="task-service">{{ task.serviceName || '上门护理服务' }}</text><text class="status-chip">已完成</text></view>
          <text class="task-person">服务对象 {{ task.elderName || '长辈信息待同步' }}</text><text class="task-time">{{ taskTime(task.scheduledStart) }}</text>
          <view class="task-card-actions">
            <button class="secondary-action" type="button" @click="openRecordsForTask(task)">查看服务记录</button>
            <button v-if="task.orderStatus === 'WAIT_REPORT'" class="primary-action" type="button" :disabled="loading" @click="generateReportForTask(task)">生成服务报告</button>
            <button class="secondary-action" type="button" @click="openSuggestions(task)">建议更新健康档案</button>
          </view>
        </view>
      </view>
      <view class="task-section">
        <view class="task-section-heading"><text>已取消</text><text>{{ canceledTasks.length }} 项</text></view>
        <view v-if="canceledTasks.length === 0" class="record-empty">暂无已取消任务</view>
        <view v-for="task in canceledTasks" :key="task.taskId" class="task-card"><view class="task-card-top"><text class="task-service">{{ task.serviceName || '上门护理服务' }}</text><text class="status-chip">已取消</text></view><text class="task-time">{{ taskTime(task.scheduledStart) }}</text></view>
      </view>
    </view>

    <StageTwentyFivePreServiceSummary
      v-else-if="activeTab === 'health-summary' && summaryTask"
      :key="summaryTask.orderId"
      :order-id="summaryTask.orderId"
      :service-name="summaryTask.serviceName || '上门护理服务'"
      :scheduled-start="summaryTask.scheduledStart"
      :elder-name="summaryTask.elderName"
      :task-status="summaryTask.taskStatus"
      :attention-refresh-key="attentionRefreshKey"
      :starting-service="loading"
      @close="closeHealthSummary"
      @start-service="startServiceAfterAttention"
      @attention-conflict="refreshAfterAttentionConflict"
    />

    <StageTwentySixQualificationPanel v-else-if="activeTab === 'qualification'" />

    <StageFiftyRecommendedArticles
      v-else-if="activeTab === 'articles' && tasks.find((item) => item.taskId === articleTaskId)"
      :order-id="tasks.find((item) => item.taskId === articleTaskId)!.orderId"
      :service-name="tasks.find((item) => item.taskId === articleTaskId)!.serviceName"
      @close="activeTab = 'tasks'"
    />

    <view v-else-if="activeTab === 'records' && isEditingRecord && recordableTask" class="record-panel">
      <view class="record-editor-heading"><view><text>填写服务记录</text><text>{{ recordableTask.serviceName || '上门护理服务' }} · {{ taskTime(recordableTask.scheduledStart) }}</text></view><button class="text-action" type="button" @click="openRecords">返回记录列表</button></view>
      <view class="record-form">
        <view class="record-task"><text>本次服务时间</text><text>{{ taskTime(recordableTask.scheduledStart) }}</text></view>
        <view class="field"><text>开始时间</text><view class="record-time-picker"><picker mode="date" :value="recordDate(recordForm.startTime)" @change="selectRecordDate('startTime', $event)"><view class="input">{{ recordDate(recordForm.startTime) }}</view></picker><picker mode="time" :value="recordTime(recordForm.startTime)" @change="selectRecordTime('startTime', $event)"><view class="input">{{ recordTime(recordForm.startTime) }}</view></picker></view></view>
        <view class="field"><text>结束时间</text><view class="record-time-picker"><picker mode="date" :value="recordDate(recordForm.endTime)" @change="selectRecordDate('endTime', $event)"><view class="input">{{ recordDate(recordForm.endTime) }}</view></picker><picker mode="time" :value="recordTime(recordForm.endTime)" @change="selectRecordTime('endTime', $event)"><view class="input">{{ recordTime(recordForm.endTime) }}</view></picker></view></view>
        <label class="field"><text>服务记录</text><textarea v-model="recordForm.content" class="textarea" placeholder="记录本次护理完成情况" /></label>
        <label class="field"><text>护理建议</text><textarea v-model="recordForm.nursingAdvice" class="textarea" placeholder="填写后续观察或护理建议" /></label>
        <view class="flag-row"><text>发现异常</text><switch :checked="recordForm.abnormalFlag" color="#0f766e" @change="changeAbnormal" /></view>
        <button class="primary-action full" type="button" :disabled="loading" @click="submitRecord">保存服务记录</button>
      </view>
    </view>

    <view v-else-if="activeTab === 'records'" class="record-panel">
      <view class="record-section">
        <view class="record-history-heading"><text>已填写</text><text>{{ recordsLoading ? '加载中' : `${serviceRecords.length} 条` }}</text></view>
        <view v-if="!recordsLoading && serviceRecords.length === 0" class="record-empty">暂无已填写的服务记录</view>
        <view v-for="record in serviceRecords" :key="record.recordId" class="saved-record">
          <view class="saved-record-heading"><text class="saved-record-title">{{ taskForRecord(record)?.serviceName || '上门护理服务' }}</text><text v-if="record.recordId === recentRecordId" class="recent-chip">刚填写</text></view>
          <text>{{ taskTime(record.startTime) }} 至 {{ taskTime(record.endTime) }}</text>
          <text>{{ record.content }}</text>
          <text v-if="record.nursingAdvice">护理建议：{{ record.nursingAdvice }}</text>
          <button v-if="taskForRecord(record)" class="text-action suggestion-record-entry" type="button" @click="openSuggestions(taskForRecord(record))">根据本次记录提出档案建议</button>
        </view>
      </view>
      <view class="record-section">
        <view class="record-history-heading"><text>未填写</text><text>{{ pendingRecordTasks.length }} 项</text></view>
        <view v-if="pendingRecordTasks.length === 0" class="record-empty">暂无待填写记录的已完成任务</view>
        <view v-for="task in pendingRecordTasks" :key="task.taskId" class="pending-record-row">
          <view><text>{{ task.serviceName || '上门护理服务' }}</text><text>{{ taskTime(task.scheduledStart) }}</text></view>
          <button class="primary-action" type="button" @click="openRecordEditor(task)">填写记录</button>
        </view>
      </view>
      <view v-if="servingTasks.length" class="record-empty">服务中的任务需先结束服务，才可填写服务记录。</view>
    </view>

    <StageTwentyThreeSuggestionPanel
      v-else-if="activeTab === 'suggestions'"
      :tasks="tasks"
      :preferred-order-id="suggestionOrderId"
      @submitted="notice = '健康档案建议已提交管理端审核，正式档案暂未修改。'"
    />

    <view class="nurse-footer"><button class="logout-button" type="button" @click="signOut">退出登录</button></view>
  </view>
</template>

<style scoped>
.nurse-app { min-height: 100vh; box-sizing: border-box; padding: 24rpx 24rpx 48rpx; background: #f4f7f6; color: #162a2a; }
.nurse-header, .task-card-top, .summary-strip, .action-row, .flag-row, .record-task { display: flex; align-items: center; justify-content: space-between; }
.nurse-header { padding: 16rpx 8rpx 28rpx; }
.header-kicker { display: block; color: #16847a; font-size: 20rpx; font-weight: 700; letter-spacing: 2rpx; }
.header-title { display: block; margin-top: 8rpx; font-size: 44rpx; font-weight: 700; }
.header-date { display: block; margin-top: 8rpx; color: #70817f; font-size: 24rpx; }
.refresh-button { flex:none; min-width:124rpx; min-height:88rpx; margin:0; padding:0 22rpx; border:1rpx solid #b9d1cc; border-radius:6rpx !important; background:#fff; color:#126f66; box-shadow:0 4rpx 12rpx rgba(26,91,81,.07); font-size:25rpx; font-weight:700; line-height:1.2; }.refresh-button:active { border-color:#75aa9f; background:#edf7f4; box-shadow:none; }
.summary-strip { margin-bottom: 24rpx; padding: 24rpx 12rpx; background: #0f766e; border-radius: 12rpx; color: #fff; }
.summary-item { flex: 1; text-align: center; border-right: 1rpx solid rgba(255,255,255,.25); }
.summary-item:last-child { border: 0; }.summary-item text:first-child { display: block; font-size: 38rpx; font-weight: 700; }.summary-item text:last-child { display: block; margin-top: 4rpx; font-size: 22rpx; opacity: .82; }
.notice { margin-bottom: 16rpx; padding: 18rpx 20rpx; border-radius: 8rpx; font-size: 25rpx; }.success { background: #def4ed; color: #0b6259; }.error { background: #fff0ef; color: #b33b32; }
.tabbar { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0; margin-bottom:24rpx; overflow:hidden; border:1rpx solid #ccdcd8; border-radius:8rpx; background:#fff; }.tabbar button { display:inline-flex; align-items:center; justify-content:center; min-height:82rpx; margin:0; padding:0 12rpx; border:0; border-right:1rpx solid #dce6e3; border-bottom:1rpx solid #dce6e3; border-radius:0; background:#fff; color:#667b76; font-size:26rpx; line-height:1.2; }.tabbar button:nth-child(2n) { border-right:0; }.tabbar button:nth-last-child(-n+2) { border-bottom:0; }.tabbar button.active { box-shadow:inset 0 -4rpx #0f766e; background:#eaf6f2; color:#0f766e; font-weight:700; }
.task-card, .record-panel, .empty-card, .action-sheet { margin-bottom: 16rpx; padding: 24rpx; background: #fff; border: 1rpx solid #e2ebea; border-radius: 10rpx; box-shadow: 0 4rpx 12rpx rgba(23, 55, 52, .05); }.task-card.selected { border-color: #0f766e; box-shadow: 0 0 0 2rpx rgba(15,118,110,.12); }
.task-service { font-size: 31rpx; font-weight: 700; }.task-person, .task-time, .task-remark, .action-meta { display: block; margin-top: 12rpx; color: #657774; font-size: 25rpx; }.task-remark { padding-top: 12rpx; border-top: 1rpx solid #edf1f0; }
.task-board { display: grid; gap: 20rpx; }.task-section { display: grid; gap: 12rpx; }.task-section-heading { display: flex; align-items: baseline; justify-content: space-between; padding: 4rpx 4rpx; color: #23443f; font-size: 30rpx; font-weight: 700; }.task-section-heading text:last-child { color: #71837f; font-size: 23rpx; font-weight: 400; }.task-section .task-card { margin: 0; }.task-card-actions { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 18rpx; }.task-card-actions .primary-action, .task-card-actions .secondary-action { flex:1; min-width:190rpx; min-height:88rpx; height:88rpx; padding:0 22rpx; line-height:1.2; }
.status-chip { padding: 7rpx 14rpx; border-radius: 999rpx; background: #e7efee; color: #47615d; font-size: 22rpx; }.status-dispatched { background: #fff2d9; color: #996300; }.status-serving, .status-on_the_way { background: #dff5ef; color: #087365; }.status-wait_report { background: #e4effd; color: #316aa3; }
.action-sheet { position: sticky; bottom: 16rpx; border-color: #b8d9d4; }.action-title { display: block; font-size: 30rpx; font-weight: 700; }.action-row { margin-top: 20rpx; }.primary-action, .secondary-action { display:inline-flex; align-items:center; justify-content:center; box-sizing:border-box; min-height:88rpx; line-height:1.2; text-align:center; }.primary-action { width: auto; margin: 0; padding: 0 34rpx; border: 0; border-radius: 8rpx; background: #0f766e; color: #fff; font-size: 28rpx; }.primary-action.full { width: 100%; height: 88rpx; }.secondary-action { width: auto; margin: 0; padding: 0 28rpx; border: 1rpx solid #b9d5d0; border-radius: 8rpx; background: #fff; color: #126f66; font-size: 26rpx; }.record-entry-action { width:100%; margin-top:18rpx; }.done-text { color: #468078; font-size: 26rpx; }
.record-task { margin-bottom: 24rpx; padding-bottom: 18rpx; border-bottom: 1rpx solid #e7eeec; font-size: 27rpx; }.record-task text:last-child { color: #6d7f7c; font-size: 23rpx; }.field { display: block; margin-bottom: 20rpx; }.field > text { display: block; margin-bottom: 10rpx; font-size: 25rpx; color: #38514d; }.input, .textarea { box-sizing: border-box; width: 100%; border: 1rpx solid #d5e0de; border-radius: 8rpx; background: #fbfdfc; padding: 16rpx; font-size: 27rpx; }.textarea { min-height: 144rpx; }.flag-row { margin: 24rpx 0; font-size: 27rpx; }.nurse-footer { padding:20rpx 0 4rpx; }.logout-button { width:100%; min-height:88rpx; margin:0; padding:0 24rpx; border:1rpx solid #c4d2ce; border-radius:6rpx !important; background:#fff; color:#365f57; box-shadow:none; font-size:25rpx; font-weight:700; line-height:1.2; }.logout-button:active { border-color:#87aaa2; background:#edf5f2; color:#24584f; }
.record-time-picker { display: grid; grid-template-columns: minmax(0, 1fr) 130rpx; gap: 12rpx; }.record-time-picker picker, .record-time-picker .input { width: 100%; }
.record-form { display: block; }
.record-section { display: grid; gap: 12rpx; margin-bottom: 24rpx; }.record-history-heading, .saved-record { display: grid; gap: 10rpx; }.record-history-heading { grid-template-columns: 1fr auto; align-items: center; margin-bottom: 4rpx; font-size: 28rpx; font-weight: 700; }.record-history-heading text:last-child { color: #70817f; font-size: 23rpx; font-weight: 400; }.saved-record { margin: 0; padding: 18rpx; border: 1rpx solid #dce8e5; border-radius: 8rpx; color: #5c706d; font-size: 24rpx; }.saved-record-heading { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }.saved-record-title { color: #1b3632; font-size: 27rpx; font-weight: 700; }.recent-chip { flex: 0 0 auto; padding: 4rpx 12rpx; border-radius: 999rpx; background: #eef5f4; color: #52716c; font-size: 21rpx; }.pending-record-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 18rpx; border: 1rpx solid #dce8e5; border-radius: 8rpx; }.pending-record-row > view { display: grid; gap: 8rpx; min-width: 0; }.pending-record-row text:first-child { color: #1b3632; font-size: 27rpx; font-weight: 700; }.pending-record-row text:last-child { color: #70817f; font-size: 23rpx; }.pending-record-row .primary-action { flex:0 0 auto; min-height:88rpx; height:88rpx; padding:0 22rpx; font-size:24rpx; line-height:1.2; }.record-empty { margin: 0; padding: 18rpx; border-radius: 8rpx; background: #f1f6f5; color: #70817f; font-size: 24rpx; }
.record-editor-heading, .record-entry-callout { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-bottom: 22rpx; }.record-editor-heading > view, .record-entry-callout > view { display: grid; gap: 8rpx; }.record-editor-heading text:first-child, .record-entry-callout text:first-child { color: #1b3632; font-size: 30rpx; font-weight: 700; }.record-editor-heading text:last-child, .record-entry-callout text:last-child { color: #70817f; font-size: 23rpx; }.record-entry-callout { padding: 18rpx; border: 1rpx solid #b8d9d4; border-radius: 8rpx; background: #f1faf7; }.text-action { width: auto; margin: 0; padding: 0; border: 0; background: transparent; color: #0f766e; font-size: 24rpx; }
.suggestion-entry { width:100%; min-height:88rpx; height:88rpx; margin-top:16rpx; padding:0 22rpx; line-height:1.2; }.suggestion-record-entry { margin-top:4rpx; padding-top:12rpx; border-top:1rpx solid #e7eeec; text-align:left; }
@media (min-width: 768px) { .nurse-app { width: 440px; margin: 0 auto; box-shadow: 0 0 0 1px #dde7e5, 0 16px 48px rgba(15, 49, 44, .1); } }
</style>
