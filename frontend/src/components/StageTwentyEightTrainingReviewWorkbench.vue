<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  getQualificationApplications,
  getQualificationPermissions
} from '@/api/stageTwentySix';
import { getAdminTrainingStatus, reviewNurseTraining } from '@/api/stageTwentyEight';
import type { QualificationApplicationRecord, QualificationTrainingOverview } from '@/types/stageTwentySix';
import type {
  CompletedTrainingReview,
  TrainingDisplayStatus,
  TrainingReviewStatus
} from '@/types/stageTwentyEight';
import {
  qualificationDateTime,
  qualificationStatusLabel
} from '@/utils/stageTwentySixRules';
import {
  TRAINING_REMARK_MAX_LENGTH,
  canReviewTrainingByPermission,
  combineTrainingExpiry,
  effectiveTrainingDisplayStatus,
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
const trainingBatchCodes = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');

type TrainingListStatus = TrainingDisplayStatus | 'NOT_STARTED' | 'LOADING' | 'ERROR';

interface TrainingListState {
  status: TrainingListStatus;
  expiredAt: string;
}

const loading = ref(false);
const allowed = ref(false);
const records = ref<QualificationApplicationRecord[]>([]);
const page = ref(1);
const size = 10;
const total = ref(0);
const selectedNurseId = ref('');
const status = ref<TrainingReviewStatus | ''>('');
const trainingBatchMonth = ref(localDateValue().slice(0, 7));
const trainingBatchCode = ref('A');
const trainingBatch = computed(() => `${trainingBatchMonth.value}-${trainingBatchCode.value}`);
const expiryDate = ref('');
const expiryTime = ref('');
const remark = ref('');
const error = ref('');
const submittingNurseId = ref('');
const completed = ref<CompletedTrainingReview | null>(null);
const currentTraining = ref<QualificationTrainingOverview | null>(null);
const trainingLoading = ref(false);
const trainingError = ref('');
const trainingListStates = ref<Record<string, TrainingListState>>({});
let listRequestSequence = 0;
let reviewRequestSequence = 0;
let contextRequestSequence = 0;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)));
const selected = computed(() => records.value.find(
  (item) => item.nurseId === selectedNurseId.value
) ?? null);
const minExpiryDate = computed(() => localDateValue());
const canSubmit = computed(() => Boolean(selected.value)
  && !submittingNurseId.value && !trainingLoading.value && !trainingError.value);

function listErrorMessage(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有查看护理培训名单的权限。';
  if (code === 502) return '护理培训名单内容不完整，请稍后刷新。';
  return '护理培训名单暂时无法读取，请稍后重试。';
}

function trainingListStatusLabel(status: TrainingListStatus) {
  if (status === 'NOT_STARTED') return '待培训';
  if (status === 'LOADING') return '读取中';
  if (status === 'ERROR') return '状态读取失败';
  return trainingStatusLabel(status);
}

function trainingListState(nurseId: string): TrainingListState {
  return trainingListStates.value[nurseId] ?? { status: 'LOADING', expiredAt: '' };
}

function setTrainingListState(nurseId: string, state: TrainingListState) {
  trainingListStates.value = { ...trainingListStates.value, [nurseId]: state };
}

function listStateFromOverview(overview: QualificationTrainingOverview): TrainingListState {
  return {
    status: effectiveTrainingDisplayStatus(overview.trainingStatus, overview.expiredAt) ?? 'ERROR',
    expiredAt: overview.expiredAt
  };
}

async function loadTrainingListStates(
  nurseRecords: QualificationApplicationRecord[],
  listSequence: number
) {
  trainingListStates.value = Object.fromEntries(
    nurseRecords.map((record) => [record.nurseId, { status: 'LOADING', expiredAt: '' }])
  );
  const results = await Promise.all(nurseRecords.map(async (record) => ({
    nurseId: record.nurseId,
    response: await getAdminTrainingStatus(record.nurseId)
  })));
  if (listSequence !== listRequestSequence) return;
  trainingListStates.value = Object.fromEntries(results.map(({ nurseId, response }) => {
    if (response.code === 404) return [nurseId, { status: 'NOT_STARTED', expiredAt: '' }];
    if (response.code !== 0) return [nurseId, { status: 'ERROR', expiredAt: '' }];
    return [nurseId, listStateFromOverview(response.data)];
  }));
}

function resetForm() {
  status.value = '';
  trainingBatchMonth.value = localDateValue().slice(0, 7);
  trainingBatchCode.value = 'A';
  expiryDate.value = '';
  expiryTime.value = '';
  remark.value = '';
  error.value = '';
}

function prefillTrainingForm(training: QualificationTrainingOverview) {
  const batchMatch = /^(\d{4}-\d{2})-([A-Z])$/.exec(training.trainingBatch);
  if (batchMatch && trainingBatchCodes.includes(batchMatch[2])) {
    trainingBatchMonth.value = batchMatch[1];
    trainingBatchCode.value = batchMatch[2];
  }

  remark.value = training.remark;
  if (training.trainingStatus === 'EXPIRED') {
    // 已过期的记录需要重新审核，默认引导管理员填写新的通过有效期。
    status.value = 'APPROVED';
    expiryDate.value = '';
    expiryTime.value = '';
    return;
  }

  status.value = training.trainingStatus;
  if (training.trainingStatus === 'APPROVED' && training.expiredAt) {
    const normalized = training.expiredAt.replace(' ', 'T');
    expiryDate.value = normalized.slice(0, 10);
    expiryTime.value = normalized.slice(11, 16);
  }
}

async function selectNurse(record: QualificationApplicationRecord) {
  if (submittingNurseId.value) return;
  selectedNurseId.value = record.nurseId;
  completed.value = null;
  resetForm();
  await loadSelectedTraining(record.nurseId);
}

function pickerValue(event: unknown) {
  return (event as { detail?: { value?: unknown } }).detail?.value;
}

function changeTrainingBatchMonth(event: unknown) {
  const month = String(pickerValue(event) ?? '').slice(0, 7);
  if (/^\d{4}-\d{2}$/.test(month)) trainingBatchMonth.value = month;
}

function changeTrainingBatchCode(event: unknown) {
  const code = trainingBatchCodes[Number(pickerValue(event))];
  if (code) trainingBatchCode.value = code;
}

function changeExpiryDate(event: unknown) {
  const date = String(pickerValue(event) ?? '');
  if (/^\d{4}-\d{2}-\d{2}$/.test(date)) expiryDate.value = date;
}

function changeExpiryTime(event: unknown) {
  const time = String(pickerValue(event) ?? '');
  if (/^\d{2}:\d{2}$/.test(time)) expiryTime.value = time;
}

function monthLabel(value: string) {
  const [year, month] = value.split('-');
  return year && month ? `${year} 年 ${month} 月` : '选择培训月份';
}

function dateLabel(value: string) {
  const [year, month, day] = value.split('-');
  return year && month && day ? `${year} 年 ${month} 月 ${day} 日` : '选择到期日期';
}

async function loadSelectedTraining(nurseId: string) {
  const sequence = ++contextRequestSequence;
  trainingLoading.value = true;
  trainingError.value = '';
  currentTraining.value = null;
  const response = await getAdminTrainingStatus(nurseId);
  if (sequence !== contextRequestSequence || selectedNurseId.value !== nurseId) return;
  trainingLoading.value = false;
  if (response.code === 404) {
    setTrainingListState(nurseId, { status: 'NOT_STARTED', expiredAt: '' });
    return;
  }
  if (response.code !== 0) {
    setTrainingListState(nurseId, { status: 'ERROR', expiredAt: '' });
    trainingError.value = response.code === 403
      ? '当前账号没有读取该护理培训状态的权限。'
      : '该护理的培训状态暂时无法读取，请稍后重试。';
    return;
  }
  currentTraining.value = response.data;
  prefillTrainingForm(response.data);
  setTrainingListState(nurseId, listStateFromOverview(response.data));
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
  if (response.code !== 0) {
    loading.value = false;
    records.value = [];
    total.value = 0;
    selectedNurseId.value = '';
    trainingListStates.value = {};
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
  await loadTrainingListStates(records.value, sequence);
  if (sequence !== listRequestSequence) return;
  loading.value = false;
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
      if (selectedNurseId.value === nurseId) await loadSelectedTraining(nurseId);
      error.value = `${responseError}已重新读取当前培训状态。`;
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
  if (selectedNurseId.value === nurseId) {
    await loadSelectedTraining(nurseId);
    if (!currentTraining.value || currentTraining.value.trainingStatus !== targetStatus) {
      error.value = '培训审核已提交，但最新状态尚未同步，请刷新后确认。';
      submittingNurseId.value = '';
      return;
    }
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
  currentTraining.value = null;
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
            <view>
              <strong>{{ record.nurseName }}</strong>
              <text>{{ record.realName }}</text>
              <text>资质审核已通过{{ record.reviewedAt ? ` · ${qualificationDateTime(record.reviewedAt)}` : '' }}</text>
              <text v-if="trainingListState(record.nurseId).status === 'APPROVED' && trainingListState(record.nurseId).expiredAt">
                培训有效期至 {{ qualificationDateTime(trainingListState(record.nurseId).expiredAt) }}
              </text>
            </view>
            <text
              class="training-chip"
              :class="`training-${trainingListState(record.nurseId).status.toLowerCase()}`"
            >{{ trainingListStatusLabel(trainingListState(record.nurseId).status) }}</text>
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

          <view class="training-current" :class="{ error: trainingError }">
            <template v-if="trainingLoading"><text>正在读取当前培训状态...</text></template>
            <template v-else-if="trainingError"><strong>当前培训状态读取失败</strong><text>{{ trainingError }}</text></template>
            <template v-else-if="currentTraining">
              <view><text>当前培训状态</text><strong>{{ trainingStatusLabel(currentTraining.trainingStatus) }}</strong></view>
              <view><text>培训批次</text><strong>{{ currentTraining.trainingBatch }}</strong></view>
              <view><text>有效期</text><strong>{{ currentTraining.expiredAt ? qualificationDateTime(currentTraining.expiredAt) : '暂无有效期' }}</strong></view>
              <text v-if="currentTraining.remark" class="training-remark">审核说明：{{ currentTraining.remark }}</text>
            </template>
            <template v-else><strong>尚未登记培训结果</strong><text>可在下方完成首次培训审核。</text></template>
          </view>

          <view class="form-section">
            <text class="field-title">培训结果</text>
            <view class="status-options">
              <button v-for="item in statusOptions" :key="item.value" type="button" :class="[`status-${item.value.toLowerCase()}`, { selected: status === item.value }]" :disabled="Boolean(submittingNurseId)" @click="selectStatus(item.value)">{{ item.label }}</button>
            </view>
          </view>

          <view class="field">
            <text>培训批次 *</text>
            <view class="batch-grid">
              <picker mode="date" fields="month" start="2020-01-01" :end="localDateValue()" :value="`${trainingBatchMonth}-01`" @change="changeTrainingBatchMonth">
                <view class="picker-control">{{ monthLabel(trainingBatchMonth) }}</view>
              </picker>
              <picker mode="selector" :range="trainingBatchCodes.map(code => `第 ${code} 批`)" :value="trainingBatchCodes.indexOf(trainingBatchCode)" @change="changeTrainingBatchCode">
                <view class="picker-control">第 {{ trainingBatchCode }} 批</view>
              </picker>
            </view>
            <small class="field-hint">系统生成的批次编号：{{ trainingBatch }}</small>
          </view>

          <view v-if="status === 'APPROVED'" class="field">
            <text>培训有效期 *</text>
            <view class="expiry-grid">
              <picker mode="date" :start="minExpiryDate" :value="expiryDate" @change="changeExpiryDate">
                <view class="picker-control">{{ dateLabel(expiryDate) }}</view>
              </picker>
              <picker mode="time" :value="expiryTime" @change="changeExpiryTime">
                <view class="picker-control">{{ expiryTime || '选择到期时间' }}</view>
              </picker>
            </view>
            <small class="field-hint">只能选择当前时间之后的有效期。</small>
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
.training-review-workbench { display:grid; gap:16px; color:#183530; }.workbench-header,.pane-heading,.pagination { display:flex; align-items:center; justify-content:space-between; gap:12px; }.workbench-header > view > text,.nurse-item text,.qualification-summary text,.qualification-summary strong,.field > text,.field small,.message text,.message strong { display:block; }.kicker { color:#23877d; font-size:12px; font-weight:800; letter-spacing:1.4px; }.title { margin-top:6px; font-size:27px; font-weight:800; }.subtitle { margin-top:6px; color:#6f837e; font-size:14px; }.workbench-header button,.pagination button { display:inline-flex; align-items:center; justify-content:center; min-height:40px; margin:0; padding:0 15px; border:1px solid #bed2ce; border-radius:5px; background:#fff; color:#176f66; font-size:13px; }.message { display:grid; gap:5px; padding:14px 17px; border-radius:6px; font-size:14px; }.message.error { border:1px solid #edb6b1; background:#fff1ef; color:#a33730; }.message.success { border:1px solid #a9d9cf; background:#eaf8f4; color:#126d63; }.workbench-grid { display:grid; grid-template-columns:minmax(290px,.85fr) minmax(520px,1.6fr); min-height:590px; border:1px solid #d8e5e2; background:#fff; }.nurse-pane,.review-pane { min-width:0; padding:18px; }.nurse-pane { border-right:1px solid #e0e9e7; }.pane-heading { min-height:38px; padding-bottom:13px; border-bottom:1px solid #e4ecea; }.pane-heading strong { font-size:17px; }.pane-heading text { color:#71847f; font-size:13px; }.nurse-list { display:grid; gap:8px; margin-top:13px; }.nurse-item { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; width:100%; min-height:94px; margin:0; padding:14px; border:1px solid #dce7e4; border-radius:6px; background:#fff; color:#23413c; text-align:left; }.nurse-item.selected { border-color:#4eaea0; background:#eaf7f4; box-shadow:inset 3px 0 #1b8d81; }.nurse-item strong { font-size:15px; }.nurse-item view > text { margin-top:5px; color:#738681; font-size:12px; }.training-chip { flex:none; padding:5px 8px; border-radius:999px; background:#edf2f1; color:#516a65; font-size:11px; font-weight:800; white-space:nowrap; }.training-approved { background:#dcf3ed; color:#087064; }.training-pending,.training-not_started { background:#fff0cf; color:#895d08; }.training-rejected { background:#ffe5e2; color:#a43d35; }.training-need_more { background:#fff0d9; color:#91600b; }.training-expired { background:#f2e7e5; color:#894b45; }.training-error { background:#ffe8e5; color:#a43d35; }.pagination { justify-content:center; margin-top:14px; }.pagination button { min-height:36px; }.pagination text { color:#667b76; font-size:12px; }.qualification-summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-top:15px; }.qualification-summary view { padding:12px 13px; border-left:3px solid #7fc0b5; background:#f5faf8; }.qualification-summary text { color:#738681; font-size:12px; }.qualification-summary strong { margin-top:5px; font-size:14px; overflow-wrap:anywhere; }.training-current { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-top:12px; padding:13px; border-left:3px solid #7fc0b5; background:#f5faf8; }.training-current > text,.training-current > strong,.training-current view > text,.training-current view > strong { display:block; }.training-current view > text,.training-current > text { color:#738681; font-size:12px; }.training-current view > strong,.training-current > strong { margin-top:4px; font-size:14px; }.training-current.error { grid-template-columns:1fr; border-left-color:#d36d65; background:#fff1ef; color:#a33730; }.training-remark { grid-column:1/-1; }.form-section,.field { display:block; margin-top:20px; }.field-title,.field > text { display:block; margin-bottom:9px; color:#294640; font-size:14px; font-weight:800; }.status-options { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; }.status-options button { display:flex; align-items:center; justify-content:center; min-height:44px; margin:0; padding:0 8px; border:1px solid #cadbd7; border-radius:5px; background:#fff; color:#506963; font-size:13px; }.status-options button.selected { font-weight:800; }.status-approved.selected { border-color:#54aa9d; background:#e3f4ef; color:#0d7167; }.status-rejected.selected { border-color:#dc8c84; background:#fff0ee; color:#a53b34; }.status-need_more.selected,.status-pending.selected { border-color:#dbb05e; background:#fff7e5; color:#805b10; }.field input,.field textarea { box-sizing:border-box; width:100%; border:1px solid #cadbd7; border-radius:6px; background:#fbfdfc; color:#193632; font-size:14px; }.field input { min-height:46px; padding:0 12px; }.field textarea { min-height:130px; padding:12px; line-height:1.6; }.field small { margin-top:6px; color:#758782; text-align:right; font-size:12px; }.expiry-grid { display:grid; grid-template-columns:minmax(0,1fr) 170px; gap:10px; }.submit-command { display:flex; align-items:center; justify-content:center; width:100%; min-height:48px; margin:20px 0 0; border:0; border-radius:5px; background:#167d72; color:#fff; font-size:15px; font-weight:800; }.pane-empty { padding:20px 8px; color:#70837e; font-size:14px; line-height:1.6; }.pane-empty.spacious { display:grid; min-height:420px; place-items:center; text-align:center; }.workbench-header button[disabled],.pagination button[disabled],.nurse-item[disabled],.status-options button[disabled],.submit-command[disabled] { opacity:.5; }
.form-section,.field,.submit-command { box-sizing:border-box; width:100%; max-width:980px; }
.batch-grid,.expiry-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; width:100%; max-width:100%; }
.batch-grid picker,.expiry-grid picker { display:block; min-width:0; width:100%; max-width:100%; }
.picker-control { box-sizing:border-box; width:100%; max-width:100%; min-height:48px; padding:0 14px; overflow:hidden; border:1px solid #cadbd7; border-radius:6px; background:#fbfdfc; color:#193632; font-size:14px; font-weight:700; line-height:48px; text-overflow:ellipsis; white-space:nowrap; }
.field .field-hint { max-width:100%; margin-top:7px; overflow-wrap:anywhere; color:#6f837e; text-align:left; line-height:1.5; }
@media (max-width:900px) { .workbench-grid { grid-template-columns:1fr; }.nurse-pane { border-right:0; border-bottom:1px solid #e0e9e7; }.qualification-summary { grid-template-columns:1fr; }.status-options { grid-template-columns:repeat(2,minmax(0,1fr)); }.batch-grid,.expiry-grid { grid-template-columns:1fr; }.pane-empty.spacious { min-height:180px; } }
</style>
