<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  getQualificationCertificatePreview,
  getQualificationApplications,
  getQualificationPermissions,
  getQualificationSkillOptions
} from '@/api/stageTwentySix';
import type {
  QualificationApplicationRecord,
  QualificationSkillOption,
  QualificationStatusFilter
} from '@/types/stageTwentySix';
import {
  canReviewQualificationByPermission,
  qualificationDateTime,
  qualificationFileSize,
  qualificationSkillDictionaryErrorMessage,
  qualificationStatusLabel
} from '@/utils/stageTwentySixRules';

const filters: Array<{ value: QualificationStatusFilter; label: string }> = [
  { value: 'PENDING', label: '待审核' },
  { value: 'ALL', label: '全部状态' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
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
const error = ref('');
const skillDictionaryError = ref('');
const previewingFileId = ref('');
let requestSequence = 0;
let previewController: AbortController | null = null;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)));

function skillLabel(code: string) {
  return skills.value.find((item) => item.value === code)?.label
    || (skillDictionaryError.value ? '技能名称暂不可用' : '技能名称待同步');
}

function readableError(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有查看护理资质申请的权限。';
  if (code === 502) return '护理资质申请数据不完整，请等待服务更新后重试。';
  return '护理资质申请暂时无法读取，请稍后重试。';
}

async function loadApplications() {
  if (!allowed.value) return;
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = '';
  const response = await getQualificationApplications({ auditStatus: statusFilter.value, page: page.value, size });
  if (sequence !== requestSequence) return;
  loading.value = false;
  if (response.code !== 0) {
    records.value = [];
    total.value = 0;
    error.value = readableError(response.code);
    return;
  }
  records.value = response.data.records;
  total.value = response.data.total;
}

async function initialize() {
  loading.value = true;
  error.value = '';
  skillDictionaryError.value = '';
  const [permissionResponse, skillResponse] = await Promise.all([
    getQualificationPermissions(),
    getQualificationSkillOptions()
  ]);
  loading.value = false;
  if (permissionResponse.code !== 0) {
    error.value = readableError(permissionResponse.code);
    return;
  }
  allowed.value = canReviewQualificationByPermission(permissionResponse.data);
  if (!allowed.value) {
    error.value = '当前账号没有查看护理资质申请的权限。';
    return;
  }
  skills.value = skillResponse.code === 0 ? skillResponse.data : [];
  skillDictionaryError.value = qualificationSkillDictionaryErrorMessage(skillResponse.code);
  await loadApplications();
}

async function changeFilter(value: QualificationStatusFilter) {
  statusFilter.value = value;
  page.value = 1;
  await loadApplications();
}

async function changePage(offset: number) {
  const nextPage = page.value + offset;
  if (nextPage < 1 || nextPage > totalPages.value) return;
  page.value = nextPage;
  await loadApplications();
}

async function previewCertificate(applicationId: string, fileId: string) {
  if (previewingFileId.value) return;
  const opened = window.open('about:blank', '_blank');
  if (!opened) {
    error.value = '浏览器未能打开证明材料，请允许新窗口后重试。';
    return;
  }
  opened.opener = null;
  previewController?.abort();
  previewController = new AbortController();
  previewingFileId.value = fileId;
  const response = await getQualificationCertificatePreview(
    applicationId, fileId, previewController.signal
  );
  if (response.code !== 0 || !response.blob) {
    opened.close();
    if (response.code !== 499) error.value = readableError(response.code);
  } else {
    const objectUrl = URL.createObjectURL(response.blob);
    opened.location.href = objectUrl;
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  }
  previewingFileId.value = '';
  previewController = null;
}

onMounted(initialize);
onBeforeUnmount(() => previewController?.abort());
</script>

<template>
  <view class="qualification-admin-panel">
    <view class="panel-heading"><view><text class="panel-kicker">护理准入</text><text class="panel-title">护理资质申请</text><text class="panel-subtitle">查看护理人员提交的真实资质材料，审核操作由资质审核工作台统一处理。</text></view><button type="button" :disabled="loading || !allowed" @click="loadApplications">刷新</button></view>

    <view class="filter-bar"><button v-for="item in filters" :key="item.value" type="button" :class="{ active: statusFilter === item.value }" :disabled="loading || !allowed" @click="changeFilter(item.value)">{{ item.label }}</button></view>
    <view v-if="error" class="message error">{{ error }}</view>
    <view v-if="skillDictionaryError" class="message warning">{{ skillDictionaryError }}</view>
    <view v-if="loading" class="empty-state">正在读取护理资质申请...</view>
    <view v-else-if="allowed && !records.length && !error" class="empty-state">当前筛选条件下暂无资质申请。</view>

    <view v-if="records.length" class="application-list">
      <article v-for="record in records" :key="record.applicationId" class="application-card">
        <view class="card-top"><view><text class="nurse-name">{{ record.nurseName }}</text><text class="submitted-time">提交于 {{ qualificationDateTime(record.submittedAt) }}</text></view><text class="status-chip" :class="`status-${record.auditStatus.toLowerCase()}`">{{ qualificationStatusLabel(record.auditStatus) }}</text></view>
        <view class="info-grid"><view><text>证件姓名</text><strong>{{ record.realName }}</strong></view><view><text>脱敏证件号</text><strong>{{ record.idNoMasked }}</strong></view><view><text>护理证书</text><strong>{{ record.certificateNoMasked }}</strong></view><view><text>审核时间</text><strong>{{ qualificationDateTime(record.reviewedAt) }}</strong></view></view>
        <view class="detail-block"><text class="block-title">护理技能</text><view class="tag-row"><text v-for="code in record.serviceSkillCodes" :key="code" class="skill-tag">{{ skillLabel(code) }}</text></view></view>
        <view class="detail-block"><text class="block-title">证明材料</text><view class="proof-grid"><view v-for="file in record.certificateFiles" :key="file.fileId" class="proof-item"><text>{{ file.originalName }}</text><text>{{ file.mimeType }} · {{ qualificationFileSize(file.size) }}</text><button v-if="file.previewable" type="button" :disabled="Boolean(previewingFileId)" @click="previewCertificate(record.applicationId, file.fileId)">{{ previewingFileId === file.fileId ? '正在打开' : '查看证明' }}</button><text v-else>当前格式暂不支持在线查看</text></view></view></view>
        <view v-if="record.reviewComment" class="review-comment"><text>审核意见</text><text>{{ record.reviewComment }}</text></view>
      </article>
    </view>

    <view v-if="allowed && totalPages > 1" class="pagination"><button type="button" :disabled="loading || page <= 1" @click="changePage(-1)">上一页</button><text>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</text><button type="button" :disabled="loading || page >= totalPages" @click="changePage(1)">下一页</button></view>
  </view>
</template>

<style scoped>
.qualification-admin-panel { display:grid; gap:18px; }.panel-heading,.card-top,.pagination { display:flex; align-items:center; justify-content:space-between; gap:16px; }.panel-kicker,.panel-title,.panel-subtitle,.nurse-name,.submitted-time,.info-grid text,.info-grid strong,.block-title,.proof-item text,.review-comment text { display:block; }.panel-kicker { color:#23877d; font-size:12px; font-weight:800; letter-spacing:1.4px; }.panel-title { margin-top:7px; color:#173c37; font-size:27px; font-weight:800; }.panel-subtitle { margin-top:7px; color:#71847f; font-size:14px; }.panel-heading > button,.pagination button { display:inline-flex; align-items:center; justify-content:center; min-height:44px; margin:0; padding:0 18px; border:1px solid #bfd2ce; border-radius:6px; background:#fff; color:#176f66; font-size:14px; line-height:1.2; }.filter-bar { display:flex; flex-wrap:wrap; gap:8px; padding:12px; border:1px solid #dce7e4; border-radius:8px; background:#fff; }.filter-bar button { display:inline-flex; align-items:center; justify-content:center; min-height:40px; margin:0; padding:0 16px; border:1px solid #c8d9d5; border-radius:6px; background:#fff; color:#49645f; font-size:14px; line-height:1.2; }.filter-bar button.active { border-color:#45aa9c; background:#e5f5f1; color:#0d7167; font-weight:800; }.message,.empty-state { padding:18px 20px; border-radius:8px; }.message.error { border:1px solid #efbbb6; background:#fff2f0; color:#a63831; }.empty-state { border:1px dashed #cadbd7; background:#fff; color:#71847f; }.application-list { display:grid; gap:14px; }.application-card { padding:20px; border:1px solid #dce7e4; border-radius:8px; background:#fff; box-shadow:0 5px 16px rgba(24,59,53,.04); }.card-top { align-items:flex-start; padding-bottom:16px; border-bottom:1px solid #e8efed; }.nurse-name { color:#173d37; font-size:20px; font-weight:800; }.submitted-time { margin-top:5px; color:#71847f; font-size:13px; }.status-chip,.skill-tag { padding:5px 10px; border-radius:999px; font-size:12px; font-weight:800; }.status-pending,.status-need_more { background:#fff0d2; color:#8b5b00; }.status-approved { background:#dcf3ed; color:#087064; }.status-rejected { background:#fde9e7; color:#a23a33; }.info-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; margin-top:16px; }.info-grid > view { min-width:0; padding:13px 14px; border-left:3px solid #8ac7bd; background:#f6faf9; }.info-grid text { color:#72847f; font-size:12px; }.info-grid strong { margin-top:5px; color:#25423d; font-size:14px; overflow-wrap:anywhere; }.detail-block { margin-top:17px; }.block-title { margin-bottom:9px; color:#284942; font-size:14px; font-weight:800; }.tag-row { display:flex; flex-wrap:wrap; gap:8px; }.skill-tag { background:#eaf5f2; color:#176f66; }.proof-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; }.proof-item { min-width:0; padding:13px; border:1px solid #dce7e4; border-radius:6px; background:#fbfdfc; }.proof-item text:first-child { color:#27433e; font-size:14px; font-weight:800; overflow-wrap:anywhere; }.proof-item text:nth-child(2),.proof-item text:last-child { margin-top:5px; color:#748681; font-size:12px; }.review-comment { display:grid; gap:5px; margin-top:17px; padding:13px 15px; border-left:4px solid #d49a32; background:#fff8e9; color:#725016; font-size:13px; }.review-comment text:first-child { font-weight:800; }.pagination { justify-content:center; }.pagination text { color:#617671; font-size:13px; }button[disabled] { opacity:.48; }
.message.warning { border:1px solid #ead19a; background:#fff8e7; color:#7b5812; }.proof-item button { display:inline-flex; align-items:center; justify-content:center; min-height:36px; margin:11px 0 0; padding:0 14px; border:1px solid #96c9c0; border-radius:5px; background:#edf8f5; color:#116f65; font-size:13px; line-height:1.2; }
@media (max-width:900px) { .info-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.proof-grid { grid-template-columns:1fr; } }
</style>
