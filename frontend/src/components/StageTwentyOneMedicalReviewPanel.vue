<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { readAuthSession } from '@/api/client';
import {
  getAdminMedicalFileDetail,
  getAdminMedicalFiles,
  reviewAdminMedicalFile
} from '@/api/stageTwentyOne';
import { getAuthPermissions } from '@/api/stageThree';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { MedicalFileType } from '@/types/stageTwenty';
import type {
  AdminMedicalFileDetail,
  AdminMedicalFileQuery,
  AdminMedicalFileRecord,
  MedicalFileReviewDecision,
  MedicalFileReviewStatus
} from '@/types/stageTwentyOne';
import {
  canEnterMedicalFileReview,
  canReviewMedicalFile,
  getLocalCalendarDate,
  isCurrentMedicalFileSelection,
  refreshReviewedMedicalFile,
  reviewCommentRequired,
  validateArchiveExtraction,
  validateMedicalFileReview
} from '@/utils/stageTwentyOneRules';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const statusOptions: Array<{ value: MedicalFileReviewStatus | ''; label: string }> = [
  { value: 'PENDING', label: '待审核' },
  { value: '', label: '全部状态' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'NEED_MORE', label: '需补充' }
];
const typeOptions: Array<{ value: MedicalFileType | ''; label: string }> = [
  { value: '', label: '全部资料' },
  { value: 'PRESCRIPTION', label: '处方' },
  { value: 'EXAMINATION_REPORT', label: '检查报告' },
  { value: 'DISCHARGE_SUMMARY', label: '出院小结' },
  { value: 'MEDICAL_RECORD', label: '既往病历' }
];
const decisionOptions: Array<{ value: MedicalFileReviewDecision; label: string; help: string }> = [
  { value: 'APPROVED', label: '通过', help: '资料内容清晰、完整，可完成审核。' },
  { value: 'REJECTED', label: '驳回', help: '资料不符合要求，并向家属说明原因。' },
  { value: 'NEED_MORE', label: '要求补充', help: '保留资料并请家属补充缺失信息。' }
];

const query = ref<AdminMedicalFileQuery>({
  page: 1,
  size: 20,
  auditStatus: 'PENDING',
  fileType: '',
  keyword: '',
  dateFrom: '',
  dateTo: ''
});
const records = ref<AdminMedicalFileRecord[]>([]);
const total = ref(0);
const selectedFileId = ref('');
const detail = ref<AdminMedicalFileDetail | null>(null);
const loadingList = ref(false);
const loadingDetail = ref(false);
const submitting = ref(false);
const listError = ref('');
const detailError = ref('');
const formError = ref('');
const successMessage = ref('');
const permissionCodes = ref<string[]>([]);
const permissionsLoading = ref(true);
const permissionsLoaded = ref(false);
const permissionError = ref('');
const decision = ref<MedicalFileReviewDecision>('APPROVED');
const reviewComment = ref('');
const extractToArchive = ref(false);
const selectedExtractedFields = ref<string[]>([]);
const today = getLocalCalendarDate();

const canUsePanel = computed(() => canEnterMedicalFileReview(
  props.authUser?.roles ?? [],
  permissionCodes.value
));
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.value.size)));
const canSubmitReview = computed(() =>
  Boolean(detail.value && canReviewMedicalFile(detail.value.auditStatus) && !submitting.value)
);
const commentIsRequired = computed(() => reviewCommentRequired(decision.value));
const selectedExtractedItems = computed(() =>
  (detail.value?.extractedItems ?? []).filter((item) => selectedExtractedFields.value.includes(item.fieldName))
);

function typeLabel(value: MedicalFileType) {
  return typeOptions.find((item) => item.value === value)?.label ?? '病历资料';
}

function statusLabel(value: MedicalFileReviewStatus) {
  return statusOptions.find((item) => item.value === value)?.label ?? '处理中';
}

function statusClass(value: MedicalFileReviewStatus) {
  return `review-status-${value.toLowerCase().replace('_', '-')}`;
}

function formatDate(value?: string) {
  if (!value) return '未记录';
  return value.replace('T', ' ').slice(0, 16);
}

function formatSize(bytes?: number) {
  if (bytes === undefined) return '未记录';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function businessError(code: number, target: 'LIST' | 'DETAIL' | 'REVIEW') {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有病历审核权限。';
  if (code === 404) return target === 'LIST' ? '病历审核服务暂不可用。' : '这份病历资料已不存在或不可访问。';
  if (code === 409) return '这份资料已被其他审核人员处理，请刷新后查看最新结果。';
  if (code === 422) return '审核内容不完整，请检查审核结论和意见。';
  if (code === 502) return '病历资料响应与当前契约不一致，请联系平台维护人员。';
  return target === 'REVIEW' ? '审核结果暂时无法保存，请稍后重试。' : '病历资料暂时无法读取，请稍后重试。';
}

function resetReviewForm(nextDetail: AdminMedicalFileDetail | null) {
  decision.value = 'APPROVED';
  reviewComment.value = '';
  extractToArchive.value = false;
  selectedExtractedFields.value = (nextDetail?.extractedItems ?? []).map((item) => item.fieldName);
  formError.value = '';
}

async function loadDetail(fileId: string) {
  selectedFileId.value = fileId;
  loadingDetail.value = true;
  detailError.value = '';
  successMessage.value = '';
  const response = await getAdminMedicalFileDetail(fileId);
  loadingDetail.value = false;
  if (!isCurrentMedicalFileSelection(fileId, selectedFileId.value)) return;
  if (response.code !== 0) {
    detail.value = null;
    detailError.value = businessError(response.code, 'DETAIL');
    return;
  }
  detail.value = response.data;
  resetReviewForm(response.data);
}

async function loadFiles(keepSelection = false, loadSelectedDetail = true) {
  if (!canUsePanel.value) {
    records.value = [];
    total.value = 0;
    listError.value = '当前账号没有病历审核权限。';
    return;
  }
  loadingList.value = true;
  listError.value = '';
  successMessage.value = '';
  const response = await getAdminMedicalFiles(query.value);
  loadingList.value = false;
  if (response.code !== 0) {
    records.value = [];
    total.value = 0;
    detail.value = null;
    selectedFileId.value = '';
    listError.value = businessError(response.code, 'LIST');
    return;
  }
  records.value = response.data.records;
  total.value = response.data.total;
  query.value.page = response.data.page;
  query.value.size = response.data.size;
  if (!loadSelectedDetail) return;
  const current = keepSelection
    ? records.value.find((record) => record.fileId === selectedFileId.value)
    : undefined;
  const next = current ?? records.value[0];
  if (next) await loadDetail(next.fileId);
  else {
    selectedFileId.value = '';
    detail.value = null;
    resetReviewForm(null);
  }
}

function applyFilters() {
  query.value.page = 1;
  loadFiles();
}

function resetFilters() {
  query.value = {
    page: 1,
    size: 20,
    auditStatus: 'PENDING',
    fileType: '',
    keyword: '',
    dateFrom: '',
    dateTo: ''
  };
  loadFiles();
}

function setStatus(value: MedicalFileReviewStatus | '') {
  query.value.auditStatus = value;
  applyFilters();
}

function setType(event: { detail: { value: number | string } }) {
  const option = typeOptions[Number(event.detail.value)];
  query.value.fileType = option?.value ?? '';
  applyFilters();
}

async function changePage(offset: number) {
  const next = query.value.page + offset;
  if (next < 1 || next > totalPages.value || loadingList.value) return;
  query.value.page = next;
  await loadFiles();
}

function setDecision(next: MedicalFileReviewDecision) {
  decision.value = next;
  if (next !== 'APPROVED') extractToArchive.value = false;
  formError.value = '';
}

function toggleExtractedField(fieldName: string) {
  const index = selectedExtractedFields.value.indexOf(fieldName);
  if (index >= 0) selectedExtractedFields.value.splice(index, 1);
  else selectedExtractedFields.value.push(fieldName);
  formError.value = '';
}

function toggleArchiveSuggestion(event: Event) {
  const checked = (event as unknown as { detail: { value: boolean } }).detail.value;
  extractToArchive.value = decision.value === 'APPROVED' && checked;
  formError.value = '';
}

async function submitReview() {
  if (!detail.value || !canSubmitReview.value) return;
  const validationError = validateMedicalFileReview(decision.value, reviewComment.value);
  if (validationError) {
    formError.value = validationError;
    return;
  }
  const extractionError = validateArchiveExtraction(
    extractToArchive.value,
    detail.value.extractedItems?.length ?? 0,
    selectedExtractedItems.value.length
  );
  if (extractionError) {
    formError.value = extractionError;
    return;
  }
  const reviewedFileId = detail.value.fileId;
  submitting.value = true;
  formError.value = '';
  successMessage.value = '';
  const response = await reviewAdminMedicalFile(detail.value.fileId, {
    auditStatus: decision.value,
    reviewComment: reviewComment.value.trim(),
    extractToArchive: decision.value === 'APPROVED' && extractToArchive.value,
    extractedItems: decision.value === 'APPROVED' && extractToArchive.value
      ? selectedExtractedItems.value
      : []
  });
  submitting.value = false;
  if (response.code !== 0) {
    const errorMessage = businessError(response.code, 'REVIEW');
    if (response.code === 409) {
      await loadFiles(true);
      listError.value = errorMessage;
    } else {
      formError.value = errorMessage;
    }
    return;
  }
  const message = response.data.auditStatus === 'APPROVED' && extractToArchive.value
    ? '审核结果已保存，并已进入后续档案审核流程；当前健康档案未被直接修改。'
    : '审核结果已保存，家属端刷新后可查看最新状态。';
  await refreshReviewedMedicalFile(
    reviewedFileId,
    () => loadFiles(true, false),
    loadDetail
  );
  successMessage.value = message;
}

async function openAuthorizedUrl(url?: string) {
  if (!url) return;
  detailError.value = '';
  const target = new URL(url, window.location.origin);
  const signatureKeys = [...target.searchParams.keys()].map((key) => key.toLowerCase());
  const isSignedUrl = signatureKeys.some((key) =>
    key.includes('signature') || key.startsWith('x-amz-') || key === 'token'
  );
  if (target.origin !== window.location.origin || isSignedUrl) {
    const opened = window.open(target.toString(), '_blank');
    if (opened) opened.opener = null;
    else detailError.value = '浏览器未能打开文件，请允许新窗口后重试。';
    return;
  }
  const opened = window.open('about:blank', '_blank');
  if (!opened) {
    detailError.value = '浏览器未能打开文件，请允许新窗口后重试。';
    return;
  }
  opened.opener = null;
  try {
    const session = readAuthSession();
    const response = await fetch(target.toString(), {
      headers: session ? { Authorization: `Bearer ${session.token}` } : {}
    });
    if (!response.ok) throw new Error('file access failed');
    const objectUrl = URL.createObjectURL(await response.blob());
    opened.location.href = objectUrl;
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch {
    opened.close();
    detailError.value = '文件授权已失效，请刷新详情后重试。';
  }
}

function permissionBusinessError(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有病历审核权限。';
  return '暂时无法读取账号权限，请稍后重试。';
}

async function initializeReviewPanel() {
  permissionsLoading.value = true;
  permissionsLoaded.value = false;
  permissionError.value = '';
  permissionCodes.value = [];
  const response = await getAuthPermissions();
  permissionsLoading.value = false;
  permissionsLoaded.value = true;
  if (response.code !== 0) {
    permissionError.value = permissionBusinessError(response.code);
    return;
  }
  permissionCodes.value = response.data.permissions;
  if (!canUsePanel.value) return;
  await loadFiles(true);
}

onMounted(initializeReviewPanel);
</script>

<template>
  <section class="medical-review-panel" aria-label="病历审核工作台">
    <header class="review-header">
      <view>
        <text class="review-kicker">病历质量审核</text>
        <text class="review-title">病历审核工作台</text>
        <text class="review-subtitle">连续处理家属上传的病历资料，审核结果会同步给用户端。</text>
      </view>
      <button type="button" class="secondary-command" :disabled="loadingList || permissionsLoading || submitting" @click="initializeReviewPanel">刷新</button>
    </header>

    <view v-if="permissionsLoading" class="access-state">
      <text>正在验证审核权限</text>
      <text>权限确认后会自动读取病历资料。</text>
    </view>

    <view v-else-if="permissionError" class="access-state">
      <text>{{ permissionError }}</text>
      <button type="button" class="secondary-command" @click="initializeReviewPanel">重新验证</button>
    </view>

    <view v-else-if="permissionsLoaded && !canUsePanel" class="access-state">
      <text>当前账号没有病历审核权限。</text>
      <text>请使用具备病历审核权限的管理员或客服账号。</text>
    </view>

    <template v-else>
      <view class="filter-band">
        <view class="status-filter" aria-label="审核状态筛选">
          <button
            v-for="option in statusOptions"
            :key="option.label"
            type="button"
            :class="{ active: query.auditStatus === option.value }"
            @click="setStatus(option.value)"
          >{{ option.label }}</button>
        </view>
        <view class="filter-fields">
          <label><text>资料类型</text><picker :range="typeOptions" range-key="label" :value="Math.max(0, typeOptions.findIndex((item) => item.value === query.fileType))" @change="setType"><view class="filter-input">{{ typeOptions.find((item) => item.value === query.fileType)?.label }}</view></picker></label>
          <label><text>长辈姓名</text><input v-model="query.keyword" maxlength="40" placeholder="输入姓名筛选" @confirm="applyFilters" /></label>
          <label><text>开始日期</text><picker mode="date" start="2000-01-01" :end="query.dateTo || today" :value="query.dateFrom" @change="query.dateFrom = $event.detail.value"><view class="filter-input">{{ query.dateFrom || '不限' }}</view></picker></label>
          <label><text>结束日期</text><picker mode="date" :start="query.dateFrom || '2000-01-01'" :end="today" :value="query.dateTo" @change="query.dateTo = $event.detail.value"><view class="filter-input">{{ query.dateTo || '不限' }}</view></picker></label>
          <view class="filter-actions"><button type="button" class="primary-command" @click="applyFilters">查询</button><button type="button" class="secondary-command" @click="resetFilters">重置</button></view>
        </view>
      </view>

      <view v-if="listError" class="message error-message" role="alert">{{ listError }}</view>
      <view v-if="successMessage" class="message success-message" role="status">{{ successMessage }}</view>

      <view class="review-workspace">
        <aside class="review-list-pane">
          <view class="pane-heading"><view><text>病历资料</text><text>按上传时间倒序</text></view><text>{{ total }} 份</text></view>
          <view v-if="loadingList" class="empty-state">正在读取待审核资料...</view>
          <view v-else-if="records.length === 0" class="empty-state">当前筛选条件下没有病历资料。</view>
          <view v-else class="review-list">
            <button
              v-for="record in records"
              :key="record.fileId"
              type="button"
              class="review-list-item"
              :class="{ active: selectedFileId === record.fileId }"
              @click="loadDetail(record.fileId)"
            >
              <view class="item-heading"><text>{{ record.title }}</text><text class="status-badge" :class="statusClass(record.auditStatus)">{{ statusLabel(record.auditStatus) }}</text></view>
              <text class="item-person">{{ record.elderName }} · {{ typeLabel(record.fileType) }}</text>
              <text class="item-date">资料日期：{{ formatDate(record.occurredAt) }}</text>
              <text class="item-date">上传时间：{{ formatDate(record.createdAt) }}</text>
            </button>
          </view>
          <view class="pagination">
            <button type="button" :disabled="query.page <= 1 || loadingList" @click="changePage(-1)">上一页</button>
            <text>第 {{ query.page }} / {{ totalPages }} 页</text>
            <button type="button" :disabled="query.page >= totalPages || loadingList" @click="changePage(1)">下一页</button>
          </view>
        </aside>

        <main class="review-detail-pane">
          <view v-if="loadingDetail" class="empty-state">正在读取病历详情...</view>
          <view v-else-if="detailError && !detail" class="empty-state error-state"><text>{{ detailError }}</text><button type="button" class="secondary-command" @click="selectedFileId && loadDetail(selectedFileId)">重新读取</button></view>
          <view v-else-if="!detail" class="empty-state">从左侧选择一份病历资料开始审核。</view>
          <template v-else>
            <section class="detail-summary">
              <view class="detail-heading">
                <view><text class="detail-title">{{ detail.title }}</text><text class="detail-person">{{ detail.elderName }} · {{ typeLabel(detail.fileType) }}</text></view>
                <text class="status-badge" :class="statusClass(detail.auditStatus)">{{ statusLabel(detail.auditStatus) }}</text>
              </view>
              <view class="detail-meta">
                <view><text>资料发生日期</text><text>{{ formatDate(detail.occurredAt) }}</text></view>
                <view><text>上传时间</text><text>{{ formatDate(detail.createdAt) }}</text></view>
                <view><text>上传人</text><text>{{ detail.uploaderName || '家属用户' }}</text></view>
                <view><text>文件信息</text><text>{{ detail.originalName || '病历资料' }} · {{ formatSize(detail.size) }}</text></view>
              </view>
              <view class="preview-actions">
                <button v-if="detail.previewUrl" type="button" class="primary-command" @click="openAuthorizedUrl(detail.previewUrl)">预览资料</button>
                <button v-if="detail.downloadUrl" type="button" class="secondary-command" @click="openAuthorizedUrl(detail.downloadUrl)">下载资料</button>
                <text v-if="!detail.previewUrl && !detail.downloadUrl">当前未提供有效的预览或下载授权，请刷新详情后再试。</text>
              </view>
              <view v-if="detailError" class="message error-message" role="alert">{{ detailError }}</view>
            </section>

            <section v-if="canReviewMedicalFile(detail.auditStatus)" class="review-form">
              <view class="section-heading"><view><text>提交审核结论</text><text>驳回或要求补充时必须填写明确意见</text></view></view>
              <view class="decision-grid">
                <button v-for="option in decisionOptions" :key="option.value" type="button" :class="{ active: decision === option.value }" @click="setDecision(option.value)"><text>{{ option.label }}</text><text>{{ option.help }}</text></button>
              </view>
              <label class="comment-field"><text>审核意见{{ commentIsRequired ? '（必填）' : '（可选）' }}</text><textarea v-model="reviewComment" maxlength="255" :placeholder="commentIsRequired ? '请说明驳回或需要补充的具体内容' : '可补充说明审核依据'" /></label>
              <view class="archive-switch" :class="{ disabled: decision !== 'APPROVED' || !detail.extractedItems?.length }">
                <view><text>进入档案审核流程</text><text>{{ detail.extractedItems?.length ? '只创建后续审核任务，不会直接修改健康档案。' : '当前资料没有可提取的档案建议。' }}</text></view>
                <switch color="#168c81" :checked="extractToArchive" :disabled="decision !== 'APPROVED' || !detail.extractedItems?.length" @change="toggleArchiveSuggestion" />
              </view>
              <view v-if="extractToArchive && detail.extractedItems?.length" class="extracted-items">
                <text class="extracted-title">选择进入后续审核的建议</text>
                <button v-for="item in detail.extractedItems" :key="item.fieldName" type="button" :class="{ active: selectedExtractedFields.includes(item.fieldName) }" @click="toggleExtractedField(item.fieldName)"><text>{{ item.fieldLabel }}</text><text>{{ item.value }}</text></button>
              </view>
              <view v-if="formError" class="message error-message" role="alert">{{ formError }}</view>
              <button type="button" class="submit-review" :disabled="!canSubmitReview" @click="submitReview">{{ submitting ? '正在保存...' : '确认提交审核' }}</button>
            </section>

            <section v-else class="review-result">
              <view class="section-heading"><view><text>审核结果</text><text>该资料已完成处理，当前页面仅供查看。</text></view></view>
              <view class="result-row"><text>处理状态</text><text class="status-badge" :class="statusClass(detail.auditStatus)">{{ statusLabel(detail.auditStatus) }}</text></view>
              <view class="result-row"><text>处理时间</text><text>{{ formatDate(detail.reviewedAt) }}</text></view>
              <view class="result-comment"><text>审核意见</text><text>{{ detail.reviewComment || '未填写补充意见' }}</text></view>
            </section>
          </template>
        </main>
      </view>
    </template>
  </section>
</template>

<style scoped>
.medical-review-panel { display:grid; gap:18px; min-width:0; color:#19332f; }
.review-header { display:flex; align-items:center; justify-content:space-between; gap:24px; padding-bottom:2px; }
.review-header>view { display:grid; gap:5px; min-width:0; }
.review-kicker,.review-title,.review-subtitle,.pane-heading text,.review-list-item>text,.detail-title,.detail-person,.detail-meta text,.section-heading text,.decision-grid text,.comment-field>text,.archive-switch text,.extracted-items text,.result-comment text { display:block; }
.review-kicker { color:#167d73; font-size:12px; font-weight:800; }
.review-title { margin-top:5px; font-size:24px; font-weight:800; }
.review-subtitle { margin-top:6px; color:#6c7f7b; font-size:13px; }
.primary-command,.secondary-command,.filter-band button,.pagination button,.preview-actions button { display:inline-flex; align-items:center; justify-content:center; min-width:0; min-height:38px; margin:0; padding:0 14px; border:1px solid #cbdad6; border-radius:5px; background:#fff; color:#315c56; font-size:13px; font-weight:700; line-height:1.2; text-align:center; white-space:nowrap; }
.primary-command { border-color:#168c81; background:#168c81; color:#fff; }
button[disabled] { opacity:.48; }
.access-state,.medical-review-panel .empty-state { display:grid; grid-template-columns:minmax(0,1fr); place-content:center; gap:8px; min-width:0; padding:28px; border:1px dashed #c8d8d4; border-radius:6px; background:#fbfdfc; box-shadow:none; color:#657a75; font-size:14px; line-height:1.6; text-align:center; overflow-wrap:break-word; backdrop-filter:none; -webkit-backdrop-filter:none; }
.access-state text:first-child { color:#9d3731; font-size:17px; font-weight:800; }
.filter-band { display:grid; gap:16px; padding:18px; border:1px solid #dce7e4; border-radius:7px; background:#fff; }
.status-filter { display:flex; flex-wrap:wrap; gap:7px; }
.status-filter button { min-height:36px; padding:0 16px; line-height:1.2; }
.status-filter button.active { border-color:#168c81; background:#e7f6f3; color:#0e756c; }
.filter-fields { display:grid; grid-template-columns:160px minmax(180px,1fr) 150px 150px auto; gap:10px; align-items:end; }
.filter-fields label { display:grid; gap:6px; color:#5f746f; font-size:12px; font-weight:700; }
.filter-fields input,.filter-input { width:100%; min-height:38px; box-sizing:border-box; padding:0 11px; border:1px solid #ccd9d6; border-radius:4px; background:#fff; color:#19332f; font-size:13px; line-height:38px; }
.filter-actions { display:flex; align-items:stretch; gap:8px; }
.filter-actions button { min-width:68px; }
.message { padding:12px 14px; border:1px solid; font-size:13px; line-height:1.5; }
.error-message { border-color:#efb8b3; background:#fff3f2; color:#a23b34; }
.success-message { border-color:#9ed8cf; background:#eaf8f5; color:#0f766e; }
.review-workspace { display:grid; grid-template-columns:minmax(300px,360px) minmax(420px,1fr); min-height:540px; border:1px solid #d8e4e1; border-radius:7px; background:#fff; overflow:hidden; }
.review-list-pane { display:flex; min-width:0; flex-direction:column; border-right:1px solid #d8e4e1; background:#f7faf9; }
.pane-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:16px; border-bottom:1px solid #d8e4e1; }
.pane-heading>view text:first-child { font-size:16px; font-weight:800; }
.pane-heading>view text:last-child,.pane-heading>text { margin-top:3px; color:#71837f; font-size:12px; }
.review-list { display:grid; gap:8px; padding:10px; }
.review-list-item { display:grid; align-content:center; align-items:normal; justify-content:stretch; justify-items:stretch; gap:7px; width:100%; min-width:0; margin:0; padding:14px; border:1px solid #d9e4e1; border-radius:5px; background:#fff; color:#405d58; line-height:1.45; text-align:left; }
.review-list-item.active { border-color:#63b9af; background:#edf8f6; box-shadow:inset 4px 0 #168c81; }
.item-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; }
.item-heading>text:first-child { min-width:0; color:#19332f; font-size:15px; font-weight:800; overflow-wrap:anywhere; }
.item-person { font-size:13px; font-weight:700; }
.item-date { color:#71837f; font-size:12px; }
.status-badge { flex:none; padding:5px 9px; border:1px solid; border-radius:12px; font-size:12px; font-weight:800; }
.review-status-pending { border-color:#efcd91; background:#fff7e7; color:#93620e; }
.review-status-approved { border-color:#9fd8cf; background:#eaf8f5; color:#0f766e; }
.review-status-rejected { border-color:#efb5b0; background:#fff1ef; color:#b13e36; }
.review-status-need-more { border-color:#bad2e9; background:#eef6fd; color:#2e6c9e; }
.pagination { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-top:auto; padding:12px; border-top:1px solid #d8e4e1; color:#657a75; font-size:12px; }
.pagination button { min-height:32px; }
.review-list-pane>.empty-state { min-height:170px; margin:14px; }
.review-detail-pane { display:grid; align-content:start; min-width:0; padding:22px; }
.review-detail-pane>.empty-state { width:100%; min-height:240px; }
.detail-summary,.review-form,.review-result { display:grid; gap:18px; }
.review-form,.review-result { margin-top:24px; padding-top:22px; border-top:1px solid #dbe6e3; }
.detail-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; }
.detail-title { font-size:22px; font-weight:800; overflow-wrap:anywhere; }
.detail-person { margin-top:7px; color:#60746f; font-size:14px; }
.detail-meta { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px 18px; padding:16px; background:#f3f8f6; }
.detail-meta view { display:grid; gap:4px; }
.detail-meta text:first-child { color:#6d807c; font-size:12px; }
.detail-meta text:last-child { font-size:14px; font-weight:700; overflow-wrap:anywhere; }
.preview-actions { display:flex; align-items:center; flex-wrap:wrap; gap:9px; }
.preview-actions>text { color:#71837f; font-size:13px; }
.section-heading>view { display:grid; gap:5px; }
.section-heading text:first-child { font-size:18px; font-weight:800; }
.section-heading text:last-child { color:#71837f; font-size:12px; }
.decision-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; }
.decision-grid button { display:grid; align-content:center; align-items:normal; justify-content:stretch; justify-items:start; gap:5px; margin:0; padding:13px; border:1px solid #cfdbd8; border-radius:5px; background:#fff; color:#49645f; line-height:1.35; text-align:left; }
.decision-grid button.active { border-color:#168c81; background:#e9f7f4; color:#0f766e; }
.decision-grid text:first-child { font-size:14px; font-weight:800; }
.decision-grid text:last-child { font-size:11px; line-height:1.45; }
.comment-field { display:grid; gap:7px; color:#536b66; font-size:13px; font-weight:700; }
.comment-field textarea { width:100%; min-height:110px; box-sizing:border-box; padding:12px; border:1px solid #cbd9d5; border-radius:4px; background:#fff; color:#19332f; font-size:14px; line-height:1.55; }
.archive-switch { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:14px 16px; border-left:4px solid #168c81; background:#edf8f6; }
.archive-switch.disabled { border-left-color:#b6c4c1; background:#f5f7f6; }
.archive-switch>view { display:grid; gap:4px; }
.archive-switch text:first-child { font-size:14px; font-weight:800; }
.archive-switch text:last-child { color:#677a76; font-size:12px; }
.extracted-items { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; }
.extracted-title { grid-column:1/-1; color:#536b66; font-size:13px; font-weight:800; }
.extracted-items button { display:grid; align-content:center; align-items:normal; justify-content:stretch; justify-items:start; gap:4px; margin:0; padding:11px; border:1px solid #cfdcd8; border-radius:4px; background:#fff; line-height:1.35; text-align:left; }
.extracted-items button.active { border-color:#63b9af; background:#eaf7f4; }
.extracted-items button text:first-child { color:#176d65; font-size:12px; font-weight:800; }
.extracted-items button text:last-child { color:#405c57; font-size:13px; }
.submit-review { display:inline-flex; align-items:center; justify-content:center; min-height:44px; margin:0; border:0; border-radius:5px; background:#168c81; color:#fff; font-size:15px; font-weight:800; line-height:1.2; }
.result-row { display:flex; align-items:center; justify-content:space-between; gap:16px; padding-bottom:12px; border-bottom:1px solid #e2eae8; color:#5d736e; font-size:13px; }
.result-comment { display:grid; gap:7px; padding:14px; background:#f4f8f7; }
.result-comment text:first-child { color:#60746f; font-size:12px; font-weight:800; }
.result-comment text:last-child { font-size:14px; line-height:1.6; white-space:pre-wrap; }
.error-state button { justify-self:start; }
@media (max-width:1100px) {
  .filter-fields { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .filter-actions { grid-column:1/-1; }
  .review-workspace { grid-template-columns:minmax(280px,320px) minmax(360px,1fr); }
}
@media (max-width:800px) {
  .review-header { align-items:stretch; }
  .review-workspace { grid-template-columns:1fr; }
  .review-list-pane { border-right:0; border-bottom:1px solid #d8e4e1; }
  .detail-meta,.decision-grid,.extracted-items { grid-template-columns:1fr; }
  .review-detail-pane { padding:16px; }
}
</style>
