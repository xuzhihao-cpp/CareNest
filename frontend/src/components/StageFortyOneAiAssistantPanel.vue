<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { Bot, CircleAlert, History, Mic, MicOff, Plus, RotateCw, SendHorizontal, ShieldAlert, Sparkles, Trash2, UserRound, Volume2, VolumeX, X } from '@lucide/vue';
import { getElderProfile, getFamilyElders } from '@/api/stageSeven';
import { createAiSession, deleteAiSession, getAiSessionMessages, listAiSessions, sendAiMessage } from '@/api/stageFortyOne';
import type { ElderProfileResponse } from '@/types/stageSeven';
import type { AiSafetyLevel, AiSessionSummary } from '@/types/stageFortyOne';

type ChatMessage = {
  id: string;
  role: 'assistant' | 'user' | 'support';
  content: string;
  safetyLevel?: AiSafetyLevel;
  assistanceCreated?: boolean;
  customerServiceCreated?: boolean;
  healthFeedbackSubmitted?: boolean;
};

const props = defineProps<{ roleCode: 'ELDER' | 'FAMILY'; elderId?: string; embedded?: boolean }>();
const draft = ref('');
const loading = ref(false);
const error = ref('');
const historyError = ref('');
const historyRetryTarget = ref<'sessions' | 'messages' | ''>('');
const historyRetrySessionId = ref('');
const historyOpen = ref(false);
type FocusTarget = HTMLElement | { $el?: HTMLElement } | null;
const historyTrigger = ref<FocusTarget>(null);
const historyDrawer = ref<HTMLElement | null>(null);
const historyCloseButton = ref<FocusTarget>(null);
const sessionsLoading = ref(false);
const messagesLoading = ref(false);
const loadingSessionId = ref('');
const activeSessionId = ref('');
const selectedElderId = ref(props.elderId ?? '');
const familyElders = ref<ElderProfileResponse[]>([]);
const sessions = ref<AiSessionSummary[]>([]);
const conversation = ref<ChatMessage[]>([]);
const conversationRef = ref<HTMLElement | null>(null);
const listening = ref(false);
const voiceSupported = ref(false);
const voiceDraftPending = ref(false);
const voiceStatus = ref('');
const speechOutputSupported = ref(false);
const readRepliesAloud = ref(false);
const deletingSessionId = ref('');
const elderProfile = ref<ElderProfileResponse | null>(null);
const elderContactLoading = ref(false);
const elderContactError = ref('');
const starterPrompts = ['上门护理前要准备什么？', '今天怎样安排饮水？', '如何记录日常照护情况？'];
const hasUserMessage = computed(() => conversation.value.some((item) => item.role === 'user'));
const canSend = computed(() => props.roleCode !== 'FAMILY' || Boolean(selectedElderId.value));
let localSequence = 0;
let elderRequestGeneration = 0;
let sessionListRequestToken = 0;
let messageRequestToken = 0;
let sendRequestToken = 0;
let conversationRefreshTimer: ReturnType<typeof window.setInterval> | null = null;
let recognition: SpeechRecognitionLike | null = null;

type SpeechRecognitionResultLike = {
  isFinal: boolean;
  0: { transcript: string };
};
type SpeechRecognitionEventLike = {
  resultIndex: number;
  results: ArrayLike<SpeechRecognitionResultLike>;
};
type SpeechRecognitionErrorLike = { error: string };
type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onstart: (() => void) | null;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorLike) => void) | null;
  onend: (() => void) | null;
};
type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

type RequestContext = { generation: number; elderId: string; sessionId?: string };

function localId(role: string) { localSequence += 1; return `${role}-${localSequence}`; }
function createRequestContext(sessionId?: string): RequestContext {
  return { generation: elderRequestGeneration, elderId: selectedElderId.value, sessionId };
}
function isCurrentElderContext(context: RequestContext) {
  return context.generation === elderRequestGeneration && context.elderId === selectedElderId.value;
}
function activeSessionStorageKey() {
  const elderScope = props.roleCode === 'FAMILY' ? selectedElderId.value || 'unselected' : props.elderId || 'self';
  return `carenest_ai_active_session_${props.roleCode}_${elderScope}`;
}
function selectedElderStorageKey() { return 'carenest_ai_selected_elder_FAMILY'; }
function readStoredElderId() {
  const stored = uni.getStorageSync(selectedElderStorageKey());
  return typeof stored === 'string' ? stored : '';
}
function persistSelectedElderId(id: string) {
  if (id) uni.setStorageSync(selectedElderStorageKey(), id);
  else uni.removeStorageSync(selectedElderStorageKey());
}
function readStoredSessionId() {
  const stored = uni.getStorageSync(activeSessionStorageKey());
  return typeof stored === 'string' ? stored : '';
}
function persistActiveSessionId(id: string) {
  if (id) uni.setStorageSync(activeSessionStorageKey(), id);
  else uni.removeStorageSync(activeSessionStorageKey());
}
function focusTarget(target: FocusTarget) {
  const element = target instanceof HTMLElement ? target : target?.$el;
  element?.focus();
}
async function scrollConversationToBottom() {
  await nextTick();
  const element = conversationRef.value;
  if (!element) return;
  element.scrollTop = element.scrollHeight;
}
function formatActivityTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getMonth() + 1}-${date.getDate()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
function sessionTitle(session: AiSessionSummary) { return session.sessionTitle?.trim() || '新的照护咨询'; }
function sessionPreview(session: AiSessionSummary) { return session.latestMessagePreview?.trim() || '尚无消息'; }
function recognitionConstructor() {
  const browserWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  };
  return browserWindow.SpeechRecognition || browserWindow.webkitSpeechRecognition;
}
function voiceErrorMessage(code: string) {
  if (code === 'not-allowed' || code === 'service-not-allowed') return '麦克风权限未开启，请在浏览器设置中允许使用麦克风。';
  if (code === 'audio-capture') return '没有检测到可用的麦克风。';
  if (code === 'no-speech') return '没有听清楚，请靠近麦克风后再说一次。';
  return '语音识别暂时不可用，请稍后重试或使用文字输入。';
}
function prepareVoiceRecognition() {
  const Recognition = recognitionConstructor();
  voiceSupported.value = Boolean(Recognition);
  if (!Recognition) return;
  recognition = new Recognition();
  recognition.lang = 'zh-CN';
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.onstart = () => {
    listening.value = true;
    voiceStatus.value = '正在听，请说出您的问题';
    error.value = '';
  };
  recognition.onresult = (event) => {
    let transcript = '';
    let hasFinalResult = false;
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      transcript += event.results[index][0]?.transcript || '';
      hasFinalResult ||= event.results[index].isFinal;
    }
    if (!transcript.trim()) return;
    draft.value = transcript.trim();
    voiceDraftPending.value = true;
    voiceStatus.value = hasFinalResult ? '已经听清，请确认文字后发送' : '正在识别...';
  };
  recognition.onerror = (event) => {
    listening.value = false;
    voiceStatus.value = '';
    error.value = voiceErrorMessage(event.error);
  };
  recognition.onend = () => {
    listening.value = false;
    if (voiceDraftPending.value && draft.value.trim()) voiceStatus.value = '已经听清，请确认文字后发送';
    else if (!error.value) voiceStatus.value = '';
  };
}
function toggleVoiceInput() {
  if (!recognition || !voiceSupported.value || loading.value || messagesLoading.value) return;
  if (listening.value) {
    recognition.stop();
    return;
  }
  voiceDraftPending.value = false;
  voiceStatus.value = '';
  draft.value = '';
  try {
    recognition.start();
  } catch {
    listening.value = false;
    error.value = '语音识别暂时无法启动，请稍后重试或使用文字输入。';
  }
}
function startVoiceInputFromWake() {
  if (!recognition) prepareVoiceRecognition();
  if (!recognition || !voiceSupported.value || listening.value || loading.value || messagesLoading.value) return false;
  toggleVoiceInput();
  return true;
}
function cancelVoiceInput(clearDraft = false) {
  if (listening.value) recognition?.abort();
  listening.value = false;
  voiceStatus.value = '';
  voiceDraftPending.value = false;
  if (clearDraft) draft.value = '';
}
function toggleSpeechOutput() {
  if (!speechOutputSupported.value) return;
  readRepliesAloud.value = !readRepliesAloud.value;
  uni.setStorageSync('carenest_elder_ai_read_aloud', readRepliesAloud.value);
  if (!readRepliesAloud.value) window.speechSynthesis.cancel();
}
function speakAnswer(content: string) {
  if (!readRepliesAloud.value || !speechOutputSupported.value || !content.trim()) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(content);
  utterance.lang = 'zh-CN';
  utterance.rate = 0.92;
  window.speechSynthesis.speak(utterance);
}
const elderEmergencyContact = computed(() => elderProfile.value?.emergencyContacts?.find((contact) =>
  Boolean(contact.contactName?.trim() || contact.contactPhone?.trim())
) ?? null);

async function loadElderContact() {
  if (props.roleCode !== 'ELDER') return;
  elderContactLoading.value = true;
  elderContactError.value = '';
  const response = await getElderProfile(props.elderId || 'elder_001');
  elderContactLoading.value = false;
  if (response.code === 0) {
    elderProfile.value = response.data;
    return;
  }
  elderProfile.value = null;
  elderContactError.value = '暂时无法读取紧急联系人，请先到健康档案中检查联系人信息。';
}

function requestFamilyCall() {
  if (props.roleCode !== 'ELDER' || elderContactLoading.value) return;
  const contact = elderEmergencyContact.value;
  const phone = contact?.contactPhone?.trim() || '';
  if (!contact || !/^1\d{10}$/.test(phone)) {
    uni.showToast({ title: elderContactError.value || '请先完善紧急联系人电话', icon: 'none' });
    return;
  }
  uni.showModal({
    title: '确认联系家属',
    content: `${contact.contactName || '紧急联系人'}\n${phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')}`,
    confirmText: '确认拨打',
    cancelText: '暂不拨打',
    success(result) {
      if (result.confirm) {
        uni.makePhoneCall({
          phoneNumber: phone,
          fail() {
            uni.showToast({ title: '当前设备无法直接拨号，请使用手机电话功能拨打', icon: 'none' });
          }
        });
      }
    }
  });
}
function mapConversationMessage(message: { messageId: string; senderRole: 'USER' | 'ASSISTANT' | 'SYSTEM'; content: string; safetyFlag: boolean; safetyLevel: AiSafetyLevel }): ChatMessage {
  return {
    id: message.messageId,
    role: message.senderRole === 'USER' ? 'user' : message.senderRole === 'SYSTEM' ? 'support' : 'assistant',
    content: message.content,
    safetyLevel: message.senderRole === 'ASSISTANT' && message.safetyFlag ? message.safetyLevel : undefined
  };
}

async function loadSessionMessages(id: string, options: { closeHistory?: boolean } = {}) {
  const context = createRequestContext(id);
  const requestToken = ++messageRequestToken;
  loadingSessionId.value = id;
  messagesLoading.value = true;
  historyError.value = '';
  historyRetryTarget.value = '';
  historyRetrySessionId.value = '';
  const response = await getAiSessionMessages(id);
  if (!isCurrentElderContext(context) || requestToken !== messageRequestToken || loadingSessionId.value !== id) return;
  messagesLoading.value = false;
  loadingSessionId.value = '';
  if (response.code !== 0) {
    historyError.value = `无法加载对话内容：${response.message}`;
    historyRetryTarget.value = 'messages';
    historyRetrySessionId.value = id;
    return;
  }
  activeSessionId.value = id;
  persistActiveSessionId(id);
  conversation.value = response.data.map(mapConversationMessage);
  await scrollConversationToBottom();
  if (options.closeHistory !== false) void closeHistory();
}

function refreshActiveConversation() {
  const sessionId = activeSessionId.value;
  if (!sessionId || messagesLoading.value || loading.value) return;
  void loadSessionMessages(sessionId, { closeHistory: false });
}

async function selectSession(id: string) {
  if (!id || (id === activeSessionId.value && !messagesLoading.value)) {
    void closeHistory();
    return;
  }
  cancelVoiceInput(true);
  sendRequestToken += 1;
  loading.value = false;
  error.value = '';
  await loadSessionMessages(id);
}

function confirmDeleteSession(session: AiSessionSummary) {
  if (deletingSessionId.value) return;
  uni.showModal({
    title: '删除这段对话？',
    content: `删除“${sessionTitle(session)}”后，将不再出现在你的历史对话中。`,
    confirmText: '删除',
    confirmColor: '#a33f36',
    cancelText: '保留',
    success(result) {
      if (result.confirm) void removeSession(session.sessionId);
    }
  });
}

async function removeSession(sessionId: string) {
  if (!sessionId || deletingSessionId.value) return;
  const context = createRequestContext(sessionId);
  deletingSessionId.value = sessionId;
  historyError.value = '';
  const response = await deleteAiSession(sessionId);
  if (!isCurrentElderContext(context) || deletingSessionId.value !== sessionId) return;
  deletingSessionId.value = '';
  if (response.code !== 0) {
    historyError.value = response.code === 403
      ? '只能删除自己创建的对话。'
      : response.message || '对话删除失败，请稍后重试。';
    return;
  }
  if (activeSessionId.value === sessionId) {
    sendRequestToken += 1;
    messageRequestToken += 1;
    activeSessionId.value = '';
    persistActiveSessionId('');
    conversation.value = [];
    loading.value = false;
    messagesLoading.value = false;
  }
  sessions.value = sessions.value.filter((item) => item.sessionId !== sessionId);
  await loadSessions();
}

async function loadSessions() {
  const context = createRequestContext();
  const requestToken = ++sessionListRequestToken;
  sessionsLoading.value = true;
  historyError.value = '';
  historyRetryTarget.value = '';
  historyRetrySessionId.value = '';
  const response = await listAiSessions(props.roleCode === 'FAMILY' ? context.elderId || undefined : undefined);
  if (!isCurrentElderContext(context) || requestToken !== sessionListRequestToken) return;
  sessionsLoading.value = false;
  if (response.code !== 0) {
    historyError.value = `无法加载历史对话：${response.message}`;
    historyRetryTarget.value = 'sessions';
    return;
  }
  sessions.value = response.data.records;
  const activeExists = sessions.value.some((session) => session.sessionId === activeSessionId.value);
  const storedSessionId = readStoredSessionId();
  const restoredSessionId = sessions.value.some((session) => session.sessionId === storedSessionId) ? storedSessionId : '';
  const nextSessionId = activeExists ? activeSessionId.value : restoredSessionId || sessions.value[0]?.sessionId || '';
  if (nextSessionId && nextSessionId !== activeSessionId.value) {
    await loadSessionMessages(nextSessionId);
  } else if (!nextSessionId && !activeSessionId.value) {
    conversation.value = [];
  }
}

async function loadFamilyContext() {
  if (props.roleCode !== 'FAMILY') return true;
  const context = createRequestContext();
  const response = await getFamilyElders();
  if (!isCurrentElderContext(context)) return;
  if (response.code !== 0) {
    historyError.value = `无法加载长辈列表：${response.message}`;
    historyRetryTarget.value = 'sessions';
    return false;
  }
  familyElders.value = response.data;
  if (!familyElders.value.length) {
    selectedElderId.value = '';
    persistSelectedElderId('');
    historyError.value = '暂无可咨询的已绑定长辈';
    historyRetryTarget.value = 'sessions';
    return false;
  }
  const storedElderId = readStoredElderId();
  if (!familyElders.value.some((elder) => elder.elderId === selectedElderId.value)) {
    selectedElderId.value = familyElders.value.some((elder) => elder.elderId === storedElderId)
      ? storedElderId
      : familyElders.value[0].elderId;
  }
  persistSelectedElderId(selectedElderId.value);
  return true;
}

async function selectElder(elderId: string) {
  if (elderId === selectedElderId.value) return;
  cancelVoiceInput(true);
  elderRequestGeneration += 1;
  sessionListRequestToken += 1;
  messageRequestToken += 1;
  sendRequestToken += 1;
  selectedElderId.value = elderId;
  persistSelectedElderId(elderId);
  sessions.value = [];
  activeSessionId.value = '';
  conversation.value = [];
  loadingSessionId.value = '';
  sessionsLoading.value = false;
  messagesLoading.value = false;
  loading.value = false;
  draft.value = '';
  error.value = '';
  historyError.value = '';
  historyRetryTarget.value = '';
  historyRetrySessionId.value = '';
  await loadSessions();
}

async function retryHistory() {
  if (historyRetryTarget.value === 'messages' && historyRetrySessionId.value) {
    await loadSessionMessages(historyRetrySessionId.value);
    return;
  }
  if (props.roleCode === 'FAMILY' && familyElders.value.length === 0) {
    const familyReady = await loadFamilyContext();
    if (!familyReady) return;
  }
  await loadSessions();
}

function startNewConversation() {
  cancelVoiceInput(true);
  sessionListRequestToken += 1;
  messageRequestToken += 1;
  sendRequestToken += 1;
  activeSessionId.value = '';
  persistActiveSessionId('');
  conversation.value = [];
  error.value = '';
  loading.value = false;
  messagesLoading.value = false;
  loadingSessionId.value = '';
  void closeHistory();
}

async function openHistory() {
  historyOpen.value = true;
  await nextTick();
  document.addEventListener('keydown', handleHistoryKeydown);
  focusTarget(historyCloseButton.value);
}

async function closeHistory() {
  const shouldRestoreFocus = historyOpen.value;
  historyOpen.value = false;
  document.removeEventListener('keydown', handleHistoryKeydown);
  await nextTick();
  if (shouldRestoreFocus) focusTarget(historyTrigger.value);
}

function historyFocusableElements() {
  return Array.from(historyDrawer.value?.querySelectorAll<HTMLElement>(
    'button:not([disabled]), uni-button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  ) ?? []).filter((element) => !element.hasAttribute('hidden'));
}

function handleHistoryKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    void closeHistory();
    return;
  }
  if (event.key !== 'Tab') return;
  const focusable = historyFocusableElements();
  if (!focusable.length) {
    event.preventDefault();
    historyDrawer.value?.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const activeElement = document.activeElement;
  if (event.shiftKey && (activeElement === first || !historyDrawer.value?.contains(activeElement))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (activeElement === last || !historyDrawer.value?.contains(activeElement))) {
    event.preventDefault();
    first.focus();
  }
}

async function useStarter(prompt: string) { voiceDraftPending.value = false; voiceStatus.value = ''; draft.value = prompt; await send(); }
async function send() {
  const content = draft.value.trim();
  if (!content || loading.value) return;
  if (!canSend.value) {
    error.value = '请先选择一位长辈后再开始咨询。';
    return;
  }
  const context = createRequestContext();
  const requestToken = ++sendRequestToken;
  const messageType = voiceDraftPending.value ? 'VOICE' : 'TEXT';
  let targetSessionId = activeSessionId.value;
  loading.value = true;
  error.value = '';
  try {
    if (!targetSessionId) {
      const created = await createAiSession({ elderId: props.roleCode === 'FAMILY' ? selectedElderId.value : props.elderId, sessionTitle: '照护咨询', sourceType: messageType });
      if (created.code !== 0) throw new Error(created.message);
      if (!isCurrentElderContext(context) || requestToken !== sendRequestToken) return;
      targetSessionId = created.data.sessionId;
      activeSessionId.value = targetSessionId;
      persistActiveSessionId(targetSessionId);
    }
    const response = await sendAiMessage(targetSessionId, { content, messageType });
    if (response.code !== 0) throw new Error(response.message);
    if (!isCurrentElderContext(context) || requestToken !== sendRequestToken || activeSessionId.value !== targetSessionId) return;
    conversation.value.push(
      { id: response.data.userMessageId || localId('user'), role: 'user', content },
      {
        id: response.data.assistantMessageId || localId('assistant'), role: 'assistant', content: response.data.answer,
        safetyLevel: response.data.safetyLevel,
        assistanceCreated: Boolean(response.data.assistanceTicketId),
        customerServiceCreated: response.data.customerServiceTicketCreated,
        healthFeedbackSubmitted: response.data.healthFeedbackSubmitted
      }
    );
    if (props.roleCode === 'ELDER' && response.data.familyAssistanceRequested) requestFamilyCall();
    draft.value = '';
    voiceDraftPending.value = false;
    voiceStatus.value = '';
    speakAnswer(response.data.answer);
    await scrollConversationToBottom();
    await loadSessions();
  } catch (exception) {
    if (isCurrentElderContext(context) && requestToken === sendRequestToken) error.value = exception instanceof Error ? exception.message : '消息发送失败，请稍后重试。';
  } finally {
    if (isCurrentElderContext(context) && requestToken === sendRequestToken) loading.value = false;
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void send(); }
}

onMounted(async () => {
  prepareVoiceRecognition();
  speechOutputSupported.value = 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window;
  const storedReadAloud = uni.getStorageSync('carenest_elder_ai_read_aloud');
  readRepliesAloud.value = props.roleCode === 'ELDER' && storedReadAloud === true;
  void loadElderContact();
  const familyReady = await loadFamilyContext();
  if (!familyReady) return;
  await loadSessions();
  conversationRefreshTimer = window.setInterval(refreshActiveConversation, 10_000);
});

onBeforeUnmount(() => {
  if (conversationRefreshTimer) window.clearInterval(conversationRefreshTimer);
  document.removeEventListener('keydown', handleHistoryKeydown);
  cancelVoiceInput();
  if (speechOutputSupported.value) window.speechSynthesis.cancel();
});

defineExpose({ startVoiceInputFromWake, scrollConversationToBottom });
</script>

<template>
  <section class="ai-panel" :class="{ embedded: props.embedded }">
    <view class="ai-workspace" :inert="historyOpen ? '' : undefined" :aria-hidden="historyOpen ? 'true' : undefined">
      <header class="ai-header">
        <view class="assistant-mark"><Sparkles :size="22" aria-hidden="true" /></view>
        <view class="header-copy"><text class="ai-title">AI 照护助手</text><text class="availability"><i></i>在线 · 日常照护咨询</text></view>
        <view class="header-actions">
          <button
            v-if="roleCode === 'ELDER'"
            type="button"
            class="icon-button"
            :disabled="!speechOutputSupported"
            :aria-label="readRepliesAloud ? '关闭回答语音朗读' : '开启回答语音朗读'"
            :aria-pressed="readRepliesAloud"
            @click="toggleSpeechOutput"
          >
            <Volume2 v-if="readRepliesAloud" :size="20" aria-hidden="true" />
            <VolumeX v-else :size="20" aria-hidden="true" />
          </button>
          <button ref="historyTrigger" type="button" class="icon-button" tabindex="0" aria-label="查看历史对话" aria-controls="ai-history-dialog" @click="openHistory"><History :size="20" aria-hidden="true" /></button>
          <button type="button" class="icon-button new-conversation" aria-label="新建对话" @click="startNewConversation"><Plus :size="21" aria-hidden="true" /></button>
        </view>
      </header>

      <view v-if="familyElders.length > 1" class="elder-selector" aria-label="选择咨询长辈">
        <button v-for="elder in familyElders" :key="elder.elderId" type="button" :class="{ active: selectedElderId === elder.elderId }" @click="selectElder(elder.elderId)">{{ elder.name }}</button>
      </view>

      <view v-if="historyError" class="history-error" role="alert">
        <text>{{ historyError }}</text>
        <button type="button" aria-label="重试加载历史对话" @click="retryHistory"><RotateCw :size="16" aria-hidden="true" />重试</button>
      </view>

      <view ref="conversationRef" class="conversation" aria-live="polite">
        <view v-if="!conversation.length && !messagesLoading" class="conversation-empty">
          <Bot :size="26" aria-hidden="true" />
          <text>从一条照护问题开始</text>
        </view>
        <view v-for="message in conversation" :key="message.id" class="message-block" :class="message.role">
          <view class="avatar"><Bot v-if="message.role === 'assistant'" :size="18" aria-hidden="true" /><UserRound v-else :size="18" aria-hidden="true" /></view>
          <view class="message-content">
            <text v-if="message.role === 'support'" class="support-label">人工客服</text>
            <view class="bubble">{{ message.content }}</view>
            <view v-if="message.safetyLevel === 'WARNING'" class="safety-note warning"><CircleAlert :size="19" aria-hidden="true" /><view><strong>请留意照护风险</strong><text>用药和诊断问题请咨询医生。</text></view></view>
          <view v-if="message.safetyLevel === 'CRITICAL'" class="safety-note critical"><ShieldAlert :size="20" aria-hidden="true" /><view><strong>紧急协助工单已创建</strong><text>请立即联系家属、平台客服或当地急救，不要等待线上回复。</text></view></view>
            <view v-if="message.healthFeedbackSubmitted" class="feedback-note"><CircleAlert :size="18" aria-hidden="true" /><text>已记录到健康反馈，授权家属可以查看这段原话。</text></view>
          </view>
        </view>
        <view v-if="loading || messagesLoading" class="message-block assistant thinking" role="status"><view class="avatar"><Bot :size="18" aria-hidden="true" /></view><view class="bubble"><i></i><i></i><i></i><text>{{ messagesLoading ? '正在加载对话' : '正在思考' }}</text></view></view>
      </view>

      <view v-if="!hasUserMessage && !conversation.length" class="starter-prompts">
        <text class="starter-label">您可以这样问</text>
        <button v-for="prompt in starterPrompts" :key="prompt" type="button" @click="useStarter(prompt)">{{ prompt }}</button>
      </view>

      <view v-if="error" class="send-error" role="alert">{{ error }}</view>

      <view class="chat-composer">
        <button
          class="voice-input"
          type="button"
          :class="{ listening }"
          :disabled="!voiceSupported || loading || messagesLoading"
          :aria-label="listening ? '停止语音输入' : '开始语音输入'"
          :aria-pressed="listening"
          @click="toggleVoiceInput"
        >
          <MicOff v-if="listening" :size="22" aria-hidden="true" />
          <Mic v-else :size="22" aria-hidden="true" />
        </button>
        <textarea v-model="draft" :disabled="loading || messagesLoading" maxlength="500" auto-height placeholder="输入问题，或点麦克风说话" aria-label="输入照护问题" @keydown="handleKeydown" />
        <button type="button" :disabled="loading || messagesLoading || !draft.trim() || !canSend" aria-label="发送消息" @click="send"><SendHorizontal :size="21" aria-hidden="true" /></button>
        <text v-if="voiceStatus" class="voice-status" role="status">{{ voiceStatus }}</text>
        <text v-else-if="!voiceSupported" class="voice-status unavailable">当前浏览器不支持语音识别，请使用文字输入</text>
        <text class="composer-note">AI 建议不能替代医生诊断或急救服务</text>
      </view>
    </view>

    <view v-if="historyOpen" class="history-layer" @click.self="closeHistory">
      <aside id="ai-history-dialog" ref="historyDrawer" class="history-drawer" role="dialog" aria-modal="true" aria-labelledby="ai-history-title" tabindex="-1">
        <view class="history-heading"><view><text id="ai-history-title">历史对话</text><text>{{ sessions.length }} 个会话</text></view><button ref="historyCloseButton" type="button" class="icon-button" tabindex="0" aria-label="关闭历史对话" @click="closeHistory"><X :size="20" aria-hidden="true" /></button></view>
        <view v-if="sessionsLoading" class="history-state" role="status">正在加载历史对话</view>
        <view v-else-if="!sessions.length" class="history-state">暂无历史对话</view>
        <view v-else class="session-list">
          <view v-for="session in sessions" :key="session.sessionId" class="session-row" :class="{ active: session.sessionId === activeSessionId || session.sessionId === loadingSessionId }">
            <button type="button" class="session-select" :disabled="deletingSessionId === session.sessionId" @click="selectSession(session.sessionId)">
              <view><text class="session-title">{{ sessionTitle(session) }}</text><text class="session-preview">{{ sessionPreview(session) }}</text></view>
              <text class="session-time">{{ formatActivityTime(session.updatedAt) }}</text>
            </button>
            <button type="button" class="session-delete" :disabled="Boolean(deletingSessionId)" :aria-label="`删除对话：${sessionTitle(session)}`" @click="confirmDeleteSession(session)">
              <RotateCw v-if="deletingSessionId === session.sessionId" :size="18" aria-hidden="true" />
              <Trash2 v-else :size="18" aria-hidden="true" />
            </button>
          </view>
        </view>
      </aside>
    </view>
  </section>
</template>

<style scoped>
.ai-panel,.ai-workspace{display:flex;min-width:0;max-width:100%;min-height:calc(100vh - 178px);flex-direction:column;color:#203129}.ai-panel{padding-bottom:166px}.ai-workspace{flex:1;min-height:0}.ai-header{display:flex;align-items:center;gap:11px;padding:3px 2px 16px;border-bottom:1px solid #e0e7e2}.assistant-mark{display:grid;place-items:center;flex:none;width:42px;height:42px;border-radius:8px;background:#e8f3ee;color:#256a56}.header-copy{min-width:0;flex:1}.ai-title,.availability{display:block;letter-spacing:0}.ai-title{font-size:18px;font-weight:780}.availability{display:flex;align-items:center;gap:5px;margin-top:3px;color:#708077;font-size:12px}.availability i{width:7px;height:7px;border-radius:50%;background:#3f9a75}.header-actions{display:flex;gap:6px}.icon-button{display:grid;place-items:center;flex:none;width:38px;height:38px;margin:0;padding:0;border:1px solid #d4e0da;border-radius:6px;background:#fff;color:#3b6759}.icon-button[aria-pressed="true"]{border-color:#4b917a;background:#e8f3ee;color:#256a56}.new-conversation{border-color:#27715c;background:#27715c;color:#fff}.elder-selector{display:flex;gap:7px;overflow-x:auto;padding:12px 0 2px}.elder-selector button{flex:none;min-height:34px;padding:0 11px;border:1px solid #d4e0da;border-radius:5px;background:#fff;color:#536960;font-size:12px}.elder-selector button.active{border-color:#4b917a;background:#e8f3ee;color:#256a56;font-weight:700}.history-error{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:12px;padding:9px 11px;border-left:4px solid #c45b4d;background:#fff5f3;color:#8b4138;font-size:12px;line-height:1.45}.history-error button{display:flex;align-items:center;gap:4px;flex:none;min-height:30px;padding:0 7px;border:0;background:transparent;color:inherit;font-size:12px}.conversation{display:flex;flex:1;flex-direction:column;gap:18px;padding:22px 0 18px;min-height:0;overflow-y:auto;scroll-behavior:smooth}.conversation-empty{display:grid;place-items:center;align-content:center;gap:9px;min-height:150px;color:#77877f;font-size:13px;text-align:center}.message-block{display:flex;align-items:flex-start;gap:9px}.message-block.user{flex-direction:row-reverse}.avatar{display:grid;place-items:center;flex:none;width:32px;height:32px;border-radius:50%;background:#e8f2ed;color:#2b6c59}.user .avatar{background:#e9ecea;color:#56665e}.message-content{min-width:0;max-width:calc(100% - 54px)}.bubble{max-width:100%;padding:13px 15px;border-radius:3px 14px 14px 14px;background:#fff;color:#31443b;font-size:15px;line-height:1.65;white-space:pre-wrap;overflow-wrap:anywhere;box-shadow:0 1px 0 rgba(42,72,59,.08)}.user .bubble{border-radius:14px 3px 14px 14px;background:#2b735e;color:#fff;box-shadow:none}.safety-note{display:flex;gap:9px;margin-top:9px;padding:12px 13px;border-left:4px solid;border-radius:4px;background:#fff}.safety-note svg{flex:none;margin-top:1px}.safety-note strong,.safety-note text{display:block}.safety-note strong{font-size:13px}.safety-note text{margin-top:4px;font-size:12px;line-height:1.5}.safety-note.warning{border-color:#c79538;color:#73571d}.safety-note.critical{border-color:#cb5c4e;background:#fff6f4;color:#8f3e35}.thinking .bubble{display:flex;align-items:center;gap:4px;color:#68786f}.thinking .bubble i{width:5px;height:5px;border-radius:50%;background:#5d8b79;animation:pulse 1s ease-in-out infinite}.thinking .bubble i:nth-child(2){animation-delay:.14s}.thinking .bubble i:nth-child(3){animation-delay:.28s}.thinking .bubble text{margin-left:5px;font-size:12px}.starter-prompts{display:grid;gap:8px;margin:0 0 14px;padding-left:41px}.starter-label{color:#718078;font-size:12px}.starter-prompts button{min-height:44px;margin:0;padding:9px 13px;border:1px solid #d7e2dc;border-radius:7px;background:#fff;color:#365d50;text-align:left;font-size:13px;line-height:1.35}.send-error{margin:0 0 9px;padding:10px 12px;border-left:4px solid #c45b4d;background:#fff5f3;color:#8b4138;font-size:13px}.chat-composer{position:fixed;right:0;bottom:calc(70px + env(safe-area-inset-bottom));left:0;z-index:35;display:grid;grid-template-columns:46px minmax(0,1fr) 46px;gap:8px;margin:0;padding:11px 16px 8px;border-top:1px solid #dfe6e2;background:rgba(244,246,243,.98);box-sizing:border-box;box-shadow:0 -8px 20px rgba(31,52,41,.06);backdrop-filter:blur(12px)}.chat-composer textarea{width:100%;min-width:0;min-height:46px;max-height:120px;box-sizing:border-box;padding:12px 13px;border:1px solid #cfdcd5;border-radius:8px;background:#fff;color:#263a31;font-size:15px;line-height:1.45}.chat-composer button{display:grid;place-items:center;width:46px;height:46px;margin:0;padding:0;border:0;border-radius:8px;background:#27715c;color:#fff}.chat-composer button.voice-input{border:1px solid #9fc9bb;background:#e7f3ee;color:#24634f}.chat-composer button.voice-input.listening{border-color:#bd5045;background:#fff0ed;color:#a13e34;box-shadow:0 0 0 4px rgba(189,80,69,.12)}.chat-composer button:disabled{background:#aab7b1;color:#eef1ef}.voice-status{grid-column:1/-1;padding:8px 10px;border-left:4px solid #37856c;background:#eaf5f0;color:#28634f;font-size:12px}.voice-status.unavailable{border-color:#9aa8a1;background:#eef1ef;color:#64736c}.composer-note{grid-column:1/-1;color:#7d8983;font-size:10px;text-align:center}.chat-composer textarea:focus,.icon-button:focus-visible,.session-select:focus-visible,.session-delete:focus-visible,.elder-selector button:focus-visible,.history-error button:focus-visible,.voice-input:focus-visible{outline:2px solid #4b917a;outline-offset:2px}.history-layer{position:fixed;inset:0;z-index:50;display:flex;justify-content:flex-end;background:rgba(24,43,35,.26)}.history-drawer{display:flex;flex-direction:column;width:min(360px,88vw);height:100%;background:#f7f9f7;box-shadow:-12px 0 28px rgba(26,51,41,.18)}.history-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:18px;border-bottom:1px solid #dfe7e2}.history-heading>view{display:grid;gap:3px}.history-heading text:first-child{font-size:16px;font-weight:780}.history-heading text:last-child{color:#75847d;font-size:12px}.history-state{padding:22px 18px;color:#718078;font-size:13px}.session-list{overflow-y:auto}.session-row{display:grid;grid-template-columns:minmax(0,1fr) 44px;border-bottom:1px solid #e1e8e4;background:transparent;color:#31443b}.session-row.active{border-left:3px solid #27715c;background:#e9f3ee}.session-select{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px;min-width:0;margin:0;padding:14px 10px 14px 18px;border:0;background:transparent;color:inherit;text-align:left}.session-select>view{display:grid;min-width:0;gap:4px}.session-delete{display:grid;place-items:center;width:38px;height:38px;align-self:center;margin:0;border:0;border-radius:6px;background:transparent;color:#a14840}.session-delete:disabled{opacity:.45}.session-title,.session-preview{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.session-title{font-size:14px;font-weight:700}.session-preview{color:#72817a;font-size:12px}.session-time{padding-top:2px;color:#72817a;font-size:11px;white-space:nowrap}@keyframes pulse{0%,100%{opacity:.3;transform:translateY(1px)}50%{opacity:1;transform:translateY(-1px)}}@media(min-width:768px){.chat-composer{left:50%;right:auto;width:440px;transform:translateX(-50%)}}@media(max-width:390px){.ai-header{align-items:flex-start}.assistant-mark{display:none}.header-actions{padding-top:2px}.availability{white-space:normal}.history-error{align-items:flex-start}.history-error button{margin-top:-3px}.chat-composer{grid-template-columns:44px minmax(0,1fr) 44px;padding-right:12px;padding-left:12px}}@media(prefers-reduced-motion:reduce){.thinking .bubble i{animation:none}}
.feedback-note{display:flex;align-items:flex-start;gap:8px;margin-top:9px;padding:10px 12px;border-left:4px solid #4b917a;background:#edf7f2;color:#28634f;font-size:12px;line-height:1.5}.feedback-note svg{flex:none;margin-top:1px}
.message-block.support .avatar{background:#fff0e9;color:#a9543d}.message-block.support .bubble{border:1px solid #ecd2c8;background:#fffaf7;color:#633c30}.support-label{display:block;margin:0 0 4px 2px;color:#9b5742;font-size:11px;font-weight:750}
.ai-panel.embedded,.ai-panel.embedded .ai-workspace{min-height:0;height:100%;max-height:100%;overflow:hidden}.ai-panel.embedded{padding-bottom:0}.ai-panel.embedded .ai-header{position:static;padding-top:0;background:transparent;box-shadow:none}.ai-panel.embedded .conversation{min-height:0;overflow-y:auto;padding-bottom:14px;scroll-behavior:smooth}.ai-panel.embedded .chat-composer{position:sticky;right:auto;bottom:0;left:auto;width:auto;transform:none;margin:0 -14px -14px;padding:11px 14px 8px;background:rgba(247,249,247,.98)}.ai-panel.embedded .history-layer{position:absolute}.ai-panel.embedded .history-drawer{width:min(330px,92vw)}
</style>
