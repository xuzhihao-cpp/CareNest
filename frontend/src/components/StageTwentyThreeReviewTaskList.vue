<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  getAdminHealthReviewTasks,
  getHealthReviewPermissions
} from '@/api/stageTwentyThree';
import {
  archiveHealthReviewTask,
  getAdminHealthArchiveChangeLogs,
  getHealthReviewTaskDetail
} from '@/api/stageTwentyFour';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type {
  AdminHealthReviewTaskQuery,
  AdminHealthReviewTaskRecord,
  HealthReviewSourceType,
  HealthReviewTaskStatus
} from '@/types/stageTwentyThree';
import type {
  HealthArchiveDecisionDraft,
  HealthArchiveFieldDecision,
  HealthArchiveChangeLogRecord,
  HealthReviewArchiveResult,
  HealthReviewTaskDetail
} from '@/types/stageTwentyFour';
import {
  canViewHealthReviewTasks,
  formatHealthSuggestionValue,
  HEALTH_REVIEW_SOURCE_LABELS,
  HEALTH_REVIEW_STATUS_LABELS
} from '@/utils/stageTwentyThreeRules';
import {
  buildArchiveDecisionDrafts,
  changeLogBusinessTitle,
  formatArchiveChangeValue,
  HEALTH_ARCHIVE_DECISION_OPTIONS,
  toArchiveDecisionRequests,
  validateArchiveDecisions
} from '@/utils/stageTwentyFourRules';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const statusOptions: Array<{ value: HealthReviewTaskStatus | ''; label: string }> = [
  { value: 'PENDING', label: '待审核' },
  { value: 'IN_REVIEW', label: '审核中' },
  { value: '', label: '全部状态' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'REJECTED', label: '未采纳' }
];
const sourceOptions: Array<{ value: HealthReviewSourceType | ''; label: string }> = [
  { value: '', label: '全部来源' },
  { value: 'SERVICE_RECORD', label: '服务记录' },
  { value: 'SERVICE_REPORT', label: '服务报告' },
  { value: 'MEDICAL_FILE', label: '病历资料' },
  { value: 'REPORT_ACK', label: '报告确认' },
  { value: 'SUGGESTION', label: '护理建议' },
  { value: 'MANUAL', label: '人工复核' }
];

const query = ref<AdminHealthReviewTaskQuery>({ page: 1, size: 20, status: 'PENDING', sourceType: '', keyword: '' });
const records = ref<AdminHealthReviewTaskRecord[]>([]);
const total = ref(0);
const permissionCodes = ref<string[]>([]);
const permissionsLoading = ref(true);
const permissionsLoaded = ref(false);
const loading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const error = ref('');
const detailError = ref('');
const historyError = ref('');
const selectedTaskId = ref('');
const detail = ref<HealthReviewTaskDetail | null>(null);
const decisions = ref<HealthArchiveDecisionDraft[]>([]);
const changeLogs = ref<HealthArchiveChangeLogRecord[]>([]);
const archiveResult = ref<HealthReviewArchiveResult | null>(null);
let listRequestSequence = 0;
let detailRequestSequence = 0;
let historyRequestSequence = 0;

const canUsePanel = computed(() => permissionsLoaded.value && canViewHealthReviewTasks(
  props.authUser?.roles ?? [],
  permissionCodes.value
));
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.value.size)));
const canSubmit = computed(() => Boolean(
  detail.value && (detail.value.status === 'PENDING' || detail.value.status === 'IN_REVIEW')
));
const allDecisionsSelected = computed(() => Boolean(
  decisions.value.length && decisions.value.every((item) => item.decision)
));

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function statusClass(status: HealthReviewTaskStatus) {
  return `status-${status.toLowerCase().replace('_', '-')}`;
}

function businessError(code: number, action: 'list' | 'detail' | 'archive' | 'history') {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return action === 'history'
    ? '当前账号无权查看这位长辈的档案变更记录。'
    : '当前账号没有健康信息审核归档权限。';
  if (code === 404) return action === 'detail'
    ? '该审核任务不存在或已被移除。'
    : '健康信息审核归档服务暂不可用。';
  if (code === 409) return '该任务已被其他审核人员处理，正在读取最新结果。';
  if (code === 422) return '归档决定与任务字段不匹配，请刷新任务后重试。';
  if (code === 502) return '审核数据响应与当前契约不一致，请联系平台维护人员。';
  return action === 'archive' ? '健康档案暂时无法归档，请稍后重试。' : '健康信息审核数据暂时无法读取，请稍后重试。';
}

function clearTaskSelection() {
  ++detailRequestSequence;
  ++historyRequestSequence;
  selectedTaskId.value = '';
  detail.value = null;
  decisions.value = [];
  changeLogs.value = [];
  archiveResult.value = null;
  detailError.value = '';
  historyError.value = '';
  detailLoading.value = false;
}

async function loadTasks(selectFallback = true) {
  if (!canUsePanel.value) return;
  const sequence = ++listRequestSequence;
  loading.value = true;
  error.value = '';
  const response = await getAdminHealthReviewTasks(query.value);
  if (sequence !== listRequestSequence) return;
  loading.value = false;
  if (response.code !== 0) {
    records.value = [];
    total.value = 0;
    clearTaskSelection();
    error.value = businessError(response.code, 'list');
    return;
  }
  records.value = response.data.records;
  total.value = response.data.total;
  query.value.page = response.data.page;
  query.value.size = response.data.size;
  if (!selectFallback) return;
  const nextTaskId = records.value.some((item) => item.taskId === selectedTaskId.value)
    ? selectedTaskId.value
    : records.value[0]?.taskId ?? '';
  if (!nextTaskId) {
    clearTaskSelection();
    return;
  }
  await selectTask(nextTaskId);
}

async function loadChangeLogs(elderId: string, taskId: string) {
  const sequence = ++historyRequestSequence;
  changeLogs.value = [];
  historyError.value = '';
  const response = await getAdminHealthArchiveChangeLogs(elderId);
  if (sequence !== historyRequestSequence || selectedTaskId.value !== taskId) return;
  if (response.code !== 0) {
    historyError.value = businessError(response.code, 'history');
    return;
  }
  changeLogs.value = response.data.records.slice(0, 5);
}

async function loadTaskDetail(taskId: string, preserveArchiveResult = false) {
  const sequence = ++detailRequestSequence;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  decisions.value = [];
  changeLogs.value = [];
  historyError.value = '';
  if (!preserveArchiveResult) archiveResult.value = null;
  const taskContext = records.value.find((item) => item.taskId === taskId);
  const response = await getHealthReviewTaskDetail(taskId, taskContext);
  if (sequence !== detailRequestSequence || selectedTaskId.value !== taskId) return;
  detailLoading.value = false;
  if (response.code !== 0) {
    detailError.value = businessError(response.code, 'detail');
    return;
  }
  detail.value = response.data;
  decisions.value = buildArchiveDecisionDrafts(response.data.fields);
  await loadChangeLogs(response.data.elderId, taskId);
}

async function selectTask(taskId: string) {
  if (!taskId) return;
  selectedTaskId.value = taskId;
  await loadTaskDetail(taskId);
}

function applyFilters() {
  query.value.page = 1;
  loadTasks();
}

function resetFilters() {
  query.value = { page: 1, size: 20, status: 'PENDING', sourceType: '', keyword: '' };
  loadTasks();
}

function setStatus(status: HealthReviewTaskStatus | '') {
  query.value.status = status;
  applyFilters();
}

function setSource(event: { detail: { value: number | string } }) {
  query.value.sourceType = sourceOptions[Number(event.detail.value)]?.value ?? '';
  applyFilters();
}

function setDecision(index: number, decision: HealthArchiveFieldDecision) {
  if (!decisions.value[index]) return;
  decisions.value[index].decision = decision;
  if (decision === 'APPROVE') decisions.value[index].comment = '';
  detailError.value = '';
}

async function changePage(offset: number) {
  const next = query.value.page + offset;
  if (next < 1 || next > totalPages.value || loading.value) return;
  query.value.page = next;
  await loadTasks();
}

async function refreshSelectedTask(message = '') {
  const taskId = selectedTaskId.value;
  await loadTasks(false);
  if (!taskId || selectedTaskId.value !== taskId) return;
  await loadTaskDetail(taskId);
  if (message && selectedTaskId.value === taskId) detailError.value = message;
}

async function submitArchive() {
  if (!detail.value || !canSubmit.value || submitting.value) return;
  const validationError = validateArchiveDecisions(decisions.value);
  if (validationError) {
    detailError.value = validationError;
    return;
  }
  const taskId = detail.value.taskId;
  submitting.value = true;
  detailError.value = '';
  archiveResult.value = null;
  const response = await archiveHealthReviewTask(taskId, {
    decisions: toArchiveDecisionRequests(decisions.value)
  });
  submitting.value = false;
  if (response.code === 409) {
    await loadTasks(false);
    if (selectedTaskId.value === taskId) {
      await loadTaskDetail(taskId);
      if (selectedTaskId.value === taskId) detailError.value = businessError(409, 'archive');
    }
    return;
  }
  if (response.code !== 0) {
    if (selectedTaskId.value !== taskId) return;
    detailError.value = businessError(response.code, 'archive');
    return;
  }
  await loadTasks(false);
  if (selectedTaskId.value !== taskId) return;
  archiveResult.value = response.data;
  await loadTaskDetail(taskId, true);
  if (selectedTaskId.value === taskId && !detail.value && !detailError.value) {
    detailError.value = '归档已完成，但最新任务详情暂时无法读取，请点击刷新。';
  }
}

async function initialize() {
  permissionsLoading.value = true;
  error.value = '';
  const response = await getHealthReviewPermissions();
  permissionsLoading.value = false;
  permissionsLoaded.value = true;
  if (response.code !== 0) {
    error.value = businessError(response.code, 'list');
    return;
  }
  permissionCodes.value = response.data;
  if (!canUsePanel.value) {
    error.value = '当前账号没有健康信息审核归档权限。';
    return;
  }
  await loadTasks();
}

onMounted(initialize);
</script>

<template>
  <section class="health-review-panel">
    <header class="panel-header">
      <view>
        <text class="eyebrow">健康档案审核</text>
        <text class="title">健康信息审核归档</text>
        <text class="subtitle">逐项核对护理与病历建议，只有采纳归档的内容会写入正式健康档案。</text>
      </view>
      <button type="button" :disabled="loading || detailLoading || !canUsePanel" @click="refreshSelectedTask()">刷新</button>
    </header>

    <view v-if="permissionsLoading" class="state-card">正在核对审核权限...</view>
    <view v-else-if="error && !canUsePanel" class="state-card error">{{ error }}</view>
    <template v-else-if="canUsePanel">
      <view class="filters">
        <view class="status-tabs">
          <button v-for="option in statusOptions" :key="option.value || 'all'" type="button" :class="{ active: query.status === option.value }" @click="setStatus(option.value)">{{ option.label }}</button>
        </view>
        <view class="filter-row">
          <picker :range="sourceOptions" range-key="label" :value="Math.max(0, sourceOptions.findIndex((item) => item.value === query.sourceType))" @change="setSource"><view class="select-box">{{ sourceOptions.find((item) => item.value === query.sourceType)?.label }}</view></picker>
          <input v-model="query.keyword" maxlength="50" placeholder="搜索长辈、服务或建议原因" @confirm="applyFilters" />
          <button type="button" class="primary" @click="applyFilters">查询</button>
          <button type="button" @click="resetFilters">重置</button>
        </view>
      </view>

      <view v-if="error" class="state-card error">{{ error }}</view>
      <view class="review-workspace">
        <aside class="task-pane">
          <view class="pane-heading"><text>审核任务</text><small>{{ loading ? '读取中' : `${total} 项` }}</small></view>
          <view v-if="!loading && records.length === 0" class="pane-empty">当前筛选条件下暂无审核任务。</view>
          <button
            v-for="record in records"
            :key="record.taskId"
            type="button"
            class="task-choice"
            :class="{ selected: selectedTaskId === record.taskId }"
            @click="selectTask(record.taskId)"
          >
            <view class="task-choice-top"><strong>{{ record.elderName }}</strong><text class="status-chip" :class="statusClass(record.status)">{{ HEALTH_REVIEW_STATUS_LABELS[record.status] }}</text></view>
            <text>{{ record.serviceName }}</text>
            <small>{{ HEALTH_REVIEW_SOURCE_LABELS[record.sourceType] }} · {{ formatDateTime(record.submittedAt) }}</small>
          </button>
          <footer v-if="totalPages > 1" class="pagination">
            <button type="button" :disabled="query.page <= 1 || loading" @click="changePage(-1)">上一页</button>
            <text>{{ query.page }} / {{ totalPages }}</text>
            <button type="button" :disabled="query.page >= totalPages || loading" @click="changePage(1)">下一页</button>
          </footer>
        </aside>

        <main class="comparison-pane">
          <view class="pane-heading"><text>来源与档案对比</text><small v-if="detail">当前档案版本 {{ detail.archiveVersion }}</small></view>
          <view v-if="detailLoading" class="pane-empty">正在读取任务详情...</view>
          <view v-else-if="detailError && !detail" class="pane-empty error-text">{{ detailError }}</view>
          <view v-else-if="!detail" class="pane-empty">请选择一项审核任务。</view>
          <template v-else>
            <view class="detail-summary">
              <view><text>服务对象</text><strong>{{ detail.elderName }}</strong></view>
              <view><text>相关服务</text><strong>{{ detail.serviceName }}</strong></view>
              <view><text>任务状态</text><strong>{{ HEALTH_REVIEW_STATUS_LABELS[detail.status] }}</strong></view>
            </view>
            <view class="evidence-band">
              <text>{{ HEALTH_REVIEW_SOURCE_LABELS[detail.evidence.sourceType] }}</text>
              <strong>{{ detail.evidence.title }}</strong>
              <p>{{ detail.evidence.summary }}</p>
              <small v-if="detail.evidence.occurredAt">发生于 {{ formatDateTime(detail.evidence.occurredAt) }}</small>
            </view>
            <article v-for="field in detail.fields" :key="`${field.sourceField}:${field.targetField}`" class="field-comparison">
              <header><strong>{{ field.fieldLabel }}</strong><text>已按健康档案字段规范整理</text></header>
              <view class="value-stack">
                <view><text>档案当前内容</text><p>{{ formatHealthSuggestionValue(field.targetField, field.currentValue) }}</p></view>
                <view><text>来源建议内容</text><p>{{ formatHealthSuggestionValue(field.targetField, field.suggestedValue) }}</p></view>
                <view class="normalized"><text>规范化后内容</text><p>{{ formatHealthSuggestionValue(field.targetField, field.normalizedValue) }}</p><small v-if="field.normalizationNote">{{ field.normalizationNote }}</small></view>
              </view>
            </article>
          </template>
        </main>

        <aside class="decision-pane">
          <view class="pane-heading"><text>逐字段处理</text><small>{{ decisions.length }} 项</small></view>
          <view v-if="!detail" class="pane-empty">选择任务后可填写审核决定。</view>
          <template v-else>
            <view v-if="archiveResult" class="archive-result">
              <strong>审核结果已同步</strong>
              <text>任务状态：{{ HEALTH_REVIEW_STATUS_LABELS[archiveResult.status] }}</text>
              <text>最新档案版本：{{ archiveResult.archiveVersion }}</text>
            </view>
            <view v-if="detailError" class="state-card error">{{ detailError }}</view>
            <view v-if="canSubmit" class="decision-list">
              <view v-for="(item, index) in decisions" :key="`${item.sourceField}:${item.targetField}`" class="decision-item">
                <strong>{{ item.fieldLabel }}</strong>
                <view class="decision-options">
                  <button v-for="option in HEALTH_ARCHIVE_DECISION_OPTIONS" :key="option.value" type="button" :class="{ active: item.decision === option.value }" :title="option.help" @click="setDecision(index, option.value)">{{ option.label }}</button>
                </view>
                <text v-if="!item.decision" class="decision-required">请明确选择这一项的处理决定</text>
                <label v-else><text>{{ item.decision === 'APPROVE' ? '审核说明（可选）' : '审核说明（必填）' }}</text><textarea v-model="item.comment" maxlength="255" :placeholder="item.decision === 'REJECT' ? '说明不采纳原因' : item.decision === 'NEED_MORE' ? '说明需要补充的内容' : '可补充归档说明'" /><small>{{ item.comment.length }}/255</small></label>
              </view>
              <button class="archive-command" type="button" :disabled="submitting || !allDecisionsSelected" @click="submitArchive">{{ submitting ? '正在提交审核结果' : allDecisionsSelected ? '提交审核结果' : '请先完成全部处理决定' }}</button>
            </view>
            <view v-else class="completed-note">该任务已经完成处理，不能重复提交。</view>
            <view class="history-summary">
              <view class="pane-heading"><text>最近变更</text><small>{{ changeLogs.length }} 条</small></view>
              <view v-if="historyError" class="state-card error">{{ historyError }}</view>
              <view v-else-if="changeLogs.length === 0" class="pane-empty compact">暂无可显示的变更记录。</view>
              <view v-for="log in changeLogs" :key="log.changeLogId" class="history-item">
                <strong>{{ changeLogBusinessTitle(log) }}</strong>
                <text>{{ formatDateTime(log.changedAt) }}<template v-if="log.archiveVersion !== undefined"> · 版本 {{ log.archiveVersion }}</template></text>
                <p>{{ formatArchiveChangeValue(log.fieldName, log.afterValue) }}</p>
              </view>
            </view>
          </template>
        </aside>
      </view>
    </template>
  </section>
</template>

<style scoped>
.health-review-panel { display:grid; gap:18px; min-width:0; color:#18312d; }.panel-header { display:flex; align-items:center; justify-content:space-between; gap:24px; }.panel-header>view { display:grid; gap:7px; }.eyebrow { color:#23877d; font-size:12px; font-weight:700; }.title { font-size:24px; font-weight:700; }.subtitle { color:#6f817d; font-size:13px; }.panel-header button,.filters button,.pagination button { display:inline-flex; align-items:center; justify-content:center; min-height:38px; margin:0; padding:0 14px; border:1px solid #cad9d6; border-radius:6px; background:#fff; color:#385752; font-size:13px; line-height:1.2; white-space:nowrap; }
.filters { display:grid; gap:14px; padding:16px; border:1px solid #dce7e4; border-radius:8px; background:#fff; }.status-tabs { display:flex; flex-wrap:wrap; gap:8px; }.status-tabs button { padding:8px 14px; }.status-tabs button.active { border-color:#69bdb2; background:#e6f6f2; color:#087369; font-weight:700; }.filter-row { display:grid; grid-template-columns:180px minmax(220px,1fr) auto auto; gap:10px; }.select-box,.filter-row input { box-sizing:border-box; width:100%; min-height:40px; padding:10px 12px; border:1px solid #d3dfdc; border-radius:6px; background:#fbfdfc; font-size:13px; }.filter-row button.primary { border-color:#147d72; background:#147d72; color:#fff; }
.state-card { padding:16px; border:1px solid #dce7e4; border-radius:7px; background:#fff; color:#667b77; }.state-card.error { border-color:#efb9b4; background:#fff1ef; color:#a83b34; }.review-workspace { display:grid; grid-template-columns:minmax(240px,.75fr) minmax(360px,1.2fr) minmax(300px,.9fr); min-height:620px; border:1px solid #dce7e4; border-radius:8px; background:#fff; overflow:hidden; }.task-pane,.comparison-pane,.decision-pane { min-width:0; padding:18px; }.task-pane,.comparison-pane { border-right:1px solid #dce7e4; }.task-pane { display:flex; flex-direction:column; gap:10px; background:#f8faf9; }.comparison-pane,.decision-pane { display:flex; flex-direction:column; gap:16px; }.pane-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; }.pane-heading text { font-size:16px; font-weight:800; }.pane-heading small { color:#71827e; font-size:12px; }.pane-empty { padding:22px 14px; color:#748580; font-size:13px; line-height:1.6; text-align:center; }.pane-empty.compact { padding:12px; }.error-text { color:#a83b34; }
.task-choice { display:grid; gap:7px; width:100%; margin:0; padding:14px; border:1px solid #d8e2df; border-radius:6px; background:#fff; color:#24433e; text-align:left; }.task-choice.selected { border-color:#61b9ad; background:#eaf7f4; }.task-choice>text { font-size:13px; }.task-choice>small { color:#748580; font-size:11px; }.task-choice-top { display:flex; align-items:center; justify-content:space-between; gap:10px; }.task-choice-top strong { min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:14px; }.status-chip { flex:0 0 auto; padding:4px 7px; border-radius:4px; background:#fff0d8; color:#956100; font-size:11px; font-weight:700; }.status-in-review { background:#e5effb; color:#31699b; }.status-archived { background:#e2f5ef; color:#087165; }.status-rejected { background:#f5e9e7; color:#9c4a42; }.pagination { display:flex; align-items:center; justify-content:center; gap:8px; margin-top:auto; padding-top:12px; color:#657a76; font-size:12px; }.pagination button { padding:7px 9px; }
.detail-summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; }.detail-summary>view { display:grid; gap:5px; padding:12px; border-left:3px solid #78bdb4; background:#f2f8f6; }.detail-summary text { color:#6b7f7a; font-size:11px; }.detail-summary strong { font-size:13px; overflow-wrap:anywhere; }.evidence-band { display:grid; gap:6px; padding:15px; border:1px solid #cbdeda; background:#f7fbfa; }.evidence-band>text { color:#168277; font-size:11px; font-weight:700; }.evidence-band>strong { font-size:15px; }.evidence-band p { margin:0; color:#4d6661; font-size:13px; line-height:1.65; }.evidence-band small { color:#788a86; font-size:11px; }.field-comparison { display:grid; gap:12px; padding-top:16px; border-top:1px solid #e3ebe9; }.field-comparison header { display:flex; align-items:baseline; justify-content:space-between; gap:12px; }.field-comparison header strong { font-size:16px; }.field-comparison header text { color:#748580; font-size:11px; }.value-stack { display:grid; gap:8px; }.value-stack>view { padding:12px; border-left:3px solid #c5d3d0; background:#f8faf9; }.value-stack>view.normalized { border-left-color:#168c81; background:#eaf7f4; }.value-stack text { display:block; margin-bottom:5px; color:#6b7d79; font-size:11px; font-weight:700; }.value-stack p { margin:0; color:#203c37; font-size:13px; line-height:1.65; white-space:pre-wrap; overflow-wrap:anywhere; }.value-stack small { display:block; margin-top:6px; color:#617773; font-size:11px; }
.archive-result { display:grid; gap:6px; padding:14px; border:1px solid #98d3c9; background:#e9f7f3; color:#12675e; }.archive-result strong { font-size:14px; }.archive-result text { font-size:12px; }.decision-list { display:grid; gap:16px; }.decision-item { display:grid; gap:10px; padding-bottom:16px; border-bottom:1px solid #e3ebe9; }.decision-item>strong { font-size:14px; }.decision-options { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:6px; }.decision-options button { min-height:40px; margin:0; padding:6px; border:1px solid #cbd9d6; border-radius:5px; background:#fff; color:#526b66; font-size:11px; line-height:1.35; }.decision-options button.active { border-color:#55b3a8; background:#e7f6f2; color:#087267; font-weight:700; }.decision-item label { display:grid; gap:6px; }.decision-item label>text { color:#61746f; font-size:12px; font-weight:700; }.decision-item textarea { box-sizing:border-box; width:100%; min-height:88px; padding:10px; border:1px solid #ccd9d6; border-radius:5px; background:#fff; font-size:13px; line-height:1.5; }.decision-item label>small { color:#81908d; font-size:10px; text-align:right; }.archive-command { width:100%; min-height:46px; margin:0; border:0; border-radius:6px; background:#147d72; color:#fff; font-size:14px; font-weight:700; }.completed-note { padding:15px; border-left:4px solid #94aaa5; background:#f2f5f4; color:#5d716d; font-size:13px; }.history-summary { display:grid; gap:10px; padding-top:16px; border-top:1px solid #dfe8e6; }.history-item { display:grid; gap:4px; padding:10px 0; border-bottom:1px solid #edf2f1; }.history-item strong { font-size:13px; }.history-item text { color:#748580; font-size:10px; }.history-item p { margin:0; color:#49635e; font-size:12px; line-height:1.55; overflow-wrap:anywhere; }
.decision-required { padding:9px 10px; border-left:3px solid #d49a35; background:#fff7e8; color:#875c0f; font-size:11px; }
@media (max-width:1200px) { .review-workspace { grid-template-columns:minmax(220px,.7fr) minmax(360px,1.3fr); }.decision-pane { grid-column:1 / -1; border-top:1px solid #dce7e4; }.decision-list { grid-template-columns:repeat(2,minmax(0,1fr)); }.archive-command { grid-column:1 / -1; }.history-summary { grid-column:1 / -1; } }
@media (max-width:800px) { .panel-header { align-items:flex-end; }.filter-row { grid-template-columns:1fr 1fr; }.review-workspace { grid-template-columns:1fr; }.task-pane,.comparison-pane { border-right:0; border-bottom:1px solid #dce7e4; }.decision-pane { grid-column:auto; }.decision-list { grid-template-columns:1fr; }.detail-summary { grid-template-columns:1fr; }.field-comparison header { align-items:flex-start; flex-direction:column; }.decision-options { grid-template-columns:1fr; } }
@media (min-width:801px) { .task-pane { max-height:calc(100vh - 260px); overflow-y:auto; overscroll-behavior:contain; scrollbar-gutter:stable; } }
@media (max-width:800px) { .task-pane { max-height:min(48vh,460px); overflow-y:auto; overscroll-behavior:contain; scrollbar-gutter:stable; } }
</style>
