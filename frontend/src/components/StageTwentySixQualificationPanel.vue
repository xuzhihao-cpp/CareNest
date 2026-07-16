<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { readAuthSession } from '@/api/client';
import {
  getCurrentQualificationApplication,
  getQualificationPermissions,
  getQualificationSkillOptions,
  getQualificationTrainingOverview,
  submitQualificationApplication,
  uploadQualificationFile
} from '@/api/stageTwentySix';
import type {
  QualificationApplicationRecord,
  QualificationAuditStatus,
  QualificationFileDraft,
  QualificationSkillOption,
  QualificationTrainingOverview
} from '@/types/stageTwentySix';
import {
  canSubmitQualification,
  canSubmitQualificationByPermission,
  QUALIFICATION_MAX_FILES,
  QUALIFICATION_MAX_FILE_SIZE_BYTES,
  QUALIFICATION_MAX_FILE_SIZE_MB,
  qualificationDateTime,
  qualificationFileSize,
  qualificationNurseStatusLabel,
  qualificationTrainingErrorMessage,
  sanitizeCertificateNo,
  sanitizeMaskedId,
  sanitizeRealName,
  validateQualificationForm
} from '@/utils/stageTwentySixRules';
import {
  canAcceptFormalOrders,
  effectiveTrainingDisplayStatus,
  trainingStatusLabel
} from '@/utils/stageTwentyEightRules';
import {
  MEDICAL_FILE_EXTENSIONS,
  medicalFileExtension,
  validateMedicalFileDescriptor,
  validateMedicalFileSignature
} from '@/utils/stageTwentyRules';
import {
  clearQualificationRetryFiles,
  readQualificationRetryFiles,
  reconcileQualificationRetryFiles,
  writeQualificationRetryFiles
} from '@/utils/stageTwentySixRetry';

const loading = ref(false);
const submitting = ref(false);
const currentVerified = ref(false);
const currentLoadFailed = ref(false);
const permissionReady = ref(false);
const canSubmitByPermission = ref(false);
const current = ref<QualificationApplicationRecord | null>(null);
const training = ref<QualificationTrainingOverview | null>(null);
const skills = ref<QualificationSkillOption[]>([]);
const selectedSkillCodes = ref<string[]>([]);
const files = ref<QualificationFileDraft[]>([]);
const error = ref('');
const notice = ref('');
const trainingError = ref('');
const realName = ref('');
const idNoMasked = ref('');
const certificateNo = ref('');

const currentStatus = computed<QualificationAuditStatus | null>(() => current.value?.auditStatus ?? null);
const canPrepareApplication = computed(() => currentVerified.value && canSubmitQualification(currentStatus.value));
const hasUploadedFiles = computed(() => files.value.some((file) => Boolean(file.uploadedFileId)));
const currentTrainingDisplayStatus = computed(() => training.value
  ? effectiveTrainingDisplayStatus(training.value.trainingStatus, training.value.expiredAt)
  : null);
const formalOrderEligible = computed(() => Boolean(training.value) && canAcceptFormalOrders({
  qualificationStatus: training.value!.qualificationStatus,
  trainingStatus: training.value!.trainingStatus,
  expiredAt: training.value!.expiredAt
}));
const formalOrderEligibilityText = computed(() => {
  if (trainingError.value) return '接单资格暂时无法确认';
  if (currentStatus.value !== 'APPROVED') return '资质尚未通过，暂不可接正式订单';
  if (!training.value || !currentTrainingDisplayStatus.value) return '培训资格尚未生效，暂不可接正式订单';
  if (currentTrainingDisplayStatus.value === 'EXPIRED') return '培训已过期，暂不可接正式订单';
  return formalOrderEligible.value ? '可接正式订单' : '培训资格未通过，暂不可接正式订单';
});
const submitLabel = computed(() => {
  if (submitting.value) return hasUploadedFiles.value ? '正在登记申请' : '正在上传资料';
  if (hasUploadedFiles.value) return '重新登记申请';
  return current.value ? '重新提交资质申请' : '提交资质申请';
});

function skillLabel(code: string) {
  return skills.value.find((item) => item.value === code)?.label || '技能信息待同步';
}

function businessError(code: number, fallback: string) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有提交护理资质的权限。';
  if (code === 409) return '当前已有待审核或已通过的申请，不能重复提交。';
  if (code === 413) return `文件超过 ${QUALIFICATION_MAX_FILE_SIZE_MB} MB，请压缩后重试。`;
  if (code === 415) return '文件格式不支持，请选择 PDF、JPG 或 PNG 文件。';
  if (code === 422) return '申请信息或证明文件不符合要求，请检查后重试。';
  if (code === 502) return fallback;
  return '资质服务暂时不可用，请稍后重试。';
}

function releaseDraft(file: QualificationFileDraft) {
  if (file.objectUrl) URL.revokeObjectURL(file.objectUrl);
}

function currentUserId() {
  return readAuthSession()?.user.userId || 'anonymous';
}

function persistUploadedDrafts() {
  writeQualificationRetryFiles(uni, currentUserId(), files.value);
}

function restoreUploadedDrafts() {
  const restored = readQualificationRetryFiles(uni, currentUserId());
  files.value = restored.map((file, index) => ({
    clientKey: `restored-${index}-${file.fileId}`,
    path: '',
    name: file.name,
    size: file.size,
    mimeType: file.mimeType,
    uploadedFileId: file.fileId,
    progress: 100,
    uploadState: 'UPLOADED',
    uploadError: ''
  }));
}

function clearDrafts(clearPersisted = false) {
  files.value.forEach(releaseDraft);
  files.value = [];
  if (clearPersisted) clearQualificationRetryFiles(uni, currentUserId());
}

function prefillFromCurrent(record: QualificationApplicationRecord) {
  if (!canSubmitQualification(record.auditStatus)) return;
  realName.value = record.realName;
  idNoMasked.value = record.idNoMasked;
  selectedSkillCodes.value = record.serviceSkillCodes.filter((code) => skills.value.some((skill) => skill.value === code));
  certificateNo.value = '';
}

async function loadContext(options?: { preserveNotice?: boolean }) {
  loading.value = true;
  error.value = '';
  if (!options?.preserveNotice) notice.value = '';
  currentVerified.value = false;
  currentLoadFailed.value = false;
  trainingError.value = '';
  const [permissionResponse, skillResponse, currentResponse, trainingResponse] = await Promise.all([
    getQualificationPermissions(),
    getQualificationSkillOptions(),
    getCurrentQualificationApplication(),
    getQualificationTrainingOverview()
  ]);
  loading.value = false;

  permissionReady.value = permissionResponse.code === 0;
  canSubmitByPermission.value = permissionResponse.code === 0
    && canSubmitQualificationByPermission(permissionResponse.data);
  if (permissionResponse.code !== 0) {
    error.value = businessError(permissionResponse.code, '权限信息暂时无法读取，请稍后重试。');
  } else if (!canSubmitByPermission.value) {
    error.value = '当前账号没有提交护理资质的权限。';
  }

  skills.value = skillResponse.code === 0 ? skillResponse.data : [];
  if (skillResponse.code !== 0 && !error.value) {
    error.value = '护理技能字典暂时无法读取，提交入口已关闭。';
  }

  if (currentResponse.code === 0) {
    current.value = currentResponse.data;
    currentVerified.value = true;
    if (reconcileQualificationRetryFiles(
      uni, currentUserId(), currentResponse.data.auditStatus
    )) clearDrafts(false);
    prefillFromCurrent(currentResponse.data);
  } else if (currentResponse.code === 404) {
    current.value = null;
    currentVerified.value = true;
  } else {
    current.value = null;
    currentLoadFailed.value = true;
    if (!error.value) error.value = businessError(currentResponse.code, '当前资质申请内容不完整，请联系平台维护人员。');
  }

  if (trainingResponse.code === 0) {
    training.value = trainingResponse.data;
  } else {
    training.value = null;
    trainingError.value = qualificationTrainingErrorMessage(trainingResponse.code);
  }
}

function refreshContext() {
  void loadContext();
}

function onRealNameInput(event: Event) {
  realName.value = sanitizeRealName((event.target as HTMLInputElement).value);
}

function onMaskedIdInput(event: Event) {
  idNoMasked.value = sanitizeMaskedId((event.target as HTMLInputElement).value);
}

function onCertificateNoInput(event: Event) {
  certificateNo.value = sanitizeCertificateNo((event.target as HTMLInputElement).value);
}

function toggleSkill(code: string) {
  if (submitting.value) return;
  selectedSkillCodes.value = selectedSkillCodes.value.includes(code)
    ? selectedSkillCodes.value.filter((item) => item !== code)
    : [...selectedSkillCodes.value, code];
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

function chooseFiles() {
  if (submitting.value || hasUploadedFiles.value) return;
  const remaining = QUALIFICATION_MAX_FILES - files.value.length;
  if (remaining <= 0) {
    error.value = `资质证明最多上传 ${QUALIFICATION_MAX_FILES} 份。`;
    return;
  }
  uni.chooseFile({
    count: remaining,
    type: 'all',
    extension: [...MEDICAL_FILE_EXTENSIONS],
    async success(result) {
      const candidates = (Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]) as Array<File | {
        path: string;
        name?: string;
        size: number;
        type?: string;
      }>;
      const paths = Array.isArray(result.tempFilePaths) ? result.tempFilePaths : [result.tempFilePaths];
      const accepted: QualificationFileDraft[] = [];
      for (let index = 0; index < candidates.length; index += 1) {
        const candidate = candidates[index];
        const rawFile = typeof File !== 'undefined' && candidate instanceof File ? candidate : undefined;
        const value = candidate as { path?: string; name?: string; size: number; type?: string };
        const objectUrl = !paths[index] && !value.path && rawFile ? URL.createObjectURL(rawFile) : undefined;
        const path = paths[index] || value.path || objectUrl || '';
        const name = rawFile?.name || value.name || path.split('/').pop() || '资质证明';
        const size = rawFile?.size ?? value.size;
        const mimeType = rawFile?.type || value.type || '';
        const descriptorError = validateMedicalFileDescriptor(
          { name, size, mimeType },
          QUALIFICATION_MAX_FILE_SIZE_BYTES,
          QUALIFICATION_MAX_FILE_SIZE_MB
        );
        if (descriptorError) {
          if (objectUrl) URL.revokeObjectURL(objectUrl);
          error.value = descriptorError;
          accepted.forEach(releaseDraft);
          return;
        }
        try {
          const signatureError = validateMedicalFileSignature(
            medicalFileExtension(name),
            await readFileHeader(rawFile, path)
          );
          if (signatureError) throw new Error(signatureError);
        } catch (reason) {
          if (objectUrl) URL.revokeObjectURL(objectUrl);
          error.value = reason instanceof Error && reason.message.includes('文件内容')
            ? reason.message
            : '无法读取文件内容，请重新选择原始文件。';
          accepted.forEach(releaseDraft);
          return;
        }
        accepted.push({
          clientKey: `${Date.now()}-${index}-${name}`,
          path,
          name,
          size,
          mimeType,
          rawFile,
          objectUrl,
          uploadedFileId: '',
          progress: 0,
          uploadState: 'READY',
          uploadError: ''
        });
      }
      files.value = [...files.value, ...accepted];
      error.value = '';
    },
    fail(result) {
      if (!result.errMsg.toLowerCase().includes('cancel')) error.value = '暂时无法选择文件，请稍后重试。';
    }
  });
}

function removeFile(clientKey: string) {
  const file = files.value.find((item) => item.clientKey === clientKey);
  if (!file || file.uploadedFileId || submitting.value) return;
  releaseDraft(file);
  files.value = files.value.filter((item) => item.clientKey !== clientKey);
}

async function submitApplication() {
  if (!permissionReady.value || !canSubmitByPermission.value || !canPrepareApplication.value) return;
  const validationError = validateQualificationForm({
    realName: realName.value,
    idNoMasked: idNoMasked.value,
    certificateNo: certificateNo.value,
    serviceSkillCodes: selectedSkillCodes.value,
    availableSkillCodes: skills.value.map((item) => item.value),
    files: files.value
  });
  if (validationError) {
    error.value = validationError;
    return;
  }

  submitting.value = true;
  error.value = '';
  notice.value = '';
  for (const file of files.value) {
    if (file.uploadedFileId) continue;
    file.uploadState = 'UPLOADING';
    file.uploadError = '';
    const response = await uploadQualificationFile(file.path, (progress) => {
      file.progress = progress;
    });
    if (response.code !== 0 || !response.data.fileId?.trim()) {
      file.uploadState = 'FAILED';
      file.uploadError = businessError(response.code, '证明文件上传失败，请检查网络后重试。');
      error.value = '部分证明文件上传失败。已上传的文件会被保留，重试时不会重复上传。';
      submitting.value = false;
      return;
    }
    file.uploadedFileId = response.data.fileId.trim();
    file.progress = 100;
    file.uploadState = 'UPLOADED';
    persistUploadedDrafts();
  }

  const response = await submitQualificationApplication({
    realName: realName.value.trim(),
    idNoMasked: idNoMasked.value,
    certificateNo: certificateNo.value,
    certificateFileIds: files.value.map((file) => file.uploadedFileId),
    serviceSkillCodes: Array.from(new Set(selectedSkillCodes.value))
  });
  submitting.value = false;
  if (response.code !== 0) {
    const message = `证明文件已上传，但申请登记未完成。${businessError(response.code, '申请登记响应不完整，请稍后重试。')}请直接点击“重新登记申请”，不要重复选择文件。`;
    if (response.code === 409) {
      await loadContext();
      if (current.value && !canSubmitQualification(current.value.auditStatus)) clearDrafts(true);
    }
    error.value = message;
    return;
  }

  clearDrafts(true);
  certificateNo.value = '';
  notice.value = `资质申请已提交，当前状态为${qualificationNurseStatusLabel(response.data.auditStatus)}。`;
  await loadContext({ preserveNotice: true });
}

onMounted(() => {
  restoreUploadedDrafts();
  void loadContext();
});
onUnmounted(() => files.value.forEach(releaseDraft));
</script>

<template>
  <view class="qualification-panel">
    <view class="panel-heading">
      <view><text class="panel-kicker">护理准入</text><text class="panel-title">准入资格</text><text class="panel-subtitle">提交真实资质材料，查看审核和培训概况。</text></view>
      <button class="refresh-command" type="button" :disabled="loading || submitting" @click="refreshContext">刷新</button>
    </view>

    <view v-if="notice" class="message success">{{ notice }}</view>
    <view v-if="error" class="message error">{{ error }}</view>

    <view class="overview-grid">
      <view class="overview-card">
        <view class="card-heading"><text>资质状态</text><text v-if="current" class="status-chip" :class="`status-${current.auditStatus.toLowerCase()}`">{{ qualificationNurseStatusLabel(current.auditStatus) }}</text></view>
        <template v-if="current">
          <text class="overview-name">{{ current.realName }}</text>
          <text class="overview-line">证件信息：{{ current.idNoMasked }}</text>
          <text class="overview-line">证书信息：{{ current.certificateNoMasked }}</text>
          <text class="overview-line">提交时间：{{ qualificationDateTime(current.submittedAt) }}</text>
          <view class="tag-row"><text v-for="code in current.serviceSkillCodes" :key="code" class="skill-tag">{{ skillLabel(code) }}</text></view>
          <view v-if="current.reviewComment" class="review-comment"><text>审核意见</text><text>{{ current.reviewComment }}</text></view>
          <view class="proof-list"><view v-for="file in current.certificateFiles" :key="file.fileId" class="proof-row"><view><text>{{ file.originalName }}</text><text>{{ file.mimeType }} · {{ qualificationFileSize(file.size) }}</text></view></view></view>
        </template>
        <view v-else-if="currentVerified" class="empty-state">尚未提交资质申请。</view>
        <view v-else-if="currentLoadFailed" class="empty-state">当前资质状态暂时无法读取，请稍后刷新。</view>
        <view v-else class="empty-state">正在读取资质状态...</view>
      </view>

      <view class="overview-card training-card">
        <view class="card-heading"><text>培训概况</text><text v-if="currentTrainingDisplayStatus" class="training-status" :class="{ expired: currentTrainingDisplayStatus === 'EXPIRED' }">{{ trainingStatusLabel(currentTrainingDisplayStatus) }}</text></view>
        <template v-if="training">
          <text v-if="training.trainingBatch" class="overview-line">培训批次：{{ training.trainingBatch }}</text>
          <text v-if="training.passedAt" class="overview-line">通过时间：{{ qualificationDateTime(training.passedAt) }}</text>
          <text v-if="training.expiredAt" class="overview-line">有效期至：{{ qualificationDateTime(training.expiredAt) }}</text>
          <text v-if="training.remark" class="overview-line">说明：{{ training.remark }}</text>
        </template>
        <view v-else-if="trainingError" class="training-error">{{ trainingError }}</view>
        <view v-else class="empty-state">尚无培训记录。</view>
        <view class="order-eligibility" :class="{ eligible: formalOrderEligible }"><text>正式订单资格</text><strong>{{ formalOrderEligibilityText }}</strong></view>
      </view>
    </view>

    <view v-if="canPrepareApplication" class="application-form">
      <view class="form-heading"><text>{{ current ? '重新整理资质材料' : '填写资质申请' }}</text><text>带 * 的内容为必填项</text></view>
      <label class="field"><text>真实姓名 *</text><input :value="realName" maxlength="32" placeholder="与证件姓名保持一致" @input="onRealNameInput" /></label>
      <label class="field"><text>脱敏证件号 *</text><input :value="idNoMasked" maxlength="18" placeholder="例如：**************1234" @input="onMaskedIdInput" /><small>仅填写 14 个星号和证件号后 4 位，不要输入完整证件号。</small></label>
      <label class="field"><text>护理证书号 *</text><input :value="certificateNo" maxlength="40" placeholder="填写证书上的编号" @input="onCertificateNoInput" /></label>

      <view class="field"><text>护理技能 *</text><view v-if="skills.length" class="skill-options"><button v-for="skill in skills" :key="skill.value" type="button" :class="{ selected: selectedSkillCodes.includes(skill.value) }" :disabled="submitting" @click="toggleSkill(skill.value)">{{ skill.label }}</button></view><view v-else class="inline-empty">护理技能暂不可选，请稍后刷新。</view></view>

      <view class="field">
        <view class="file-heading"><view><text>资质证明 *</text><small>支持 PDF、JPG、PNG，单份不超过 20 MB，最多 3 份。</small></view><button type="button" :disabled="submitting || hasUploadedFiles || files.length >= QUALIFICATION_MAX_FILES" @click="chooseFiles">选择文件</button></view>
        <view v-if="files.length" class="draft-list">
          <view v-for="file in files" :key="file.clientKey" class="draft-row">
            <view class="draft-main"><text>{{ file.name }}</text><text>{{ file.mimeType }} · {{ qualificationFileSize(file.size) }}</text><view v-if="file.uploadState === 'UPLOADING'" class="progress-track"><view :style="{ width: `${file.progress}%` }" /></view><text v-if="file.uploadState === 'UPLOADED'" class="upload-ok">文件已上传，等待登记申请</text><text v-if="file.uploadError" class="upload-error">{{ file.uploadError }}</text></view>
            <button type="button" class="remove-command" :disabled="submitting || Boolean(file.uploadedFileId)" @click="removeFile(file.clientKey)">移除</button>
          </view>
        </view>
        <view v-else class="inline-empty">尚未选择资质证明。</view>
        <text v-if="hasUploadedFiles" class="retry-tip">文件已上传。为避免重复上传，登记完成前不能更换或移除。</text>
      </view>

      <button class="submit-command" type="button" :disabled="submitting || loading || !canSubmitByPermission || !skills.length" @click="submitApplication">{{ submitLabel }}</button>
    </view>

    <view v-else-if="currentVerified && current" class="locked-state">{{ current.auditStatus === 'APPROVED' ? '资质已通过，无需重复提交。' : '申请正在审核中，请等待审核结果。' }}</view>
  </view>
</template>

<style scoped>
.qualification-panel { display:grid; gap:20rpx; }.panel-heading,.card-heading,.file-heading,.draft-row { display:flex; align-items:center; justify-content:space-between; gap:16rpx; }.panel-kicker,.panel-title,.panel-subtitle,.overview-name,.overview-line,.review-comment text,.proof-row text,.field > text,.field small,.form-heading text,.draft-main text { display:block; }.panel-kicker { color:#16847a; font-size:20rpx; font-weight:700; letter-spacing:2rpx; }.panel-title { margin-top:6rpx; color:#17332f; font-size:38rpx; font-weight:800; }.panel-subtitle { margin-top:8rpx; color:#6a7e79; font-size:23rpx; }.refresh-command,.file-heading button,.remove-command { display:inline-flex; align-items:center; justify-content:center; box-sizing:border-box; min-height:72rpx; margin:0; padding:0 22rpx; border:1rpx solid #b9d1cc; border-radius:6rpx; background:#fff; color:#126f66; font-size:24rpx; line-height:1.2; }.message { padding:18rpx 20rpx; border-radius:8rpx; font-size:24rpx; }.message.success { background:#e6f7f1; color:#0b6259; }.message.error { border:1rpx solid #efb5b0; background:#fff1ef; color:#a73831; }.overview-grid { display:grid; gap:16rpx; }.overview-card,.application-form,.locked-state { padding:24rpx; border:1rpx solid #dbe7e4; border-radius:10rpx; background:#fff; box-shadow:0 4rpx 14rpx rgba(23,55,52,.05); }.card-heading { margin-bottom:18rpx; color:#274b45; font-size:27rpx; font-weight:800; }.status-chip,.training-status,.skill-tag { padding:6rpx 13rpx; border-radius:999rpx; font-size:21rpx; font-weight:700; }.status-pending,.status-need_more { background:#fff2d9; color:#946200; }.status-approved { background:#dcf4ed; color:#0a7164; }.status-rejected { background:#fde9e7; color:#a33b34; }.training-status { background:#edf4f2; color:#476863; }.overview-name { margin-bottom:12rpx; font-size:31rpx; font-weight:800; }.overview-line { margin-top:9rpx; color:#607671; font-size:24rpx; }.tag-row { display:flex; flex-wrap:wrap; gap:10rpx; margin-top:16rpx; }.skill-tag { background:#eaf5f2; color:#176f66; }.review-comment { display:grid; gap:7rpx; margin-top:18rpx; padding:16rpx; border-left:5rpx solid #d49a32; background:#fff8e9; color:#765113; font-size:23rpx; }.review-comment text:first-child { font-weight:800; }.proof-list { display:grid; gap:10rpx; margin-top:18rpx; }.proof-row { padding:14rpx 16rpx; border:1rpx solid #e0e9e7; border-radius:7rpx; background:#fbfdfc; }.proof-row text:first-child { color:#28433e; font-size:24rpx; font-weight:700; overflow-wrap:anywhere; }.proof-row text:last-child { margin-top:5rpx; color:#758580; font-size:21rpx; }.empty-state,.inline-empty,.locked-state { color:#6c807b; font-size:24rpx; }.training-card { box-shadow:none; background:#f8fbfa; }.form-heading { display:flex; align-items:baseline; justify-content:space-between; gap:12rpx; margin-bottom:24rpx; }.form-heading text:first-child { color:#1d3b36; font-size:30rpx; font-weight:800; }.form-heading text:last-child { color:#7b8d88; font-size:21rpx; }.field { display:block; margin-bottom:24rpx; }.field > text,.file-heading > view > text { margin-bottom:10rpx; color:#294640; font-size:25rpx; font-weight:700; }.field input { box-sizing:border-box; width:100%; min-height:84rpx; padding:0 18rpx; border:1rpx solid #ccdcd8; border-radius:8rpx; background:#fbfdfc; color:#17312d; font-size:26rpx; }.field small,.file-heading small { margin-top:8rpx; color:#7a8c87; font-size:21rpx; line-height:1.5; }.skill-options { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12rpx; }.skill-options button { display:flex; align-items:center; justify-content:center; min-height:80rpx; margin:0; padding:0 12rpx; border:1rpx solid #c8d9d5; border-radius:7rpx; background:#fff; color:#526b66; font-size:24rpx; line-height:1.2; }.skill-options button.selected { border-color:#4cae9f; background:#e4f5f1; color:#0d7268; font-weight:700; }.draft-list { display:grid; gap:12rpx; margin-top:16rpx; }.draft-row { align-items:flex-start; padding:16rpx; border:1rpx solid #dce7e4; border-radius:8rpx; background:#fbfdfc; }.draft-main { min-width:0; flex:1; }.draft-main text:first-child { color:#24423d; font-size:24rpx; font-weight:700; overflow-wrap:anywhere; }.draft-main text:nth-child(2) { margin-top:5rpx; color:#738681; font-size:21rpx; }.remove-command { flex:none; min-height:64rpx; color:#b33c34; border-color:#edc4bf; }.progress-track { height:8rpx; margin-top:12rpx; overflow:hidden; border-radius:999rpx; background:#dfe9e7; }.progress-track view { height:100%; background:#16968a; }.upload-ok,.retry-tip { margin-top:9rpx; color:#0c7569 !important; font-size:21rpx !important; }.upload-error { margin-top:9rpx; color:#b43c34 !important; font-size:21rpx !important; }.retry-tip { display:block; line-height:1.5; }.submit-command { display:flex; align-items:center; justify-content:center; width:100%; min-height:88rpx; margin:4rpx 0 0; border:0; border-radius:8rpx; background:#0f766e; color:#fff; font-size:27rpx; font-weight:800; line-height:1.2; }.locked-state { text-align:center; background:#f2f7f6; box-shadow:none; }.refresh-command[disabled],.submit-command[disabled],.file-heading button[disabled],.remove-command[disabled] { opacity:.48; }
.training-error { padding:16rpx; border:1rpx solid #efb5b0; border-radius:7rpx; background:#fff1ef; color:#a73831; font-size:23rpx; line-height:1.5; }
.training-status.expired { background:#fff0d8; color:#8b5b05; }.order-eligibility { display:grid; gap:7rpx; margin-top:18rpx; padding:16rpx; border-left:5rpx solid #c28a29; background:#fff8e9; color:#755113; }.order-eligibility.eligible { border-left-color:#219486; background:#e8f7f3; color:#0b665c; }.order-eligibility text { font-size:21rpx; font-weight:700; }.order-eligibility strong { font-size:25rpx; }
</style>
