<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { getAdminOrders } from '@/api/stageEleven';
import { getPreferredNurseBindings } from '@/api/stageThirty';
import { createFollowUp, getElderFollowUps } from '@/api/stageFortyNineToFiftyFive';
import type { FollowUpRecord, FollowUpType } from '@/types/stageFortyNineToFiftyFive';

const props = defineProps<{ mode: 'ADMIN' | 'FAMILY' }>();
const elders = ref<Array<{ elderId: string; name: string; orderId?: string }>>([]);
const selectedElderId = ref('');
const records = ref<FollowUpRecord[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const notice = ref('');
const typeOptions: Array<{ value: FollowUpType; label: string }> = [
  { value: 'PHONE', label: '电话随访' }, { value: 'ONLINE', label: '线上随访' },
  { value: 'HOME', label: '上门随访' }, { value: 'AI', label: '智能随访' },
  { value: 'CUSTOMER_SERVICE', label: '客服回访' }
];
const form = ref({ typeIndex: 0, content: '', date: '', time: '09:00', needReminder: false });
let controller: AbortController | null = null;
let sequence = 0;
const selectedElder = computed(() => elders.value.find((item) => item.elderId === selectedElderId.value));

function formatTime(value?: string) {
  if (!value) return '无需再次随访';
  return value.replace('T', ' ').slice(0, 16);
}
function followUpTypeLabel(value: string) { return typeOptions.find((item) => item.value === value)?.label || '随访记录'; }

async function loadElders() {
  error.value = '';
  if (props.mode === 'ADMIN') {
    const response = await getAdminOrders({ page: 1, size: 100 });
    if (response.code !== 0) { error.value = response.message || '服务对象暂时无法读取。'; return; }
    const seen = new Set<string>();
    elders.value = response.data.records.flatMap((order) => {
      if (!order.elderId || seen.has(order.elderId)) return [];
      seen.add(order.elderId);
      return [{ elderId: order.elderId, name: order.contactName || `服务对象 ${seen.size}`, orderId: order.orderId }];
    });
  } else {
    const response = await getPreferredNurseBindings();
    if (response.code !== 0) { error.value = response.message || '绑定长辈暂时无法读取。'; return; }
    elders.value = response.data.filter((item) => item.bindingStatus === 'ACTIVE').map((item, index) => ({ elderId: item.elderId, name: `已绑定长辈 ${index + 1}` }));
  }
  if (!selectedElderId.value || !elders.value.some((item) => item.elderId === selectedElderId.value)) selectedElderId.value = elders.value[0]?.elderId || '';
  if (props.mode === 'FAMILY' && selectedElderId.value) await loadRecords();
}

async function loadRecords() {
  if (!selectedElderId.value) { records.value = []; return; }
  controller?.abort(); controller = new AbortController(); const current = ++sequence;
  loading.value = true; error.value = '';
  const response = await getElderFollowUps(selectedElderId.value, controller.signal);
  if (current !== sequence) return;
  loading.value = false;
  if (response.code === 499) return;
  if (response.code !== 0) { records.value = []; error.value = response.code === 404 ? '随访记录查询服务尚未开放，请稍后再试。' : response.message || '随访记录暂时无法读取。'; return; }
  records.value = response.data;
}

function changeElder(event: { detail: { value: string | number } }) {
  selectedElderId.value = elders.value[Number(event.detail.value)]?.elderId || '';
  if (props.mode === 'FAMILY') void loadRecords();
}

async function submit() {
  if (saving.value) return;
  if (!selectedElder.value) { error.value = '请选择需要随访的服务对象。'; return; }
  if (form.value.content.trim().length < 2) { error.value = '请填写本次随访内容。'; return; }
  if (form.value.needReminder && !form.value.date) { error.value = '设置提醒时，请选择下次随访日期。'; return; }
  saving.value = true; error.value = ''; notice.value = '';
  const nextFollowUpAt = form.value.date ? `${form.value.date}T${form.value.time}:00` : undefined;
  const response = await createFollowUp({
    elderId: selectedElder.value.elderId, orderId: selectedElder.value.orderId,
    followUpType: typeOptions[form.value.typeIndex].value, content: form.value.content.trim(),
    nextFollowUpAt, needReminder: form.value.needReminder
  });
  saving.value = false;
  if (response.code !== 0) { error.value = response.message || '随访记录暂时无法保存。'; return; }
  notice.value = response.data.createdReminderTaskId ? '随访记录已保存，并已创建后续提醒。' : '随访记录已保存。';
  form.value.content = '';
}

onMounted(loadElders);
onBeforeUnmount(() => { sequence += 1; controller?.abort(); });
</script>

<template>
  <view class="follow-up-panel">
    <view class="panel-head"><view><text class="eyebrow">持续照护</text><text class="title">{{ mode === 'ADMIN' ? '随访登记' : '随访记录' }}</text><text class="subtitle">{{ mode === 'ADMIN' ? '记录回访内容，并按需安排下一次提醒。' : '查看平台为长辈留下的随访与后续安排。' }}</text></view><button type="button" class="secondary" @click="mode === 'ADMIN' ? loadElders() : loadRecords()">刷新</button></view>
    <view v-if="error" class="message error">{{ error }}</view><view v-if="notice" class="message success">{{ notice }}</view>
    <label>选择长辈</label><picker :range="elders" range-key="name" @change="changeElder"><view class="picker-value">{{ selectedElder?.name || '暂无可选长辈' }}</view></picker>
    <template v-if="mode === 'ADMIN'">
      <label>随访方式</label><picker :range="typeOptions" range-key="label" :value="form.typeIndex" @change="form.typeIndex = Number($event.detail.value)"><view class="picker-value">{{ typeOptions[form.typeIndex].label }}</view></picker>
      <label>随访内容 *</label><textarea v-model="form.content" maxlength="1000" placeholder="填写沟通情况、健康变化和后续安排" />
      <label class="check-row"><checkbox :checked="form.needReminder" @click="form.needReminder = !form.needReminder" /><text>需要安排下一次随访提醒</text></label>
      <view v-if="form.needReminder" class="date-row"><picker mode="date" :value="form.date" @change="form.date = $event.detail.value"><view class="picker-value">{{ form.date || '选择日期' }}</view></picker><picker mode="time" :value="form.time" @change="form.time = $event.detail.value"><view class="picker-value">{{ form.time }}</view></picker></view>
      <button type="button" class="primary" :disabled="saving || !selectedElder" @click="submit">{{ saving ? '保存中...' : '保存随访记录' }}</button>
    </template>
    <template v-else>
      <view v-if="loading" class="empty">正在读取随访记录...</view>
      <article v-for="record in records" :key="record.followUpId" class="record-card"><view><strong>{{ followUpTypeLabel(record.followUpType) }}</strong><text>{{ record.content }}</text></view><text class="time">下次安排：{{ formatTime(record.nextFollowUpAt) }}</text></article>
      <view v-if="!loading && !error && !records.length" class="empty">暂无随访记录。</view>
    </template>
  </view>
</template>

<style scoped>
.follow-up-panel{padding:24px;border:1px solid #dce7e4;background:#fff;color:#17312e}.panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.eyebrow,.title,.subtitle{display:block}.eyebrow{color:#147d72;font-size:12px;font-weight:700}.title{margin-top:6px;font-size:25px;font-weight:700}.subtitle{margin-top:7px;color:#6d7f7a;font-size:14px}.follow-up-panel>label{display:block;margin:18px 0 8px;color:#3e5c55;font-size:13px;font-weight:700}.picker-value,.follow-up-panel textarea{box-sizing:border-box;width:100%;min-height:48px;padding:13px 15px;border:1px solid #ccdcd7;border-radius:5px;background:#fbfdfc;font-size:14px}.follow-up-panel textarea{height:120px}.check-row{display:flex!important;align-items:center;gap:8px}.date-row{display:grid;grid-template-columns:minmax(0,1fr) 160px;gap:12px;margin-top:12px}.primary,.secondary{display:inline-flex;align-items:center;justify-content:center;min-height:44px;margin:18px 0 0;padding:0 18px;border-radius:5px;font-size:14px}.primary{width:100%;border:1px solid #147d72;background:#147d72;color:#fff}.secondary{margin:0;border:1px solid #bfd1cc;background:#fff;color:#176d64}.message,.empty{margin-top:18px;padding:14px 16px;border-left:4px solid #b8cbc5;background:#f3f8f6;color:#657873}.message.error{border-color:#d66a5f;background:#fff2f0;color:#a33c33}.message.success{border-color:#259589;background:#edf8f5;color:#13766c}.record-card{display:grid;gap:12px;margin-top:14px;padding:18px;border:1px solid #dce7e4;border-radius:6px;background:#fbfdfc}.record-card strong,.record-card text{display:block}.record-card text{margin-top:7px;color:#526a63;line-height:1.55}.record-card .time{margin:0;color:#72827e;font-size:13px}@media(max-width:640px){.follow-up-panel{padding:20rpx}.date-row{grid-template-columns:minmax(0,1fr) 140rpx}}
</style>
