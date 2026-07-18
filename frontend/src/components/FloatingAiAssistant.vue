<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { Sparkles, X } from '@lucide/vue';
import StageFortyOneAiAssistantPanel from '@/components/StageFortyOneAiAssistantPanel.vue';

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
type AiAssistantExpose = {
  startVoiceInputFromWake: () => boolean;
  scrollConversationToBottom: () => Promise<void>;
};

const open = ref(false);
const wakeSupported = ref(false);
const wakeListening = ref(false);
const wakeStatus = ref('正在监听“小惠小惠”');
const aiPanel = ref<AiAssistantExpose | null>(null);
let wakeRecognition: SpeechRecognitionLike | null = null;
let wakeRestartTimer: ReturnType<typeof window.setTimeout> | null = null;
let manualStop = false;

function recognitionConstructor() {
  const browserWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  };
  return browserWindow.SpeechRecognition || browserWindow.webkitSpeechRecognition;
}

function normalizeWakeText(value: string) {
  return value.replace(/[\s，。！？,.!?、]/g, '');
}

function startWakeListening() {
  if (!wakeRecognition || open.value || wakeListening.value || manualStop) return;
  try {
    wakeRecognition.start();
  } catch {
    wakeStatus.value = wakeSupported.value ? '正在监听“小惠小惠”' : '点击小惠咨询';
  }
}

function scheduleWakeStart() {
  if (!wakeSupported.value || open.value || manualStop) return;
  if (wakeRestartTimer) window.clearTimeout(wakeRestartTimer);
  wakeRestartTimer = window.setTimeout(() => startWakeListening(), 450);
}

function stopWakeListening() {
  if (wakeRestartTimer) {
    window.clearTimeout(wakeRestartTimer);
    wakeRestartTimer = null;
  }
  if (wakeListening.value) wakeRecognition?.abort();
  wakeListening.value = false;
}

function prepareWakeRecognition() {
  const Recognition = recognitionConstructor();
  wakeSupported.value = Boolean(Recognition);
  if (!Recognition || wakeRecognition) return;

  wakeRecognition = new Recognition();
  wakeRecognition.lang = 'zh-CN';
  wakeRecognition.continuous = true;
  wakeRecognition.interimResults = true;
  wakeRecognition.onstart = () => {
    wakeListening.value = true;
    wakeStatus.value = '正在监听“小惠小惠”';
  };
  wakeRecognition.onresult = (event) => {
    let transcript = '';
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      transcript += event.results[index][0]?.transcript || '';
    }
    if (normalizeWakeText(transcript).includes('小惠小惠') && !open.value) void openAssistant();
  };
  wakeRecognition.onerror = (event: SpeechRecognitionErrorLike) => {
    wakeListening.value = false;
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      manualStop = true;
      wakeStatus.value = '麦克风未授权';
      return;
    }
    wakeStatus.value = wakeSupported.value ? '正在监听“小惠小惠”' : '点击小惠咨询';
  };
  wakeRecognition.onend = () => {
    wakeListening.value = false;
    if (!manualStop && !open.value) scheduleWakeStart();
  };
}

async function openAssistant() {
  open.value = true;
  stopWakeListening();
  await nextTick();
  await aiPanel.value?.scrollConversationToBottom();
  const started = aiPanel.value?.startVoiceInputFromWake();
  wakeStatus.value = started ? '已唤醒，请直接说问题' : '已打开小惠';
}

function closeAssistant() {
  open.value = false;
  wakeStatus.value = wakeSupported.value ? '正在监听“小惠小惠”' : '点击小惠咨询';
  scheduleWakeStart();
}

onMounted(() => {
  prepareWakeRecognition();
  if (wakeSupported.value) startWakeListening();
});

onBeforeUnmount(() => {
  manualStop = true;
  stopWakeListening();
});
</script>

<template>
  <teleport to="body">
    <view class="floating-ai">
      <transition name="assistant-pop">
        <view v-if="open" class="floating-panel" role="dialog" aria-label="小惠 AI 照护助手">
          <view class="floating-panel-header">
            <view>
              <text class="panel-kicker">小惠在线</text>
              <text class="panel-title">AI 照护助手</text>
            </view>
            <button type="button" aria-label="关闭小惠" @click="closeAssistant">
              <X :size="21" aria-hidden="true" />
            </button>
          </view>
          <StageFortyOneAiAssistantPanel ref="aiPanel" role-code="ELDER" embedded />
        </view>
      </transition>

      <view v-if="!open" class="wake-hint">{{ wakeStatus }}</view>
      <button
        v-if="!open"
        type="button"
        class="floating-button"
        :class="{ listening: wakeListening }"
        :aria-label="wakeSupported ? '打开小惠，或说小惠小惠唤醒' : '打开小惠 AI 照护助手'"
        :aria-pressed="wakeListening"
        @click="openAssistant"
      >
        <Sparkles :size="27" aria-hidden="true" />
      </button>
    </view>
  </teleport>
</template>

<style scoped>
.floating-ai{position:fixed;right:max(14px,calc((100vw - 440px)/2 + 14px));bottom:calc(88px + env(safe-area-inset-bottom));z-index:999;display:flex;align-items:flex-end;gap:8px;pointer-events:none}
.floating-button,.floating-panel{pointer-events:auto}
.floating-button{display:grid;place-items:center;width:64px;height:64px;margin:0;padding:0;border:0;border-radius:50%;background:#27715c;color:#fff;box-shadow:0 16px 34px rgba(39,113,92,.32),0 0 0 7px rgba(39,113,92,.1)}
.floating-button.listening{background:#2c8269;box-shadow:0 16px 34px rgba(39,113,92,.34),0 0 0 9px rgba(39,113,92,.16)}
.wake-hint{align-self:center;max-width:128px;margin-right:1px;padding:7px 9px;border:1px solid #dbe5df;border-radius:8px;background:rgba(255,255,255,.96);color:#466259;font-size:12px;font-weight:650;line-height:1.25;box-shadow:0 8px 18px rgba(36,60,50,.12);pointer-events:none}
.floating-panel{position:fixed;right:max(10px,calc((100vw - 440px)/2 + 10px));bottom:calc(82px + env(safe-area-inset-bottom));display:flex;flex-direction:column;width:min(420px,calc(100vw - 20px));height:min(690px,calc(100vh - 108px));box-sizing:border-box;padding:14px;border:1px solid #dfe8e3;border-radius:10px;background:#f7f9f7;color:#203129;box-shadow:0 22px 58px rgba(25,49,39,.24);overflow:hidden}
.floating-panel-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:2px 2px 12px}
.floating-panel-header view{display:grid;gap:2px}
.panel-kicker{color:#4e796b;font-size:12px;font-weight:750}
.panel-title{color:#203129;font-size:19px;font-weight:820}
.floating-panel-header button{display:grid;place-items:center;width:38px;height:38px;margin:0;padding:0;border:1px solid #d6e2dc;border-radius:7px;background:#fff;color:#45675c}
.assistant-pop-enter-active,.assistant-pop-leave-active{transition:opacity .18s ease,transform .18s ease}
.assistant-pop-enter-from,.assistant-pop-leave-to{opacity:0;transform:translateY(12px) scale(.98)}
@media(max-width:370px){.floating-ai{right:10px}.floating-panel{right:8px;width:calc(100vw - 16px);height:min(660px,calc(100vh - 104px))}.wake-hint{display:none}}
@media(prefers-reduced-motion:reduce){.assistant-pop-enter-active,.assistant-pop-leave-active{transition:none}}
</style>
