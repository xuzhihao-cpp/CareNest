<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import { generateServiceReport } from '@/api/stageFifteen';
import { createServiceRecord, getOrderServiceRecords } from '@/api/stageFourteen';
import { getNurseTasks } from '@/api/stageThirteen';
import { acceptNurseTask, updateNurseTaskStatus } from '@/api/stageTwelve';
import type { AuthUser } from '@/types/stageTwo';
import type { NurseTaskDetailRecord } from '@/types/stageThirteen';
import type { NurseTaskStatus } from '@/types/stageTwelve';
import type { CareExecutionRecord } from '@/types/stageFourteen';

const user = ref<AuthUser | null>(null);
const tasks = ref<NurseTaskDetailRecord[]>([]);
const serviceRecords = ref<CareExecutionRecord[]>([]);
const recordsLoading = ref(false);
const selectedTaskId = ref('');
const activeTab = ref<'tasks' | 'records'>('tasks');
const isEditingRecord = ref(false);
const loading = ref(false);
const notice = ref('');
const error = ref('');
const recordForm = ref({
  startTime: new Date().toISOString().slice(0, 16),
  endTime: '',
  content: '',
  nursingAdvice: '',
  abnormalFlag: false
});

const selectedTask = computed(() => tasks.value.find((task) => task.taskId === selectedTaskId.value) ?? tasks.value[0] ?? null);
const pendingCount = computed(() => tasks.value.filter((task) => task.taskStatus === 'DISPATCHED').length);
const activeCount = computed(() => tasks.value.filter((task) => ['ACCEPTED', 'ON_THE_WAY', 'SERVING'].includes(task.taskStatus)).length);
const completedCount = computed(() => tasks.value.filter((task) => ['WAIT_REPORT', 'WAIT_CONFIRM', 'COMPLETED'].includes(task.orderStatus)).length);
const recordableTask = computed(() => selectedTask.value?.taskStatus === 'SERVING' ? selectedTask.value : null);
const workTasks = computed(() => tasks.value.filter((task) => !['WAIT_CONFIRM', 'COMPLETED'].includes(task.orderStatus)));

type RecordTimeField = 'startTime' | 'endTime';

function padTime(value: number) {
  return String(value).padStart(2, '0');
}

function localDate(value = new Date()) {
  return `${value.getFullYear()}-${padTime(value.getMonth() + 1)}-${padTime(value.getDate())}`;
}

function recordDate(value: string) {
  return value.slice(0, 10) || localDate();
}

function recordTime(value: string) {
  return value.slice(11, 16) || '09:00';
}

function selectRecordDate(field: RecordTimeField, event: { detail: { value: string } }) {
  recordForm.value[field] = `${event.detail.value}T${recordTime(recordForm.value[field])}`;
}

function selectRecordTime(field: RecordTimeField, event: { detail: { value: string } }) {
  recordForm.value[field] = `${recordDate(recordForm.value[field])}T${event.detail.value}`;
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
  if (!selectedTaskId.value || !tasks.value.some((task) => task.taskId === selectedTaskId.value)) {
    selectedTaskId.value = tasks.value.find((task) => task.taskStatus === 'SERVING')?.taskId ?? workTasks.value[0]?.taskId ?? '';
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
  activeTab.value = 'records';
  isEditingRecord.value = false;
}

function openRecordEditor() {
  const servingTask = tasks.value.find((task) => task.taskStatus === 'SERVING');
  if (!servingTask) {
    error.value = '当前没有可填写记录的服务中任务';
    return;
  }
  selectedTaskId.value = servingTask.taskId;
  activeTab.value = 'records';
  isEditingRecord.value = true;
}

function taskForRecord(record: CareExecutionRecord) {
  return tasks.value.find((task) => task.orderId === record.orderId);
}

async function progressTask(targetStatus: NurseTaskStatus) {
  if (!selectedTask.value) return;
  loading.value = true;
  error.value = '';
  const task = selectedTask.value;
  const response = targetStatus === 'ACCEPTED'
    ? await acceptNurseTask(task.taskId, { nurseId: task.nurseId, dispatchRemark: '护理员已接单', targetStatus })
    : await updateNurseTaskStatus(task.taskId, { nurseId: task.nurseId, dispatchRemark: '', targetStatus });
  loading.value = false;
  if (response.code !== 0) {
    error.value = `${response.code} ${response.message}`;
    return;
  }
  notice.value = `任务已更新为${label(response.data.orderStatus)}`;
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
  notice.value = '服务记录已保存，已进入报告处理。';
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
  notice.value = '服务报告已生成，等待长辈或家属确认。';
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
        <text class="header-kicker">CARE WORKBENCH</text>
        <text class="header-title">{{ user?.displayName || '护理工作台' }}</text>
        <text class="header-date">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }) }}</text>
      </view>
      <button class="refresh-button" type="button" :disabled="loading" @click="loadTasks">刷新</button>
    </view>

    <view class="summary-strip">
      <view class="summary-item"><text>{{ pendingCount }}</text><text>待接单</text></view>
      <view class="summary-item"><text>{{ activeCount }}</text><text>进行中</text></view>
      <view class="summary-item"><text>{{ completedCount }}</text><text>待报告</text></view>
    </view>

    <view v-if="notice" class="notice success">{{ notice }}</view>
    <view v-if="error" class="notice error">{{ error }}</view>

    <view class="tabbar">
      <button :class="{ active: activeTab === 'tasks' }" type="button" @click="activeTab = 'tasks'">我的任务</button>
      <button :class="{ active: activeTab === 'records' }" type="button" @click="openRecords">服务记录</button>
    </view>

    <template v-if="activeTab === 'tasks'">
      <view v-if="workTasks.length === 0 && !loading" class="empty-card">
        <text class="empty-title">今日暂无任务</text>
        <text>新派发的任务会自动出现在这里。</text>
      </view>
      <view v-for="task in workTasks" :key="task.taskId" class="task-card" :class="{ selected: selectedTaskId === task.taskId }" @click="selectedTaskId = task.taskId">
        <view class="task-card-top">
          <text class="task-service">{{ task.serviceId || '上门护理服务' }}</text>
          <text class="status-chip" :class="`status-${task.taskStatus.toLowerCase()}`">{{ label(task.taskStatus) }}</text>
        </view>
        <text class="task-person">服务对象 {{ task.elderId || '长辈信息待同步' }}</text>
        <text class="task-time">{{ taskTime(task.scheduledStart) }}</text>
        <text v-if="task.dispatchRemark" class="task-remark">{{ task.dispatchRemark }}</text>
      </view>

      <view v-if="selectedTask && workTasks.some((task) => task.taskId === selectedTask.taskId)" class="action-sheet">
        <text class="action-title">{{ selectedTask.serviceId || '当前任务' }}</text>
        <text class="action-meta">订单 {{ selectedTask.orderId }}</text>
        <view class="action-row">
          <button v-if="selectedTask.taskStatus === 'DISPATCHED'" class="primary-action" type="button" :disabled="loading" @click="progressTask('ACCEPTED')">接单</button>
          <button v-if="selectedTask.taskStatus === 'ACCEPTED'" class="primary-action" type="button" :disabled="loading" @click="progressTask('ON_THE_WAY')">开始前往</button>
          <button v-if="selectedTask.taskStatus === 'ON_THE_WAY'" class="primary-action" type="button" :disabled="loading" @click="progressTask('SERVING')">开始服务</button>
          <button v-if="selectedTask.taskStatus === 'SERVING'" class="primary-action" type="button" :disabled="loading" @click="openRecordEditor">填写记录</button>
          <button v-if="selectedTask.orderStatus === 'WAIT_REPORT'" class="primary-action" type="button" :disabled="loading" @click="submitReport">生成报告</button>
          <text v-if="selectedTask.orderStatus === 'WAIT_CONFIRM' || selectedTask.orderStatus === 'COMPLETED'" class="done-text">该任务已完成处理</text>
        </view>
      </view>
    </template>

    <view v-else-if="isEditingRecord && recordableTask" class="record-panel">
      <view class="record-editor-heading"><view><text>填写服务记录</text><text>{{ recordableTask.serviceId || '上门护理服务' }} · {{ taskTime(recordableTask.scheduledStart) }}</text></view><button class="text-action" type="button" @click="openRecords">返回记录列表</button></view>
      <template>
        <view class="record-task"><text>本次服务时间</text><text>{{ taskTime(recordableTask.scheduledStart) }}</text></view>
        <view class="field"><text>开始时间</text><view class="record-time-picker"><picker mode="date" :value="recordDate(recordForm.startTime)" @change="selectRecordDate('startTime', $event)"><view class="input">{{ recordDate(recordForm.startTime) }}</view></picker><picker mode="time" :value="recordTime(recordForm.startTime)" @change="selectRecordTime('startTime', $event)"><view class="input">{{ recordTime(recordForm.startTime) }}</view></picker></view></view>
        <view class="field"><text>结束时间</text><view class="record-time-picker"><picker mode="date" :value="recordDate(recordForm.endTime)" @change="selectRecordDate('endTime', $event)"><view class="input">{{ recordDate(recordForm.endTime) }}</view></picker><picker mode="time" :value="recordTime(recordForm.endTime)" @change="selectRecordTime('endTime', $event)"><view class="input">{{ recordTime(recordForm.endTime) }}</view></picker></view></view>
        <label class="field"><text>服务记录</text><textarea v-model="recordForm.content" class="textarea" placeholder="记录本次护理完成情况" /></label>
        <label class="field"><text>护理建议</text><textarea v-model="recordForm.nursingAdvice" class="textarea" placeholder="填写后续观察或护理建议" /></label>
        <view class="flag-row"><text>发现异常</text><switch :checked="recordForm.abnormalFlag" color="#0f766e" @change="changeAbnormal" /></view>
        <button class="primary-action full" type="button" :disabled="loading" @click="submitRecord">保存服务记录</button>
      </template>
    </view>

    <view v-else class="record-panel">
      <view class="record-history-heading"><text>已保存的服务记录</text><text>{{ recordsLoading ? '加载中' : `${serviceRecords.length} 条` }}</text></view>
      <view v-if="!recordsLoading && serviceRecords.length === 0" class="record-empty">暂无服务记录</view>
      <view v-for="record in serviceRecords" :key="record.recordId" class="saved-record">
        <text class="saved-record-title">{{ taskForRecord(record)?.serviceId || '上门护理服务' }}</text>
        <text>{{ taskTime(record.startTime) }} 至 {{ taskTime(record.endTime) }}</text>
        <text>{{ record.content }}</text>
        <text v-if="record.nursingAdvice">护理建议：{{ record.nursingAdvice }}</text>
      </view>
      <view v-if="tasks.some((task) => task.taskStatus === 'SERVING')" class="record-entry-callout"><view><text>当前有服务中的任务</text><text>完成护理后可填写本次服务记录。</text></view><button class="primary-action" type="button" @click="openRecordEditor">填写记录</button></view>
      <view v-else class="record-empty">当前没有可填写记录的服务中任务。</view>
    </view>

    <view class="nurse-footer"><button type="button" @click="signOut">退出登录</button></view>
  </view>
</template>

<style scoped>
.nurse-app { min-height: 100vh; box-sizing: border-box; padding: 24rpx 24rpx 48rpx; background: #f4f7f6; color: #162a2a; }
.nurse-header, .task-card-top, .summary-strip, .action-row, .flag-row, .record-task { display: flex; align-items: center; justify-content: space-between; }
.nurse-header { padding: 16rpx 8rpx 28rpx; }
.header-kicker { display: block; color: #16847a; font-size: 20rpx; font-weight: 700; letter-spacing: 2rpx; }
.header-title { display: block; margin-top: 8rpx; font-size: 44rpx; font-weight: 700; }
.header-date { display: block; margin-top: 8rpx; color: #70817f; font-size: 24rpx; }
.refresh-button, .nurse-footer button { margin: 0; border: 0; background: transparent; color: #167d73; font-size: 26rpx; }
.summary-strip { margin-bottom: 24rpx; padding: 24rpx 12rpx; background: #0f766e; border-radius: 12rpx; color: #fff; }
.summary-item { flex: 1; text-align: center; border-right: 1rpx solid rgba(255,255,255,.25); }
.summary-item:last-child { border: 0; }.summary-item text:first-child { display: block; font-size: 38rpx; font-weight: 700; }.summary-item text:last-child { display: block; margin-top: 4rpx; font-size: 22rpx; opacity: .82; }
.notice { margin-bottom: 16rpx; padding: 18rpx 20rpx; border-radius: 8rpx; font-size: 25rpx; }.success { background: #def4ed; color: #0b6259; }.error { background: #fff0ef; color: #b33b32; }
.tabbar { display: flex; gap: 8rpx; margin-bottom: 20rpx; border-bottom: 1rpx solid #d9e2e0; }.tabbar button { flex: 1; margin: 0; border: 0; border-radius: 0; background: transparent; color: #70817f; font-size: 28rpx; }.tabbar button.active { color: #0f766e; border-bottom: 4rpx solid #0f766e; font-weight: 700; }
.task-card, .record-panel, .empty-card, .action-sheet { margin-bottom: 16rpx; padding: 24rpx; background: #fff; border: 1rpx solid #e2ebea; border-radius: 10rpx; box-shadow: 0 4rpx 12rpx rgba(23, 55, 52, .05); }.task-card.selected { border-color: #0f766e; box-shadow: 0 0 0 2rpx rgba(15,118,110,.12); }
.task-service { font-size: 31rpx; font-weight: 700; }.task-person, .task-time, .task-remark, .action-meta { display: block; margin-top: 12rpx; color: #657774; font-size: 25rpx; }.task-remark { padding-top: 12rpx; border-top: 1rpx solid #edf1f0; }
.status-chip { padding: 7rpx 14rpx; border-radius: 999rpx; background: #e7efee; color: #47615d; font-size: 22rpx; }.status-dispatched { background: #fff2d9; color: #996300; }.status-serving, .status-on_the_way { background: #dff5ef; color: #087365; }.status-wait_report { background: #e4effd; color: #316aa3; }
.action-sheet { position: sticky; bottom: 16rpx; border-color: #b8d9d4; }.action-title { display: block; font-size: 30rpx; font-weight: 700; }.action-row { margin-top: 20rpx; }.primary-action { width: auto; margin: 0; padding: 0 34rpx; border: 0; border-radius: 8rpx; background: #0f766e; color: #fff; font-size: 28rpx; }.primary-action.full { width: 100%; height: 84rpx; }.done-text { color: #468078; font-size: 26rpx; }
.record-task { margin-bottom: 24rpx; padding-bottom: 18rpx; border-bottom: 1rpx solid #e7eeec; font-size: 27rpx; }.record-task text:last-child { color: #6d7f7c; font-size: 23rpx; }.field { display: block; margin-bottom: 20rpx; }.field > text { display: block; margin-bottom: 10rpx; font-size: 25rpx; color: #38514d; }.input, .textarea { box-sizing: border-box; width: 100%; border: 1rpx solid #d5e0de; border-radius: 8rpx; background: #fbfdfc; padding: 16rpx; font-size: 27rpx; }.textarea { min-height: 144rpx; }.flag-row { margin: 24rpx 0; font-size: 27rpx; }.nurse-footer { padding: 12rpx 0 4rpx; text-align: center; }
.record-time-picker { display: grid; grid-template-columns: minmax(0, 1fr) 130rpx; gap: 12rpx; }.record-time-picker picker, .record-time-picker .input { width: 100%; }
.record-history-heading, .saved-record { display: grid; gap: 10rpx; }.record-history-heading { grid-template-columns: 1fr auto; align-items: center; margin-bottom: 18rpx; font-size: 28rpx; font-weight: 700; }.record-history-heading text:last-child { color: #70817f; font-size: 23rpx; font-weight: 400; }.saved-record { margin-bottom: 14rpx; padding: 18rpx; border: 1rpx solid #dce8e5; border-radius: 8rpx; color: #5c706d; font-size: 24rpx; }.saved-record-title { color: #1b3632; font-size: 27rpx; font-weight: 700; }.record-empty { margin-bottom: 18rpx; padding: 18rpx; border-radius: 8rpx; background: #f1f6f5; color: #70817f; font-size: 24rpx; }
.record-editor-heading, .record-entry-callout { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-bottom: 22rpx; }.record-editor-heading > view, .record-entry-callout > view { display: grid; gap: 8rpx; }.record-editor-heading text:first-child, .record-entry-callout text:first-child { color: #1b3632; font-size: 30rpx; font-weight: 700; }.record-editor-heading text:last-child, .record-entry-callout text:last-child { color: #70817f; font-size: 23rpx; }.record-entry-callout { padding: 18rpx; border: 1rpx solid #b8d9d4; border-radius: 8rpx; background: #f1faf7; }.text-action { width: auto; margin: 0; padding: 0; border: 0; background: transparent; color: #0f766e; font-size: 24rpx; }
@media (min-width: 768px) { .nurse-app { width: 440px; margin: 0 auto; box-shadow: 0 0 0 1px #dde7e5, 0 16px 48px rgba(15, 49, 44, .1); } }
</style>
