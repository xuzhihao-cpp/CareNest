<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  addCustomerServiceTicketMessage,
  getCustomerServiceTicket,
  listCustomerServiceTickets,
  updateCustomerServiceTicketStatus
} from '@/api/stageFortyOne';
import {
  addTicketFollowUp,
  getAiAuditSession,
  listAiAuditSessions,
  listTicketFollowUps
} from '@/api/stageFortyFourToFortyEight';
import type { CustomerServiceTicket, CustomerServiceTicketDetail } from '@/types/stageFortyOne';
import type { AiAuditDetail, AiAuditSession, FollowUpMethod, TicketFollowUp } from '@/types/stageFortyFourToFortyEight';

type Tab = 'TICKETS' | 'AI_AUDIT';
const tab = ref<Tab>('TICKETS');
const tickets = ref<CustomerServiceTicket[]>([]);
const selected = ref<CustomerServiceTicketDetail | null>(null);
const followUps = ref<TicketFollowUp[]>([]);
const aiSessions = ref<AiAuditSession[]>([]);
const aiDetail = ref<AiAuditDetail | null>(null);
const error = ref('');
const notice = ref('');
const reply = ref('');
const followUpContent = ref('');
const followUpResult = ref('继续跟进');
const followUpMethod = ref<FollowUpMethod>('PHONE');
const nextDate = ref('');
const nextTime = ref('09:00');
const loading = ref(false);
const requestSequence = ref(0);

const methodLabels: Record<FollowUpMethod, string> = {
  PHONE: '电话回访', ONLINE: '在线回访', HOME: '上门回访', AI: '智能回访', CUSTOMER_SERVICE: '客服回访'
};
const statusLabels: Record<string, string> = {
  PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', CLOSED: '已关闭'
};
const priorityLabels: Record<string, string> = { NORMAL: '一般', URGENT: '紧急' };
const safetyLabels: Record<string, string> = { NORMAL: '一般咨询', WARNING: '需要关注', CRITICAL: '高风险' };
const canFinish = computed(() => selected.value?.ticket.priority !== 'URGENT' || followUps.value.length > 0);

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '无后续时间';
}

async function loadTickets() {
  const sequence = ++requestSequence.value;
  loading.value = true;
  error.value = '';
  const response = await listCustomerServiceTickets({ size: 50 });
  if (sequence !== requestSequence.value) return;
  loading.value = false;
  if (response.code === 0) tickets.value = response.data.records;
  else {
    tickets.value = [];
    error.value = response.code === 403 ? '当前账号没有处理客服工单的权限。' : '客服工单暂时无法读取。';
  }
}

async function openTicket(ticket: CustomerServiceTicket) {
  const sequence = ++requestSequence.value;
  error.value = '';
  const [detailResponse, followUpResponse] = await Promise.all([
    getCustomerServiceTicket(ticket.ticketId),
    listTicketFollowUps(ticket.ticketId)
  ]);
  if (sequence !== requestSequence.value) return;
  if (detailResponse.code !== 0) {
    error.value = '工单详情暂时无法读取。';
    return;
  }
  selected.value = detailResponse.data;
  followUps.value = followUpResponse.code === 0 ? followUpResponse.data : [];
}

async function changeStatus(status: 'PROCESSING' | 'RESOLVED' | 'CLOSED') {
  if (!selected.value || loading.value) return;
  if ((status === 'RESOLVED' || status === 'CLOSED') && !canFinish.value) {
    error.value = '紧急工单必须先完成至少一次回访，才能结案。';
    return;
  }
  const ticket = selected.value.ticket;
  loading.value = true;
  const response = await updateCustomerServiceTicketStatus(ticket.ticketId, status, status === 'RESOLVED' ? '问题已处理完成' : '工单状态已更新');
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.code === 409 ? '当前状态不能执行此操作，紧急工单请先完成回访。' : '工单状态更新失败。';
    return;
  }
  notice.value = '工单状态已更新。';
  await loadTickets();
  await openTicket(response.data);
}

async function sendReply() {
  if (!selected.value || !reply.value.trim() || loading.value) return;
  const ticket = selected.value.ticket;
  loading.value = true;
  const response = await addCustomerServiceTicketMessage(ticket.ticketId, reply.value.trim());
  loading.value = false;
  if (response.code !== 0) {
    error.value = '回复发送失败，请稍后重试。';
    return;
  }
  reply.value = '';
  await openTicket(ticket);
}

async function saveFollowUp() {
  if (!selected.value || loading.value) return;
  if (followUpContent.value.trim().length < 2 || followUpResult.value.trim().length < 2) {
    error.value = '请填写回访内容和处理结果。';
    return;
  }
  const ticket = selected.value.ticket;
  loading.value = true;
  const response = await addTicketFollowUp(ticket.ticketId, {
    method: followUpMethod.value,
    content: followUpContent.value.trim(),
    nextFollowUpAt: nextDate.value ? `${nextDate.value}T${nextTime.value}:00` : null,
    result: followUpResult.value.trim()
  });
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.code === 409 ? '该工单已经关闭，不能继续添加回访。' : '回访记录保存失败。';
    return;
  }
  followUpContent.value = '';
  followUpResult.value = '继续跟进';
  nextDate.value = '';
  notice.value = '回访记录已保存。';
  await loadTickets();
  await openTicket(ticket);
}

async function loadAiAudit() {
  const sequence = ++requestSequence.value;
  loading.value = true;
  error.value = '';
  const response = await listAiAuditSessions(false);
  if (sequence !== requestSequence.value) return;
  loading.value = false;
  if (response.code === 0) aiSessions.value = response.data.records;
  else {
    aiSessions.value = [];
    error.value = response.code === 403 ? '当前账号没有查看 AI 风险记录的权限。' : 'AI 会话记录暂时无法读取。';
  }
}

async function openAiSession(item: AiAuditSession) {
  const sequence = ++requestSequence.value;
  const response = await getAiAuditSession(item.sessionId);
  if (sequence !== requestSequence.value) return;
  if (response.code === 0) aiDetail.value = response.data;
  else error.value = 'AI 会话详情暂时无法读取。';
}

function switchTab(value: Tab) {
  requestSequence.value += 1;
  tab.value = value;
  error.value = '';
  notice.value = '';
  if (value === 'TICKETS') loadTickets(); else loadAiAudit();
}

onMounted(loadTickets);
</script>

<template>
  <view class="cs-panel">
    <view class="heading"><view><text>客服与风险协助</text><small>处理人工协助、回访和 AI 高风险会话</small></view><button type="button" :disabled="loading" @click="tab === 'TICKETS' ? loadTickets() : loadAiAudit()">刷新</button></view>
    <view class="tabs"><button type="button" :class="{ active: tab === 'TICKETS' }" @click="switchTab('TICKETS')">客服工单</button><button type="button" :class="{ active: tab === 'AI_AUDIT' }" @click="switchTab('AI_AUDIT')">AI 风险审阅</button></view>
    <view v-if="notice" class="notice success">{{ notice }}</view><view v-if="error" class="notice error">{{ error }}</view>
    <view v-if="tab === 'TICKETS'" class="layout">
      <view class="list"><view v-if="!tickets.length" class="empty">暂无客服工单。</view><button v-for="ticket in tickets" :key="ticket.ticketId" type="button" :class="{ selected: selected?.ticket.ticketId === ticket.ticketId }" @click="openTicket(ticket)"><view><strong>{{ ticket.elderName }}</strong><span :class="`priority ${ticket.priority.toLowerCase()}`">{{ priorityLabels[ticket.priority] }}</span></view><text>{{ statusLabels[ticket.ticketStatus] }} · {{ ticket.category }}</text><small>{{ ticket.description }}</small></button></view>
      <view class="detail"><view v-if="!selected" class="empty">从左侧选择工单开始处理。</view><template v-else><view class="detail-title"><view><strong>{{ selected.ticket.elderName }}的协助请求</strong><small>{{ selected.ticket.requesterName || '用户发起' }}</small></view><span>{{ statusLabels[selected.ticket.ticketStatus] }}</span></view><p>{{ selected.ticket.description }}</p><view class="messages"><view v-for="message in selected.messages" :key="message.messageId" class="message"><b>{{ message.senderRole === 'CUSTOMER_SERVICE' ? '客服' : message.senderRole === 'ADMIN' ? '管理员' : '用户' }}</b><text>{{ message.content }}</text><small>{{ formatTime(message.createdAt) }}</small></view></view><view v-if="selected.ticket.ticketStatus === 'PENDING'" class="actions"><button class="primary" type="button" @click="changeStatus('PROCESSING')">开始处理</button></view><template v-if="selected.ticket.ticketStatus === 'PROCESSING'"><view class="reply"><textarea v-model="reply" maxlength="1000" placeholder="回复用户"/><button type="button" :disabled="loading || !reply.trim()" @click="sendReply">发送回复</button></view><view class="follow-form"><strong>记录回访</strong><view class="method-row"><button v-for="(label,key) in methodLabels" :key="key" type="button" :class="{ active: followUpMethod === key }" @click="followUpMethod=key">{{ label }}</button></view><textarea v-model="followUpContent" maxlength="800" placeholder="记录沟通内容、用户反馈和需要继续处理的事项"/><input v-model="followUpResult" maxlength="128" placeholder="本次回访结果"/><view class="date-row"><picker mode="date" :value="nextDate" @change="nextDate=String($event.detail.value)"><view>{{ nextDate || '下次回访日期（可选）' }}</view></picker><picker mode="time" :value="nextTime" @change="nextTime=String($event.detail.value)"><view>{{ nextTime }}</view></picker></view><button class="primary" type="button" :disabled="loading" @click="saveFollowUp">保存回访</button></view><view class="actions"><button type="button" :disabled="!canFinish" @click="changeStatus('CLOSED')">关闭工单</button><button class="primary" type="button" :disabled="!canFinish" @click="changeStatus('RESOLVED')">确认已解决</button></view><view v-if="!canFinish" class="urgent-tip">紧急工单完成回访后才能结案。</view></template><view class="follow-history"><strong>回访记录</strong><view v-if="!followUps.length" class="empty">尚未记录回访。</view><view v-for="item in followUps" :key="item.followUpId" class="follow-row"><view><b>{{ methodLabels[item.method] }}</b><small>{{ formatTime(item.createdAt) }}</small></view><text>{{ item.content }}</text><span>结果：{{ item.result }}<template v-if="item.nextFollowUpAt"> · 下次 {{ formatTime(item.nextFollowUpAt) }}</template></span></view></view></template></view>
    </view>
    <view v-else class="layout"><view class="list"><view v-if="!aiSessions.length" class="empty">暂无 AI 会话记录。</view><button v-for="item in aiSessions" :key="item.sessionId" type="button" :class="{ selected: aiDetail?.session.sessionId === item.sessionId, risk: item.riskFlag }" @click="openAiSession(item)"><view><strong>{{ item.elderName }}</strong><span>{{ safetyLabels[item.safetyLevel] }}</span></view><text>{{ item.sessionTitle || '日常照护咨询' }}</text><small>{{ item.latestMessageSummary || '暂无会话摘要' }}</small></button></view><view class="detail"><view v-if="!aiDetail" class="empty">从左侧选择会话查看安全摘要。</view><template v-else><view class="detail-title"><view><strong>{{ aiDetail.session.elderName }}的照护咨询</strong><small>{{ safetyLabels[aiDetail.session.safetyLevel] }} · {{ formatTime(aiDetail.session.updatedAt) }}</small></view></view><view class="ai-note">这里只展示会话摘要用于安全审阅；高风险问题由规则引擎转人工，不以 AI 回答替代医护判断。</view><view class="messages"><view v-for="(message,index) in aiDetail.messages" :key="index" class="message" :class="{ risk: message.safetyFlag }"><b>{{ message.senderRole === 'ASSISTANT' ? '照护助手' : '用户' }}</b><text>{{ message.contentSummary || '内容摘要不可用' }}</text><small>{{ formatTime(message.createdAt) }}</small></view></view></template></view></view>
  </view>
</template>

<style scoped>
.cs-panel{display:grid;gap:18px}.heading,.detail-title,.actions,.follow-row>view,.list button>view{display:flex;align-items:center;justify-content:space-between;gap:12px}.heading>view,.detail-title>view{display:grid;gap:4px}.heading text{font-size:24px;font-weight:700}.heading small,.detail-title small{color:#6d807c}.heading button,.actions button,.reply button,.follow-form>button{min-height:42px;margin:0;padding:0 18px;border:1px solid #bdd2cd;border-radius:6px;background:#fff;color:#126d63}.tabs{display:flex;gap:8px}.tabs button{min-height:44px;margin:0;padding:0 20px;border:1px solid #c7d8d4;border-radius:6px;background:#fff;color:#536a66}.tabs button.active{border-color:#249a8d;background:#e7f6f2;color:#0d7367;font-weight:700}.notice,.empty,.urgent-tip,.ai-note{padding:15px;border-radius:6px}.success{background:#e5f6f1;color:#0d6e62}.error{background:#fff0ef;color:#ad3c32}.layout{display:grid;grid-template-columns:minmax(300px,.8fr) minmax(420px,1.45fr);border:1px solid #dbe7e4;background:#fff}.list,.detail{min-width:0;padding:18px}.list{border-right:1px solid #dbe7e4}.list>button{display:grid;gap:7px;width:100%;margin:0 0 10px;padding:14px;border:1px solid #dce7e5;border-radius:6px;background:#fff;text-align:left;color:#1a3631}.list>button.selected{border-color:#289b8e;background:#eaf7f4}.list>button.risk{border-left:4px solid #d24f43}.list text,.list small{color:#667a76;overflow-wrap:anywhere}.priority,.list button span,.detail-title>span{padding:5px 10px;border-radius:999px;background:#edf4f2;color:#48635e;font-size:12px}.priority.urgent{background:#fde7e4;color:#a63a31}.detail{display:grid;align-content:start;gap:16px}.detail-title strong{font-size:21px}.detail p{margin:0;padding:14px;background:#f4f8f7;color:#3f5b56}.messages,.follow-history,.follow-form{display:grid;gap:10px}.message,.follow-row{display:grid;gap:6px;padding:12px;border:1px solid #e0e9e7;border-radius:6px}.message.risk{border-color:#e8aca6;background:#fff6f4}.message text,.follow-row text{color:#334e49}.message small,.follow-row small,.follow-row span{color:#71817e;font-size:12px}.actions{justify-content:flex-end}.actions .primary,.follow-form>.primary{border-color:#0f766e;background:#0f766e;color:#fff}.reply{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px}.reply textarea,.follow-form textarea,.follow-form input{box-sizing:border-box;width:100%;padding:12px;border:1px solid #ceddda;border-radius:6px;background:#fbfdfc}.reply textarea,.follow-form textarea{min-height:90px}.follow-form{padding:16px;border:1px solid #bcd9d4;background:#f2faf8}.method-row{display:flex;flex-wrap:wrap;gap:8px}.method-row button{min-height:38px;margin:0;padding:0 14px;border:1px solid #c6d7d3;border-radius:6px;background:#fff;color:#526b66}.method-row button.active{border-color:#25998c;background:#e4f5f1;color:#0d7367}.date-row{display:grid;grid-template-columns:1fr 140px;gap:10px}.date-row picker view{min-height:42px;padding:0 12px;display:flex;align-items:center;border:1px solid #ceddda;border-radius:6px;background:#fff}.urgent-tip{background:#fff5df;color:#8a5b00}.ai-note{background:#eef6f4;color:#365f57}.empty{background:#f3f7f6;color:#6b7d79}@media(max-width:900px){.layout{grid-template-columns:1fr}.list{border-right:0;border-bottom:1px solid #dbe7e4}}
</style>
