<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Check, Clock3, HandHeart, History, RefreshCw } from '@lucide/vue';
import { actOnElderReminder, getElderReminderRecords, getElderReminders } from '@/api/stageThirtyTwo';
import type { ReminderAction, ReminderItem, ReminderRecord, ReminderStatus } from '@/types/stageThirtyTwo';

defineEmits<{ (event: 'close'): void }>();

const reminders = ref<ReminderItem[]>([]);
const records = ref<ReminderRecord[]>([]);
const view = ref<'tasks' | 'records'>('tasks');
const loading = ref(false);
const error = ref('');
const message = ref('');
const workingId = ref('');
const completingId = ref('');
const statusLabels: Record<ReminderStatus, string> = { PENDING: '待完成', DONE: '已完成', SNOOZED: '稍后提醒', MISSED: '已错过', NEED_HELP: '需要协助' };
const actionable = (item: ReminderItem) => ['PENDING', 'SNOOZED', 'MISSED', 'NEED_HELP'].includes(item.status);
const byTime = (a: ReminderItem, b: ReminderItem) => new Date(a.reminderAt).getTime() - new Date(b.reminderAt).getTime();
const activeReminders = computed(() => reminders.value.filter(actionable).sort(byTime));
const nextReminder = computed(() => activeReminders.value[0] || null);
const remainingActive = computed(() => activeReminders.value.slice(1));
const todayReminders = computed(() => remainingActive.value.filter((item) => sameDay(item.reminderAt, new Date())));
const laterReminders = computed(() => remainingActive.value.filter((item) => !sameDay(item.reminderAt, new Date())));
const completedReminders = computed(() => reminders.value.filter((item) => item.status === 'DONE').sort((a, b) => byTime(b, a)));

function sameDay(value: string, target: Date) { const date = new Date(value); return date.getFullYear() === target.getFullYear() && date.getMonth() === target.getMonth() && date.getDate() === target.getDate(); }
function formatDate(value: string | undefined, includeDate = true) { if (!value) return ''; const date = new Date(value); if (Number.isNaN(date.getTime())) return value.replace('T', ' '); return new Intl.DateTimeFormat('zh-CN', includeDate ? { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false } : { hour: '2-digit', minute: '2-digit', hour12: false }).format(date); }
function timingLabel(value: string) { const minutes = Math.round((new Date(value).getTime() - Date.now()) / 60000); if (minutes < -1) return `已过时间 ${Math.abs(minutes)} 分钟`; if (minutes <= 1) return '就是现在'; if (minutes < 60) return `${minutes} 分钟后`; const hours = Math.round(minutes / 60); if (hours < 24) return `${hours} 小时后`; return formatDate(value); }
function statusLabel(value: ReminderStatus) { return statusLabels[value] || value; }
function actionLabel(value: string) { return value === 'DONE' ? '完成提醒' : value === 'SNOOZE' ? '稍后提醒' : '请求协助'; }

async function loadTasks() { loading.value = true; error.value = ''; const response = await getElderReminders(1, 50); if (response.code === 0) reminders.value = response.data.records; else error.value = response.message || '提醒读取失败'; loading.value = false; }
async function loadRecords() { loading.value = true; error.value = ''; const response = await getElderReminderRecords(1, 50); if (response.code === 0) records.value = response.data.records; else error.value = response.message || '记录读取失败'; loading.value = false; }
async function switchView(next: 'tasks' | 'records') { view.value = next; message.value = ''; if (next === 'records') await loadRecords(); else await loadTasks(); }
async function execute(item: ReminderItem, action: ReminderAction) {
  if (workingId.value) return;
  workingId.value = item.reminderId; error.value = ''; message.value = '';
  const response = await actOnElderReminder(item.reminderId, action === 'SNOOZE' ? { action, snoozeMinutes: 30 } : { action });
  if (response.code === 0) {
    if (action === 'DONE') completingId.value = item.reminderId;
    message.value = action === 'DONE' ? '已完成，做得很好' : action === 'SNOOZE' ? '将在 30 分钟后再次提醒' : '协助请求已提交';
    await new Promise((resolve) => setTimeout(resolve, action === 'DONE' ? 180 : 0));
    await loadTasks(); completingId.value = '';
  } else error.value = response.message || '操作失败，请重试';
  workingId.value = '';
}
onMounted(loadTasks);
</script>

<template>
  <section class="reminder-panel">
    <header class="panel-head">
      <view><text class="eyebrow">今天的安排</text><text class="panel-title">把重要的事放心交给提醒</text></view>
      <button class="icon-button" type="button" aria-label="刷新提醒" @click="view === 'tasks' ? loadTasks() : loadRecords()"><RefreshCw :size="21" aria-hidden="true" /></button>
    </header>

    <view class="segment" role="tablist">
      <button type="button" :class="{ active: view === 'tasks' }" @click="switchView('tasks')"><Clock3 :size="18" aria-hidden="true" />提醒</button>
      <button type="button" :class="{ active: view === 'records' }" @click="switchView('records')"><History :size="18" aria-hidden="true" />记录</button>
    </view>

    <view v-if="loading" class="state">正在读取提醒...</view>
    <view v-else-if="error" class="state error"><text>{{ error }}</text><button type="button" @click="view === 'tasks' ? loadTasks() : loadRecords()">重新读取</button></view>
    <template v-else-if="view === 'tasks'">
      <view v-if="message" class="success-message" role="status">{{ message }}</view>
      <article v-if="nextReminder" class="next-reminder" :class="{ completing: completingId === nextReminder.reminderId }">
        <view class="next-label"><Clock3 :size="17" aria-hidden="true" /><text>下一项</text><strong>{{ timingLabel(nextReminder.reminderAt) }}</strong></view>
        <text class="next-title">{{ nextReminder.title }}</text>
        <text class="next-content">{{ nextReminder.content }}</text>
        <text class="next-time">{{ formatDate(nextReminder.reminderAt) }}</text>
        <button class="complete-action" type="button" :disabled="Boolean(workingId)" @click="execute(nextReminder, 'DONE')"><Check :size="22" aria-hidden="true" />完成</button>
        <view class="secondary-actions">
          <button type="button" :disabled="Boolean(workingId)" @click="execute(nextReminder, 'SNOOZE')"><Clock3 :size="18" aria-hidden="true" />稍后 30 分钟</button>
          <button type="button" :disabled="Boolean(workingId)" @click="execute(nextReminder, 'NEED_HELP')"><HandHeart :size="18" aria-hidden="true" />需要协助</button>
        </view>
      </article>
      <view v-else class="empty-state"><Check :size="30" aria-hidden="true" /><strong>目前没有待办提醒</strong><text>新的提醒会显示在这里</text></view>

      <section v-if="todayReminders.length" class="reminder-group"><h2>今天</h2><view v-for="item in todayReminders" :key="item.reminderId" class="reminder-row"><view class="row-time">{{ formatDate(item.reminderAt, false) }}</view><view class="row-main"><strong>{{ item.title }}</strong><text>{{ item.content }}</text></view><text class="row-status">{{ statusLabel(item.status) }}</text></view></section>
      <section v-if="laterReminders.length" class="reminder-group"><h2>稍后</h2><view v-for="item in laterReminders" :key="item.reminderId" class="reminder-row"><view class="row-time wide">{{ formatDate(item.reminderAt) }}</view><view class="row-main"><strong>{{ item.title }}</strong><text>{{ item.content }}</text></view><text class="row-status">{{ statusLabel(item.status) }}</text></view></section>
      <section v-if="completedReminders.length" class="reminder-group completed-group"><h2>已完成</h2><view v-for="item in completedReminders" :key="item.reminderId" class="reminder-row"><Check :size="18" aria-hidden="true" /><view class="row-main"><strong>{{ item.title }}</strong><text>{{ formatDate(item.completedAt || item.reminderAt) }}</text></view><text class="row-status done">已完成</text></view></section>
    </template>

    <template v-else>
      <view v-if="!records.length" class="empty-state"><History :size="30" aria-hidden="true" /><strong>还没有执行记录</strong><text>完成提醒后会保留在这里</text></view>
      <view v-for="record in records" :key="`${record.reminderId}-${record.actedAt}`" class="record-row"><view class="record-mark"><Check :size="17" aria-hidden="true" /></view><view class="row-main"><strong>{{ record.title }}</strong><text>{{ formatDate(record.actedAt) }}</text></view><view class="record-result"><strong>{{ actionLabel(record.action) }}</strong><text>{{ statusLabel(record.toStatus) }}</text></view></view>
    </template>
  </section>
</template>

<style scoped>
.reminder-panel{color:#203129}.panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:4px 2px 18px}.eyebrow,.panel-title{display:block;letter-spacing:0}.eyebrow{color:#5c7268;font-size:13px}.panel-title{max-width:280px;margin-top:5px;font-size:22px;font-weight:760;line-height:1.3}.icon-button{display:grid;place-items:center;flex:none;width:44px;height:44px;margin:0;padding:0;border:1px solid #dce5df;border-radius:7px;background:#fff;color:#356d5b}.segment{display:grid;grid-template-columns:1fr 1fr;margin-bottom:18px;border-bottom:1px solid #dce4de}.segment button{display:flex;min-height:48px;align-items:center;justify-content:center;gap:7px;margin:0;border:0;border-bottom:3px solid transparent;border-radius:0;background:transparent;color:#6c7c74;font-size:15px}.segment button.active{border-bottom-color:#2d7863;color:#245f50;font-weight:750}.next-reminder{padding:21px 20px;border:1px solid #dce5df;border-left:5px solid #c58b2d;border-radius:8px;background:#fff;transition:opacity .18s ease,transform .18s ease}.next-reminder.completing{opacity:0;transform:translateY(-6px)}.next-label{display:flex;align-items:center;gap:6px;color:#80611f;font-size:13px}.next-label strong{margin-left:auto;color:#6f5318}.next-title,.next-content,.next-time{display:block}.next-title{margin-top:18px;font-size:24px;font-weight:780;line-height:1.25}.next-content{margin-top:8px;color:#53635b;font-size:16px;line-height:1.6}.next-time{margin-top:9px;color:#68786f;font-size:14px}.complete-action{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;min-height:52px;margin:22px 0 0;border:0;border-radius:7px;background:#26715c;color:#fff;font-size:17px;font-weight:750}.secondary-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:9px}.secondary-actions button{display:flex;min-width:0;min-height:46px;align-items:center;justify-content:center;gap:5px;margin:0;padding:0 7px;border:1px solid #d7e2dc;border-radius:7px;background:#f7faf8;color:#466159;font-size:13px}.secondary-actions button:last-child{border-color:#edd1cb;background:#fff8f6;color:#93483e}.reminder-group{margin-top:27px}.reminder-group h2{margin:0 0 8px;padding:0 2px;color:#485b52;font-size:15px}.reminder-row,.record-row{display:flex;min-height:64px;align-items:center;gap:11px;padding:11px 2px;border-bottom:1px solid #e3e9e5}.row-time{flex:none;width:48px;color:#4d675c;font-size:14px;font-weight:700}.row-time.wide{width:80px;font-size:12px}.row-main{min-width:0;flex:1}.row-main strong,.row-main text{display:block}.row-main strong{font-size:15px;overflow-wrap:anywhere}.row-main text{margin-top:4px;color:#718078;font-size:12px;line-height:1.35;overflow-wrap:anywhere}.row-status{flex:none;color:#8a681e;font-size:11px}.row-status.done{color:#668076}.completed-group .reminder-row{color:#6e7b74}.record-mark{display:grid;place-items:center;width:32px;height:32px;border-radius:50%;background:#eaf3ee;color:#2c735f}.record-result{text-align:right}.record-result strong,.record-result text{display:block}.record-result strong{font-size:12px}.record-result text{margin-top:3px;color:#738078;font-size:11px}.state,.empty-state{display:flex;min-height:180px;flex-direction:column;align-items:center;justify-content:center;gap:7px;color:#718078;text-align:center}.empty-state strong{color:#354a41;font-size:17px}.empty-state text{font-size:13px}.state.error{color:#91473d}.state.error button{min-height:44px;margin-top:8px;border:1px solid #dfc3be;border-radius:7px;background:#fff;color:#91473d}.success-message{margin-bottom:12px;padding:12px 14px;border-left:4px solid #4d927b;background:#edf6f1;color:#2f6957;font-size:14px}@media(prefers-reduced-motion:reduce){.next-reminder{transition:none}}
</style>
