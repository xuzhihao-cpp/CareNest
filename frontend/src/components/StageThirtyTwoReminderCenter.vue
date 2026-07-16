<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { actOnElderReminder, getElderReminderRecords, getElderReminders } from '@/api/stageThirtyTwo';
import type { ReminderAction, ReminderItem, ReminderRecord, ReminderStatus } from '@/types/stageThirtyTwo';

const emit = defineEmits<{ (event: 'close'): void }>();

const reminders = ref<ReminderItem[]>([]);
const records = ref<ReminderRecord[]>([]);
const view = ref<'tasks' | 'records'>('tasks');
const statusFilter = ref<ReminderStatus | ''>('');
const loading = ref(false);
const error = ref('');
const message = ref('');
const workingId = ref('');
const statusLabels: Record<ReminderStatus, string> = { PENDING: '待完成', DONE: '已完成', SNOOZED: '稍后提醒', MISSED: '已错过', NEED_HELP: '需要协助' };
const statusOptions: Array<{ value: ReminderStatus | ''; label: string }> = [{ value: '', label: '全部提醒' }, ...Object.entries(statusLabels).map(([value, label]) => ({ value: value as ReminderStatus, label }))];
const pendingCount = computed(() => reminders.value.filter((item) => item.status === 'PENDING' || item.status === 'NEED_HELP').length);

function formatDate(value: string | undefined) { if (!value) return ''; const date = new Date(value); return Number.isNaN(date.getTime()) ? value.replace('T', ' ') : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date).replace('/', '-'); }
function statusLabel(value: ReminderStatus) { return statusLabels[value] ?? value; }
function actionLabel(value: string) { return value === 'DONE' ? '完成提醒' : value === 'SNOOZE' ? '稍后提醒' : '请求协助'; }
function canAct(item: ReminderItem) { return ['PENDING', 'SNOOZED', 'MISSED', 'NEED_HELP'].includes(item.status); }
async function loadTasks() { loading.value = true; error.value = ''; const response = await getElderReminders(1, 50, statusFilter.value); if (response.code === 0) reminders.value = response.data.records; else error.value = response.message; loading.value = false; }
async function loadRecords() { loading.value = true; error.value = ''; const response = await getElderReminderRecords(1, 50); if (response.code === 0) records.value = response.data.records; else error.value = response.message; loading.value = false; }
async function switchView(next: 'tasks' | 'records') { view.value = next; if (next === 'records') await loadRecords(); else await loadTasks(); }
async function execute(item: ReminderItem, action: ReminderAction) { if (workingId.value) return; workingId.value = item.reminderId; message.value = ''; const payload = action === 'SNOOZE' ? { action, snoozeMinutes: 30 } : { action }; const response = await actOnElderReminder(item.reminderId, payload); if (response.code === 0) { message.value = actionLabel(action) + '已记录'; await loadTasks(); } else error.value = response.message; workingId.value = ''; }
async function filterChanged() { await loadTasks(); }
function handleStatusChange(event: { detail: { value: string | number } }) { statusFilter.value = statusOptions[Number(event.detail.value)].value; void filterChanged(); }
onMounted(loadTasks);
</script>

<template>
  <view class="reminder-panel">
    <view class="panel-head"><view><text class="eyebrow">提醒中心</text><text class="panel-title">今天的照护提醒</text><text class="panel-meta">{{ pendingCount }} 项待处理，执行结果会保留在记录中</text></view><view class="head-actions"><button class="refresh" type="button" @click="view === 'tasks' ? loadTasks() : loadRecords()" aria-label="刷新">↻</button><button class="close" type="button" @click="emit('close')">返回</button></view></view>
    <view class="switch-row"><button :class="{ active: view === 'tasks' }" type="button" @click="switchView('tasks')">提醒任务</button><button :class="{ active: view === 'records' }" type="button" @click="switchView('records')">执行记录</button></view>
    <view v-if="view === 'tasks'" class="filter-row"><picker :range="statusOptions" range-key="label" @change="handleStatusChange"><view class="filter">{{ statusOptions.find((option) => option.value === statusFilter)?.label }} <text>⌄</text></view></picker></view>
    <view v-if="loading" class="state">正在读取真实提醒...</view><view v-else-if="error" class="state error">{{ error }} <button type="button" @click="view === 'tasks' ? loadTasks() : loadRecords()">重新读取</button></view><view v-else-if="message" class="state success">{{ message }}</view>
    <template v-else-if="view === 'tasks'"><view v-for="item in reminders" :key="item.reminderId" class="reminder-card" :class="`status-${item.status.toLowerCase()}`"><view class="card-top"><text class="reminder-title">{{ item.title }}</text><text class="status">{{ statusLabel(item.status) }}</text></view><text class="reminder-content">{{ item.content }}</text><text class="reminder-time">{{ formatDate(item.reminderAt) }}</text><view v-if="canAct(item)" class="actions"><button type="button" @click="execute(item, 'DONE')">完成</button><button type="button" @click="execute(item, 'SNOOZE')">稍后30分钟</button><button type="button" @click="execute(item, 'NEED_HELP')">需要协助</button></view></view><view v-if="!reminders.length" class="state">暂无提醒</view></template>
    <template v-else><view v-for="record in records" :key="`${record.reminderId}-${record.actedAt}`" class="record-row"><view><text class="record-title">{{ record.title }}</text><text class="record-time">{{ formatDate(record.actedAt) }}</text></view><text class="record-action">{{ actionLabel(record.action) }}</text><text class="record-status">{{ statusLabel(record.toStatus) }}</text></view><view v-if="!records.length" class="state">暂无执行记录</view></template>
  </view>
</template>

<style scoped>
.reminder-panel { background:#fff; border:1rpx solid #e1e9e4; border-radius:10rpx; padding:24rpx; color:#27352f; }.panel-head,.card-top,.record-row { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }.head-actions { display:flex; align-items:center; gap:10rpx; }.eyebrow { display:block; color:#2d7c68; font-size:20rpx; font-weight:700; letter-spacing:2rpx; }.panel-title { display:block; margin-top:8rpx; font-size:34rpx; font-weight:700; }.panel-meta,.reminder-time,.record-time { display:block; margin-top:8rpx; color:#75827b; font-size:22rpx; }.refresh,.close { min-height:64rpx; padding:0 14rpx; border:1rpx solid #c5d6cd; border-radius:5rpx; background:#f7fbf8; color:#2d7c68; font-size:22rpx; }.refresh { width:64rpx; padding:0; font-size:32rpx; }.switch-row { display:flex; margin:24rpx 0 16rpx; border-bottom:1rpx solid #e7eee9; }.switch-row button { flex:1; min-height:72rpx; border:0; border-bottom:4rpx solid transparent; border-radius:0; background:transparent; color:#75827b; font-size:25rpx; }.switch-row button.active { border-bottom-color:#2d7c68; color:#2d7c68; font-weight:700; }.filter { padding:16rpx 20rpx; border:1rpx solid #d8e3dc; border-radius:6rpx; color:#416257; font-size:23rpx; }.reminder-card { margin-top:16rpx; padding:20rpx; border-left:6rpx solid #a9c4b8; background:#f7faf8; border-radius:6rpx; }.status-need_help { border-left-color:#d26858; background:#fff7f5; }.status-pending { border-left-color:#d59c35; }.reminder-title,.record-title { font-size:27rpx; font-weight:700; }.status,.record-status { padding:6rpx 12rpx; border-radius:4rpx; background:#e6f2ec; color:#2d7c68; font-size:20rpx; }.status-pending,.status-need_help .status { background:#fff0d1; color:#976512; }.reminder-content { display:block; margin-top:14rpx; color:#46574f; font-size:24rpx; line-height:1.55; }.actions { display:flex; flex-wrap:wrap; gap:12rpx; margin-top:18rpx; }.actions button { min-height:64rpx; padding:0 16rpx; border:1rpx solid #b8cec2; border-radius:5rpx; background:#fff; color:#2d6d5a; font-size:22rpx; }.record-row { align-items:center; padding:18rpx 0; border-bottom:1rpx solid #edf1ed; }.record-row > view { min-width:0; flex:1; }.record-title { display:block; }.record-action { color:#45675a; font-size:22rpx; }.record-status { flex:none; }.state { padding:30rpx 10rpx; color:#75827b; text-align:center; font-size:23rpx; }.state.error { color:#a94a3e; }.state.success { color:#2d7c68; }.state button { margin-top:14rpx; min-height:60rpx; padding:0 18rpx; border:1rpx solid #bdcec7; border-radius:5rpx; background:#fff; color:#2d7c68; font-size:22rpx; }
</style>
