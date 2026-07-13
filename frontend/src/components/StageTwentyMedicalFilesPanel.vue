<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { readAuthSession } from '@/api/client';
import { getFamilyBindings } from '@/api/stageSix';
import { getFamilyElders } from '@/api/stageSeven';
import { resolveElderResourceId } from '@/api/stageNineteen';
import {
  getMedicalFiles,
  registerMedicalFile,
  uploadMedicalFileAsset
} from '@/api/stageTwenty';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { BindingResponse } from '@/types/stageSix';
import type { ElderProfileResponse } from '@/types/stageSeven';
import type {
  MedicalFileAuditStatus,
  MedicalFileRecord,
  MedicalFileStatusFilter,
  MedicalFileType,
  MedicalFileTypeFilter,
  SelectedMedicalFile,
  UploadFlowStage
} from '@/types/stageTwenty';
import {
  canDiscardSelectedMedicalFile,
  canUploadMedicalFiles,
  canViewMedicalFiles,
  hasValidUploadedFileId,
  MEDICAL_FILE_EXTENSIONS,
  medicalFileExtension,
  nextMedicalFileSubmitStep,
  validateMedicalFileDescriptor,
  validateMedicalFileSignature
} from '@/utils/stageTwentyRules';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const fileTypeOptions: Array<{ value: MedicalFileType; label: string; help: string }> = [
  { value: 'PRESCRIPTION', label: '处方', help: '门诊或住院用药处方' },
  { value: 'EXAMINATION_REPORT', label: '检查报告', help: '检验、影像或体检结果' },
  { value: 'DISCHARGE_SUMMARY', label: '出院小结', help: '住院治疗与出院说明' },
  { value: 'MEDICAL_RECORD', label: '既往病历', help: '其他既往就诊资料' }
];

const statusOptions: Array<{ value: MedicalFileAuditStatus; label: string }> = [
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '未通过' },
  { value: 'NEED_MORE', label: '需补充' }
];

const maxFileSizeMb = Number(import.meta.env.VITE_MEDICAL_FILE_MAX_MB || 10);
const maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
const today = new Date().toISOString().slice(0, 10);

const profiles = ref<ElderProfileResponse[]>([]);
const bindings = ref<BindingResponse[]>([]);
const selectedElderId = ref('');
const records = ref<MedicalFileRecord[]>([]);
const loading = ref(false);
const flowStage = ref<UploadFlowStage>('IDLE');
const uploadProgress = ref(0);
const selectedFile = ref<SelectedMedicalFile | null>(null);
const uploadedFileId = ref('');
const fileType = ref<MedicalFileType>('PRESCRIPTION');
const title = ref('');
const occurredAt = ref(today);
const typeFilter = ref<MedicalFileTypeFilter>('ALL');
const statusFilter = ref<MedicalFileStatusFilter>('ALL');
const error = ref('');
const listError = ref('');
const successMessage = ref('');

const isFamily = computed(() => props.roleCode === 'FAMILY');
const isElder = computed(() => props.roleCode === 'ELDER');
const selectedProfile = computed(() =>
  profiles.value.find((item) => item.elderId === selectedElderId.value) ?? null
);
const selectedBinding = computed(() =>
  bindings.value.find((item) => item.elderId === selectedElderId.value) ?? null
);
const canView = computed(() => {
  if (isElder.value) return true;
  return canViewMedicalFiles(selectedBinding.value);
});
const canUpload = computed(() =>
  isFamily.value
  && canUploadMedicalFiles(selectedBinding.value)
);
const isSubmitting = computed(() => flowStage.value === 'UPLOADING' || flowStage.value === 'REGISTERING');
const actionLabel = computed(() => {
  if (flowStage.value === 'UPLOADING') return `正在上传 ${uploadProgress.value}%`;
  if (flowStage.value === 'REGISTERING') return '正在登记...';
  if (uploadedFileId.value) return '重新登记';
  return '上传并提交审核';
});
const filteredRecords = computed(() => records.value.filter((item) => {
  const typeMatches = typeFilter.value === 'ALL' || item.fileType === typeFilter.value;
  const statusMatches = statusFilter.value === 'ALL' || item.auditStatus === statusFilter.value;
  return typeMatches && statusMatches;
}));
const selectedElderName = computed(() =>
  selectedProfile.value?.name
  || selectedBinding.value?.elderName
  || props.authUser?.displayName
  || '长辈'
);

function typeLabel(value: MedicalFileType) {
  return fileTypeOptions.find((item) => item.value === value)?.label ?? '病历资料';
}

function statusLabel(value: MedicalFileAuditStatus) {
  return statusOptions.find((item) => item.value === value)?.label ?? '处理中';
}

function statusClass(value: MedicalFileAuditStatus) {
  return {
    'PENDING_REVIEW': 'status-pending',
    'APPROVED': 'status-approved',
    'REJECTED': 'status-rejected',
    'NEED_MORE': 'status-more'
  }[value];
}

function formatDate(value?: string) {
  if (!value) return '未记录';
  return value.replace('T', ' ').slice(0, 16);
}

function formatFileSize(bytes?: number) {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function businessError(code: number, target: 'LIST' | 'UPLOAD' | 'REGISTER') {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return target === 'LIST'
    ? '当前没有查看这位长辈病历资料的权限。'
    : '当前绑定没有上传病历资料的权限。';
  if (code === 413) return `文件超过 ${maxFileSizeMb} MB，请压缩后重新选择。`;
  if (code === 415) return '文件格式不支持，请选择 PDF、JPG 或 PNG 文件。';
  if (code === 422) return '资料信息不完整或格式不正确，请检查后重试。';
  if (code === 404) return '病历资料服务暂时不可用，请稍后重试。';
  return target === 'UPLOAD' ? '文件上传失败，请检查网络后重试。' : '病历资料暂时无法处理，请稍后重试。';
}

function releaseSelectedFile() {
  if (selectedFile.value?.objectUrl) URL.revokeObjectURL(selectedFile.value.objectUrl);
}

function resetUploadForm() {
  releaseSelectedFile();
  flowStage.value = 'IDLE';
  uploadProgress.value = 0;
  selectedFile.value = null;
  uploadedFileId.value = '';
  fileType.value = 'PRESCRIPTION';
  title.value = '';
  occurredAt.value = today;
  error.value = '';
}

async function readFileHeader(rawFile: Blob | undefined, path: string) {
  let blob: Blob;
  if (rawFile) {
    blob = rawFile;
  } else {
    blob = await fetch(path).then((response) => {
      if (!response.ok) throw new Error('file read failed');
      return response.blob();
    });
  }
  return new Uint8Array(await blob.slice(0, 8).arrayBuffer());
}

function chooseMedicalFile() {
  if (!canUpload.value || !canDiscardSelectedMedicalFile(uploadedFileId.value, isSubmitting.value)) return;
  uni.chooseFile({
    count: 1,
    type: 'all',
    extension: [...MEDICAL_FILE_EXTENSIONS],
    async success(result) {
      const files = (Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]) as Array<File | {
        path: string;
        name?: string;
        size: number;
        type?: string;
      }>;
      const candidate = files[0];
      if (!candidate) return;
      const paths = Array.isArray(result.tempFilePaths) ? result.tempFilePaths : [result.tempFilePaths];
      const rawFile = typeof File !== 'undefined' && candidate instanceof File ? candidate : undefined;
      const candidateRecord = candidate as { path?: string; name?: string; size: number; type?: string };
      const objectUrl = !paths[0] && !candidateRecord.path && rawFile ? URL.createObjectURL(rawFile) : undefined;
      const path = paths[0] || candidateRecord.path || objectUrl || '';
      const name = rawFile?.name || candidateRecord.name || path.split('/').pop() || '已选择文件';
      const size = rawFile?.size ?? candidateRecord.size;
      const mimeType = rawFile?.type || candidateRecord.type || '';
      const descriptorError = validateMedicalFileDescriptor(
        { name, size, mimeType },
        maxFileSizeBytes,
        maxFileSizeMb
      );
      if (descriptorError) {
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        error.value = descriptorError;
        return;
      }
      try {
        const header = await readFileHeader(rawFile, path);
        const signatureError = validateMedicalFileSignature(medicalFileExtension(name), header);
        if (signatureError) {
          if (objectUrl) URL.revokeObjectURL(objectUrl);
          error.value = signatureError;
          return;
        }
      } catch {
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        error.value = '无法读取文件内容，请重新选择原始文件。';
        return;
      }
      releaseSelectedFile();
      selectedFile.value = {
        path,
        name,
        size,
        mimeType,
        rawFile,
        objectUrl
      };
      uploadedFileId.value = '';
      uploadProgress.value = 0;
      flowStage.value = 'IDLE';
      error.value = '';
      successMessage.value = '';
      if (!title.value.trim()) title.value = name.replace(/\.[^.]+$/, '').slice(0, 80);
    },
    fail(result) {
      if (!result.errMsg.toLowerCase().includes('cancel')) {
        error.value = '暂时无法选择文件，请稍后重试。';
      }
    }
  });
}

function removeSelectedFile() {
  if (!canDiscardSelectedMedicalFile(uploadedFileId.value, isSubmitting.value)) return;
  releaseSelectedFile();
  selectedFile.value = null;
  uploadedFileId.value = '';
  uploadProgress.value = 0;
  flowStage.value = 'IDLE';
  error.value = '';
}

function validateUpload() {
  if (!selectedElderId.value) return '请先选择一位长辈。';
  if (!canUpload.value) return '当前绑定没有上传病历资料的权限。';
  if (!selectedFile.value && !uploadedFileId.value) return '请先选择需要上传的文件。';
  if (!title.value.trim()) return '请填写资料标题。';
  if (title.value.trim().length > 80) return '资料标题不能超过 80 个字。';
  if (!occurredAt.value) return '请选择资料发生日期。';
  if (occurredAt.value > today) return '资料发生日期不能晚于今天。';
  return '';
}

async function submitMedicalFile() {
  const validationError = validateUpload();
  if (validationError) {
    error.value = validationError;
    return;
  }
  error.value = '';
  successMessage.value = '';

  if (!uploadedFileId.value && selectedFile.value) {
    flowStage.value = 'UPLOADING';
    uploadProgress.value = 0;
    const uploadResponse = await uploadMedicalFileAsset(
      selectedFile.value.path,
      (progress) => { uploadProgress.value = progress; }
    );
    if (uploadResponse.code !== 0) {
      flowStage.value = 'FAILED';
      error.value = businessError(uploadResponse.code, 'UPLOAD');
      return;
    }
    if (!hasValidUploadedFileId(uploadResponse.data)) {
      flowStage.value = 'FAILED';
      error.value = '文件服务未返回有效上传凭证，请稍后重试。';
      return;
    }
    uploadedFileId.value = uploadResponse.data.fileId.trim();
    uploadProgress.value = 100;
  }

  if (nextMedicalFileSubmitStep(uploadedFileId.value) !== 'REGISTER_ONLY') {
    flowStage.value = 'FAILED';
    error.value = '文件尚未完成上传，请重新提交。';
    return;
  }

  flowStage.value = 'REGISTERING';
  const registerResponse = await registerMedicalFile(selectedElderId.value, {
    fileId: uploadedFileId.value,
    fileType: fileType.value,
    title: title.value.trim(),
    occurredAt: occurredAt.value
  });
  if (registerResponse.code !== 0) {
    flowStage.value = 'FAILED';
    error.value = uploadedFileId.value
      ? `文件已上传，但登记未完成。${businessError(registerResponse.code, 'REGISTER')}请点击“重新登记”，不要重复选择文件。`
      : businessError(registerResponse.code, 'REGISTER');
    return;
  }

  resetUploadForm();
  successMessage.value = '病历资料已提交，当前状态为待审核。';
  await loadFiles();
}

async function loadFiles() {
  if (!selectedElderId.value) return;
  if (!canView.value) {
    records.value = [];
    listError.value = '当前绑定没有查看病历资料的权限。';
    return;
  }
  loading.value = true;
  listError.value = '';
  const response = await getMedicalFiles(selectedElderId.value);
  loading.value = false;
  if (response.code === 0) {
    records.value = response.data;
    return;
  }
  records.value = [];
  listError.value = businessError(response.code, 'LIST');
}

async function selectElder(elderId: string) {
  if (elderId === selectedElderId.value || uploadedFileId.value) return;
  selectedElderId.value = elderId;
  resetUploadForm();
  typeFilter.value = 'ALL';
  statusFilter.value = 'ALL';
  await loadFiles();
}

async function loadContext() {
  loading.value = true;
  listError.value = '';
  successMessage.value = '';
  if (isFamily.value) {
    const [profileResponse, bindingResponse] = await Promise.all([
      getFamilyElders(),
      getFamilyBindings()
    ]);
    loading.value = false;
    if (profileResponse.code !== 0 || bindingResponse.code !== 0) {
      profiles.value = [];
      bindings.value = [];
      listError.value = businessError(profileResponse.code !== 0 ? profileResponse.code : bindingResponse.code, 'LIST');
      return;
    }
    profiles.value = profileResponse.data;
    bindings.value = bindingResponse.data.filter((item) => item.bindingStatus === 'ACTIVE');
    const firstVisible = profiles.value.find((profile) => {
      const binding = bindings.value.find((item) => item.elderId === profile.elderId);
      return binding?.scopeCodes.includes('HEALTH_VIEW');
    });
    selectedElderId.value = firstVisible?.elderId ?? profiles.value[0]?.elderId ?? '';
    if (selectedElderId.value) await loadFiles();
    return;
  }

  loading.value = false;
  if (isElder.value && props.authUser?.userId) {
    selectedElderId.value = resolveElderResourceId(props.authUser.userId);
    await loadFiles();
  }
}

function selectOccurredAt(event: { detail: { value: string } }) {
  occurredAt.value = event.detail.value;
}

async function openAuthorizedUrl(url?: string) {
  if (!url) return;
  error.value = '';
  const target = new URL(url, window.location.origin);
  const signatureKeys = [...target.searchParams.keys()].map((key) => key.toLowerCase());
  const isSignedUrl = signatureKeys.some((key) =>
    key.includes('signature') || key.startsWith('x-amz-') || key === 'token'
  );
  if (target.origin !== window.location.origin || isSignedUrl) {
    const opened = window.open(target.toString(), '_blank');
    if (opened) opened.opener = null;
    else error.value = '浏览器未能打开文件，请允许新窗口后重试。';
    return;
  }

  const opened = window.open('about:blank', '_blank');
  if (!opened) {
    error.value = '浏览器未能打开文件，请允许新窗口后重试。';
    return;
  }
  opened.opener = null;
  try {
    const session = readAuthSession();
    const response = await fetch(target.toString(), {
      headers: session ? { Authorization: `Bearer ${session.token}` } : {}
    });
    if (!response.ok) throw new Error('download failed');
    const objectUrl = URL.createObjectURL(await response.blob());
    opened.location.href = objectUrl;
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch {
    opened.close();
    error.value = '文件预览授权已失效，请刷新资料列表后重试。';
  }
}

watch(
  () => props.authUser?.userId,
  (current, previous) => {
    if (current && current !== previous && !selectedElderId.value) loadContext();
  }
);

onMounted(loadContext);
</script>

<template>
  <view class="medical-files-panel" aria-label="病历资料">
    <view class="medical-heading">
      <view>
        <text class="medical-kicker">病历资料</text>
        <text class="medical-title">{{ isFamily ? '上传与审核进度' : '我的病历资料' }}</text>
        <text class="medical-subtitle">
          {{ isFamily ? '上传处方、检查报告、出院小结或既往病历。' : '查看家属已上传的资料及审核结果。' }}
        </text>
      </view>
      <button type="button" class="refresh-command" :disabled="loading || isSubmitting || Boolean(uploadedFileId)" @click="loadContext">刷新</button>
    </view>

    <scroll-view v-if="isFamily && profiles.length" class="elder-selector" scroll-x="true" :show-scrollbar="false">
      <view class="elder-selector-row">
        <button
          v-for="profile in profiles"
          :key="profile.elderId"
          type="button"
          class="elder-choice"
          :class="{ active: selectedElderId === profile.elderId }"
          :disabled="isSubmitting || Boolean(uploadedFileId)"
          @click="selectElder(profile.elderId)"
        >
          <text>{{ profile.name }}</text>
          <text>{{ bindings.some((item) => item.elderId === profile.elderId && item.scopeCodes.includes('HEALTH_EDIT')) ? '可上传' : '仅查看' }}</text>
        </button>
      </view>
    </scroll-view>

    <view v-if="isFamily && selectedElderId" class="upload-section">
      <view class="section-heading">
        <view><text>提交新资料</text><text>资料提交后由平台审核，不会直接写入健康档案。</text></view>
        <text class="person-chip">{{ selectedElderName }}</text>
      </view>

      <template v-if="canUpload">
        <view class="form-block">
          <text class="field-label">资料类别</text>
          <view class="type-grid">
            <button
              v-for="option in fileTypeOptions"
              :key="option.value"
              type="button"
              :class="{ active: fileType === option.value }"
              :disabled="isSubmitting || Boolean(uploadedFileId)"
              @click="fileType = option.value"
            >
              <text>{{ option.label }}</text>
              <text>{{ option.help }}</text>
            </button>
          </view>
        </view>

        <view class="form-block">
          <text class="field-label">选择文件</text>
          <view v-if="selectedFile" class="selected-file">
            <view><text>{{ selectedFile.name }}</text><text>{{ formatFileSize(selectedFile.size) }}</text></view>
            <view class="file-actions">
              <button type="button" :disabled="!canDiscardSelectedMedicalFile(uploadedFileId, isSubmitting)" @click="chooseMedicalFile">重新选择</button>
              <button type="button" class="danger-command" :disabled="!canDiscardSelectedMedicalFile(uploadedFileId, isSubmitting)" @click="removeSelectedFile">移除</button>
            </view>
          </view>
          <button v-else type="button" class="file-picker" :disabled="isSubmitting" @click="chooseMedicalFile">选择 PDF 或图片</button>
          <text v-if="uploadedFileId" class="field-help retry-help">文件已上传，请完成重新登记；为避免重复上传，暂不能更换或移除。</text>
          <text v-else class="field-help">支持 PDF、JPG、PNG，单个文件不超过 {{ maxFileSizeMb }} MB。</text>
        </view>

        <view class="form-grid">
          <label class="medical-field">
            <text>资料标题</text>
            <input v-model="title" maxlength="80" :disabled="isSubmitting" placeholder="例如：2026年7月门诊检查报告" />
          </label>
          <label class="medical-field">
            <text>资料发生日期</text>
            <picker mode="date" start="1900-01-01" :end="today" :value="occurredAt" :disabled="isSubmitting" @change="selectOccurredAt">
              <view class="date-field">{{ occurredAt || '请选择日期' }}</view>
            </picker>
          </label>
        </view>

        <view v-if="flowStage === 'UPLOADING' || uploadProgress > 0" class="progress-block">
          <view><text>{{ flowStage === 'REGISTERING' ? '正在登记资料' : '正在上传文件' }}</text><text>{{ uploadProgress }}%</text></view>
          <view class="progress-track"><view :style="{ width: `${uploadProgress}%` }" /></view>
        </view>

        <view v-if="error" class="inline-error" role="alert">{{ error }}</view>
        <button class="submit-command" type="button" :disabled="isSubmitting" @click="submitMedicalFile">{{ actionLabel }}</button>
      </template>
      <view v-else class="permission-state">
        <text>当前绑定仅可查看病历资料。</text>
        <text>长辈同意健康编辑权限后，家属才能上传新资料。</text>
      </view>
    </view>

    <view v-if="successMessage" class="inline-success" role="status">{{ successMessage }}</view>

    <view class="records-section">
      <view class="section-heading">
        <view><text>{{ isFamily ? `${selectedElderName}的病历资料` : '病历资料' }}</text><text>审核状态以平台最新结果为准。</text></view>
        <text class="record-count">{{ records.length }} 份</text>
      </view>

      <template v-if="records.length">
        <scroll-view class="filter-scroll" scroll-x="true" :show-scrollbar="false">
          <view class="filter-row">
            <button type="button" :class="{ active: typeFilter === 'ALL' }" @click="typeFilter = 'ALL'">全部类型</button>
            <button v-for="option in fileTypeOptions" :key="option.value" type="button" :class="{ active: typeFilter === option.value }" @click="typeFilter = option.value">{{ option.label }}</button>
          </view>
        </scroll-view>
        <scroll-view class="filter-scroll" scroll-x="true" :show-scrollbar="false">
          <view class="filter-row">
            <button type="button" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">全部状态</button>
            <button v-for="option in statusOptions" :key="option.value" type="button" :class="{ active: statusFilter === option.value }" @click="statusFilter = option.value">{{ option.label }}</button>
          </view>
        </scroll-view>
      </template>

      <view v-if="loading" class="list-state"><text>正在读取病历资料...</text></view>
      <view v-else-if="listError" class="list-state error-state">
        <text>{{ listError }}</text>
        <button type="button" @click="loadFiles">重新读取</button>
      </view>
      <view v-else-if="records.length === 0" class="list-state">
        <text class="state-title">暂无病历资料</text>
        <text>{{ isFamily ? '上传并完成登记后，资料和审核状态会显示在这里。' : '家属上传资料后，您可以在这里查看处理进度。' }}</text>
      </view>
      <view v-else-if="filteredRecords.length === 0" class="list-state">
        <text class="state-title">当前筛选下没有资料</text>
        <text>可以切换资料类型或审核状态查看。</text>
      </view>
      <view v-else class="record-list">
        <view v-for="record in filteredRecords" :key="record.medicalFileId" class="record-card">
          <view class="record-heading">
            <view><text>{{ record.title }}</text><text>{{ typeLabel(record.fileType) }}</text></view>
            <text class="status-badge" :class="statusClass(record.auditStatus)">{{ statusLabel(record.auditStatus) }}</text>
          </view>
          <view class="record-meta">
            <text>资料日期：{{ formatDate(record.occurredAt) }}</text>
            <text>上传时间：{{ formatDate(record.uploadedAt) }}</text>
            <text v-if="record.originalFileName">文件名称：{{ record.originalFileName }}</text>
            <text v-if="record.fileSize">文件大小：{{ formatFileSize(record.fileSize) }}</text>
          </view>
          <view v-if="record.auditOpinion" class="audit-opinion">
            <text>处理意见</text><text>{{ record.auditOpinion }}</text>
          </view>
          <view v-if="record.previewUrl || record.downloadUrl" class="record-actions">
            <button v-if="record.previewUrl" type="button" @click="openAuthorizedUrl(record.previewUrl)">预览</button>
            <button v-if="record.downloadUrl" type="button" @click="openAuthorizedUrl(record.downloadUrl)">下载</button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.medical-files-panel { display:grid; gap:20rpx; min-width:0; color:#17312e; }
.medical-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:20rpx; padding:12rpx 4rpx 4rpx; }
.medical-kicker,.medical-title,.medical-subtitle,.elder-choice text,.section-heading text,.field-label,.field-help,.medical-field>text,.selected-file text,.permission-state text,.state-title,.record-heading text,.record-meta text,.audit-opinion text { display:block; }
.medical-kicker { color:#0f766e; font-size:22rpx; font-weight:700; }
.medical-title { margin-top:6rpx; font-size:36rpx; font-weight:800; }
.medical-subtitle { margin-top:8rpx; color:#607671; font-size:25rpx; line-height:1.55; }
.refresh-command,.file-picker,.submit-command,.file-actions button,.list-state button,.record-actions button { min-height:68rpx; padding:0 22rpx; border:1rpx solid #bfd4cf; border-radius:4rpx; background:#fff; color:#176d65; font-size:24rpx; font-weight:750; }
.refresh-command { flex:none; min-height:64rpx; }
button[disabled] { opacity:.48; }
.elder-selector,.filter-scroll { margin:0 -24rpx; width:calc(100% + 48rpx); white-space:nowrap; }
.elder-selector-row,.filter-row { display:flex; gap:12rpx; padding:0 24rpx; }
.elder-choice { display:grid; gap:4rpx; min-width:180rpx; padding:18rpx 20rpx; border:1rpx solid #d8e4e1; border-radius:4rpx; background:#fff; text-align:left; }
.elder-choice.active { border-color:#67bdb4; background:#e8f7f4; }
.elder-choice text:first-child { font-size:27rpx; font-weight:800; }
.elder-choice text:last-child { color:#6e827d; font-size:22rpx; }
.upload-section,.records-section { display:grid; gap:20rpx; padding:24rpx; border:1rpx solid #dce7e4; background:#fff; }
.section-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:18rpx; }
.section-heading>view { display:grid; gap:5rpx; }
.section-heading view text:first-child { font-size:29rpx; font-weight:800; }
.section-heading view text:last-child { color:#6d817c; font-size:22rpx; line-height:1.45; }
.person-chip,.record-count { flex:none; padding:8rpx 12rpx; border:1rpx solid #abd8d1; border-radius:4rpx; background:#eaf7f4; color:#176d65; font-size:22rpx; font-weight:750; }
.form-block,.medical-field { display:grid; gap:10rpx; }
.field-label,.medical-field>text { color:#516964; font-size:23rpx; font-weight:750; }
.type-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10rpx; }
.type-grid button { display:grid; gap:4rpx; min-height:92rpx; padding:14rpx; border:1rpx solid #cbdad6; border-radius:4rpx; background:#fff; text-align:left; }
.type-grid button.active { border-color:#5db6ab; background:#e8f7f4; color:#0f766e; }
.type-grid text:first-child { font-size:25rpx; font-weight:800; }
.type-grid text:last-child { color:#71837f; font-size:20rpx; line-height:1.35; }
.selected-file { display:grid; gap:14rpx; padding:18rpx; border-left:6rpx solid #168c81; background:#eff8f6; }
.selected-file>view:first-child { display:grid; gap:4rpx; min-width:0; }
.selected-file>view:first-child text:first-child { max-width:100%; overflow-wrap:anywhere; color:#17312e; font-size:25rpx; font-weight:800; }
.selected-file>view:first-child text:last-child { color:#6b7f7a; font-size:22rpx; }
.file-actions { display:flex; gap:10rpx; }
.file-actions button { flex:1; min-width:0; }
.file-actions .danger-command { border-color:#ecc4c0; color:#bc3f37; }
.file-picker { width:100%; border-style:dashed; }
.field-help { color:#768783; font-size:21rpx; line-height:1.45; }
.retry-help { color:#9a5b08; font-weight:700; }
.form-grid { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:14rpx; }
.medical-field input,.date-field { width:100%; min-height:76rpx; padding:0 16rpx; box-sizing:border-box; border:1rpx solid #cbdad6; border-radius:4rpx; background:#fff; color:#17312e; font-size:25rpx; font-weight:650; line-height:76rpx; }
.progress-block { display:grid; gap:10rpx; }
.progress-block>view:first-child { display:flex; justify-content:space-between; color:#526c67; font-size:22rpx; font-weight:700; }
.progress-track { height:12rpx; overflow:hidden; background:#dfeae7; }
.progress-track view { height:100%; background:#168c81; transition:width .2s ease; }
.submit-command { width:100%; min-height:82rpx; border-color:#137f75; background:#137f75; color:#fff; font-size:27rpx; }
.permission-state,.list-state { display:grid; gap:10rpx; padding:28rpx 22rpx; border:1rpx dashed #c3d4d0; background:#f8faf9; color:#607671; font-size:24rpx; line-height:1.5; }
.permission-state text:first-child,.state-title { color:#17312e; font-size:27rpx; font-weight:800; }
.inline-error,.inline-success { padding:18rpx 20rpx; border:1rpx solid #efb7b2; background:#fff2f1; color:#a3342e; font-size:23rpx; line-height:1.55; }
.inline-success { border-color:#9fd8cf; background:#eaf8f5; color:#0f766e; }
.filter-row button { flex:none; min-height:58rpx; padding:0 16rpx; border:1rpx solid #cbdad6; border-radius:4rpx; background:#fff; color:#5f746f; font-size:22rpx; font-weight:700; }
.filter-row button.active { border-color:#5db6ab; background:#e8f7f4; color:#0f766e; }
.error-state { border-color:#efb7b2; background:#fff6f5; color:#9d3731; }
.error-state button { justify-self:start; margin-top:4rpx; }
.record-list { display:grid; gap:14rpx; }
.record-card { display:grid; gap:16rpx; padding:20rpx; border:1rpx solid #d8e4e1; background:#fbfcfc; }
.record-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }
.record-heading>view { display:grid; gap:5rpx; min-width:0; }
.record-heading view text:first-child { max-width:100%; overflow-wrap:anywhere; font-size:27rpx; font-weight:800; }
.record-heading view text:last-child { color:#687d78; font-size:22rpx; }
.status-badge { flex:none; padding:8rpx 12rpx; border:1rpx solid; border-radius:4rpx; font-size:22rpx; font-weight:750; }
.status-pending { border-color:#eccd92; background:#fff6e4; color:#916116; }
.status-approved { border-color:#9fd8cf; background:#eaf8f5; color:#0f766e; }
.status-rejected { border-color:#efb7b2; background:#fff1ef; color:#ad3933; }
.status-more { border-color:#afd1eb; background:#edf7ff; color:#286990; }
.record-meta { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8rpx 14rpx; color:#58706b; font-size:22rpx; }
.audit-opinion { display:grid; gap:6rpx; padding:14rpx 16rpx; border-left:5rpx solid #d6a14a; background:#fff8eb; }
.audit-opinion text:first-child { color:#815c1d; font-size:21rpx; font-weight:800; }
.audit-opinion text:last-child { color:#55452b; font-size:23rpx; line-height:1.5; }
.record-actions { display:flex; gap:10rpx; }
.record-actions button { flex:1; }
@media (max-width:390px) {
  .medical-heading { align-items:stretch; }
  .medical-subtitle { max-width:240px; }
  .form-grid,.record-meta { grid-template-columns:1fr; }
  .section-heading { align-items:flex-start; }
}
</style>
