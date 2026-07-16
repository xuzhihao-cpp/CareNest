<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  getQualificationApplications,
  getQualificationCertificatePreview,
  getQualificationPermissions,
  getQualificationSkillOptions
} from '@/api/stageTwentySix';
import {
  findReviewedQualificationApplication,
  reviewQualificationApplication
} from '@/api/stageTwentySeven';
import type {
  QualificationApplicationRecord,
  QualificationSkillOption,
  QualificationStatusFilter
} from '@/types/stageTwentySix';
import type {
  CompletedQualificationReview,
  QualificationReviewDecision
} from '@/types/stageTwentySeven';
import {
  canReviewQualificationByPermission,
  qualificationDateTime,
  qualificationFileSize,
  qualificationSkillDictionaryErrorMessage,
  qualificationStatusLabel
} from '@/utils/stageTwentySixRules';
import {
  QUALIFICATION_REVIEW_COMMENT_MAX_LENGTH,
  areQualificationSkillsResolved,
  canReviewQualificationStatus,
  qualificationCertificatePreviewErrorMessage,
  qualificationReviewErrorMessage,
  validateQualificationReview
} from '@/utils/stageTwentySevenRules';

const filters: Array<{ value: QualificationStatusFilter; label: string }> = [
  { value: 'PENDING', label: '待审核' },
  { value: 'ALL', label: '全部状态' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'NEED_MORE', label: '需补充' }
];
const decisions: Array<{ value: QualificationReviewDecision; label: string }> = [
  { value: 'APPROVED', label: '通过' },
  { value: 'REJECTED', label: '驳回' },
  { value: 'NEED_MORE', label: '需补充' }
];

const loading = ref(false);
const allowed = ref(false);
const records = ref<QualificationApplicationRecord[]>([]);
const skills = ref<QualificationSkillOption[]>([]);
const statusFilter = ref<QualificationStatusFilter>('PENDING');
const page = ref(1);
const size = 10;
const total = ref(0);
const selectedApplicationId = ref('');
const decision = ref<QualificationReviewDecision | ''>('');
const reviewComment = ref('');
const skillsReviewed = ref(false);
const reviewedFileIds = ref<string[]>([]);
const error = ref('');
const skillDictionaryError = ref('');
const completedReview = ref<CompletedQualificationReview | null>(null);
const submittingApplicationId = ref('');
const previewingFileId = ref('');
let listRequestSequence = 0;
let reviewRequestSequence = 0;
let contextRequestSequence = 0;
let previewController: AbortController | null = null;
let pendingPreviewWindow: Window | null = null;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)));
const selected = computed(() => records.value.find(
  (item) => item.applicationId === selectedApplicationId.value
) ?? null);
const allFilesReviewed = computed(() => Boolean(selected.value)
  && selected.value!.certificateFiles.length > 0
  && selected.value!.certificateFiles.every((file) => reviewedFileIds.value.includes(file.fileId)));
const selectedSkillsResolved = computed(() => Boolean(selected.value)
  && areQualificationSkillsResolved(
    selected.value!.serviceSkillCodes,
    skills.value,
    !skillDictionaryError.value
  ));
const allMaterialsReviewed = computed(() => Boolean(selected.value)
  && selectedSkillsResolved.value
  && skillsReviewed.value
  && allFilesReviewed.value);
const canSubmit = computed(() => Boolean(selected.value)
  && canReviewQualificationStatus(selected.value!.auditStatus)
  && !submittingApplicationId.value);

function readableListError(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有查看护理资质申请的权限。';
  if (code === 502) return '护理资质申请数据不完整，请等待服务更新后重试。';
  return '护理资质申请暂时无法读取，请稍后重试。';
}

function skillLabel(code: string) {
  return skills.value.find((item) => item.value === code)?.label
    || (skillDictionaryError.value ? '技能名称暂不可用' : '技能名称待同步');
}

function fileTypeLabel(mimeType: string) {
  if (mimeType === 'application/pdf') return 'PDF 文件';
  if (mimeType === 'image/jpeg') return '图片文件';
  if (mimeType === 'image/png') return '图片文件';
  return '证明文件';
}

function resetReviewForm() {
  decision.value = '';
  reviewComment.value = '';
  skillsReviewed.value = false;
  reviewedFileIds.value = [];
  error.value = '';
}

function cancelPreview() {
  previewController?.abort();
  previewController = null;
  if (pendingPreviewWindow && !pendingPreviewWindow.closed) pendingPreviewWindow.close();
  pendingPreviewWindow = null;
  previewingFileId.value = '';
}

function selectApplication(record: QualificationApplicationRecord) {
  if (submittingApplicationId.value === record.applicationId) return;
  cancelPreview();
  selectedApplicationId.value = record.applicationId;
  completedReview.value = null;
  resetReviewForm();
}

async function loadApplications() {
  if (!allowed.value) return;
  const sequence = ++listRequestSequence;
  loading.value = true;
  error.value = '';
  const response = await getQualificationApplications({
    auditStatus: statusFilter.value,
    page: page.value,
    size
  });
  if (sequence !== listRequestSequence) return;
  loading.value = false;
  if (response.code !== 0) {
    records.value = [];
    total.value = 0;
    selectedApplicationId.value = '';
    resetReviewForm();
    error.value = readableListError(response.code);
    return;
  }
  records.value = response.data.records;
  total.value = response.data.total;
  if (selectedApplicationId.value
      && !records.value.some((item) => item.applicationId === selectedApplicationId.value)) {
    selectedApplicationId.value = '';
    resetReviewForm();
  }
}

async function initialize() {
  const sequence = ++contextRequestSequence;
  loading.value = true;
  error.value = '';
  const [permissionResponse, skillResponse] = await Promise.all([
    getQualificationPermissions(),
    getQualificationSkillOptions()
  ]);
  if (sequence !== contextRequestSequence) return;
  loading.value = false;
  if (permissionResponse.code !== 0) {
    error.value = readableListError(permissionResponse.code);
    return;
  }
  allowed.value = canReviewQualificationByPermission(permissionResponse.data);
  if (!allowed.value) {
    error.value = '当前账号没有审核护理资质的权限。';
    return;
  }
  skills.value = skillResponse.code === 0 ? skillResponse.data : [];
  skillDictionaryError.value = qualificationSkillDictionaryErrorMessage(skillResponse.code);
  await loadApplications();
}

async function refreshWorkbench() {
  if (!allowed.value || submittingApplicationId.value) return;
  const sequence = ++contextRequestSequence;
  loading.value = true;
  const skillResponse = await getQualificationSkillOptions();
  if (sequence !== contextRequestSequence) return;
  loading.value = false;
  skills.value = skillResponse.code === 0 ? skillResponse.data : [];
  skillDictionaryError.value = qualificationSkillDictionaryErrorMessage(skillResponse.code);
  skillsReviewed.value = false;
  await loadApplications();
}

async function changeFilter(value: QualificationStatusFilter) {
  if (submittingApplicationId.value) return;
  cancelPreview();
  statusFilter.value = value;
  page.value = 1;
  selectedApplicationId.value = '';
  completedReview.value = null;
  resetReviewForm();
  await loadApplications();
}

async function changePage(offset: number) {
  if (submittingApplicationId.value) return;
  const nextPage = page.value + offset;
  if (nextPage < 1 || nextPage > totalPages.value) return;
  cancelPreview();
  page.value = nextPage;
  selectedApplicationId.value = '';
  resetReviewForm();
  await loadApplications();
}

function markFileReviewed(fileId: string) {
  if (!reviewedFileIds.value.includes(fileId)) {
    reviewedFileIds.value = [...reviewedFileIds.value, fileId];
  }
}

async function previewCertificate(applicationId: string, fileId: string) {
  if (previewingFileId.value || selectedApplicationId.value !== applicationId) return;
  const opened = window.open('about:blank', '_blank');
  if (!opened) {
    error.value = '浏览器未能打开证明材料，请允许新窗口后重试。';
    return;
  }
  opened.opener = null;
  cancelPreview();
  previewController = new AbortController();
  pendingPreviewWindow = opened;
  previewingFileId.value = fileId;
  const response = await getQualificationCertificatePreview(
    applicationId, fileId, previewController.signal
  );
  if (selectedApplicationId.value !== applicationId) {
    opened.close();
    return;
  }
  if (response.code !== 0 || !response.blob) {
    opened.close();
    error.value = qualificationCertificatePreviewErrorMessage(response.code);
  } else {
    const objectUrl = URL.createObjectURL(response.blob);
    opened.location.href = objectUrl;
    pendingPreviewWindow = null;
    markFileReviewed(fileId);
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  }
  previewController = null;
  pendingPreviewWindow = null;
  previewingFileId.value = '';
}

async function submitReview() {
  const record = selected.value;
  if (!record || submittingApplicationId.value) return;
  const validationError = validateQualificationReview({
    applicationStatus: record.auditStatus,
    decision: decision.value,
    reviewComment: reviewComment.value,
    allMaterialsReviewed: allMaterialsReviewed.value
  });
  if (validationError) {
    error.value = validationError;
    return;
  }

  const applicationId = record.applicationId;
  const nurseName = record.nurseName;
  const targetDecision = decision.value as QualificationReviewDecision;
  const sequence = ++reviewRequestSequence;
  submittingApplicationId.value = applicationId;
  error.value = '';
  const response = await reviewQualificationApplication(applicationId, {
    auditStatus: targetDecision,
    reviewComment: reviewComment.value.trim()
  });
  if (sequence !== reviewRequestSequence) return;

  if (response.code !== 0) {
    const responseError = qualificationReviewErrorMessage(response.code);
    if (response.code === 409) {
      await loadApplications();
      if (sequence !== reviewRequestSequence) return;
      error.value = responseError;
    } else if (selectedApplicationId.value === applicationId) {
      error.value = responseError;
    }
    submittingApplicationId.value = '';
    return;
  }
  if (response.data.qualificationStatus !== targetDecision) {
    if (selectedApplicationId.value === applicationId) {
      error.value = '审核结果与提交决定不一致，请刷新列表确认最终状态。';
    }
    submittingApplicationId.value = '';
    return;
  }

  const latestResponse = await findReviewedQualificationApplication(
    applicationId,
    targetDecision,
    () => sequence === reviewRequestSequence
  );
  if (sequence !== reviewRequestSequence) return;
  if (latestResponse.code !== 0) {
    await loadApplications();
    if (sequence !== reviewRequestSequence) return;
    error.value = latestResponse.code === 404
      ? '审核请求已受理，但未能在最新列表中找到该申请，请刷新后确认最终状态。'
      : '审核请求已受理，但最新审核结果暂时无法读取，请刷新后确认。';
    submittingApplicationId.value = '';
    return;
  }
  if (selectedApplicationId.value === applicationId) {
    selectedApplicationId.value = '';
    resetReviewForm();
  }
  await loadApplications();
  if (sequence !== reviewRequestSequence) return;
  completedReview.value = {
    applicationId,
    nurseName,
    decision: targetDecision,
    record: latestResponse.data
  };
  submittingApplicationId.value = '';
}

onMounted(initialize);
onBeforeUnmount(() => {
  ++contextRequestSequence;
  ++listRequestSequence;
  ++reviewRequestSequence;
  cancelPreview();
});
</script>

<template>
  <section class="qualification-review-workbench">
    <header class="workbench-header">
      <view><text class="kicker">护理准入</text><text class="title">资质审核工作台</text><text class="subtitle">从真实申请列表选择护理人员，核对材料后提交审核决定。</text></view>
      <button type="button" :disabled="loading || !allowed || Boolean(submittingApplicationId)" @click="refreshWorkbench">刷新</button>
    </header>

    <nav class="filter-bar" aria-label="申请状态筛选">
      <button v-for="item in filters" :key="item.value" type="button" :class="{ active: statusFilter === item.value }" :disabled="loading || !allowed || Boolean(submittingApplicationId)" @click="changeFilter(item.value)">{{ item.label }}</button>
    </nav>
    <view v-if="skillDictionaryError" class="message warning">{{ skillDictionaryError }}</view>
    <view v-if="error" class="message error">{{ error }}</view>
    <view v-if="completedReview" class="message success"><strong>{{ completedReview.nurseName }}的资质审核已完成</strong><text>最新状态：{{ qualificationStatusLabel(completedReview.record.auditStatus) }} · {{ qualificationDateTime(completedReview.record.reviewedAt) }}</text><text>请从左侧选择下一份申请。</text></view>

    <view class="workbench-grid">
      <aside class="application-pane">
        <view class="pane-heading"><strong>申请列表</strong><text>{{ total }} 份</text></view>
        <view v-if="loading" class="pane-empty">正在读取申请...</view>
        <view v-else-if="!records.length" class="pane-empty">当前筛选条件下暂无申请。</view>
        <view v-else class="application-list">
          <button v-for="record in records" :key="record.applicationId" type="button" class="application-item" :class="{ selected: selectedApplicationId === record.applicationId }" :disabled="Boolean(submittingApplicationId)" @click="selectApplication(record)">
            <view><strong>{{ record.nurseName }}</strong><text>{{ qualificationDateTime(record.submittedAt) }} 提交</text></view>
            <text class="status-chip" :class="`status-${record.auditStatus.toLowerCase()}`">{{ qualificationStatusLabel(record.auditStatus) }}</text>
          </button>
        </view>
        <view v-if="totalPages > 1" class="pagination"><button type="button" :disabled="loading || Boolean(submittingApplicationId) || page <= 1" @click="changePage(-1)">上一页</button><text>{{ page }} / {{ totalPages }}</text><button type="button" :disabled="loading || Boolean(submittingApplicationId) || page >= totalPages" @click="changePage(1)">下一页</button></view>
      </aside>

      <main class="detail-pane">
        <template v-if="selected">
          <view class="pane-heading"><strong>申请资料</strong><text>{{ qualificationStatusLabel(selected.auditStatus) }}</text></view>
          <view class="identity-summary"><view><text>申请人</text><strong>{{ selected.nurseName }}</strong></view><view><text>证件姓名</text><strong>{{ selected.realName }}</strong></view><view><text>脱敏证件号</text><strong>{{ selected.idNoMasked }}</strong></view><view><text>护理证书</text><strong>{{ selected.certificateNoMasked }}</strong></view></view>

          <section class="review-section">
            <view class="section-heading"><strong>护理技能</strong><button type="button" :class="{ confirmed: skillsReviewed }" :disabled="!selectedSkillsResolved || Boolean(submittingApplicationId)" @click="skillsReviewed = !skillsReviewed">{{ skillsReviewed ? '已核对' : '确认已核对' }}</button></view>
            <view class="tag-row"><text v-for="code in selected.serviceSkillCodes" :key="code" class="skill-tag">{{ skillLabel(code) }}</text></view>
            <view v-if="!selectedSkillsResolved" class="material-warning">护理技能暂时无法完整核对，恢复技能名称后才能提交审核。</view>
          </section>

          <section class="review-section">
            <view class="section-heading"><strong>证明材料</strong><text>{{ reviewedFileIds.length }} / {{ selected.certificateFiles.length }} 已核对</text></view>
            <view class="proof-list">
              <article v-for="file in selected.certificateFiles" :key="file.fileId" class="proof-item">
                <view><strong>{{ file.originalName }}</strong><text>{{ fileTypeLabel(file.mimeType) }} · {{ qualificationFileSize(file.size) }}</text></view>
                <button v-if="file.previewable" type="button" :class="{ confirmed: reviewedFileIds.includes(file.fileId) }" :disabled="Boolean(previewingFileId)" @click="previewCertificate(selected.applicationId, file.fileId)">{{ previewingFileId === file.fileId ? '正在打开' : reviewedFileIds.includes(file.fileId) ? '已查看' : '查看证明' }}</button>
                <button v-else type="button" :class="{ confirmed: reviewedFileIds.includes(file.fileId) }" @click="markFileReviewed(file.fileId)">{{ reviewedFileIds.includes(file.fileId) ? '已线下核对' : '标记线下核对' }}</button>
              </article>
            </view>
          </section>

          <view v-if="selected.reviewComment" class="previous-comment"><text>历史审核说明</text><strong>{{ selected.reviewComment }}</strong></view>
        </template>
        <view v-else class="pane-empty spacious">从左侧选择一份申请查看详细材料。</view>
      </main>

      <aside class="decision-pane">
        <template v-if="selected">
          <view class="pane-heading"><strong>审核决定</strong><text v-if="allMaterialsReviewed">材料已核对</text><text v-else>请先核对材料</text></view>
          <view v-if="!canReviewQualificationStatus(selected.auditStatus)" class="readonly-note">该申请已经处理，仅供查看，不能重复审核。</view>
          <template v-else>
            <view class="decision-options"><button v-for="item in decisions" :key="item.value" type="button" :class="['decision-' + item.value.toLowerCase(), { selected: decision === item.value }]" :disabled="Boolean(submittingApplicationId)" @click="decision = item.value">{{ item.label }}</button></view>
            <label class="comment-field"><text>审核意见{{ decision === 'REJECTED' || decision === 'NEED_MORE' ? ' *' : '（选填）' }}</text><textarea v-model="reviewComment" :maxlength="QUALIFICATION_REVIEW_COMMENT_MAX_LENGTH" placeholder="说明审核依据，驳回或要求补充时请写明具体原因" /><small>{{ reviewComment.length }} / {{ QUALIFICATION_REVIEW_COMMENT_MAX_LENGTH }}</small></label>
            <button class="submit-command" type="button" :disabled="!canSubmit" @click="submitReview">{{ submittingApplicationId === selected.applicationId ? '正在提交审核' : '提交审核决定' }}</button>
          </template>
        </template>
        <view v-else-if="completedReview" class="completed-card"><strong>本次审核已完成</strong><text>{{ completedReview.nurseName }}</text><text>{{ qualificationStatusLabel(completedReview.record.auditStatus) }}</text><text>{{ qualificationDateTime(completedReview.record.reviewedAt) }}</text></view>
        <view v-else class="pane-empty spacious">选择申请后在这里填写审核决定。</view>
      </aside>
    </view>
  </section>
</template>

<style scoped>
.qualification-review-workbench { display:grid; gap:16px; color:#183530; }.workbench-header,.pane-heading,.section-heading,.pagination { display:flex; align-items:center; justify-content:space-between; gap:12px; }.workbench-header > view > text,.application-item text,.identity-summary text,.identity-summary strong,.proof-item text,.proof-item strong,.previous-comment text,.previous-comment strong,.completed-card text,.completed-card strong { display:block; }.kicker { color:#23877d; font-size:12px; font-weight:800; letter-spacing:1.4px; }.title { margin-top:6px; font-size:27px; font-weight:800; }.subtitle { margin-top:6px; color:#6f837e; font-size:14px; }.workbench-header button,.pagination button,.section-heading button,.proof-item button { display:inline-flex; align-items:center; justify-content:center; min-height:40px; margin:0; padding:0 15px; border:1px solid #bed2ce; border-radius:5px; background:#fff; color:#176f66; font-size:13px; line-height:1.2; }.filter-bar { display:flex; flex-wrap:wrap; gap:8px; padding:11px; border:1px solid #dce7e4; border-radius:7px; background:#fff; }.filter-bar button { display:inline-flex; align-items:center; justify-content:center; min-height:40px; margin:0; padding:0 16px; border:1px solid #c9d9d6; border-radius:5px; background:#fff; color:#4d6661; font-size:14px; }.filter-bar button.active { border-color:#47aa9c; background:#e6f5f1; color:#0c7167; font-weight:800; }.message { display:grid; gap:4px; padding:14px 17px; border-radius:6px; font-size:14px; }.message.warning { border:1px solid #ead19a; background:#fff8e7; color:#7b5812; }.message.error { border:1px solid #edb6b1; background:#fff1ef; color:#a33730; }.message.success { border:1px solid #a9d9cf; background:#eaf8f4; color:#126d63; }.workbench-grid { display:grid; grid-template-columns:minmax(250px,0.8fr) minmax(420px,1.35fr) minmax(290px,0.85fr); min-height:590px; border:1px solid #d8e5e2; background:#fff; }.application-pane,.detail-pane,.decision-pane { min-width:0; padding:18px; }.application-pane,.detail-pane { border-right:1px solid #e0e9e7; }.pane-heading { min-height:38px; padding-bottom:13px; border-bottom:1px solid #e4ecea; }.pane-heading strong { font-size:17px; }.pane-heading text { color:#71847f; font-size:13px; }.application-list { display:grid; gap:8px; margin-top:13px; }.application-item { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; width:100%; min-height:82px; margin:0; padding:14px; border:1px solid #dce7e4; border-radius:6px; background:#fff; color:#23413c; text-align:left; }.application-item.selected { border-color:#4eaea0; background:#eaf7f4; box-shadow:inset 3px 0 #1b8d81; }.application-item strong { font-size:15px; }.application-item view > text { margin-top:7px; color:#738681; font-size:12px; }.status-chip { flex:none; padding:5px 8px; border-radius:999px; font-size:11px; font-weight:800; }.status-pending,.status-need_more { background:#fff0d2; color:#8c5b00; }.status-approved { background:#dcf3ed; color:#087064; }.status-rejected { background:#fde9e7; color:#a23a33; }.pagination { justify-content:center; margin-top:14px; }.pagination button { min-height:36px; }.pagination text { color:#667b76; font-size:12px; }.identity-summary { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin-top:15px; }.identity-summary view { padding:12px 13px; border-left:3px solid #7fc0b5; background:#f5faf8; }.identity-summary text { color:#738681; font-size:12px; }.identity-summary strong { margin-top:5px; font-size:14px; overflow-wrap:anywhere; }.review-section { margin-top:20px; }.section-heading { margin-bottom:10px; }.section-heading strong { font-size:15px; }.section-heading text { color:#71847f; font-size:12px; }.section-heading button.confirmed,.proof-item button.confirmed { border-color:#62b6a9; background:#e5f5f1; color:#0c7167; font-weight:800; }.tag-row { display:flex; flex-wrap:wrap; gap:8px; }.skill-tag { padding:6px 10px; border-radius:999px; background:#eaf5f2; color:#176f66; font-size:12px; font-weight:700; }.proof-list { display:grid; gap:9px; }.proof-item { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:13px; border:1px solid #dce7e4; border-radius:6px; background:#fbfdfc; }.proof-item view { min-width:0; }.proof-item strong { font-size:14px; overflow-wrap:anywhere; }.proof-item view text { margin-top:5px; color:#738681; font-size:12px; }.proof-item button { flex:none; }.previous-comment,.readonly-note,.completed-card { display:grid; gap:6px; margin-top:18px; padding:13px; border-left:4px solid #d59b32; background:#fff8e9; color:#725016; font-size:13px; }.previous-comment text { font-weight:800; }.decision-options { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:7px; margin-top:15px; }.decision-options button { display:flex; align-items:center; justify-content:center; min-height:44px; margin:0; padding:0 8px; border:1px solid #cadbd7; border-radius:5px; background:#fff; color:#506963; font-size:13px; }.decision-options button.selected { font-weight:800; }.decision-approved.selected { border-color:#54aa9d; background:#e3f4ef; color:#0d7167; }.decision-rejected.selected { border-color:#dc8c84; background:#fff0ee; color:#a53b34; }.decision-need_more.selected { border-color:#dbb05e; background:#fff7e5; color:#805b10; }.comment-field { display:block; margin-top:18px; }.comment-field > text,.comment-field small { display:block; }.comment-field > text { margin-bottom:8px; font-size:14px; font-weight:800; }.comment-field textarea { box-sizing:border-box; width:100%; min-height:150px; padding:12px; border:1px solid #cadbd7; border-radius:6px; background:#fbfdfc; color:#193632; font-size:14px; line-height:1.6; }.comment-field small { margin-top:6px; color:#758782; text-align:right; font-size:12px; }.submit-command { display:flex; align-items:center; justify-content:center; width:100%; min-height:48px; margin:17px 0 0; border:0; border-radius:5px; background:#167d72; color:#fff; font-size:15px; font-weight:800; }.pane-empty { padding:20px 8px; color:#70837e; font-size:14px; line-height:1.6; }.pane-empty.spacious { display:grid; min-height:300px; place-items:center; text-align:center; }.completed-card { border-left-color:#4ba99a; background:#ebf8f4; color:#126c62; }.completed-card strong { font-size:16px; }.readonly-note { border-left-color:#82a8a1; background:#f2f7f6; color:#48645f; }.workbench-header button[disabled],.filter-bar button[disabled],.application-item[disabled],.decision-options button[disabled],.proof-item button[disabled],.submit-command[disabled] { opacity:.5; }
.material-warning { margin-top:10px; padding:10px 12px; border-left:3px solid #d59b32; background:#fff8e9; color:#795718; font-size:12px; line-height:1.5; }
@media (max-width:1100px) { .workbench-grid { grid-template-columns:280px minmax(0,1fr); }.decision-pane { grid-column:1 / -1; border-top:1px solid #e0e9e7; }.detail-pane { border-right:0; } }
@media (max-width:760px) { .workbench-header { align-items:flex-start; }.workbench-grid { grid-template-columns:1fr; }.application-pane,.detail-pane { border-right:0; border-bottom:1px solid #e0e9e7; }.decision-pane { grid-column:auto; }.identity-summary { grid-template-columns:1fr; }.decision-options { grid-template-columns:1fr; }.proof-item { align-items:flex-start; flex-direction:column; }.proof-item button { width:100%; }.pane-empty.spacious { min-height:150px; } }
</style>
