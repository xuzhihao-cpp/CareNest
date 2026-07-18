<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { Bell, PencilLine, Plus, RefreshCw, Trash2, UserRound } from '@lucide/vue';
import { createNurseReminder, deleteNurseReminder, getNurseReminders, updateNurseReminder } from '@/api/stageNurseReminders';
import type { NurseTaskDetailRecord } from '@/types/stageThirteen';
import type {
  NurseReminderItem,
  NurseReminderStatus,
  NurseReminderType
} from '@/types/stageNurseReminders';

const props = defineProps<{
  tasks: NurseTaskDetailRecord[];
  selectedTaskId: string;
}>();

const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'select-task', taskId: string): void;
}>();

const reminderTypeOptions: Array<{ value: NurseReminderType; label: string }> = [
  { value: 'MEDICATION', label: '用药提醒' },
  { value: 'MEASUREMENT', label: '测量提醒' },
  { value: 'REHAB', label: '康复提醒' },
  { value: 'REVISIT', label: '复诊提醒' },
  { value: 'FOLLOW_UP', label: '随访提醒' },
  { value: 'CUSTOM', label: '自定义提醒' }
];

const statusOptions: Array<{ value: NurseReminderStatus | ''; label: string }> = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待处理' },
  { value: 'NEED_HELP', label: '需协助' },
  { value: 'SNOOZED', label: '稍后提醒' },
  { value: 'MISSED', label: '已错过' },
  { value: 'DONE', label: '已完成' }
];

const editStatusOptions: Array<{ value: NurseReminderStatus; label: string }> = [
  { value: 'PENDING', label: '待处理' },
  { value: 'DONE', label: '已完成' },
  { value: 'SNOOZED', label: '稍后提醒' },
  { value: 'MISSED', label: '已错过' },
  { value: 'NEED_HELP', label: '需协助' }
];

const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const reminders = ref<NurseReminderItem[]>([]);
const filterStatus = ref<'' | NurseReminderStatus>('');
const selectedReminderId = ref('');
const error = ref('');
const notice = ref('');
const requestSeq = ref(0);

const form = ref({
  reminderType: 'MEDICATION' as NurseReminderType,
  title: '',
  content: '',
  scheduledAt: localDateTime(new Date(Date.now() + 60 * 60 * 1000)),
  reminderStatus: 'PENDING' as NurseReminderStatus
});

const targetTask = computed(() => props.tasks.find((task) => task.taskId === props.selectedTaskId) ?? props.tasks[0] ?? null);
const targetElderId = computed(() => targetTask.value?.elderId || '');
const targetElderName = computed(() => targetTask.value?.elderName || '');
const targetTaskLabel = computed(() => targetTask.value
  ? `${targetTask.value.serviceName || '上门护理任务'} · ${taskTime(targetTask.value.scheduledStart)}`
  : '请先选择一条护理任务');

const elderOptions = computed(() => {
  const seen = new Map<string, NurseTaskDetailRecord>();
  for (const task of props.tasks) {
    if (task.elderId && !seen.has(task.elderId)) {
      seen.set(task.elderId, task);
    }
  }
  return Array.from(seen.values());
});

const visibleReminders = computed(() => reminders.value);
const editing = computed(() => Boolean(selectedReminderId.value));

function padTime(value: number) {
  return String(value).padStart(2, '0');
}

function localDate(value = new Date()) {
  return `${value.getFullYear()}-${padTime(value.getMonth() + 1)}-${padTime(value.getDate())}`;
}

function localDateTime(value = new Date()) {
  return `${localDate(value)}T${padTime(value.getHours())}:${padTime(value.getMinutes())}`;
}

function taskTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function reminderDate(value: string) {
  return value.slice(0, 10) || localDate();
}

function reminderTime(value: string) {
  return value.slice(11, 16) || '09:00';
}

function updateReminderDate(event: { detail: { value: string } }) {
  form.value.scheduledAt = `${event.detail.value}T${reminderTime(form.value.scheduledAt)}`;
}

function updateReminderTime(event: { detail: { value: string } }) {
  form.value.scheduledAt = `${reminderDate(form.value.scheduledAt)}T${event.detail.value}`;
}

function statusLabel(value: NurseReminderStatus) {
  return editStatusOptions.find((item) => item.value === value)?.label || value;
}

function typeLabel(value: NurseReminderType) {
  return reminderTypeOptions.find((item) => item.value === value)?.label || value;
}

function rowStatusLabel(value: NurseReminderStatus) {
  return statusLabel(value);
}

function resetForm() {
  selectedReminderId.value = '';
  form.value = {
    reminderType: 'MEDICATION',
    title: '',
    content: '',
    scheduledAt: localDateTime(new Date(Date.now() + 60 * 60 * 1000)),
    reminderStatus: 'PENDING'
  };
}

function chooseTask(taskId: string) {
  emit('select-task', taskId);
}

function openCreate() {
  resetForm();
  notice.value = '';
  error.value = '';
}

async function openEdit(item: NurseReminderItem) {
  selectedReminderId.value = item.reminderId;
  form.value = {
    reminderType: item.reminderType,
    title: item.title,
    content: item.content || '',
    scheduledAt: item.scheduledAt.slice(0, 16),
    reminderStatus: item.reminderStatus
  };
  notice.value = '';
  error.value = '';
  await nextTick();
  uni.pageScrollTo({ scrollTop: 0, duration: 160 });
}

async function confirmDelete(item: NurseReminderItem) {
  await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '删除提醒',
      content: `确定删除“${item.title}”吗？`,
      confirmText: '删除',
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false)
    });
  }).then(async (confirmed) => {
    if (!confirmed || deletingId.value) {
      return;
    }
    const elderId = targetElderId.value;
    if (!elderId) {
      error.value = '请选择一条可管理的护理任务';
      return;
    }
    deletingId.value = item.reminderId;
    error.value = '';
    notice.value = '';
    const response = await deleteNurseReminder(elderId, item.reminderId);
    deletingId.value = '';
    if (response.code !== 0) {
      error.value = response.message || '删除提醒失败';
      return;
    }
    notice.value = '提醒已删除';
    if (selectedReminderId.value === item.reminderId) {
      openCreate();
    }
    await loadReminders();
  });
}

async function loadReminders() {
  const elderId = targetElderId.value;
  const seq = ++requestSeq.value;
  if (!elderId) {
    loading.value = false;
    reminders.value = [];
    error.value = '请先选择一条已分配的护理任务';
    return;
  }
  loading.value = true;
  error.value = '';
  const response = await getNurseReminders(elderId, { page: 1, size: 50, status: filterStatus.value });
  if (seq !== requestSeq.value) {
    return;
  }
  loading.value = false;
  if (response.code !== 0) {
    reminders.value = [];
    error.value = response.message || '提醒读取失败';
    return;
  }
  reminders.value = response.data.records;
  if (selectedReminderId.value && !reminders.value.some((item) => item.reminderId === selectedReminderId.value)) {
    openCreate();
  }
}

async function submitReminder() {
  const elderId = targetElderId.value;
  if (!elderId) {
    error.value = '请先选择一条已分配的护理任务';
    return;
  }
  if (!form.value.title.trim()) {
    error.value = '请填写提醒标题';
    return;
  }
  if (!form.value.scheduledAt) {
    error.value = '请填写提醒时间';
    return;
  }
  saving.value = true;
  error.value = '';
  notice.value = '';
  const payload = {
    reminderType: form.value.reminderType,
    title: form.value.title.trim(),
    content: form.value.content.trim(),
    scheduledAt: form.value.scheduledAt,
    reminderStatus: form.value.reminderStatus
  };
  const wasEditing = Boolean(selectedReminderId.value);
  const response = wasEditing
    ? await updateNurseReminder(elderId, selectedReminderId.value, payload)
    : await createNurseReminder(elderId, payload);
  saving.value = false;
  if (response.code !== 0) {
    error.value = response.message || '保存提醒失败';
    return;
  }
  selectedReminderId.value = response.data.reminderId;
  notice.value = wasEditing ? '提醒已更新' : '提醒已创建';
  await loadReminders();
}

function selectReminderType(value: NurseReminderType) {
  form.value.reminderType = value;
}

function selectReminderStatus(value: NurseReminderStatus) {
  form.value.reminderStatus = value;
}

watch(targetElderId, () => {
  openCreate();
  loadReminders();
}, { immediate: true });

watch(filterStatus, () => {
  loadReminders();
});

onMounted(() => {
  if (!selectedReminderId.value) {
    openCreate();
  }
});
</script>

<template>
  <section class="nurse-reminder-panel">
    <header class="panel-head">
      <view>
        <text class="eyebrow">护理端提醒设置</text>
        <text class="panel-title">直接修改老人的提醒</text>
        <text class="panel-subtitle">{{ targetElderName || '请先选择一条护理任务' }}</text>
      </view>
      <view class="head-actions">
        <button class="icon-button" type="button" aria-label="刷新提醒" @click="loadReminders">
          <RefreshCw :size="20" aria-hidden="true" />
        </button>
        <button class="text-action" type="button" @click="emit('close')">返回任务</button>
      </view>
    </header>

    <view v-if="elderOptions.length" class="task-strip">
      <button
        v-for="task in elderOptions"
        :key="task.elderId"
        type="button"
        :class="{ active: task.elderId === targetElderId }"
        @click="chooseTask(task.taskId)"
      >
        <UserRound :size="16" aria-hidden="true" />
        <text>{{ task.elderName || '未命名老人' }}</text>
        <text>{{ task.serviceName || '护理任务' }}</text>
      </button>
    </view>

    <view class="summary-strip">
      <view><text>当前任务</text><text>{{ targetTaskLabel }}</text></view>
      <view><text>提醒数量</text><text>{{ reminders.length }}</text></view>
    </view>

    <view class="filter-row">
      <button
        v-for="option in statusOptions"
        :key="option.value || 'all'"
        type="button"
        :class="{ active: filterStatus === option.value }"
        @click="filterStatus = option.value"
      >
        {{ option.label }}
      </button>
    </view>

    <view v-if="loading" class="state">正在读取提醒...</view>
    <view v-else-if="error" class="state error">
      <text>{{ error }}</text>
      <button type="button" @click="loadReminders">重新读取</button>
    </view>
    <template v-else>
      <view v-if="notice" class="notice success">{{ notice }}</view>
      <view v-if="!targetElderId" class="state empty">
        <Bell :size="28" aria-hidden="true" />
        <strong>请先选择一条护理任务</strong>
        <text>选中任务后，就可以直接修改对应老人的提醒。</text>
      </view>
      <template v-else>
        <section class="form-panel">
          <view class="form-head">
            <view>
              <text>{{ editing ? '编辑提醒' : '新建提醒' }}</text>
              <text>{{ targetElderName || '当前老人' }} · {{ editing ? '已选择一条提醒' : '可直接新建一条提醒' }}</text>
            </view>
            <button type="button" class="text-action" @click="openCreate">清空表单</button>
          </view>

          <view class="field">
            <text>提醒类型</text>
            <view class="choice-grid">
              <button
                v-for="option in reminderTypeOptions"
                :key="option.value"
                type="button"
                :class="{ active: form.reminderType === option.value }"
                @click="selectReminderType(option.value)"
              >
                {{ option.label }}
              </button>
            </view>
          </view>

          <label class="field">
            <text>提醒标题</text>
            <input v-model="form.title" class="input" type="text" placeholder="例如：早间用药提醒" />
          </label>

          <label class="field">
            <text>提醒内容</text>
            <textarea v-model="form.content" class="textarea" placeholder="填写提醒的具体说明" />
          </label>

          <view class="field">
            <text>提醒时间</text>
            <view class="time-grid">
              <picker mode="date" :value="reminderDate(form.scheduledAt)" @change="updateReminderDate">
                <view class="input">{{ reminderDate(form.scheduledAt) }}</view>
              </picker>
              <picker mode="time" :value="reminderTime(form.scheduledAt)" @change="updateReminderTime">
                <view class="input">{{ reminderTime(form.scheduledAt) }}</view>
              </picker>
            </view>
          </view>

          <view class="field">
            <text>提醒状态</text>
            <view class="choice-grid status-grid">
              <button
                v-for="option in editStatusOptions"
                :key="option.value"
                type="button"
                :class="{ active: form.reminderStatus === option.value }"
                @click="selectReminderStatus(option.value)"
              >
                {{ option.label }}
              </button>
            </view>
          </view>

          <button class="primary-action" type="button" :disabled="saving" @click="submitReminder">
            <Plus v-if="!editing" :size="18" aria-hidden="true" />
            <PencilLine v-else :size="18" aria-hidden="true" />
            {{ saving ? '正在保存...' : editing ? '保存修改' : '新建提醒' }}
          </button>
        </section>

        <section class="list-panel">
          <view class="section-head">
            <text>提醒列表</text>
            <text>{{ visibleReminders.length }} 条</text>
          </view>
          <view v-if="!visibleReminders.length" class="empty-state">
            <Bell :size="28" aria-hidden="true" />
            <strong>暂无提醒</strong>
            <text>可以先新建一条老人提醒。</text>
          </view>
          <view v-for="item in visibleReminders" :key="item.reminderId" class="reminder-card" :class="{ selected: selectedReminderId === item.reminderId }">
            <view class="reminder-top">
              <view class="reminder-main">
                <text class="reminder-title">{{ item.title }}</text>
                <text class="reminder-meta">{{ typeLabel(item.reminderType) }} · {{ item.elderName }}</text>
              </view>
              <text class="status-chip" :class="`status-${item.reminderStatus.toLowerCase()}`">{{ rowStatusLabel(item.reminderStatus) }}</text>
            </view>
            <text class="reminder-content">{{ item.content || '无补充说明' }}</text>
            <text class="reminder-time">{{ item.scheduledAt.replace('T', ' ').slice(0, 16) }}</text>
            <view class="card-actions">
              <button class="secondary-action" type="button" @click="openEdit(item)">
                <PencilLine :size="16" aria-hidden="true" />
                编辑
              </button>
              <button
                v-if="item.sourceType === 'NURSE_MANUAL'"
                class="danger-action"
                type="button"
                :disabled="deletingId === item.reminderId"
                @click="confirmDelete(item)"
              >
                <Trash2 :size="16" aria-hidden="true" />
                {{ deletingId === item.reminderId ? '删除中...' : '删除' }}
              </button>
            </view>
          </view>
        </section>
      </template>
    </template>
  </section>
</template>

<style scoped>
.nurse-reminder-panel { display: grid; gap: 16px; color: #203129; }
.panel-head, .form-head, .reminder-top, .summary-strip, .section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.eyebrow, .panel-title, .panel-subtitle { display: block; letter-spacing: 0; }
.eyebrow { color: #5c7268; font-size: 12px; font-weight: 800; }
.panel-title { margin-top: 4px; font-size: 21px; font-weight: 780; line-height: 1.28; }
.panel-subtitle { margin-top: 5px; color: #66776f; font-size: 13px; line-height: 1.4; }
.head-actions { display: flex; align-items: center; gap: 8px; }
.icon-button { display: grid; place-items: center; width: 44px; height: 44px; margin: 0; padding: 0; border: 1px solid #dce5df; border-radius: 8px; background: #fff; color: #356d5b; }
.text-action { width: auto; min-height: 44px; margin: 0; padding: 0 8px; border: 0; background: transparent; color: #0f766e; font-size: 14px; }
.task-strip { display: grid; gap: 8px; }
.task-strip button { display: grid; grid-template-columns: auto 1fr; gap: 2px 8px; align-items: center; margin: 0; padding: 10px 12px; border: 1px solid #d9e4de; border-radius: 8px; background: #fff; color: #3e5b51; text-align: left; }
.task-strip button.active { border-color: #0f766e; background: #edf7f4; color: #0f766e; }
.task-strip text:last-child { grid-column: 2; color: #718078; font-size: 12px; }
.summary-strip { padding: 14px 16px; border-radius: 8px; background: #f1f7f4; }
.summary-strip view { display: grid; gap: 4px; }
.summary-strip text:first-child { color: #6b7c73; font-size: 12px; }
.summary-strip text:last-child { color: #21443b; font-size: 14px; font-weight: 700; }
.filter-row { display: flex; flex-wrap: wrap; gap: 8px; }
.filter-row button, .choice-grid button { margin: 0; border-radius: 8px; }
.filter-row button { min-height: 40px; padding: 0 14px; border: 1px solid #d9e4de; background: #fff; color: #5b6d66; font-size: 13px; }
.filter-row button.active, .choice-grid button.active { border-color: #0f766e; background: #eaf6f2; color: #0f766e; font-weight: 700; }
.form-panel, .list-panel { display: grid; gap: 14px; padding: 16px; border: 1px solid #dfe7e2; border-radius: 8px; background: #fff; }
.form-head text:first-child, .section-head text:first-child { font-size: 16px; font-weight: 760; }
.form-head text:last-child, .section-head text:last-child { color: #718078; font-size: 12px; }
.field { display: block; }
.field > text { display: block; margin-bottom: 8px; color: #38514d; font-size: 13px; }
.choice-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.choice-grid button { min-height: 40px; padding: 0 12px; border: 1px solid #d9e4de; background: #fbfdfc; color: #546861; font-size: 13px; }
.status-grid button { min-width: calc(50% - 4px); }
.input, .textarea { box-sizing: border-box; width: 100%; border: 1px solid #d5e0de; border-radius: 8px; background: #fbfdfc; padding: 12px; font-size: 14px; }
.textarea { min-height: 120px; }
.time-grid { display: grid; grid-template-columns: minmax(0, 1fr) 110px; gap: 10px; }
.time-grid picker, .time-grid .input { width: 100%; }
.primary-action, .secondary-action, .danger-action { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 44px; margin: 0; padding: 0 16px; border-radius: 8px; font-size: 14px; }
.primary-action { border: 0; background: #0f766e; color: #fff; font-weight: 700; }
.secondary-action { border: 1px solid #b9d5d0; background: #fff; color: #126f66; }
.danger-action { border: 1px solid #efc7c0; background: #fff8f6; color: #b2463d; }
.reminder-card { display: grid; gap: 10px; padding: 14px; border: 1px solid #dde7e2; border-radius: 8px; background: #fbfcfb; }
.reminder-card.selected { border-color: #0f766e; box-shadow: 0 0 0 2px rgba(15, 118, 110, .08); }
.reminder-main { display: grid; gap: 4px; min-width: 0; }
.reminder-title { color: #1d3730; font-size: 15px; font-weight: 760; overflow-wrap: anywhere; }
.reminder-meta, .reminder-content, .reminder-time { display: block; color: #6b7c73; font-size: 12px; line-height: 1.45; overflow-wrap: anywhere; }
.reminder-content { color: #3f554e; font-size: 13px; }
.status-chip { flex: none; padding: 5px 10px; border-radius: 999px; background: #e7efee; color: #47615d; font-size: 11px; }
.status-pending { background: #fff2d9; color: #996300; }
.status-done { background: #e3f3ea; color: #2f6f58; }
.status-snoozed { background: #e4effd; color: #316aa3; }
.status-missed { background: #f5ecec; color: #9a4b43; }
.status-need_help { background: #fde9e6; color: #ad4d3f; }
.card-actions { display: flex; gap: 8px; }
.card-actions > button { flex: 1; }
.state, .empty-state { display: flex; min-height: 160px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #718078; text-align: center; }
.state.error { padding: 12px; border: 1px solid #f0cbc7; border-radius: 8px; background: #fff8f7; color: #a3453c; }
.state.error button { min-height: 42px; margin-top: 8px; border: 1px solid #e1b7b2; border-radius: 8px; background: #fff; color: #a3453c; }
.empty-state strong { color: #354a41; font-size: 16px; }
.empty-state text { font-size: 13px; }
.notice.success { padding: 12px 14px; border-left: 4px solid #4d927b; background: #edf6f1; color: #2f6957; font-size: 13px; }
@media (max-width: 420px) {
  .panel-head, .form-head, .reminder-top, .summary-strip, .section-head { flex-direction: column; }
  .head-actions { width: 100%; justify-content: space-between; }
  .card-actions { flex-direction: column; }
  .status-grid button { min-width: calc(100% - 0px); }
  .time-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
