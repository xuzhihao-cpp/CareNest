<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { Bot, CircleAlert, SendHorizontal, ShieldAlert, Sparkles, UserRound } from '@lucide/vue';
import { createAiSession, sendAiMessage } from '@/api/stageFortyOne';
import type { AiSafetyLevel } from '@/types/stageFortyOne';

type ChatMessage = {
  id: string;
  role: 'assistant' | 'user';
  content: string;
  safetyLevel?: AiSafetyLevel;
  assistanceCreated?: boolean;
  customerServiceCreated?: boolean;
};

const props = defineProps<{ roleCode: 'ELDER' | 'FAMILY'; elderId?: string }>();
const draft = ref('');
const loading = ref(false);
const error = ref('');
const sessionId = ref('');
const conversation = ref<ChatMessage[]>([{
  id: 'welcome', role: 'assistant', content: '您好，我是 CareNest AI 照护助手。您可以问我日常照护、上门服务准备和平台使用问题。'
}]);
const starterPrompts = ['上门护理前要准备什么？', '今天怎样安排饮水？', '如何记录日常照护情况？'];
const hasUserMessage = computed(() => conversation.value.some((item) => item.role === 'user'));
let localSequence = 0;

function localId(role: string) { localSequence += 1; return `${role}-${localSequence}`; }
async function useStarter(prompt: string) { draft.value = prompt; await send(); }
async function send() {
  const content = draft.value.trim();
  if (!content || loading.value) return;
  conversation.value.push({ id: localId('user'), role: 'user', content });
  draft.value = ''; loading.value = true; error.value = '';
  await nextTick();
  try {
    if (!sessionId.value) {
      const created = await createAiSession({ elderId: props.elderId, sessionTitle: '照护咨询', sourceType: 'TEXT' });
      if (created.code !== 0) throw new Error(created.message);
      sessionId.value = created.data.sessionId;
    }
    const response = await sendAiMessage(sessionId.value, { content, messageType: 'TEXT' });
    if (response.code !== 0) throw new Error(response.message);
    conversation.value.push({
      id: localId('assistant'), role: 'assistant', content: response.data.answer,
      safetyLevel: response.data.safetyLevel,
      assistanceCreated: Boolean(response.data.assistanceTicketId),
      customerServiceCreated: response.data.customerServiceTicketCreated
    });
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '消息发送失败，请稍后重试';
  } finally { loading.value = false; }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void send(); }
}
</script>

<template>
  <section class="ai-panel">
    <header class="ai-header">
      <view class="assistant-mark"><Sparkles :size="22" aria-hidden="true" /></view>
      <view><text class="ai-title">AI 照护助手</text><text class="availability"><i></i>在线 · 日常照护咨询</text></view>
    </header>

    <view class="conversation" aria-live="polite">
      <view v-for="message in conversation" :key="message.id" class="message-block" :class="message.role">
        <view class="avatar"><Bot v-if="message.role === 'assistant'" :size="18" aria-hidden="true" /><UserRound v-else :size="18" aria-hidden="true" /></view>
        <view class="message-content">
          <view class="bubble">{{ message.content }}</view>
          <view v-if="message.safetyLevel === 'WARNING'" class="safety-note warning"><CircleAlert :size="19" aria-hidden="true" /><view><strong>平台已收到协助请求</strong><text>客服会根据情况跟进。用药和诊断问题请咨询医生。</text></view></view>
          <view v-if="message.safetyLevel === 'CRITICAL'" class="safety-note critical"><ShieldAlert :size="20" aria-hidden="true" /><view><strong>紧急协助工单已创建</strong><text>请立即联系家属、平台客服或当地急救，不要等待线上回复。</text></view></view>
        </view>
      </view>

      <view v-if="loading" class="message-block assistant thinking" role="status"><view class="avatar"><Bot :size="18" aria-hidden="true" /></view><view class="bubble"><i></i><i></i><i></i><text>正在思考</text></view></view>
    </view>

    <view v-if="!hasUserMessage" class="starter-prompts">
      <text class="starter-label">您可以这样问</text>
      <button v-for="prompt in starterPrompts" :key="prompt" type="button" @click="useStarter(prompt)">{{ prompt }}</button>
    </view>

    <view v-if="error" class="send-error" role="alert">{{ error }}</view>

    <view class="chat-composer">
      <textarea v-model="draft" :disabled="loading" maxlength="500" auto-height placeholder="输入照护问题" aria-label="输入照护问题" @keydown="handleKeydown" />
      <button type="button" :disabled="loading || !draft.trim()" aria-label="发送消息" @click="send"><SendHorizontal :size="21" aria-hidden="true" /></button>
      <text class="composer-note">AI 建议不能替代医生诊断或急救服务</text>
    </view>
  </section>
</template>

<style scoped>
.ai-panel{display:flex;min-height:calc(100vh - 178px);flex-direction:column;color:#203129}.ai-header{display:flex;align-items:center;gap:11px;padding:3px 2px 16px;border-bottom:1px solid #e0e7e2}.assistant-mark{display:grid;place-items:center;width:42px;height:42px;border-radius:8px;background:#e8f3ee;color:#256a56}.ai-title,.availability{display:block;letter-spacing:0}.ai-title{font-size:18px;font-weight:780}.availability{display:flex;align-items:center;gap:5px;margin-top:3px;color:#708077;font-size:12px}.availability i{width:7px;height:7px;border-radius:50%;background:#3f9a75}.conversation{display:flex;flex:1;flex-direction:column;gap:18px;padding:22px 0 18px}.message-block{display:flex;align-items:flex-start;gap:9px}.message-block.user{flex-direction:row-reverse}.avatar{display:grid;place-items:center;flex:none;width:32px;height:32px;border-radius:50%;background:#e8f2ed;color:#2b6c59}.user .avatar{background:#e9ecea;color:#56665e}.message-content{min-width:0;max-width:calc(100% - 54px)}.bubble{max-width:100%;padding:13px 15px;border-radius:3px 14px 14px 14px;background:#fff;color:#31443b;font-size:15px;line-height:1.65;white-space:pre-wrap;overflow-wrap:anywhere;box-shadow:0 1px 0 rgba(42,72,59,.08)}.user .bubble{border-radius:14px 3px 14px 14px;background:#2b735e;color:#fff;box-shadow:none}.safety-note{display:flex;gap:9px;margin-top:9px;padding:12px 13px;border-left:4px solid;border-radius:4px;background:#fff}.safety-note svg{flex:none;margin-top:1px}.safety-note strong,.safety-note text{display:block}.safety-note strong{font-size:13px}.safety-note text{margin-top:4px;font-size:12px;line-height:1.5}.safety-note.warning{border-color:#c79538;color:#73571d}.safety-note.critical{border-color:#cb5c4e;background:#fff6f4;color:#8f3e35}.thinking .bubble{display:flex;align-items:center;gap:4px;color:#68786f}.thinking .bubble i{width:5px;height:5px;border-radius:50%;background:#5d8b79;animation:pulse 1s ease-in-out infinite}.thinking .bubble i:nth-child(2){animation-delay:.14s}.thinking .bubble i:nth-child(3){animation-delay:.28s}.thinking .bubble text{margin-left:5px;font-size:12px}.starter-prompts{display:grid;gap:8px;margin:0 0 14px;padding-left:41px}.starter-label{color:#718078;font-size:12px}.starter-prompts button{min-height:44px;margin:0;padding:9px 13px;border:1px solid #d7e2dc;border-radius:7px;background:#fff;color:#365d50;text-align:left;font-size:13px;line-height:1.35}.send-error{margin:0 0 9px;padding:10px 12px;border-left:4px solid #c45b4d;background:#fff5f3;color:#8b4138;font-size:13px}.chat-composer{position:sticky;bottom:76px;z-index:10;display:grid;grid-template-columns:minmax(0,1fr) 46px;gap:8px;margin:0 -4px;padding:11px 4px 6px;border-top:1px solid #dfe6e2;background:#f4f6f3}.chat-composer textarea{width:100%;min-height:46px;max-height:120px;box-sizing:border-box;padding:12px 13px;border:1px solid #cfdcd5;border-radius:8px;background:#fff;color:#263a31;font-size:15px;line-height:1.45}.chat-composer button{display:grid;place-items:center;width:46px;height:46px;margin:0;padding:0;border:0;border-radius:8px;background:#27715c;color:#fff}.chat-composer button:disabled{background:#aab7b1;color:#eef1ef}.composer-note{grid-column:1/-1;color:#7d8983;font-size:10px;text-align:center}.chat-composer textarea:focus{border-color:#4b917a;outline:2px solid rgba(75,145,122,.14)}@keyframes pulse{0%,100%{opacity:.3;transform:translateY(1px)}50%{opacity:1;transform:translateY(-1px)}}@media(prefers-reduced-motion:reduce){.thinking .bubble i{animation:none}}
</style>
