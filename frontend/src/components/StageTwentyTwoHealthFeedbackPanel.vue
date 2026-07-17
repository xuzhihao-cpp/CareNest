<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { getFamilyBindings } from '@/api/stageSix';
import {
  createElderHealthFeedback,
  getAuthorizedHealthFeedbackVoice,
  getFamilyHealthFeedback,
  uploadHealthFeedbackVoice
} from '@/api/stageTwentyTwo';
import type { BindingResponse } from '@/types/stageSix';
import type { AuthUser } from '@/types/stageTwo';
import type {
  HealthFeedbackInputType,
  HealthFeedbackQuery,
  HealthFeedbackRecord,
  HealthFeedbackSeverity,
  HealthFeedbackType,
  SelectedVoiceFile
} from '@/types/stageTwentyTwo';
import {
  canViewFamilyHealthFeedback,
  isCurrentFeedbackRequest,
  resolveHealthFeedbackInputType,
  validateHealthFeedback,
  validateVoiceFileDescriptor
} from '@/utils/stageTwentyTwoRules';

const props = defineProps<{
  roleCode: 'ELDER' | 'FAMILY';
  authUser: AuthUser | null;
}>();
const emit = defineEmits<{
  (event: 'open-profile'): void;
  (event: 'open-assistance'): void;
}>();

const maxVoiceSizeMb = 12;
const maxVoiceSizeBytes = maxVoiceSizeMb * 1024 * 1024;
const typeOptions: Array<{ value: HealthFeedbackType; label: string; help: string }> = [
  { value: 'PAIN', label: '疼痛', help: '身体有疼痛或不舒服' },
  { value: 'DIZZINESS', label: '头晕', help: '头晕、站立不稳或乏力' },
  { value: 'SLEEP', label: '睡眠', help: '入睡困难、早醒或睡不好' },
  { value: 'DIET', label: '饮食', help: '食欲、进食或饮水有变化' },
  { value: 'MENTAL_STATE', label: '精神状态', help: '情绪、精神或反应有变化' }
];
const severityOptions: Array<{ value: HealthFeedbackSeverity; label: string; help: string }> = [
  { value: 'LOW', label: '轻微', help: '有一点不舒服' },
  { value: 'MEDIUM', label: '明显', help: '需要多留意' },
  { value: 'HIGH', label: '严重', help: '现在很不舒服' }
];

const feedbackType = ref<HealthFeedbackType>('PAIN');
const severity = ref<HealthFeedbackSeverity>('LOW');
const content = ref('');
const selectedVoice = ref<SelectedVoiceFile | null>(null);
const uploadedVoiceFileId = ref('');
const uploadProgress = ref(0);
const submitting = ref(false);
const recording = ref(false);
const elderError = ref('');
const successMessage = ref('');
const aiAdvice = ref('');

const bindings = ref<BindingResponse[]>([]);
const selectedElderId = ref('');
const bindingLoading = ref(false);
const recordsLoading = ref(false);
const familyError = ref('');
const records = ref<HealthFeedbackRecord[]>([]);
const voicePlaybackUrls = ref<Record<string, string>>({});
const voicePlaybackStates = ref<Record<string, 'LOADING' | 'READY' | 'FAILED'>>({});
const total = ref(0);
const query = ref<HealthFeedbackQuery>({
  page: 1,
  size: 20,
  feedbackType: '',
  severity: '',
  dateFrom: '',
  dateTo: ''
});

const isElder = computed(() => props.roleCode === 'ELDER');
const authorizedBindings = computed(() => bindings.value.filter(canViewFamilyHealthFeedback));
const selectedBinding = computed(() => authorizedBindings.value.find((item) => item.elderId === selectedElderId.value));
const inputType = computed<HealthFeedbackInputType>(() =>
  resolveHealthFeedbackInputType(content.value, Boolean(selectedVoice.value || uploadedVoiceFileId.value))
);
const voiceLocked = computed(() => Boolean(uploadedVoiceFileId.value));
const actionLabel = computed(() => {
  if (submitting.value) return uploadedVoiceFileId.value ? '正在记录...' : '正在提交...';
  if (uploadedVoiceFileId.value && elderError.value) return '重新提交反馈';
  return '记录我的情况';
});
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.value.size)));
let recordsRequestSequence = 0;

function feedbackTypeLabel(value: HealthFeedbackType) {
  return typeOptions.find((item) => item.value === value)?.label ?? '身体情况';
}

function severityLabel(value: HealthFeedbackSeverity) {
  return severityOptions.find((item) => item.value === value)?.label ?? '未标明';
}

function inputTypeLabel(value: HealthFeedbackInputType) {
  return value === 'VOICE' ? '语音反馈' : value === 'TEXT' ? '文字反馈' : '快捷反馈';
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ');
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(date).replace(/\//g, '-');
}

function friendlyError(code: number, fallback: string) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前绑定没有查看健康反馈的权限。';
  if (code === 404) return '暂时没有找到对应的健康反馈。';
  if (code === 422) return fallback || '反馈内容未通过校验，请检查后重试。';
  return fallback || '服务暂时不可用，请稍后重试。';
}

interface RecorderStopResult {
  tempFilePath: string;
  duration?: number;
  fileSize?: number;
}
interface RecorderManagerLike {
  start(options: { duration: number; format: string }): void;
  stop(): void;
  onStart(callback: () => void): void;
  onStop(callback: (result: RecorderStopResult) => void): void;
  onError(callback: () => void): void;
}

let recorder: RecorderManagerLike | null = null;
let browserRecorder: MediaRecorder | null = null;
let browserRecordingStream: MediaStream | null = null;
let browserRecordingChunks: Blob[] = [];
let browserRecordingStartedAt = 0;
let browserRecordingTimer: ReturnType<typeof setTimeout> | null = null;

function releaseBrowserRecording() {
  if (browserRecordingTimer) clearTimeout(browserRecordingTimer);
  browserRecordingTimer = null;
  browserRecordingStream?.getTracks().forEach((track) => track.stop());
  browserRecordingStream = null;
  browserRecorder = null;
  browserRecordingChunks = [];
}

async function startBrowserRecording() {
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    elderError.value = '当前浏览器无法使用麦克风录音，请更换支持录音的浏览器或设备。';
    return;
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mediaRecorder = new MediaRecorder(stream);
    browserRecordingStream = stream;
    browserRecorder = mediaRecorder;
    browserRecordingChunks = [];
    browserRecordingStartedAt = Date.now();
    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size) browserRecordingChunks.push(event.data);
    };
    mediaRecorder.onerror = () => {
      recording.value = false;
      releaseBrowserRecording();
      elderError.value = '录音过程中出现问题，请检查麦克风后重试。';
    };
    mediaRecorder.onstop = () => {
      const mimeType = mediaRecorder.mimeType || 'audio/webm';
      const blob = new Blob(browserRecordingChunks, { type: mimeType });
      const durationSeconds = Math.max(1, Math.ceil((Date.now() - browserRecordingStartedAt) / 1000));
      const extension = mimeType.includes('ogg') ? 'ogg' : mimeType.includes('mp4') ? 'm4a' : 'webm';
      const voice: SelectedVoiceFile = {
        path: '',
        name: `健康反馈录音.${extension}`,
        size: blob.size,
        mimeType,
        durationSeconds,
        blob
      };
      recording.value = false;
      releaseBrowserRecording();
      const validation = validateVoiceFileDescriptor(voice, maxVoiceSizeBytes, maxVoiceSizeMb);
      if (validation) {
        elderError.value = validation;
        return;
      }
      selectedVoice.value = voice;
      uploadProgress.value = 0;
      elderError.value = '';
      successMessage.value = '';
      aiAdvice.value = '';
    };
    mediaRecorder.start(250);
    recording.value = true;
    elderError.value = '';
    successMessage.value = '';
    aiAdvice.value = '';
    browserRecordingTimer = setTimeout(() => {
      if (browserRecorder?.state === 'recording') browserRecorder.stop();
    }, 60000);
  } catch (exception) {
    releaseBrowserRecording();
    recording.value = false;
    const denied = exception instanceof DOMException && ['NotAllowedError', 'SecurityError'].includes(exception.name);
    elderError.value = denied
      ? '麦克风权限未开启，请在浏览器设置中允许使用麦克风。'
      : '无法开始录音，请检查麦克风是否可用。';
  }
}

function recorderManager() {
  if (recorder) return recorder;
  const provider = uni as unknown as { getRecorderManager?: () => RecorderManagerLike };
  recorder = provider.getRecorderManager?.() ?? null;
  if (!recorder) return null;
  recorder.onStart(() => {
    recording.value = true;
    elderError.value = '';
  });
  recorder.onStop((result) => {
    recording.value = false;
    const voice: SelectedVoiceFile = {
      path: result.tempFilePath,
      name: result.tempFilePath.split('/').pop() || '健康反馈语音.mp3',
      size: result.fileSize ?? 1,
      mimeType: 'audio/mpeg',
      durationSeconds: result.duration === undefined ? undefined : Math.ceil(result.duration / 1000)
    };
    const validation = validateVoiceFileDescriptor(voice, maxVoiceSizeBytes, maxVoiceSizeMb);
    if (validation) {
      elderError.value = validation;
      return;
    }
    selectedVoice.value = voice;
    uploadProgress.value = 0;
    successMessage.value = '';
    aiAdvice.value = '';
  });
  recorder.onError(() => {
    recording.value = false;
    elderError.value = '无法使用录音功能，请检查麦克风权限后重试。';
  });
  return recorder;
}

function toggleRecording() {
  if (submitting.value || voiceLocked.value) return;
  const browserCanRecord = typeof navigator !== 'undefined'
    && Boolean(navigator.mediaDevices?.getUserMedia)
    && typeof MediaRecorder !== 'undefined';
  if (browserCanRecord || browserRecorder) {
    if (recording.value && browserRecorder?.state === 'recording') browserRecorder.stop();
    else if (!recording.value) void startBrowserRecording();
    return;
  }
  const manager = recorderManager();
  if (!manager) {
    elderError.value = '当前设备不支持直接录音，请更换支持录音的设备。';
    return;
  }
  if (recording.value) manager.stop();
  else manager.start({ duration: 60000, format: 'mp3' });
}

function removeVoice() {
  if (submitting.value || voiceLocked.value) return;
  selectedVoice.value = null;
  uploadProgress.value = 0;
  elderError.value = '';
  aiAdvice.value = '';
}

async function submitFeedback() {
  if (submitting.value) return;
  elderError.value = '';
  successMessage.value = '';
  aiAdvice.value = '';
  submitting.value = true;
  let fileId = uploadedVoiceFileId.value;
  if (selectedVoice.value && !fileId) {
    const validation = validateVoiceFileDescriptor(selectedVoice.value, maxVoiceSizeBytes, maxVoiceSizeMb);
    if (validation) {
      elderError.value = validation;
      submitting.value = false;
      return;
    }
    const upload = await uploadHealthFeedbackVoice(selectedVoice.value, (value) => { uploadProgress.value = value; });
    if (upload.code !== 0) {
      elderError.value = friendlyError(upload.code, upload.message);
      submitting.value = false;
      return;
    }
    fileId = upload.data.fileId;
    uploadedVoiceFileId.value = fileId;
  }
  const resolvedInputType = resolveHealthFeedbackInputType(content.value, Boolean(fileId));
  const payload = {
    feedbackType: feedbackType.value,
    severity: severity.value,
    content: content.value.trim(),
    inputType: resolvedInputType,
    fileId: resolvedInputType === 'VOICE' ? fileId : null
  };
  const validation = validateHealthFeedback(payload);
  if (validation) {
    elderError.value = validation;
    submitting.value = false;
    return;
  }
  const response = await createElderHealthFeedback(payload);
  submitting.value = false;
  if (response.code !== 0) {
    elderError.value = fileId
      ? `${friendlyError(response.code, response.message)} 语音已上传，可直接重新提交，无需再次选择。`
      : friendlyError(response.code, response.message);
    return;
  }
  feedbackType.value = 'PAIN';
  severity.value = 'LOW';
  content.value = '';
  selectedVoice.value = null;
  uploadedVoiceFileId.value = '';
  uploadProgress.value = 0;
  successMessage.value = '已记录，将供授权家属和护理人员参考';
  aiAdvice.value = response.data.aiAdvice;
}

async function loadBindings() {
  if (isElder.value) return;
  bindingLoading.value = true;
  familyError.value = '';
  const response = await getFamilyBindings();
  bindingLoading.value = false;
  if (response.code !== 0) {
    bindings.value = [];
    familyError.value = friendlyError(response.code, response.message);
    return;
  }
  bindings.value = response.data;
  const permitted = authorizedBindings.value;
  if (!permitted.some((item) => item.elderId === selectedElderId.value)) {
    selectedElderId.value = permitted[0]?.elderId ?? '';
  }
  if (!selectedElderId.value) {
    recordsRequestSequence += 1;
    releaseVoicePlaybackUrls();
    records.value = [];
    total.value = 0;
  } else {
    await loadRecords();
  }
}

function releaseVoicePlaybackUrls() {
  Object.values(voicePlaybackUrls.value).forEach((url) => {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url);
  });
  voicePlaybackUrls.value = {};
  voicePlaybackStates.value = {};
}

async function loadVoicePlayback(
  feedbackRecords: HealthFeedbackRecord[],
  requestSequence: number,
  requestedElderId: string
) {
  const voiceRecords = feedbackRecords.filter((record) => record.inputType === 'VOICE');
  voicePlaybackStates.value = Object.fromEntries(voiceRecords.map((record) => [
    record.feedbackId,
    record.voiceUrl ? 'LOADING' : 'FAILED'
  ]));
  await Promise.all(voiceRecords.filter((record) => record.voiceUrl).map(async (record) => {
    const response = await getAuthorizedHealthFeedbackVoice(record.voiceUrl!);
    if (!isCurrentFeedbackRequest(
      requestSequence,
      recordsRequestSequence,
      requestedElderId,
      selectedElderId.value
    )) {
      if (response.code === 0 && response.data.revokeOnRelease) {
        URL.revokeObjectURL(response.data.playbackUrl);
      }
      return;
    }
    if (response.code !== 0 || !response.data.playbackUrl) {
      voicePlaybackStates.value = { ...voicePlaybackStates.value, [record.feedbackId]: 'FAILED' };
      return;
    }
    voicePlaybackUrls.value = { ...voicePlaybackUrls.value, [record.feedbackId]: response.data.playbackUrl };
    voicePlaybackStates.value = { ...voicePlaybackStates.value, [record.feedbackId]: 'READY' };
  }));
}

async function loadRecords() {
  if (!selectedElderId.value || !canViewFamilyHealthFeedback(selectedBinding.value)) return;
  const requestedElderId = selectedElderId.value;
  const requestSequence = ++recordsRequestSequence;
  const requestQuery = { ...query.value };
  recordsLoading.value = true;
  familyError.value = '';
  const response = await getFamilyHealthFeedback(requestedElderId, requestQuery);
  if (!isCurrentFeedbackRequest(
    requestSequence,
    recordsRequestSequence,
    requestedElderId,
    selectedElderId.value
  )) return;
  recordsLoading.value = false;
  if (response.code !== 0) {
    releaseVoicePlaybackUrls();
    records.value = [];
    total.value = 0;
    familyError.value = friendlyError(response.code, response.message);
    return;
  }
  releaseVoicePlaybackUrls();
  records.value = response.data.records;
  total.value = response.data.total;
  void loadVoicePlayback(response.data.records, requestSequence, requestedElderId);
}

function selectElder(elderId: string) {
  if (selectedElderId.value === elderId) return;
  recordsRequestSequence += 1;
  recordsLoading.value = false;
  releaseVoicePlaybackUrls();
  records.value = [];
  total.value = 0;
  familyError.value = '';
  query.value.page = 1;
  selectedElderId.value = elderId;
  loadRecords();
}

function applyFilters() {
  if (query.value.dateFrom && query.value.dateTo && query.value.dateFrom > query.value.dateTo) {
    familyError.value = '开始日期不能晚于结束日期。';
    return;
  }
  query.value.page = 1;
  loadRecords();
}

function resetFilters() {
  query.value = { page: 1, size: 20, feedbackType: '', severity: '', dateFrom: '', dateTo: '' };
  loadRecords();
}

function changePage(direction: -1 | 1) {
  const next = query.value.page + direction;
  if (next < 1 || next > totalPages.value) return;
  query.value.page = next;
  loadRecords();
}

onMounted(loadBindings);
onUnmounted(() => {
  if (browserRecorder?.state === 'recording') {
    browserRecorder.onstop = null;
    browserRecorder.stop();
  }
  releaseBrowserRecording();
  recordsRequestSequence += 1;
  releaseVoicePlaybackUrls();
});
</script>

<template>
  <view class="health-feedback-panel">
    <template v-if="isElder">
      <view class="feedback-heading">
        <text class="feedback-kicker">我的健康反馈</text>
        <text class="feedback-title">今天感觉怎么样？</text>
        <text class="feedback-subtitle">选择最接近的情况，也可以补充文字或语音。</text>
      </view>

      <view class="feedback-section">
        <text class="section-title">哪里不舒服</text>
        <view class="type-grid">
          <button v-for="option in typeOptions" :key="option.value" type="button" :class="{ active: feedbackType === option.value }" :disabled="submitting" @click="feedbackType = option.value">
            <text>{{ option.label }}</text><text>{{ option.help }}</text>
          </button>
        </view>
      </view>

      <view class="feedback-section">
        <text class="section-title">现在的感受</text>
        <view class="severity-grid">
          <button v-for="option in severityOptions" :key="option.value" type="button" :class="[`severity-${option.value.toLowerCase()}`, { active: severity === option.value }]" :disabled="submitting" @click="severity = option.value">
            <text>{{ option.label }}</text><text>{{ option.help }}</text>
          </button>
        </view>
        <view v-if="severity === 'HIGH'" class="high-guidance">
          <text>如果不适持续加重，请及时联系家属或寻求线下医疗帮助。</text>
          <view class="guidance-actions">
            <button type="button" @click="emit('open-profile')">查看家属联系方式</button>
            <button type="button" @click="emit('open-assistance')">平台协助</button>
          </view>
        </view>
      </view>

      <view class="feedback-section">
        <label class="content-field">
          <view><text class="section-title">补充说明</text><text>{{ content.length }} / 512</text></view>
          <textarea v-model="content" maxlength="512" :disabled="submitting" placeholder="例如：从早上起床后开始头晕，坐下休息后稍有缓解" />
        </label>
        <view class="voice-block">
          <view class="voice-heading"><text class="section-title">语音补充</text><text>直接录音，最长 60 秒</text></view>
          <view v-if="selectedVoice" class="selected-voice">
            <view><text>{{ selectedVoice.name }}</text><text>{{ uploadedVoiceFileId ? '语音已上传，等待记录' : '语音已准备' }}</text></view>
            <button type="button" :disabled="submitting || voiceLocked" @click="removeVoice">移除</button>
          </view>
          <view class="voice-actions">
            <button type="button" :disabled="submitting || voiceLocked" @click="toggleRecording">{{ recording ? '停止录音' : '开始录音' }}</button>
          </view>
          <view v-if="uploadProgress > 0" class="upload-progress"><view><text>语音上传进度</text><text>{{ uploadProgress }}%</text></view><view class="progress-track"><view :style="{ width: `${uploadProgress}%` }" /></view></view>
        </view>
      </view>

      <view class="submission-summary">
        <text>{{ feedbackTypeLabel(feedbackType) }} · {{ severityLabel(severity) }}</text>
        <text>{{ inputTypeLabel(inputType) }}</text>
      </view>
      <view v-if="elderError" class="inline-error" role="alert">{{ elderError }}</view>
      <view v-if="successMessage" class="inline-success" role="status">{{ successMessage }}</view>
      <view v-if="aiAdvice" class="ai-advice" role="status">
        <text>AI 照护建议</text>
        <text>{{ aiAdvice }}</text>
        <text>建议仅供日常照护参考，不能替代医生诊断或急救服务。</text>
      </view>
      <button class="submit-command" type="button" :disabled="submitting || recording" @click="submitFeedback">{{ actionLabel }}</button>
    </template>

    <template v-else>
      <view class="feedback-heading family-heading">
        <view><text class="feedback-kicker">健康反馈</text><text class="feedback-title">长辈近期情况</text><text class="feedback-subtitle">查看长辈主动记录的身体感受和生活状态。</text></view>
        <button class="refresh-command" type="button" :disabled="bindingLoading || recordsLoading" @click="loadBindings">刷新</button>
      </view>

      <view v-if="bindingLoading" class="list-state">正在读取绑定信息...</view>
      <view v-else-if="authorizedBindings.length === 0" class="list-state">
        <text class="state-title">暂无可查看的长辈</text>
        <text>只有已生效且包含健康查看权限的绑定，才能读取健康反馈。</text>
      </view>
      <template v-else>
        <scroll-view class="elder-selector" scroll-x="true" :show-scrollbar="false">
          <view class="elder-selector-row">
            <button v-for="binding in authorizedBindings" :key="binding.bindingId" type="button" class="elder-choice" :class="{ active: selectedElderId === binding.elderId }" @click="selectElder(binding.elderId)">
              <text>{{ binding.elderName }}</text><text>{{ binding.relationType === 'SON' ? '父子关系' : binding.relationType === 'DAUGHTER' ? '母女关系' : '家庭绑定' }}</text>
            </button>
          </view>
        </scroll-view>

        <view class="filter-section">
          <view class="filter-heading"><text>{{ selectedBinding?.elderName }}的反馈记录</text><text>共 {{ total }} 条</text></view>
          <view class="filter-grid">
            <label><text>反馈类型</text><picker mode="selector" :range="['全部类型', ...typeOptions.map(item => item.label)]" :value="query.feedbackType ? typeOptions.findIndex(item => item.value === query.feedbackType) + 1 : 0" @change="query.feedbackType = Number($event.detail.value) === 0 ? '' : typeOptions[Number($event.detail.value) - 1].value"><view class="picker-field">{{ query.feedbackType ? feedbackTypeLabel(query.feedbackType) : '全部类型' }}</view></picker></label>
            <label><text>感受程度</text><picker mode="selector" :range="['全部程度', ...severityOptions.map(item => item.label)]" :value="query.severity ? severityOptions.findIndex(item => item.value === query.severity) + 1 : 0" @change="query.severity = Number($event.detail.value) === 0 ? '' : severityOptions[Number($event.detail.value) - 1].value"><view class="picker-field">{{ query.severity ? severityLabel(query.severity) : '全部程度' }}</view></picker></label>
            <label><text>开始日期</text><picker mode="date" :value="query.dateFrom" @change="query.dateFrom = String($event.detail.value)"><view class="picker-field">{{ query.dateFrom || '不限' }}</view></picker></label>
            <label><text>结束日期</text><picker mode="date" :value="query.dateTo" @change="query.dateTo = String($event.detail.value)"><view class="picker-field">{{ query.dateTo || '不限' }}</view></picker></label>
          </view>
          <view class="filter-actions"><button type="button" @click="applyFilters">筛选</button><button type="button" @click="resetFilters">清除条件</button></view>
        </view>

        <view v-if="familyError" class="inline-error" role="alert">{{ familyError }}</view>
        <view v-if="recordsLoading" class="list-state">正在读取健康反馈...</view>
        <view v-else-if="records.length === 0 && !familyError" class="list-state"><text class="state-title">暂无健康反馈</text><text>长辈提交后，记录会按时间显示在这里。</text></view>
        <view v-else class="feedback-timeline">
          <view v-for="record in records" :key="record.feedbackId" class="timeline-record" :class="`record-${record.severity.toLowerCase()}`">
            <view class="record-heading"><view><text>{{ feedbackTypeLabel(record.feedbackType) }}</text><text>{{ inputTypeLabel(record.inputType) }}</text></view><text class="severity-badge">{{ severityLabel(record.severity) }}</text></view>
            <text class="record-time">{{ formatDateTime(record.createdAt) }}</text>
            <text v-if="record.content" class="record-content">{{ record.content }}</text>
            <view v-if="record.inputType === 'VOICE'" class="voice-record">
              <audio v-if="voicePlaybackStates[record.feedbackId] === 'READY'" :src="voicePlaybackUrls[record.feedbackId]" controls />
              <text v-else-if="voicePlaybackStates[record.feedbackId] === 'LOADING'">正在准备语音...</text>
              <text v-else>语音暂时无法播放，请稍后刷新。</text>
            </view>
            <text v-if="record.severity === 'HIGH'" class="record-guidance">情况较为明显，请及时与长辈联系并持续留意。</text>
          </view>
        </view>
        <view v-if="totalPages > 1" class="pagination"><button type="button" :disabled="query.page <= 1" @click="changePage(-1)">上一页</button><text>第 {{ query.page }} / {{ totalPages }} 页</text><button type="button" :disabled="query.page >= totalPages" @click="changePage(1)">下一页</button></view>
      </template>
    </template>
  </view>
</template>

<style scoped>
.health-feedback-panel { display:grid; gap:20rpx; min-width:0; color:#17312e; }
.feedback-heading { display:grid; gap:7rpx; padding:12rpx 4rpx 4rpx; }
.feedback-heading text,.section-title,.type-grid text,.severity-grid text,.high-guidance text,.content-field text,.voice-heading text,.selected-voice text,.submission-summary text,.state-title,.elder-choice text,.filter-heading text,.filter-grid label>text,.record-heading text,.record-time,.record-content,.record-guidance { display:block; }
.feedback-kicker { color:#0f766e; font-size:22rpx; font-weight:800; }
.feedback-title { font-size:38rpx; font-weight:850; }
.feedback-subtitle { color:#637974; font-size:24rpx; line-height:1.5; }
.family-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:18rpx; }
.family-heading>view { display:grid; gap:7rpx; }
.feedback-section,.filter-section { display:grid; gap:18rpx; padding:24rpx; border:1rpx solid #d8e5e2; background:#fff; }
.section-title { font-size:28rpx; font-weight:850; }
.type-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12rpx; }
.type-grid button { display:grid; gap:5rpx; min-height:112rpx; padding:18rpx; border:1rpx solid #cadbd7; border-radius:4rpx; background:#fff; text-align:left; }
.type-grid button:last-child { grid-column:1 / -1; }
.type-grid button.active { border-color:#42aa9f; background:#e7f7f4; color:#0d7067; }
.type-grid text:first-child { font-size:29rpx; font-weight:850; }
.type-grid text:last-child { color:#687e79; font-size:21rpx; line-height:1.4; }
.severity-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10rpx; }
.severity-grid button { display:grid; gap:5rpx; min-height:100rpx; padding:14rpx 8rpx; border:1rpx solid #d0dcd9; border-radius:4rpx; background:#fff; text-align:center; }
.severity-grid text:first-child { font-size:27rpx; font-weight:850; }
.severity-grid text:last-child { color:#6e7e7a; font-size:19rpx; line-height:1.35; }
.severity-low.active { border-color:#5ea6c7; background:#edf7fc; color:#236d91; }
.severity-medium.active { border-color:#d7a348; background:#fff7e7; color:#885b10; }
.severity-high.active { border-color:#d66e65; background:#fff0ef; color:#a1322b; }
.high-guidance { display:grid; gap:14rpx; padding:18rpx; border-left:6rpx solid #c74c43; background:#fff2f1; color:#83332e; font-size:23rpx; line-height:1.55; }
.guidance-actions { display:flex; flex-wrap:wrap; gap:10rpx; }
.high-guidance button { min-height:80rpx; margin:0; padding:0 18rpx; border:1rpx solid #d99b96; border-radius:4rpx; background:#fff; color:#973b34; font-size:22rpx; font-weight:750; }
.content-field { display:grid; gap:10rpx; }
.content-field>view,.voice-heading,.selected-voice,.submission-summary,.upload-progress>view:first-child,.filter-heading,.record-heading,.pagination { display:flex; align-items:flex-start; justify-content:space-between; gap:14rpx; }
.content-field>view>text:last-child,.voice-heading>text:last-child { color:#758783; font-size:21rpx; }
.content-field textarea { width:100%; min-height:180rpx; padding:18rpx; box-sizing:border-box; border:1rpx solid #cadbd7; border-radius:4rpx; background:#fbfcfc; color:#17312e; font-size:25rpx; line-height:1.55; }
.voice-block { display:grid; gap:14rpx; padding-top:18rpx; border-top:1rpx solid #e0e8e6; }
.selected-voice { align-items:center; padding:16rpx; border-left:6rpx solid #178b81; background:#edf8f6; }
.selected-voice>view { display:grid; gap:4rpx; min-width:0; }
.selected-voice text:first-child { max-width:100%; overflow-wrap:anywhere; font-size:24rpx; font-weight:800; }
.selected-voice text:last-child { color:#617873; font-size:21rpx; }
.selected-voice button,.voice-actions button,.refresh-command,.filter-actions button,.pagination button { min-height:80rpx; padding:0 18rpx; border:1rpx solid #bdd3ce; border-radius:4rpx; background:#fff; color:#176d65; font-size:22rpx; font-weight:750; }
.voice-actions { display:grid; grid-template-columns:minmax(0,1fr); gap:10rpx; }
.voice-actions button:first-child { border-color:#167f76; background:#167f76; color:#fff; }
.upload-progress { display:grid; gap:9rpx; color:#5d736e; font-size:21rpx; }
.progress-track { height:10rpx; overflow:hidden; background:#dfeae7; }
.progress-track>view { height:100%; background:#168c81; transition:width .2s ease; }
.submission-summary { padding:16rpx 20rpx; border-left:6rpx solid #438ab0; background:#eef7fc; color:#315d74; font-size:23rpx; font-weight:750; }
.inline-error,.inline-success { padding:18rpx 20rpx; border:1rpx solid #efb7b2; background:#fff2f1; color:#a3342e; font-size:23rpx; line-height:1.55; }
.inline-success { border-color:#9fd8cf; background:#eaf8f5; color:#0f766e; }
.ai-advice { display:grid; gap:10rpx; padding:22rpx; border:1rpx solid #a8d8cf; border-left:7rpx solid #178b81; background:#eff9f6; color:#24423c; }
.ai-advice text { display:block; font-size:24rpx; line-height:1.6; }
.ai-advice text:first-child { color:#0f766e; font-size:28rpx; font-weight:850; }
.ai-advice text:last-child { color:#6b7f7a; font-size:20rpx; }
.submit-command { min-height:90rpx; border:1rpx solid #117b72; border-radius:4rpx; background:#117b72; color:#fff; font-size:30rpx; font-weight:850; }
button[disabled] { opacity:.48; }
.elder-selector { margin:0 -24rpx; width:calc(100% + 48rpx); white-space:nowrap; }
.elder-selector-row { display:flex; gap:12rpx; padding:0 24rpx; }
.elder-choice { display:grid; gap:4rpx; min-width:190rpx; padding:18rpx 20rpx; border:1rpx solid #d4e1de; border-radius:4rpx; background:#fff; text-align:left; }
.elder-choice.active { border-color:#54b4aa; background:#e8f7f4; }
.elder-choice text:first-child { font-size:27rpx; font-weight:850; }
.elder-choice text:last-child { color:#6b7e79; font-size:21rpx; }
.filter-heading text:first-child { font-size:28rpx; font-weight:850; }
.filter-heading text:last-child { color:#6b7f7a; font-size:22rpx; }
.filter-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14rpx; }
.filter-grid label { display:grid; gap:8rpx; min-width:0; }
.filter-grid label>text { color:#58706b; font-size:21rpx; font-weight:750; }
.picker-field { min-height:88rpx; padding:0 14rpx; overflow:hidden; box-sizing:border-box; border:1rpx solid #cadbd7; border-radius:4rpx; background:#fff; color:#17312e; font-size:23rpx; font-weight:700; line-height:88rpx; text-overflow:ellipsis; white-space:nowrap; }
.filter-actions { display:flex; gap:10rpx; }
.filter-actions button { flex:1; }
.filter-actions button:first-child { border-color:#167f76; background:#167f76; color:#fff; }
.list-state { display:grid; gap:8rpx; padding:26rpx 20rpx; border:1rpx dashed #c4d6d2; background:#fafbfb; color:#607671; font-size:23rpx; line-height:1.5; }
.state-title { color:#17312e; font-size:27rpx; font-weight:850; }
.feedback-timeline { display:grid; gap:14rpx; }
.timeline-record { display:grid; gap:12rpx; padding:20rpx; border:1rpx solid #d6e2df; border-left-width:7rpx; background:#fff; }
.record-low { border-left-color:#5a9ec0; }
.record-medium { border-left-color:#d29a3d; background:#fffdf8; }
.record-high { border-color:#e1a8a3; border-left-color:#c8473f; background:#fff7f6; }
.record-heading>view { display:grid; gap:4rpx; }
.record-heading view text:first-child { font-size:28rpx; font-weight:850; }
.record-heading view text:last-child { color:#6b7e79; font-size:21rpx; }
.severity-badge { flex:none; padding:7rpx 12rpx; border:1rpx solid currentColor; border-radius:4rpx; color:#6b5b39; font-size:21rpx; font-weight:800; }
.record-time { color:#6c7f7b; font-size:22rpx; }
.record-content { color:#243b37; font-size:24rpx; line-height:1.6; white-space:pre-wrap; }
.voice-record { min-width:0; padding:12rpx; background:#edf6f4; color:#5b716c; font-size:22rpx; }
.voice-record audio { width:100%; }
.record-guidance { padding:12rpx 14rpx; background:#fff0ef; color:#943a33; font-size:22rpx; line-height:1.5; }
.pagination { align-items:center; }
.pagination text { color:#607671; font-size:22rpx; }
@media (max-width:390px) {
  .severity-grid { grid-template-columns:1fr; }
  .severity-grid button { min-height:80rpx; }
  .family-heading { align-items:stretch; }
  .filter-grid { grid-template-columns:1fr; }
}
</style>
