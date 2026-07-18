<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { MessageCircleMore, RefreshCw, ShieldAlert } from '@lucide/vue';
import { addCustomerServiceTicketMessage } from '@/api/stageFortyOne';
import { getAiAuditSession, listAiAuditSessions } from '@/api/stageFortyFourToFortyEight';
import type { AiAuditDetail, AiAuditSession } from '@/types/stageFortyFourToFortyEight';

const sessions = ref<AiAuditSession[]>([]);
const selected = ref<AiAuditDetail | null>(null);
const loading = ref(false);
const sending = ref(false);
const error = ref('');
const notice = ref('');
const reply = ref('');
let requestSequence = 0;
let replySequence = 0;

const selectedSessionId = computed(() => selected.value?.session.sessionId ?? '');
const selectedTicketId = computed(() => selected.value?.session.customerServiceTicketId ?? '');
const selectedNeedsReply = computed(() => Boolean(selected.value?.session.pendingHumanReply));
const safetyLabel: Record<string, string> = { NORMAL: '日常咨询', WARNING: '需要关注', CRITICAL: '紧急关注' };

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '';
}

function senderLabel(role: string) {
  if (role === 'USER') return '长辈';
  if (role === 'SYSTEM') return '人工客服';
  return '照护助手';
}

async function loadSessions(preserveSelection = true) {
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = '';
  const response = await listAiAuditSessions(false);
  if (sequence !== requestSequence) return;
  loading.value = false;
  if (response.code !== 0) {
    sessions.value = [];
    selected.value = null;
    error.value = response.code === 403 ? '当前账号没有查看客服会话的权限。' : '客服会话暂时无法读取，请稍后重试。';
    return;
  }
  sessions.value = response.data.records;
  if (!preserveSelection || !sessions.value.some((item) => item.sessionId === selectedSessionId.value)) selected.value = null;
}

async function openSession(item: AiAuditSession) {
  const sequence = ++requestSequence;
  replySequence += 1;
  error.value = '';
  notice.value = '';
  const response = await getAiAuditSession(item.sessionId);
  if (sequence !== requestSequence) return;
  if (response.code !== 0) {
    error.value = response.code === 403 ? '当前账号没有查看该会话的权限。' : '会话详情暂时无法读取，请稍后重试。';
    return;
  }
  selected.value = response.data;
  reply.value = '';
}

async function refresh() {
  const currentId = selectedSessionId.value;
  await loadSessions();
  const current = sessions.value.find((item) => item.sessionId === currentId);
  if (current) await openSession(current);
}

async function sendReply() {
  const content = reply.value.trim();
  const ticketId = selectedTicketId.value;
  const sessionId = selectedSessionId.value;
  if (!ticketId || !sessionId || sending.value) return;
  if (!content) {
    error.value = '请先填写回复内容。';
    return;
  }
  sending.value = true;
  const sequence = ++replySequence;
  error.value = '';
  const response = await addCustomerServiceTicketMessage(ticketId, content);
  if (sequence !== replySequence || sessionId !== selectedSessionId.value) {
    sending.value = false;
    return;
  }
  sending.value = false;
  if (response.code !== 0) {
    error.value = response.code === 403 ? '当前账号没有发送客服回复的权限。' : '回复发送失败，请稍后重试。';
    return;
  }
  reply.value = '';
  notice.value = '回复已发送，对方将在对话中看到这条消息。';
  await refresh();
}

onMounted(() => { void loadSessions(false); });
</script>

<template>
  <section class="service-desk">
    <header class="desk-heading">
      <view>
        <text class="eyebrow">客服工作台</text>
        <h2>对话与人工协助</h2>
        <p>查看全部照护咨询；紧急且尚未回复的会话将优先显示。</p>
      </view>
      <button type="button" class="refresh" :disabled="loading" @click="refresh"><RefreshCw :size="17" aria-hidden="true" />刷新会话</button>
    </header>

    <view v-if="notice" class="notice success" role="status">{{ notice }}</view>
    <view v-if="error" class="notice error" role="alert">{{ error }}</view>

    <view class="desk-layout">
      <aside class="session-list" aria-label="客服会话列表">
        <view class="list-heading"><strong>全部会话</strong><text>{{ sessions.length }} 条</text></view>
        <view v-if="loading" class="empty">正在读取会话…</view>
        <view v-else-if="!sessions.length" class="empty">暂无可处理的会话。</view>
        <button v-for="session in sessions" :key="session.sessionId" type="button" class="session-card" :class="{ selected: selectedSessionId === session.sessionId, urgent: session.pendingHumanReply }" @click="openSession(session)">
          <view class="session-title">
            <strong>{{ session.elderName }}</strong>
            <view class="badges"><i v-if="session.pendingHumanReply" class="urgent-dot" aria-label="等待人工回复"></i><span :class="{ critical: session.safetyLevel === 'CRITICAL' }">{{ safetyLabel[session.safetyLevel] }}</span></view>
          </view>
          <text>{{ session.sessionTitle || '照护咨询' }}</text>
          <small>{{ session.latestMessageSummary || '暂无会话内容' }}</small>
        </button>
      </aside>

      <main class="conversation-detail">
        <view v-if="!selected" class="empty empty-detail">从左侧选择会话，即可查看完整对话并回复。</view>
        <template v-else>
          <header class="detail-heading">
            <view><h3>{{ selected.session.elderName }}的照护咨询</h3><text>{{ safetyLabel[selected.session.safetyLevel] }} · {{ formatTime(selected.session.updatedAt) }}</text></view>
            <span v-if="selectedNeedsReply" class="needs-reply"><ShieldAlert :size="16" aria-hidden="true" />等待人工回复</span>
          </header>

          <view class="conversation">
            <view v-for="(message, index) in selected.messages" :key="`${message.createdAt}-${index}`" class="message-row" :class="{ user: message.senderRole === 'USER', support: message.senderRole === 'SYSTEM', risk: message.safetyFlag }">
              <view class="sender-avatar" aria-hidden="true"><MessageCircleMore :size="17" /></view>
              <view class="message-body"><strong>{{ senderLabel(message.senderRole) }}</strong><p>{{ message.content || message.contentSummary || '内容暂不可读取' }}</p><small>{{ formatTime(message.createdAt) }}</small></view>
            </view>
          </view>

          <view v-if="selectedTicketId" class="reply-box">
            <view class="reply-heading"><view><strong>回复用户</strong><small>回复会同步显示在长辈端 AI 对话中。</small></view><text>{{ reply.length }}/1000</text></view>
            <textarea v-model="reply" maxlength="1000" placeholder="输入处理进展、解决方式或后续安排" />
            <button type="button" class="send" :disabled="sending || !reply.trim()" @click="sendReply">{{ sending ? '正在发送…' : '发送回复' }}</button>
          </view>
          <view v-else class="no-ticket">该会话目前无需创建人工协助工单，客服可继续关注后续消息。</view>
        </template>
      </main>
    </view>
  </section>
</template>

<style scoped>
.service-desk{display:grid;gap:18px;color:#1f3731}.desk-heading,.detail-heading,.list-heading,.reply-heading{display:flex;align-items:center;justify-content:space-between;gap:14px}.desk-heading>view{display:grid;gap:5px}.eyebrow{color:#16877b;font-size:13px;font-weight:750}.desk-heading h2,.detail-heading h3{margin:0;font-size:25px;letter-spacing:0}.desk-heading p,.detail-heading text,.reply-heading small{margin:0;color:#67807a;font-size:14px;line-height:1.55}.refresh{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:42px;margin:0;padding:0 15px;border:1px solid #b9d4cd;border-radius:6px;background:#fff;color:#146e63;font-weight:700}.notice{padding:12px 14px;border-radius:6px}.notice.success{background:#e7f5f0;color:#0e7164}.notice.error{background:#fff0ef;color:#a23f36}.desk-layout{display:grid;grid-template-columns:minmax(290px,.68fr) minmax(480px,1.55fr);height:min(700px,calc(100vh - 220px));min-height:580px;overflow:hidden;border:1px solid #d8e5e2;background:#fff}.session-list{min-width:0;max-height:100%;overflow-y:auto;padding:16px;border-right:1px solid #d8e5e2;box-sizing:border-box}.list-heading{position:sticky;top:-16px;z-index:2;margin:-16px -16px 12px;padding:16px 19px 12px;background:#fff;border-bottom:1px solid #edf3f1}.list-heading strong{font-size:17px}.list-heading text{color:#71827d;font-size:13px}.session-card{display:block!important;box-sizing:border-box;width:100%;margin:0 0 10px;padding:14px;border:1px solid #dce7e4;border-left:4px solid transparent;border-radius:6px;background:#fff;color:#244139;text-align:left!important}.session-title{display:grid!important;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:10px;width:100%;text-align:left}.session-title strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:18px}.session-card.selected{border-color:#36a896;background:#e8f6f2}.session-card.urgent{border-left-color:#d95046}.session-card>text,.session-card>small{display:block!important;width:100%;margin-top:7px;color:#647a74;overflow:hidden;text-align:left!important;text-overflow:ellipsis;white-space:nowrap}.session-card>small{font-size:13px}.badges{display:flex;align-items:center;justify-content:flex-end;gap:7px}.badges span,.needs-reply{display:inline-flex;align-items:center;gap:5px;padding:4px 8px;border-radius:999px;background:#edf4f2;color:#546b65;font-size:12px;white-space:nowrap}.badges span.critical,.needs-reply{background:#fff0ef;color:#af4037}.urgent-dot{width:9px;height:9px;flex:none;border-radius:50%;background:#d95046;box-shadow:0 0 0 3px #ffe9e6}.conversation-detail{display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:16px;min-width:0;min-height:0;overflow:hidden;padding:22px}.detail-heading{padding-bottom:14px;border-bottom:1px solid #e2ebe8}.detail-heading>view{display:grid;gap:4px}.conversation{display:grid;align-content:start;gap:12px;min-height:0;overflow-y:auto;padding-right:4px}.message-row{display:flex;align-items:flex-start;gap:10px}.message-row.user{flex-direction:row-reverse}.sender-avatar{display:grid;place-items:center;flex:none;width:34px;height:34px;border-radius:50%;background:#e7f2ee;color:#27725d}.message-row.user .sender-avatar{background:#e9efed;color:#61746c}.message-row.support .sender-avatar{background:#fff0e9;color:#a6503b}.message-body{max-width:min(680px,calc(100% - 46px));padding:11px 13px;border:1px solid #dde8e5;border-radius:4px 13px 13px 13px;background:#f7faf9}.message-row.user .message-body{border-radius:13px 4px 13px 13px;background:#e7f5f0}.message-row.support .message-body{border-color:#ead0c6;background:#fffaf7}.message-row.risk .message-body{border-left:4px solid #d95046}.message-body strong,.message-body small{display:block}.message-body strong{font-size:13px}.message-body p{margin:5px 0;color:#37534c;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.message-body small{color:#778782;font-size:11px}.reply-box{display:grid;gap:10px;padding-top:16px;border-top:1px solid #e2ebe8}.reply-heading>view{display:grid;gap:2px}.reply-heading text{color:#71827d;font-size:12px}.reply-box textarea{width:100%;min-height:106px;box-sizing:border-box;padding:12px;border:1px solid #cbdcd7;border-radius:6px;background:#fbfdfc;color:#263f37;line-height:1.55}.send{justify-self:end;min-height:44px;margin:0;padding:0 20px;border:1px solid #13796e;border-radius:6px;background:#13796e;color:#fff;font-weight:750}.send:disabled,.refresh:disabled{opacity:.55}.empty{padding:18px;background:#f3f7f6;color:#687d77}.empty-detail{align-self:center;text-align:center}.no-ticket{padding:12px;border-left:4px solid #a6c9bf;background:#f1f7f5;color:#58716a}@media(max-width:900px){.desk-layout{grid-template-columns:1fr;height:auto;min-height:0;overflow:visible}.session-list{max-height:420px;border-right:0;border-bottom:1px solid #d8e5e2}.conversation-detail{min-height:520px}.conversation{max-height:none}}@media(max-width:560px){.desk-heading{align-items:flex-start;flex-direction:column}.refresh{width:100%}.desk-layout{margin:0 -4px}.conversation-detail{padding:16px}.message-body{max-width:calc(100% - 44px)}}
</style>
