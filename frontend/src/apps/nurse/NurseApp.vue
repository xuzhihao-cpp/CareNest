<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import { generateServiceReport } from '@/api/stageFifteen';
import { createServiceRecord } from '@/api/stageFourteen';
import { getNurseTasks } from '@/api/stageThirteen';
import { acceptNurseTask, updateNurseTaskStatus } from '@/api/stageTwelve';
import type { AuthUser } from '@/types/stageTwo';
import type { NurseTaskDetailRecord } from '@/types/stageThirteen';
import type { NurseTaskStatus } from '@/types/stageTwelve';

const user = ref<AuthUser | null>(null);
const tasks = ref<NurseTaskDetailRecord[]>([]);
const selectedTaskId = ref('');
const activeTab = ref<'tasks' | 'records'>('tasks');
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
    selectedTaskId.value = tasks.value[0]?.taskId ?? '';
  }
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
  loading.value = true;
  error.value = '';
  const response = await createServiceRecord(selectedTask.value.orderId, recordForm.value);
  loading.value = false;
  if (response.code !== 0) {
    error.value = `${response.code} ${response.message}`;
    return;
  }
  notice.value = '服务记录已保存，已进入报告处理。';
  activeTab.value = 'tasks';
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
      <button :class="{ active: activeTab === 'records' }" type="button" @click="activeTab = 'records'">服务记录</button>
    </view>

    <template v-if="activeTab === 'tasks'">
      <view v-if="tasks.length === 0 && !loading" class="empty-card">
        <text class="empty-title">今日暂无任务</text>
        <text>新派发的任务会自动出现在这里。</text>
      </view>
      <view v-for="task in tasks" :key="task.taskId" class="task-card" :class="{ selected: selectedTaskId === task.taskId }" @click="selectedTaskId = task.taskId">
        <view class="task-card-top">
          <text class="task-service">{{ task.serviceId || '上门护理服务' }}</text>
          <text class="status-chip" :class="`status-${task.taskStatus.toLowerCase()}`">{{ label(task.taskStatus) }}</text>
        </view>
        <text class="task-person">服务对象 {{ task.elderId || '长辈信息待同步' }}</text>
        <text class="task-time">{{ taskTime(task.scheduledStart) }}</text>
        <text v-if="task.dispatchRemark" class="task-remark">{{ task.dispatchRemark }}</text>
      </view>

      <view v-if="selectedTask" class="action-sheet">
        <text class="action-title">{{ selectedTask.serviceId || '当前任务' }}</text>
        <text class="action-meta">订单 {{ selectedTask.orderId }}</text>
        <view class="action-row">
          <button v-if="selectedTask.taskStatus === 'DISPATCHED'" class="primary-action" type="button" :disabled="loading" @click="progressTask('ACCEPTED')">接单</button>
          <button v-if="selectedTask.taskStatus === 'ACCEPTED'" class="primary-action" type="button" :disabled="loading" @click="progressTask('ON_THE_WAY')">开始前往</button>
          <button v-if="selectedTask.taskStatus === 'ON_THE_WAY'" class="primary-action" type="button" :disabled="loading" @click="progressTask('SERVING')">开始服务</button>
          <button v-if="selectedTask.taskStatus === 'SERVING'" class="primary-action" type="button" :disabled="loading" @click="activeTab = 'records'">填写记录</button>
          <button v-if="selectedTask.orderStatus === 'WAIT_REPORT'" class="primary-action" type="button" :disabled="loading" @click="submitReport">生成报告</button>
          <text v-if="selectedTask.orderStatus === 'WAIT_CONFIRM' || selectedTask.orderStatus === 'COMPLETED'" class="done-text">该任务已完成处理</text>
        </view>
      </view>
    </template>

    <view v-else class="record-panel">
      <view v-if="!selectedTask" class="empty-card"><text class="empty-title">请选择一项任务</text></view>
      <template v-else>
        <view class="record-task"><text>{{ selectedTask.serviceId || '上门护理服务' }}</text><text>{{ taskTime(selectedTask.scheduledStart) }}</text></view>
        <label class="field"><text>开始时间</text><input v-model="recordForm.startTime" class="input" /></label>
        <label class="field"><text>结束时间</text><input v-model="recordForm.endTime" class="input" placeholder="例如 2026-07-20T10:00" /></label>
        <label class="field"><text>服务记录</text><textarea v-model="recordForm.content" class="textarea" placeholder="记录本次护理完成情况" /></label>
        <label class="field"><text>护理建议</text><textarea v-model="recordForm.nursingAdvice" class="textarea" placeholder="填写后续观察或护理建议" /></label>
        <view class="flag-row"><text>发现异常</text><switch :checked="recordForm.abnormalFlag" color="#0f766e" @change="changeAbnormal" /></view>
        <button class="primary-action full" type="button" :disabled="loading" @click="submitRecord">保存服务记录</button>
      </template>
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
@media (min-width: 768px) { .nurse-app { width: 440px; margin: 0 auto; box-shadow: 0 0 0 1px #dde7e5, 0 16px 48px rgba(15, 49, 44, .1); } }
</style>
