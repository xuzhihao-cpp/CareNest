<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { uploadMedicalFileAsset } from '@/api/stageTwenty';
import {
  getNurseExceptionProofs,
  getNurseMetricChecklist,
  getOrderEvidences,
  runMetricCheck,
  submitCareEvidence,
  submitMetricExceptionProof
} from '@/api/stageThirtyFourToForty';
import type {
  CareMetricEvidenceType,
  EvidenceResponse,
  ExceptionProofResponse,
  MetricCheckResponse,
  MetricChecklistItem,
  ProofReasonType
} from '@/types/stageThirtyFourToForty';
import type { NurseTaskDetailRecord } from '@/types/stageThirteen';
import { hasValidUploadedFileId } from '@/utils/stageTwentyRules';
import {
  CARE_METRIC_EVIDENCE_LABELS,
  CARE_METRIC_STATUS_LABELS,
  EVIDENCE_AUDIT_STATUS_LABELS,
  compactBusinessId,
  evidenceNeedsFile,
  PROOF_REASON_LABELS,
  PROOF_REASON_TYPES,
  PROOF_STATUS_LABELS,
  stageThirtyFourToFortyError,
  validateEvidenceSubmission,
  validateExceptionProofSubmission
} from '@/utils/stageThirtyFourToFortyRules';

const props = defineProps<{
  task: NurseTaskDetailRecord | null;
}>();

type UploadTarget = 'EVIDENCE' | 'PROOF';
type ChosenFile = {
  path: string;
  name: string;
  size?: number;
};

const emit = defineEmits<{
  refreshTasks: [];
}>();

const checklist = ref<MetricChecklistItem[]>([]);
const evidences = ref<EvidenceResponse[]>([]);
const proofs = ref<ExceptionProofResponse[]>([]);
const checkResult = ref<MetricCheckResponse>({ items: [] });
const selectedMetricItemId = ref('');
const selectedProofMetricItemId = ref('');
const evidenceDescription = ref('');
const selectedEvidenceFile = ref<ChosenFile | null>(null);
const uploadedEvidenceFileId = ref('');
const evidenceUploadProgress = ref(0);
const selectedProofFile = ref<ChosenFile | null>(null);
const proofFileIds = ref<string[]>([]);
const proofUploadProgress = ref(0);
const proofReasonType = ref<ProofReasonType>('ELDER_REFUSED');
const proofReasonText = ref('');
const loading = ref(false);
const uploadingTarget = ref<UploadTarget | ''>('');
const notice = ref('');
const error = ref('');

const selectedMetric = computed(() =>
  checklist.value.find((item) => item.itemId === selectedMetricItemId.value) ?? null
);
const selectedProofMetric = computed(() =>
  checklist.value.find((item) => item.itemId === selectedProofMetricItemId.value) ?? null
);
const evidenceType = computed<CareMetricEvidenceType>(() => selectedMetric.value?.evidenceType ?? 'PHOTO');
const evidenceMetrics = computed(() => checklist.value.filter((item) => item.evidenceType !== 'NONE'));
const missingMetrics = computed(() => checklist.value.filter((item) => item.status === 'MISSING'));
const hasMetricCheckResult = computed(() => checkResult.value.items.length > 0);
const hasMissingMetrics = computed(() => hasMetricCheckResult.value && missingMetrics.value.length > 0);
const hasSubmittedRecords = computed(() => evidences.value.length > 0 || proofs.value.length > 0);
const passCount = computed(() => checklist.value.filter((item) => item.status === 'PASS').length);
const pendingCount = computed(() => checklist.value.filter((item) =>
  item.status === 'PENDING' || item.status === 'SUBMITTED'
).length);
const evidenceFileRequired = computed(() => evidenceNeedsFile(evidenceType.value));
const orderId = computed(() => props.task?.orderId ?? '');
const isServingTask = computed(() => props.task?.taskStatus === 'SERVING');
const proofReasonOptions = PROOF_REASON_TYPES.map((value) => ({ value, label: PROOF_REASON_LABELS[value] }));

function resetFeedback() {
  error.value = '';
  notice.value = '';
}

function taskTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function submittedTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '';
}

function formatSize(bytes?: number) {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function pickerIndex<T extends string>(options: Array<{ value: T }>, current: T) {
  return Math.max(0, options.findIndex((item) => item.value === current));
}

function setProofReason(event: { detail: { value: string | number } }) {
  const option = proofReasonOptions[Number(event.detail.value)];
  if (option) proofReasonType.value = option.value;
}

function normalizeTempPaths(value: string[] | string | undefined) {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string' && item.length > 0);
  return typeof value === 'string' && value.length > 0 ? [value] : [];
}

function selectMetric(item: MetricChecklistItem) {
  selectedMetricItemId.value = item.itemId;
  selectedEvidenceFile.value = null;
  uploadedEvidenceFileId.value = '';
  evidenceUploadProgress.value = 0;
  evidenceDescription.value = '';
  resetFeedback();
}

function selectProofMetric(item: MetricChecklistItem) {
  selectedProofMetricItemId.value = item.itemId;
  proofReasonText.value = '';
  proofFileIds.value = [];
  selectedProofFile.value = null;
  proofUploadProgress.value = 0;
  resetFeedback();
}

function handleChosenFile(target: UploadTarget, files: Array<{ path?: string; name?: string; size?: number }>, paths: string[]) {
  const candidate = files[0];
  const path = paths[0] || candidate?.path || '';
  if (!path) {
    error.value = '未取得本地文件路径，请重新选择文件。';
    return;
  }
  const selected = {
    path,
    name: candidate?.name || path.split('/').pop() || '已选择文件',
    size: candidate?.size
  };
  if (target === 'EVIDENCE') {
    selectedEvidenceFile.value = selected;
    uploadedEvidenceFileId.value = '';
    evidenceUploadProgress.value = 0;
  } else {
    selectedProofFile.value = selected;
    proofUploadProgress.value = 0;
  }
  resetFeedback();
}

function chooseEvidenceFile() {
  if (!evidenceFileRequired.value) return;
  if (evidenceType.value === 'PHOTO') {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success(result) {
        const files = (result.tempFiles ?? []) as Array<{ path?: string; name?: string; size?: number }>;
        handleChosenFile('EVIDENCE', files, normalizeTempPaths(result.tempFilePaths));
      },
      fail(result) {
        if (!result.errMsg.toLowerCase().includes('cancel')) error.value = '暂时无法选择照片，请稍后重试。';
      }
    });
    return;
  }
  uni.chooseFile({
    count: 1,
    type: 'all',
    success(result) {
      const files = (Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]) as Array<{
        path?: string;
        name?: string;
        size?: number;
      }>;
      handleChosenFile('EVIDENCE', files, normalizeTempPaths(result.tempFilePaths));
    },
    fail(result) {
      if (!result.errMsg.toLowerCase().includes('cancel')) error.value = '暂时无法选择附件，请稍后重试。';
    }
  });
}

function chooseProofFile() {
  uni.chooseFile({
    count: 1,
    type: 'all',
    success(result) {
      const files = (Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]) as Array<{
        path?: string;
        name?: string;
        size?: number;
      }>;
      handleChosenFile('PROOF', files, normalizeTempPaths(result.tempFilePaths));
    },
    fail(result) {
      if (!result.errMsg.toLowerCase().includes('cancel')) error.value = '暂时无法选择证明文件，请稍后重试。';
    }
  });
}

async function uploadChosenFile(target: UploadTarget) {
  const file = target === 'EVIDENCE' ? selectedEvidenceFile.value : selectedProofFile.value;
  if (!file) {
    error.value = target === 'EVIDENCE' ? '请先选择留档文件。' : '请先选择证明文件。';
    return '';
  }
  uploadingTarget.value = target;
  const response = await uploadMedicalFileAsset(file.path, (progress) => {
    if (target === 'EVIDENCE') evidenceUploadProgress.value = progress;
    else proofUploadProgress.value = progress;
  });
  uploadingTarget.value = '';
  if (response.code !== 0) {
    error.value = stageThirtyFourToFortyError(response.code, target === 'EVIDENCE' ? 'EVIDENCE' : 'PROOF');
    return '';
  }
  if (!hasValidUploadedFileId(response.data)) {
    error.value = '文件服务未返回有效文件凭证，请稍后重试。';
    return '';
  }
  return response.data.fileId.trim();
}

async function loadMetricWork() {
  if (!orderId.value) {
    checklist.value = [];
    evidences.value = [];
    proofs.value = [];
    checkResult.value = { items: [] };
    return;
  }
  loading.value = true;
  resetFeedback();
  const [checklistResponse, evidenceResponse, proofResponse] = await Promise.all([
    getNurseMetricChecklist(orderId.value),
    getOrderEvidences(orderId.value),
    getNurseExceptionProofs(orderId.value)
  ]);
  loading.value = false;
  if (checklistResponse.code !== 0) {
    checklist.value = [];
    if (checklistResponse.code === 404) {
      notice.value = '当前订单的质量清单正在准备中，请稍后刷新。';
    } else {
      error.value = stageThirtyFourToFortyError(checklistResponse.code, 'CHECKLIST');
    }
    return;
  }
  if (evidenceResponse.code !== 0 || proofResponse.code !== 0) {
    error.value = stageThirtyFourToFortyError(evidenceResponse.code !== 0 ? evidenceResponse.code : proofResponse.code, 'EVIDENCE');
  }
  checklist.value = checklistResponse.data.items;
  evidences.value = evidenceResponse.code === 0 ? evidenceResponse.data : [];
  proofs.value = proofResponse.code === 0 ? proofResponse.data : [];
  if (!selectedMetricItemId.value || !checklist.value.some((item) => item.itemId === selectedMetricItemId.value)) {
    selectedMetricItemId.value = evidenceMetrics.value[0]?.itemId ?? '';
  }
  if (!selectedProofMetricItemId.value || !checklist.value.some((item) => item.itemId === selectedProofMetricItemId.value)) {
    selectedProofMetricItemId.value = missingMetrics.value[0]?.itemId ?? '';
  }
}

async function submitEvidence() {
  const metric = selectedMetric.value;
  if (evidenceFileRequired.value && !uploadedEvidenceFileId.value) {
    if (!selectedEvidenceFile.value) {
      error.value = '请先选择并上传留档文件。';
      return;
    }
    const fileId = await uploadChosenFile('EVIDENCE');
    if (!fileId) return;
    uploadedEvidenceFileId.value = fileId;
  }
  const validationError = validateEvidenceSubmission(
    metric,
    evidenceType.value,
    uploadedEvidenceFileId.value,
    evidenceDescription.value
  );
  if (validationError) {
    error.value = validationError;
    return;
  }
  loading.value = true;
  resetFeedback();
  const response = await submitCareEvidence(orderId.value, {
    metricItemId: metric?.itemId,
    fileId: uploadedEvidenceFileId.value || undefined,
    evidenceType: evidenceType.value,
    description: evidenceDescription.value.trim()
  });
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.message || stageThirtyFourToFortyError(response.code, 'EVIDENCE');
    return;
  }
  notice.value = `留档已提交，当前状态为${EVIDENCE_AUDIT_STATUS_LABELS[response.data.auditStatus]}。`;
  selectedEvidenceFile.value = null;
  uploadedEvidenceFileId.value = '';
  evidenceUploadProgress.value = 0;
  await loadMetricWork();
}

async function executeMetricCheck() {
  loading.value = true;
  resetFeedback();
  const response = await runMetricCheck(orderId.value);
  loading.value = false;
  if (response.code !== 0) {
    error.value = stageThirtyFourToFortyError(response.code, 'CHECK');
    return;
  }
  checkResult.value = response.data;
  notice.value = `指标校验完成，共 ${response.data.items.length} 项结果。`;
  await loadMetricWork();
  emit('refreshTasks');
}

async function addProofFile() {
  const fileId = await uploadChosenFile('PROOF');
  if (!fileId) return;
  proofFileIds.value = Array.from(new Set([...proofFileIds.value, fileId]));
  selectedProofFile.value = null;
  proofUploadProgress.value = 0;
  notice.value = '证明文件已上传，可继续添加或提交原因证明。';
}

function removeProofFile(fileId: string) {
  proofFileIds.value = proofFileIds.value.filter((item) => item !== fileId);
}

async function submitProof() {
  const metric = selectedProofMetric.value;
  const payload = {
    reasonType: proofReasonType.value,
    reasonText: proofReasonText.value.trim(),
    fileIds: proofFileIds.value
  };
  const validationError = validateExceptionProofSubmission(metric, payload);
  if (validationError) {
    error.value = validationError;
    return;
  }
  loading.value = true;
  resetFeedback();
  const response = await submitMetricExceptionProof(metric?.itemId ?? '', payload);
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.message || stageThirtyFourToFortyError(response.code, 'PROOF');
    return;
  }
  notice.value = `原因证明已提交，当前状态为${PROOF_STATUS_LABELS[response.data.reviewStatus]}。`;
  proofReasonText.value = '';
  proofFileIds.value = [];
  selectedProofFile.value = null;
  await loadMetricWork();
}

watch(
  () => orderId.value,
  () => { void loadMetricWork(); }
);

onMounted(loadMetricWork);
</script>

<template>
  <view class="care-metric-nurse-panel">
    <view class="quality-heading">
      <view>
        <text class="quality-kicker">服务质量</text>
        <text class="quality-title">质量留证</text>
        <text class="quality-subtitle">
          {{ task ? `${task.serviceName || '上门护理服务'} · ${taskTime(task.scheduledStart)}` : '请先从任务列表选择订单' }}
        </text>
      </view>
      <button type="button" :disabled="loading || !task" @click="loadMetricWork">刷新</button>
    </view>

    <view v-if="!task" class="quality-state">请选择一个护理任务后处理订单留档、指标校验和原因证明。</view>
    <template v-else>
      <view class="quality-summary">
        <view><text>{{ checklist.length }}</text><text>必填指标</text></view>
        <template v-if="isServingTask">
          <view><text>{{ passCount }}</text><text>已达标</text></view>
          <view><text>{{ pendingCount }}</text><text>待处理</text></view>
          <view><text>{{ missingMetrics.length }}</text><text>未完成</text></view>
        </template>
        <view v-else class="pre-accept-summary"><text>服务开始后记录完成情况</text></view>
      </view>

      <view v-if="notice" class="quality-notice success">{{ notice }}</view>
      <view v-if="error" class="quality-notice error">{{ error }}</view>

      <view class="quality-section">
        <view class="section-head">
          <view><text>订单留档清单</text><text>清单来自后端订单快照；没有返回时不展示占位数据。</text></view>
          <button v-if="isServingTask" type="button" class="secondary" :disabled="loading" @click="executeMetricCheck">执行指标校验</button>
        </view>
        <view v-if="checklist.length === 0" class="quality-state">当前订单尚未生成留档清单，请联系管理端生成。</view>
        <view v-else class="metric-list">
          <button
            v-for="item in checklist"
            :key="item.itemId"
            type="button"
            class="metric-card"
            :class="{ active: item.itemId === selectedMetricItemId, missing: item.status === 'MISSING' }"
            @click="selectMetric(item)"
          >
            <view><text>{{ item.metricCode }}</text><text>{{ CARE_METRIC_EVIDENCE_LABELS[item.evidenceType] }} · {{ item.required ? '必填' : '选填' }}</text></view>
            <view v-if="isServingTask"><strong>{{ CARE_METRIC_STATUS_LABELS[item.status] }}</strong><small>权重 {{ item.scoreWeight }}</small></view>
            <view v-else><strong>服务要求</strong><small>权重 {{ item.scoreWeight }}</small></view>
          </button>
        </view>
      </view>

      <view v-if="isServingTask" class="quality-section">
        <view class="section-head"><view><text>提交护理留档</text><text>照片和附件会先上传文件服务，再把文件凭证写入留档记录。</text></view></view>
        <view v-if="!selectedMetric" class="quality-state">请选择一个需要留档的指标。</view>
        <template v-else>
          <view class="selected-metric-strip">
            <text>{{ selectedMetric.metricCode }}</text>
            <text>{{ CARE_METRIC_EVIDENCE_LABELS[evidenceType] }}</text>
            <text>{{ CARE_METRIC_STATUS_LABELS[selectedMetric.status] }}</text>
          </view>
          <view v-if="evidenceFileRequired" class="file-block">
            <view v-if="selectedEvidenceFile" class="selected-file">
              <view><text>{{ selectedEvidenceFile.name }}</text><text>{{ formatSize(selectedEvidenceFile.size) }}</text></view>
              <button type="button" class="secondary" :disabled="uploadingTarget === 'EVIDENCE'" @click="chooseEvidenceFile">重新选择</button>
            </view>
            <button v-else type="button" class="file-picker" :disabled="uploadingTarget === 'EVIDENCE'" @click="chooseEvidenceFile">
              选择{{ evidenceType === 'PHOTO' ? '照片' : '附件' }}
            </button>
            <view v-if="evidenceUploadProgress > 0" class="progress"><text>上传进度</text><view><view :style="{ width: `${evidenceUploadProgress}%` }" /></view></view>
          </view>
          <label class="quality-field">
            <text>留档说明</text>
            <textarea v-model="evidenceDescription" maxlength="500" placeholder="记录本项完成情况，文字留档必填" />
          </label>
          <button type="button" class="primary full" :disabled="loading || uploadingTarget === 'EVIDENCE'" @click="submitEvidence">提交留档</button>
        </template>
      </view>

      <view v-if="isServingTask && hasMetricCheckResult" class="quality-section">
        <view class="section-head"><view><text>指标校验结果</text><text>服务结束后执行校验；未完成项可提交原因证明。</text></view></view>
        <view class="check-result-list">
          <view v-for="item in checkResult.items" :key="item.metricItemId" class="check-result" :class="{ missing: item.missingEvidence }">
            <text>{{ item.metricName }}</text>
            <strong>{{ CARE_METRIC_STATUS_LABELS[item.checkResult] }}</strong>
            <small>评分影响 {{ item.scoreImpact }}</small>
          </view>
        </view>
      </view>

      <view v-if="isServingTask && hasMissingMetrics" class="quality-section">
        <view class="section-head"><view><text>未完成原因证明</text><text>只允许对校验结果为未完成的指标提交，且必须附带文件凭证。</text></view></view>
        <template>
          <view class="missing-selector">
            <button
              v-for="item in missingMetrics"
              :key="item.itemId"
              type="button"
              :class="{ active: item.itemId === selectedProofMetricItemId }"
              @click="selectProofMetric(item)"
            >
              {{ item.metricCode }}
            </button>
          </view>
          <picker :range="proofReasonOptions" range-key="label" :value="pickerIndex(proofReasonOptions, proofReasonType)" @change="setProofReason">
            <view class="select-box">{{ PROOF_REASON_LABELS[proofReasonType] }}</view>
          </picker>
          <label class="quality-field">
            <text>未完成原因</text>
            <textarea v-model="proofReasonText" maxlength="500" placeholder="说明本项未完成的真实原因" />
          </label>
          <view class="file-block">
            <view v-if="selectedProofFile" class="selected-file">
              <view><text>{{ selectedProofFile.name }}</text><text>{{ formatSize(selectedProofFile.size) }}</text></view>
              <button type="button" class="secondary" :disabled="uploadingTarget === 'PROOF'" @click="chooseProofFile">重新选择</button>
            </view>
            <button v-else type="button" class="file-picker" :disabled="uploadingTarget === 'PROOF'" @click="chooseProofFile">选择证明文件</button>
            <button type="button" class="secondary full" :disabled="uploadingTarget === 'PROOF' || !selectedProofFile" @click="addProofFile">上传并加入证明</button>
            <view v-if="proofUploadProgress > 0" class="progress"><text>上传进度</text><view><view :style="{ width: `${proofUploadProgress}%` }" /></view></view>
            <view v-if="proofFileIds.length" class="proof-files">
              <view v-for="fileId in proofFileIds" :key="fileId">
                <text>{{ compactBusinessId(fileId) }}</text>
                <button type="button" @click="removeProofFile(fileId)">移除</button>
              </view>
            </view>
          </view>
          <button type="button" class="primary full" :disabled="loading || uploadingTarget === 'PROOF'" @click="submitProof">提交原因证明</button>
        </template>
      </view>

      <view v-if="isServingTask && hasSubmittedRecords" class="quality-section">
        <view class="section-head"><view><text>已提交留档</text><text>可查看本次已经填写并提交的内容与审核状态。</text></view></view>
        <view class="record-grid">
          <view>
            <text class="record-title">留档记录</text>
            <view v-for="item in evidences" :key="item.evidenceId" class="record-row">
              <text>{{ item.metricName || '服务留档' }}</text>
              <small v-if="item.description">{{ item.description }}</small>
              <small>{{ item.fileId ? '已附文件' : '文字留档' }}<template v-if="submittedTime(item.submittedAt)"> · {{ submittedTime(item.submittedAt) }}</template></small>
              <strong>{{ EVIDENCE_AUDIT_STATUS_LABELS[item.auditStatus] }}</strong>
            </view>
          </view>
          <view>
            <text class="record-title">原因证明</text>
            <view v-for="item in proofs" :key="item.proofId" class="record-row">
              <text>未完成原因证明</text>
              <strong>{{ PROOF_STATUS_LABELS[item.reviewStatus] }}</strong>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.care-metric-nurse-panel { display:grid; gap:20rpx; color:#17312e; }
.quality-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:18rpx; padding:8rpx 4rpx 0; }
.quality-kicker,.quality-title,.quality-subtitle,.section-head text,.quality-field text,.selected-file text,.record-title { display:block; }
.quality-kicker { color:#0f766e; font-size:22rpx; font-weight:800; letter-spacing:2rpx; }
.quality-title { margin-top:6rpx; font-size:38rpx; font-weight:850; }
.quality-subtitle { margin-top:8rpx; color:#627872; font-size:24rpx; line-height:1.45; }
.quality-heading button,.primary,.secondary,.file-picker,.proof-files button { min-height:82rpx; margin:0; padding:0 22rpx; border-radius:8rpx; font-size:25rpx; font-weight:780; }
.quality-heading button,.secondary,.file-picker,.proof-files button { border:1rpx solid #bfd4cf; background:#fff; color:#126f66; }
.primary { border:0; background:#0f766e; color:#fff; }
.full { width:100%; }
button[disabled] { opacity:.48; }
.quality-state { padding:22rpx; border:1rpx dashed #c3d4d0; background:#f8faf9; color:#607671; font-size:24rpx; line-height:1.5; }
.quality-state.compact { padding:16rpx; font-size:23rpx; }
.quality-summary { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10rpx; padding:20rpx 12rpx; border-radius:12rpx; background:#0f766e; color:#fff; }
.quality-summary>view { text-align:center; border-right:1rpx solid rgba(255,255,255,.22); }
.quality-summary>view:last-child { border-right:0; }
.quality-summary text:first-child { display:block; font-size:34rpx; font-weight:850; }
.quality-summary text:last-child { display:block; margin-top:4rpx; font-size:21rpx; opacity:.84; }
.quality-notice { padding:18rpx 20rpx; border-radius:8rpx; font-size:24rpx; line-height:1.55; }
.quality-notice.success { border:1rpx solid #9fd8cf; background:#eaf8f5; color:#0f766e; }
.quality-notice.error { border:1rpx solid #efb7b2; background:#fff2f1; color:#a3342e; }
.quality-section { display:grid; gap:18rpx; padding:24rpx; border:1rpx solid #dce7e4; background:#fff; }
.section-head { display:flex; align-items:flex-start; justify-content:space-between; gap:14rpx; }
.section-head view text:first-child { font-size:29rpx; font-weight:850; }
.section-head view text:last-child { margin-top:6rpx; color:#687b76; font-size:22rpx; line-height:1.45; }
.section-head .secondary { flex:none; min-height:76rpx; }
.metric-list { display:grid; gap:12rpx; }
.metric-card { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:12rpx; align-items:center; width:100%; margin:0; padding:18rpx; border:1rpx solid #d8e4e1; border-radius:8rpx; background:#fbfdfc; text-align:left; }
.metric-card.active { border-color:#0f766e; background:#eaf7f4; }
.metric-card.missing { border-color:#f1ca7b; background:#fff8e8; }
.metric-card text:first-child { display:block; color:#17312e; font-size:26rpx; font-weight:850; }
.metric-card text:last-child,.metric-card small { display:block; margin-top:5rpx; color:#687b76; font-size:22rpx; }
.metric-card strong { display:block; color:#087b78; font-size:25rpx; }
.selected-metric-strip { display:grid; grid-template-columns:1fr auto auto; gap:8rpx; align-items:center; padding:16rpx; border-left:6rpx solid #0f766e; background:#f1faf7; }
.selected-metric-strip text { font-size:23rpx; font-weight:750; }
.file-block { display:grid; gap:12rpx; }
.selected-file { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:12rpx; align-items:center; padding:16rpx; border:1rpx solid #d5e4e0; background:#fbfdfc; }
.selected-file text:first-child { overflow-wrap:anywhere; font-size:25rpx; font-weight:800; }
.selected-file text:last-child { color:#70817f; font-size:22rpx; }
.file-picker { width:100%; border-style:dashed; }
.progress { display:grid; gap:8rpx; color:#607671; font-size:22rpx; font-weight:700; }
.progress>view { height:12rpx; overflow:hidden; background:#dfeae7; }
.progress>view view { height:100%; background:#0f766e; transition:width .2s ease; }
.quality-field { display:grid; gap:10rpx; }
.quality-field>text { color:#516964; font-size:23rpx; font-weight:800; }
.quality-field textarea,.select-box { box-sizing:border-box; width:100%; border:1rpx solid #cbdad6; border-radius:8rpx; background:#fff; color:#17312e; font-size:25rpx; }
.quality-field textarea { min-height:148rpx; padding:16rpx; line-height:1.45; }
.select-box { min-height:84rpx; padding:0 18rpx; line-height:84rpx; font-weight:750; }
.check-result-list { display:grid; gap:12rpx; }
.check-result { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:8rpx; padding:16rpx; border-left:6rpx solid #0f766e; background:#f1faf7; }
.check-result.missing { border-left-color:#ffb84d; background:#fff8e8; }
.check-result text { font-size:25rpx; font-weight:800; }
.check-result strong { color:#087b78; font-size:24rpx; }
.check-result small { grid-column:1 / -1; color:#687b76; font-size:22rpx; }
.missing-selector { display:flex; gap:10rpx; overflow:auto; padding-bottom:2rpx; }
.missing-selector button { flex:none; min-height:74rpx; margin:0; padding:0 18rpx; border:1rpx solid #d6e2df; border-radius:8rpx; background:#fff; color:#526b66; font-size:23rpx; font-weight:750; }
.missing-selector button.active { border-color:#0f766e; background:#eaf7f4; color:#0f766e; }
.proof-files { display:grid; gap:8rpx; }
.proof-files>view { display:flex; align-items:center; justify-content:space-between; gap:12rpx; padding:12rpx 14rpx; border:1rpx solid #e1ebe8; background:#fbfdfc; }
.proof-files text { overflow-wrap:anywhere; color:#17312e; font-size:23rpx; font-weight:750; }
.proof-files button { flex:none; min-height:64rpx; color:#a3342e; border-color:#efc4c0; font-size:22rpx; }
.record-grid { display:grid; grid-template-columns:1fr 1fr; gap:14rpx; }
.record-grid>view { display:grid; gap:10rpx; min-width:0; }
.record-title { color:#17312e; font-size:25rpx; font-weight:850; }
.record-row { display:flex; align-items:center; justify-content:space-between; gap:12rpx; padding:14rpx; border:1rpx solid #dfe9e6; background:#fbfdfc; }
.record-row text { overflow-wrap:anywhere; color:#526b66; font-size:22rpx; }
.record-row strong { flex:none; color:#087b78; font-size:23rpx; }
@media (max-width:390px) {
  .quality-summary { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .quality-summary>view:nth-child(2n) { border-right:0; }
  .record-grid,.selected-metric-strip { grid-template-columns:1fr; }
}
</style>
