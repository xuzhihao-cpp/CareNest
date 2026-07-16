<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  getQualificationApplications,
  getQualificationPermissions
} from '@/api/stageTwentySix';
import { reviewNurseTraining } from '@/api/stageTwentyEight';
import type { QualificationApplicationRecord } from '@/types/stageTwentySix';
import type {
  CompletedTrainingReview,
  TrainingReviewStatus
} from '@/types/stageTwentyEight';
import {
  qualificationDateTime,
  qualificationStatusLabel
} from '@/utils/stageTwentySixRules';
import {
  TRAINING_BATCH_MAX_LENGTH,
  TRAINING_REMARK_MAX_LENGTH,
  canReviewTrainingByPermission,
  combineTrainingExpiry,
  expiryFieldsForTrainingStatus,
  localDateValue,
  trainingReviewErrorMessage,
  trainingStatusLabel,
  validateTrainingReview
} from '@/utils/stageTwentyEightRules';

const statusOptions: Array<{ value: TrainingReviewStatus; label: string }> = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '通过' },
  { value: 'REJECTED', label: '未通过' },
  { value: 'NEED_MORE', label: '需补充' }
];

const loading = ref(false);
const allowed = ref(false);
const records = ref<QualificationApplicationRecord[]>([]);
const page = ref(1);
const size = 10;
const total = ref(0);
const selectedNurseId = ref('');
const status = ref<TrainingReviewStatus | ''>('');
const trainingBatch = ref('');
const expiryDate = ref('');
const expiryTime = ref('');
const remark = ref('');
const error = ref('');
const submittingNurseId = ref('');
const completed = ref<CompletedTrainingReview | null>(null);
let listRequestSequence = 0;
let reviewRequestSequence = 0;
let contextRequestSequence = 0;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)));
const selected = computed(() => records.value.find(
  (item) => item.nurseId === selectedNurseId.value
) ?? null);
const minExpiryDate = computed(() => localDateValue());
const canSubmit = computed(() => Boolean(selected.value) && !submittingNurseId.value);

function listErrorMessage(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有查看护理培训名单的权限。';
  if (code === 502) return '护理培训名单内容不完整，请稍后刷新。';
  return '护理培训名单暂时无法读取，请稍后重试。';
}

function resetForm() {
  status.value = '';
  trainingBatch.value = '';
  expiryDate.value = '';
  expiryTime.value = '';
  remark.value = '';
  error.value = '';
}

function selectNurse(record: QualificationApplicationRecord) {
  if (submittingNurseId.value) return;
  selectedNurseId.value = record.nurseId;
  completed.value = null;
  resetForm();
}

function selectStatus(value: TrainingReviewStatus) {
  if (submittingNurseId.value) return;
  status.value = value;
  const expiry = expiryFieldsForTrainingStatus(value, expiryDate.value, expiryTime.value);
  expiryDate.value = expiry.expiryDate;
  expiryTime.value = expiry.expiryTime;
}

async function loadApprovedNurses() {
  if (!allowed.value) return;
  const sequence = ++listRequestSequence;
  loading.value = true;
  error.value = '';
  const response = await getQualificationApplications({
    auditStatus: 'APPROVED',
    page: page.value,
    size
  });
  if (sequence !== listRequestSequence) return;
  loading.value = false;
  if (response.code !== 0) {
    records.value = [];
    total.value = 0;
    selectedNurseId.value = '';
    resetForm();
    error.value = listErrorMessage(response.code);
    return;
  }
  const unique = new Map<string, QualificationApplicationRecord>();
  response.data.records.forEach((record) => {
    if (!unique.has(record.nurseId)) unique.set(record.nurseId, record);
  });
  records.value = Array.from(unique.values());
  total.value = response.data.total;
  if (selectedNurseId.value && !records.value.some((item) => item.nurseId === selectedNurseId.value)) {
    selectedNurseId.value = '';
    resetForm();
  }
}

async function initialize() {
  const sequence = ++contextRequestSequence;
  loading.value = true;
  const response = await getQualificationPermissions();
  if (sequence !== contextRequestSequence) return;
  loading.value = false;
  if (response.code !== 0) {
    error.value = listErrorMessage(response.code);
    return;
  }
  allowed.value = canReviewTrainingByPermission(response.data);
  if (!allowed.value) {
    error.value = '当前账号没有审核护理培训资格的权限。';
    return;
  }
  await loadApprovedNurses();
}

async function changePage(offset: number) {
  if (submittingNurseId.value) return;
  const next = page.value + offset;
  if (next < 1 || next > totalPages.value) return;
  page.value = next;
  selectedNurseId.value = '';
  resetForm();
  await loadApprovedNurses();
}

async function submitReview() {
  const nurse = selected.value;
  if (!nurse || submittingNurseId.value) return;
  const expiredAt = status.value === 'APPROVED'
    ? combineTrainingExpiry(expiryDate.value, expiryTime.value)
    : '';
  const validationError = validateTrainingReview({
    qualificationStatus: nurse.auditStatus,
    status: status.value,
    trainingBatch: trainingBatch.value,
    expiredAt,
    remark: remark.value
  });
  if (validationError) {
    error.value = validationError;
    return;
  }

  const nurseId = nurse.nurseId;
  const nurseName = nurse.nurseName;
  const targetStatus = status.value as TrainingReviewStatus;
  const sequence = ++reviewRequestSequence;
  submittingNurseId.value = nurseId;
  error.value = '';
  const response = await reviewNurseTraining(nurseId, {
    status: targetStatus,
    trainingBatch: trainingBatch.value.trim(),
    expiredAt,
    remark: remark.value.trim()
  });
  if (sequence !== reviewRequestSequence) return;
  if (response.code !== 0) {
    const responseError = trainingReviewErrorMessage(response.code);
    if (response.code === 409) {
      error.value = `${responseError}当前管理端尚不能重新读取该护理人员的培训结果，请勿重复提交。`;
    } else if (selectedNurseId.value === nurseId) {
      error.value = responseError;
    }
    submittingNurseId.value = '';
    return;
  }
  if (response.data.nurseId !== nurseId || response.data.trainingStatus !== targetStatus) {
    error.value = '培训审核结果与提交内容不一致，请刷新后确认最终状态。';
    submittingNurseId.value = '';
    return;
  }
  completed.value = { nurseName, qualification: nurse, result: response.data };
  if (selectedNurseId.value === nurseId) {
    selectedNurseId.value = '';
    resetForm();
  }
  submittingNurseId.value = '';
}

onMounted(initialize);
onBeforeUnmount(() => {
  ++contextRequestSequence;
  ++listRequestSequence;
  ++reviewRequestSequence;
});
</script>

<template>
  <section class="training-review-workbench">
    <header class="workbench-header">
      <view><text class="kicker">护理准入</text><text class="title">培训资格审核</text><text class="subtitle">资质已通过护理人员</text></view>
      <button type="button" :disabled="loading || !allowed || Boolean(submittingNurseId)" @click="loadApprovedNurses">刷新</button>
    </header>

    <view v-if="error" class="message error">{{ error }}</view>
    <view v-if="completed" class="message success">
      <strong>{{ completed.nurseName }}的培训审核已提交</strong>
      <text>本次提交结果：{{ trainingStatusLabel(completed.result.trainingStatus) }}</text>
      <text v-if="completed.result.expiredAt">有效期至：{{ qualificationDateTime(completed.result.expiredAt) }}</text>
    </view>

    <view class="workbench-grid">
      <aside class="nurse-pane">
        <view class="pane-heading"><strong>护理名单</strong><text>{{ total }} 人</text></view>
        <view v-if="loading" class="pane-empty">正在读取护理名单...</view>
        <view v-else-if="!records.length" class="pane-empty">暂无资质已通过的护理人员。</view>
        <view v-else class="nurse-list">
          <button
            v-for="record in records"
            :key="record.nurseId"
            type="button"
            class="nurse-item"
            :class="{ selected: selectedNurseId === record.nurseId }"
            :disabled="Boolean(submittingNurseId)"
            @click="selectNurse(record)"
          >
            <view><strong>{{ record.nurseName }}</strong><text>{{ record.realName }}</text><text>{{ qualificationDateTime(record.reviewedAt) }} 通过资质审核</text></view>
            <text class="qualification-chip">{{ qualificationStatusLabel(record.auditStatus) }}</text>
          </button>
        </view>
        <view v-if="totalPages > 1" class="pagination">
          <button type="button" :disabled="loading || Boolean(submittingNurseId) || page <= 1" @click="changePage(-1)">上一页</button>
          <text>{{ page }} / {{ totalPages }}</text>
          <button type="button" :disabled="loading || Boolean(submittingNurseId) || page >= totalPages" @click="changePage(1)">下一页</button>
        </view>
      </aside>

      <main class="review-pane">
        <template v-if="selected">
          <view class="pane-heading"><strong>{{ selected.nurseName }}</strong><text>培训审核</text></view>
          <view class="qualification-summary">
            <view><text>证件姓名</text><strong>{{ selected.realName }}</strong></view>
            <view><text>护理证书</text><strong>{{ selected.certificateNoMasked }}</strong></view>
            <view><text>资质状态</text><strong>{{ qualificationStatusLabel(selected.auditStatus) }}</strong></view>
          </view>

          <view class="form-section">
            <text class="field-title">培训结果</text>
            <view class="status-options">
              <button v-for="item in statusOptions" :key="item.value" type="button" :class="[`status-${item.value.toLowerCase()}`, { selected: status === item.value }]" :disabled="Boolean(submittingNurseId)" @click="selectStatus(item.value)">{{ item.label }}</button>
            </view>
          </view>

          <label class="field">
            <text>培训批次 *</text>
            <input v-model="trainingBatch" :maxlength="TRAINING_BATCH_MAX_LENGTH" placeholder="填写本次培训批次" />
            <small>{{ trainingBatch.length }} / {{ TRAINING_BATCH_MAX_LENGTH }}</small>
          </label>

          <view v-if="status === 'APPROVED'" class="field">
            <text>培训有效期 *</text>
            <view class="expiry-grid">
              <input v-model="expiryDate" type="date" :min="minExpiryDate" aria-label="培训有效期日期" />
              <input v-model="expiryTime" type="time" aria-label="培训有效期时间" />
            </view>
          </view>

          <label class="field">
            <text>审核说明{{ status === 'REJECTED' || status === 'NEED_MORE' ? ' *' : '（选填）' }}</text>
            <textarea v-model="remark" :maxlength="TRAINING_REMARK_MAX_LENGTH" placeholder="记录培训结果，未通过或需补充时请写明原因" />
            <small>{{ remark.length }} / {{ TRAINING_REMARK_MAX_LENGTH }}</small>
          </label>

          <button class="submit-command" type="button" :disabled="!canSubmit" @click="submitReview">
            {{ submittingNurseId === selected.nurseId ? '正在提交培训审核' : '提交培训审核' }}
          </button>
        </template>
        <view v-else class="pane-empty spacious">请选择一位资质已通过的护理人员。</view>
      </main>
    </view>
  </section>
</template>

<style scoped>
.training-review-workbench { display:grid; gap:16px; color:#183530; }.workbench-header,.pane-heading,.pagination { display:flex; align-items:center; justify-content:space-between; gap:12px; }.workbench-header > view > text,.nurse-item text,.qualification-summary text,.qualification-summary strong,.field > text,.field small,.message text,.message strong { display:block; }.kicker { color:#23877d; font-size:12px; font-weight:800; letter-spacing:1.4px; }.title { margin-top:6px; font-size:27px; font-weight:800; }.subtitle { margin-top:6px; color:#6f837e; font-size:14px; }.workbench-header button,.pagination button { display:inline-flex; align-items:center; justify-content:center; min-height:40px; margin:0; padding:0 15px; border:1px solid #bed2ce; border-radius:5px; background:#fff; color:#176f66; font-size:13px; }.message { display:grid; gap:5px; padding:14px 17px; border-radius:6px; font-size:14px; }.message.error { border:1px solid #edb6b1; background:#fff1ef; color:#a33730; }.message.success { border:1px solid #a9d9cf; background:#eaf8f4; color:#126d63; }.workbench-grid { display:grid; grid-template-columns:minmax(290px,.85fr) minmax(520px,1.6fr); min-height:590px; border:1px solid #d8e5e2; background:#fff; }.nurse-pane,.review-pane { min-width:0; padding:18px; }.nurse-pane { border-right:1px solid #e0e9e7; }.pane-heading { min-height:38px; padding-bottom:13px; border-bottom:1px solid #e4ecea; }.pane-heading strong { font-size:17px; }.pane-heading text { color:#71847f; font-size:13px; }.nurse-list { display:grid; gap:8px; margin-top:13px; }.nurse-item { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; width:100%; min-height:94px; margin:0; padding:14px; border:1px solid #dce7e4; border-radius:6px; background:#fff; color:#23413c; text-align:left; }.nurse-item.selected { border-color:#4eaea0; background:#eaf7f4; box-shadow:inset 3px 0 #1b8d81; }.nurse-item strong { font-size:15px; }.nurse-item view > text { margin-top:5px; color:#738681; font-size:12px; }.qualification-chip { flex:none; padding:5px 8px; border-radius:999px; background:#dcf3ed; color:#087064; font-size:11px; font-weight:800; }.pagination { justify-content:center; margin-top:14px; }.pagination button { min-height:36px; }.pagination text { color:#667b76; font-size:12px; }.qualification-summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-top:15px; }.qualification-summary view { padding:12px 13px; border-left:3px solid #7fc0b5; background:#f5faf8; }.qualification-summary text { color:#738681; font-size:12px; }.qualification-summary strong { margin-top:5px; font-size:14px; overflow-wrap:anywhere; }.form-section,.field { display:block; margin-top:20px; }.field-title,.field > text { display:block; margin-bottom:9px; color:#294640; font-size:14px; font-weight:800; }.status-options { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; }.status-options button { display:flex; align-items:center; justify-content:center; min-height:44px; margin:0; padding:0 8px; border:1px solid #cadbd7; border-radius:5px; background:#fff; color:#506963; font-size:13px; }.status-options button.selected { font-weight:800; }.status-approved.selected { border-color:#54aa9d; background:#e3f4ef; color:#0d7167; }.status-rejected.selected { border-color:#dc8c84; background:#fff0ee; color:#a53b34; }.status-need_more.selected,.status-pending.selected { border-color:#dbb05e; background:#fff7e5; color:#805b10; }.field input,.field textarea { box-sizing:border-box; width:100%; border:1px solid #cadbd7; border-radius:6px; background:#fbfdfc; color:#193632; font-size:14px; }.field input { min-height:46px; padding:0 12px; }.field textarea { min-height:130px; padding:12px; line-height:1.6; }.field small { margin-top:6px; color:#758782; text-align:right; font-size:12px; }.expiry-grid { display:grid; grid-template-columns:minmax(0,1fr) 170px; gap:10px; }.submit-command { display:flex; align-items:center; justify-content:center; width:100%; min-height:48px; margin:20px 0 0; border:0; border-radius:5px; background:#167d72; color:#fff; font-size:15px; font-weight:800; }.pane-empty { padding:20px 8px; color:#70837e; font-size:14px; line-height:1.6; }.pane-empty.spacious { display:grid; min-height:420px; place-items:center; text-align:center; }.workbench-header button[disabled],.pagination button[disabled],.nurse-item[disabled],.status-options button[disabled],.submit-command[disabled] { opacity:.5; }
@media (max-width:900px) { .workbench-grid { grid-template-columns:1fr; }.nurse-pane { border-right:0; border-bottom:1px solid #e0e9e7; }.qualification-summary { grid-template-columns:1fr; }.status-options { grid-template-columns:repeat(2,minmax(0,1fr)); }.expiry-grid { grid-template-columns:1fr; }.pane-empty.spacious { min-height:180px; } }
</style>
