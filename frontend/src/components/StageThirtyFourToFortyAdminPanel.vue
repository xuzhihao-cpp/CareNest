<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getServiceItems } from '@/api/stageEight';
import {
  generateMetricChecklist,
  getAdminEvidences,
  getAdminExceptionProofs,
  getCareMetricConfig,
  getMetricCheckResult,
  reviewCareEvidence,
  reviewMetricExceptionProof,
  runMetricCheck,
  saveCareMetricConfig
} from '@/api/stageThirtyFourToForty';
import type { ServiceItemResponse } from '@/types/stageEight';
import type {
  CareMetricConfigItem,
  EvidenceAuditStatus,
  EvidenceResponse,
  MetricCheckResponse,
  MetricChecklistResponse,
  ProofReviewResponse,
  ScoreDecision
} from '@/types/stageThirtyFourToForty';
import {
  CARE_METRIC_EVIDENCE_LABELS,
  CARE_METRIC_EVIDENCE_TYPES,
  CARE_METRIC_STATUS_LABELS,
  CARE_METRIC_TYPE_LABELS,
  CARE_METRIC_TYPES,
  compactBusinessId,
  createEmptyMetricConfigItem,
  EVIDENCE_AUDIT_STATUS_LABELS,
  EVIDENCE_REVIEW_TARGETS,
  PROOF_STATUS_LABELS,
  PROOF_REVIEW_TARGETS,
  SCORE_DECISION_LABELS,
  stageThirtyFourToFortyError,
  validateEvidenceReview,
  validateMetricConfigItems,
  validateProofReview
} from '@/utils/stageThirtyFourToFortyRules';

const props = defineProps<{
  permissions: string[];
}>();

type AdminMetricSection = 'config' | 'checklist' | 'evidence' | 'proof';
type PickerEvent = { detail: { value: number | string } };

const section = ref<AdminMetricSection>('config');
const services = ref<ServiceItemResponse[]>([]);
const selectedServiceId = ref('');
const configVersion = ref<number | null>(null);
const configItems = ref<CareMetricConfigItem[]>([createEmptyMetricConfigItem()]);
const orderId = ref('');
const checklist = ref<MetricChecklistResponse>({ items: [] });
const checkResult = ref<MetricCheckResponse>({ items: [] });
const evidences = ref<EvidenceResponse[]>([]);
const proofs = ref<ProofReviewResponse[]>([]);
const reviewForms = ref<Record<string, { auditStatus: Exclude<EvidenceAuditStatus, 'PENDING'>; reviewComment: string }>>({});
const proofForms = ref<Record<string, {
  reviewResult: Exclude<ProofReviewResponse['reviewStatus'], 'PENDING'>;
  reviewComment: string;
  scoreDecision: ScoreDecision;
}>>({});
const loading = ref(false);
const error = ref('');
const notice = ref('');

const canManageMetrics = computed(() => props.permissions.includes('CARE_METRIC_CONFIG_MANAGE'));
const canReviewEvidence = computed(() => props.permissions.includes('CARE_EVIDENCE_REVIEW'));
const canUsePanel = computed(() => canManageMetrics.value || canReviewEvidence.value);
const serviceOptions = computed(() => services.value.map((item) => ({
  value: item.serviceId,
  label: `${item.serviceName} (${item.status === 'ON_SHELF' ? '上架' : '下架'})`
})));
const metricTypeOptions = CARE_METRIC_TYPES.map((value) => ({ value, label: CARE_METRIC_TYPE_LABELS[value] }));
const evidenceTypeOptions = CARE_METRIC_EVIDENCE_TYPES.map((value) => ({ value, label: CARE_METRIC_EVIDENCE_LABELS[value] }));
const evidenceReviewOptions = EVIDENCE_REVIEW_TARGETS.map((value) => ({ value, label: EVIDENCE_AUDIT_STATUS_LABELS[value] }));
const proofReviewOptions = PROOF_REVIEW_TARGETS.map((value) => ({ value, label: PROOF_STATUS_LABELS[value] }));
const scoreDecisionOptions = (['NO_DEDUCTION', 'DEDUCT'] as ScoreDecision[]).map((value) => ({
  value,
  label: SCORE_DECISION_LABELS[value]
}));
const summary = computed(() => ({
  pendingEvidence: evidences.value.filter((item) => item.auditStatus === 'PENDING').length,
  pendingProofs: proofs.value.filter((item) => item.reviewStatus === 'PENDING').length,
  missing: checkResult.value.items.filter((item) => item.checkResult === 'MISSING').length
}));

function pickerIndex<T extends string>(options: Array<{ value: T }>, current: T) {
  return Math.max(0, options.findIndex((item) => item.value === current));
}

function eventValue(event: unknown) {
  if (!event || typeof event !== 'object') return '';
  const detail = (event as { detail?: unknown }).detail;
  if (!detail || typeof detail !== 'object') return '';
  const value = (detail as { value?: unknown }).value;
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' ? value : '';
}

function servicePickerIndex() {
  return Math.max(0, serviceOptions.value.findIndex((item) => item.value === selectedServiceId.value));
}

function setSelectedService(event: PickerEvent) {
  const option = serviceOptions.value[Number(event.detail.value)];
  if (!option) return;
  selectedServiceId.value = option.value;
  void loadMetricConfig();
}

function setMetricType(index: number, event: PickerEvent) {
  const option = metricTypeOptions[Number(event.detail.value)];
  if (option) configItems.value[index].metricType = option.value;
}

function setMetricEvidenceType(index: number, event: PickerEvent) {
  const option = evidenceTypeOptions[Number(event.detail.value)];
  if (option) configItems.value[index].evidenceType = option.value;
}

function setMetricRequired(index: number, event: unknown) {
  configItems.value[index].required = Boolean(eventValue(event));
}

function setMetricWeight(index: number, event: unknown) {
  configItems.value[index].scoreWeight = Number(eventValue(event));
}

function setEvidenceReviewStatus(evidenceId: string, event: PickerEvent) {
  const option = evidenceReviewOptions[Number(event.detail.value)];
  if (!option) return;
  reviewForms.value[evidenceId] = {
    ...(reviewForms.value[evidenceId] ?? { reviewComment: '' }),
    auditStatus: option.value
  };
}

function setProofReviewResult(proofId: string, event: PickerEvent) {
  const option = proofReviewOptions[Number(event.detail.value)];
  if (!option) return;
  proofForms.value[proofId] = {
    ...(proofForms.value[proofId] ?? { reviewComment: '' }),
    reviewResult: option.value,
    scoreDecision: option.value === 'APPROVED' ? 'NO_DEDUCTION' : 'DEDUCT'
  };
}

function setScoreDecision(proofId: string, event: PickerEvent) {
  const option = scoreDecisionOptions[Number(event.detail.value)];
  if (!option) return;
  proofForms.value[proofId] = {
    ...(proofForms.value[proofId] ?? { reviewResult: 'APPROVED', reviewComment: '' }),
    scoreDecision: option.value
  };
}

function resetFeedback() {
  error.value = '';
  notice.value = '';
}

function addMetricItem() {
  configItems.value.push(createEmptyMetricConfigItem());
}

function removeMetricItem(index: number) {
  if (configItems.value.length === 1) {
    configItems.value = [createEmptyMetricConfigItem()];
    return;
  }
  configItems.value.splice(index, 1);
}

async function loadServices() {
  if (!canManageMetrics.value) return;
  loading.value = true;
  resetFeedback();
  const response = await getServiceItems('normal', true);
  loading.value = false;
  if (response.code !== 0) {
    error.value = stageThirtyFourToFortyError(response.code, 'CONFIG');
    return;
  }
  services.value = response.data.records;
  selectedServiceId.value = selectedServiceId.value || services.value[0]?.serviceId || '';
  if (selectedServiceId.value) await loadMetricConfig();
}

async function loadMetricConfig() {
  if (!selectedServiceId.value) return;
  loading.value = true;
  resetFeedback();
  const response = await getCareMetricConfig(selectedServiceId.value);
  loading.value = false;
  if (response.code !== 0) {
    configVersion.value = null;
    error.value = stageThirtyFourToFortyError(response.code, 'CONFIG');
    return;
  }
  configVersion.value = response.data.configVersion;
}

async function submitMetricConfig() {
  const validationError = validateMetricConfigItems(configItems.value);
  if (validationError) {
    error.value = validationError;
    return;
  }
  loading.value = true;
  resetFeedback();
  const response = await saveCareMetricConfig(selectedServiceId.value, { items: configItems.value });
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.message || stageThirtyFourToFortyError(response.code, 'CONFIG');
    return;
  }
  configVersion.value = response.data.configVersion;
  notice.value = `护理指标配置已保存，当前版本 ${response.data.configVersion}。`;
}

async function generateChecklist() {
  loading.value = true;
  resetFeedback();
  const response = await generateMetricChecklist(orderId.value);
  loading.value = false;
  if (response.code !== 0) {
    checklist.value = { items: [] };
    error.value = stageThirtyFourToFortyError(response.code, 'CHECKLIST');
    return;
  }
  checklist.value = response.data;
  notice.value = `已读取订单留档清单，共 ${response.data.items.length} 项。`;
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
  notice.value = `指标完成校验已执行，共 ${response.data.items.length} 项结果。`;
}

async function loadMetricCheckResult() {
  loading.value = true;
  resetFeedback();
  const response = await getMetricCheckResult(orderId.value);
  loading.value = false;
  if (response.code !== 0) {
    checkResult.value = { items: [] };
    error.value = stageThirtyFourToFortyError(response.code, 'CHECK');
    return;
  }
  checkResult.value = response.data;
}

function syncEvidenceForms() {
  const next: typeof reviewForms.value = {};
  evidences.value.forEach((item) => {
    next[item.evidenceId] = reviewForms.value[item.evidenceId] ?? {
      auditStatus: 'APPROVED',
      reviewComment: ''
    };
  });
  reviewForms.value = next;
}

function syncProofForms() {
  const next: typeof proofForms.value = {};
  proofs.value.forEach((item) => {
    next[item.proofId] = proofForms.value[item.proofId] ?? {
      reviewResult: 'APPROVED',
      reviewComment: '',
      scoreDecision: 'NO_DEDUCTION'
    };
  });
  proofForms.value = next;
}

async function loadEvidenceQueue() {
  if (!canReviewEvidence.value) return;
  loading.value = true;
  resetFeedback();
  const response = await getAdminEvidences();
  loading.value = false;
  if (response.code !== 0) {
    evidences.value = [];
    error.value = stageThirtyFourToFortyError(response.code, 'EVIDENCE');
    return;
  }
  evidences.value = response.data;
  syncEvidenceForms();
}

async function submitEvidenceReview(evidence: EvidenceResponse) {
  const form = reviewForms.value[evidence.evidenceId];
  const validationError = validateEvidenceReview(form);
  if (validationError) {
    error.value = validationError;
    return;
  }
  loading.value = true;
  resetFeedback();
  const response = await reviewCareEvidence(evidence.evidenceId, form);
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.message || stageThirtyFourToFortyError(response.code, 'EVIDENCE');
    return;
  }
  notice.value = `留档 ${compactBusinessId(response.data.evidenceId)} 已更新为${EVIDENCE_AUDIT_STATUS_LABELS[response.data.auditStatus]}。`;
  await loadEvidenceQueue();
}

async function loadProofQueue() {
  if (!canReviewEvidence.value) return;
  loading.value = true;
  resetFeedback();
  const response = await getAdminExceptionProofs();
  loading.value = false;
  if (response.code !== 0) {
    proofs.value = [];
    error.value = stageThirtyFourToFortyError(response.code, 'PROOF');
    return;
  }
  proofs.value = response.data;
  syncProofForms();
}

async function submitProofReview(proof: ProofReviewResponse) {
  const form = proofForms.value[proof.proofId];
  const validationError = validateProofReview(form);
  if (validationError) {
    error.value = validationError;
    return;
  }
  loading.value = true;
  resetFeedback();
  const response = await reviewMetricExceptionProof(proof.proofId, form);
  loading.value = false;
  if (response.code !== 0) {
    error.value = response.message || stageThirtyFourToFortyError(response.code, 'PROOF');
    return;
  }
  notice.value = `证明 ${compactBusinessId(response.data.proofId)} 已更新为${PROOF_STATUS_LABELS[response.data.reviewStatus]}，${SCORE_DECISION_LABELS[response.data.scoreDecision]}。`;
  await loadProofQueue();
}

async function initialize() {
  if (!canUsePanel.value) return;
  if (!canManageMetrics.value && (section.value === 'config' || section.value === 'checklist')) {
    section.value = 'evidence';
  }
  if (!canReviewEvidence.value && (section.value === 'evidence' || section.value === 'proof')) {
    section.value = 'config';
  }
  await Promise.all([loadServices(), loadEvidenceQueue(), loadProofQueue()]);
}

watch(
  () => props.permissions.join(','),
  () => { void initialize(); }
);

onMounted(initialize);
</script>

<template>
  <view class="care-metric-admin-panel">
    <view class="metric-admin-heading">
      <view>
        <text class="metric-kicker">阶段 34-40</text>
        <text class="metric-title">护理指标与留档质控</text>
        <text class="metric-subtitle">配置护理指标、生成订单清单，并审核留档和未完成原因证明。</text>
      </view>
      <button type="button" :disabled="loading || !canUsePanel" @click="initialize">刷新</button>
    </view>

    <view v-if="!canUsePanel" class="metric-state">当前账号缺少护理指标配置或留档审核权限，入口已关闭。</view>
    <template v-else>
      <view class="metric-summary">
        <view><text>配置权限</text><strong>{{ canManageMetrics ? '已开放' : '未开放' }}</strong></view>
        <view><text>待审留档</text><strong>{{ summary.pendingEvidence }}</strong></view>
        <view><text>待审证明</text><strong>{{ summary.pendingProofs }}</strong></view>
        <view><text>未完成指标</text><strong>{{ summary.missing }}</strong></view>
      </view>

      <view class="metric-tabs">
        <button type="button" :class="{ active: section === 'config' }" :disabled="!canManageMetrics" @click="section = 'config'">指标配置</button>
        <button type="button" :class="{ active: section === 'checklist' }" :disabled="!canManageMetrics" @click="section = 'checklist'">清单生成</button>
        <button type="button" :class="{ active: section === 'evidence' }" :disabled="!canReviewEvidence" @click="section = 'evidence'">留档审核</button>
        <button type="button" :class="{ active: section === 'proof' }" :disabled="!canReviewEvidence" @click="section = 'proof'">豁免审核</button>
      </view>

      <view v-if="notice" class="metric-notice success">{{ notice }}</view>
      <view v-if="error" class="metric-notice error">{{ error }}</view>

      <view v-if="section === 'config'" class="metric-section">
        <view class="section-head">
          <view><text>服务项目指标配置</text><text>GET 接口仅返回版本号；下方编辑保存后会生成新版本，不改写历史订单。</text></view>
          <text class="version-pill">版本 {{ configVersion ?? '未读取' }}</text>
        </view>
        <view class="service-selector">
          <picker :range="serviceOptions" range-key="label" :value="servicePickerIndex()" @change="setSelectedService">
            <view class="select-box">{{ serviceOptions.find((item) => item.value === selectedServiceId)?.label || '选择服务项目' }}</view>
          </picker>
          <input v-model="selectedServiceId" placeholder="或直接输入服务项目 ID" @blur="loadMetricConfig" />
        </view>

        <view class="metric-config-list">
          <view v-for="(item, index) in configItems" :key="index" class="metric-config-row">
            <view class="row-index">#{{ index + 1 }}</view>
            <label><text>指标编码</text><input v-model="item.metricCode" placeholder="SERVICE_PHOTO" /></label>
            <label><text>指标名称</text><input v-model="item.metricName" placeholder="服务照片" /></label>
            <label>
              <text>阶段类型</text>
              <picker :range="metricTypeOptions" range-key="label" :value="pickerIndex(metricTypeOptions, item.metricType)" @change="setMetricType(index, $event)">
                <view class="select-box">{{ CARE_METRIC_TYPE_LABELS[item.metricType] }}</view>
              </picker>
            </label>
            <label>
              <text>留档类型</text>
              <picker :range="evidenceTypeOptions" range-key="label" :value="pickerIndex(evidenceTypeOptions, item.evidenceType)" @change="setMetricEvidenceType(index, $event)">
                <view class="select-box">{{ CARE_METRIC_EVIDENCE_LABELS[item.evidenceType] }}</view>
              </picker>
            </label>
            <label><text>权重</text><input type="number" :value="String(item.scoreWeight)" @input="setMetricWeight(index, $event)" /></label>
            <view class="required-switch"><text>必填</text><switch :checked="item.required" color="#0b8f9d" @change="setMetricRequired(index, $event)" /></view>
            <label class="description-field"><text>说明</text><textarea v-model="item.description" maxlength="500" placeholder="写清护理人员需要完成的动作" /></label>
            <button type="button" class="row-remove" @click="removeMetricItem(index)">移除</button>
          </view>
        </view>
        <view class="section-actions">
          <button type="button" class="secondary" @click="addMetricItem">新增指标</button>
          <button type="button" class="primary" :disabled="loading || !selectedServiceId" @click="submitMetricConfig">保存配置</button>
        </view>
      </view>

      <view v-else-if="section === 'checklist'" class="metric-section">
        <view class="section-head"><view><text>订单留档清单</text><text>清单按订单保存快照，后续配置变更不会覆盖历史订单。</text></view></view>
        <view class="order-command-row">
          <input v-model="orderId" placeholder="输入订单 ID" />
          <button type="button" class="primary" :disabled="loading" @click="generateChecklist">生成或读取清单</button>
          <button type="button" class="secondary" :disabled="loading" @click="executeMetricCheck">执行完成校验</button>
          <button type="button" class="secondary" :disabled="loading" @click="loadMetricCheckResult">读取校验结果</button>
        </view>
        <view v-if="checklist.items.length" class="checklist-table">
          <view class="table-head"><text>指标</text><text>留档</text><text>状态</text><text>权重</text></view>
          <view v-for="item in checklist.items" :key="item.itemId" class="table-row">
            <text>{{ item.metricCode }}</text>
            <text>{{ CARE_METRIC_EVIDENCE_LABELS[item.evidenceType] }} · {{ item.required ? '必填' : '选填' }}</text>
            <text>{{ CARE_METRIC_STATUS_LABELS[item.status] }}</text>
            <text>{{ item.scoreWeight }}</text>
          </view>
        </view>
        <view v-else class="metric-state">输入订单后生成留档清单；没有后端返回时这里不会展示假清单。</view>
        <view v-if="checkResult.items.length" class="check-result-list">
          <view v-for="item in checkResult.items" :key="item.metricItemId" class="check-result-card" :class="{ missing: item.missingEvidence }">
            <text>{{ item.metricName }}</text>
            <strong>{{ CARE_METRIC_STATUS_LABELS[item.checkResult] }}</strong>
            <small>评分影响 {{ item.scoreImpact }}</small>
          </view>
        </view>
      </view>

      <view v-else-if="section === 'evidence'" class="metric-section">
        <view class="section-head">
          <view><text>护理留档审核</text><text>只展示后端待审记录；驳回或补材料必须填写意见。</text></view>
          <button type="button" class="secondary" :disabled="loading" @click="loadEvidenceQueue">刷新队列</button>
        </view>
        <view v-if="evidences.length === 0" class="metric-state">暂无待审核留档。</view>
        <view v-else class="review-list">
          <view v-for="item in evidences" :key="item.evidenceId" class="review-card">
            <view><text>留档记录</text><strong>{{ compactBusinessId(item.evidenceId) }}</strong><small>{{ EVIDENCE_AUDIT_STATUS_LABELS[item.auditStatus] }}</small></view>
            <picker :range="evidenceReviewOptions" range-key="label" :value="pickerIndex(evidenceReviewOptions, reviewForms[item.evidenceId]?.auditStatus || 'APPROVED')" @change="setEvidenceReviewStatus(item.evidenceId, $event)">
              <view class="select-box">{{ EVIDENCE_AUDIT_STATUS_LABELS[reviewForms[item.evidenceId]?.auditStatus || 'APPROVED'] }}</view>
            </picker>
            <textarea v-model="reviewForms[item.evidenceId].reviewComment" maxlength="500" placeholder="审核意见，驳回或补材料必填" />
            <button type="button" class="primary" :disabled="loading || item.auditStatus !== 'PENDING'" @click="submitEvidenceReview(item)">提交审核</button>
          </view>
        </view>
      </view>

      <view v-else class="metric-section">
        <view class="section-head">
          <view><text>未完成原因豁免审核</text><text>通过必须不扣分，驳回必须扣分并填写意见。</text></view>
          <button type="button" class="secondary" :disabled="loading" @click="loadProofQueue">刷新队列</button>
        </view>
        <view v-if="proofs.length === 0" class="metric-state">暂无待审核原因证明。</view>
        <view v-else class="review-list">
          <view v-for="item in proofs" :key="item.proofId" class="review-card">
            <view><text>证明记录</text><strong>{{ compactBusinessId(item.proofId) }}</strong><small>{{ PROOF_STATUS_LABELS[item.reviewStatus] }}</small></view>
            <picker :range="proofReviewOptions" range-key="label" :value="pickerIndex(proofReviewOptions, proofForms[item.proofId]?.reviewResult || 'APPROVED')" @change="setProofReviewResult(item.proofId, $event)">
              <view class="select-box">{{ PROOF_STATUS_LABELS[proofForms[item.proofId]?.reviewResult || 'APPROVED'] }}</view>
            </picker>
            <picker :range="scoreDecisionOptions" range-key="label" :value="pickerIndex(scoreDecisionOptions, proofForms[item.proofId]?.scoreDecision || 'NO_DEDUCTION')" @change="setScoreDecision(item.proofId, $event)">
              <view class="select-box">{{ SCORE_DECISION_LABELS[proofForms[item.proofId]?.scoreDecision || 'NO_DEDUCTION'] }}</view>
            </picker>
            <textarea v-model="proofForms[item.proofId].reviewComment" maxlength="500" placeholder="审核意见，驳回必填" />
            <button type="button" class="primary" :disabled="loading || item.reviewStatus !== 'PENDING'" @click="submitProofReview(item)">提交审核</button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.care-metric-admin-panel { display:grid; gap:18px; min-width:0; color:#17312e; }
.metric-admin-heading { display:flex; justify-content:space-between; gap:20px; align-items:flex-start; padding:4px 0 2px; }
.metric-kicker,.metric-title,.metric-subtitle,.section-head text,.review-card text,.review-card small { display:block; }
.metric-kicker { color:#0b8f9d; font-size:12px; font-weight:800; letter-spacing:1.2px; }
.metric-title { margin-top:6px; font-size:26px; font-weight:800; }
.metric-subtitle { margin-top:8px; color:#61736f; font-size:14px; line-height:1.5; }
.metric-admin-heading button,.primary,.secondary,.row-remove { min-height:40px; margin:0; padding:0 16px; border-radius:6px; font-size:13px; font-weight:750; }
.metric-admin-heading button,.secondary,.row-remove { border:1px solid #c4d6d2; background:#fff; color:#126f66; }
.primary { border:0; background:#0b8f9d; color:#fff; }
button[disabled] { opacity:.48; }
.metric-state { padding:18px; border:1px dashed #c7d7d3; background:#f8faf9; color:#647872; font-size:14px; line-height:1.6; }
.metric-summary { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; }
.metric-summary>view { display:grid; gap:6px; padding:16px; border:1px solid #dce7e4; background:#fff; }
.metric-summary text { color:#657872; font-size:12px; }
.metric-summary strong { color:#173c37; font-size:24px; }
.metric-tabs { display:flex; gap:8px; padding:6px; border:1px solid #d7e3e0; background:#fff; }
.metric-tabs button { flex:1; min-height:42px; margin:0; border:0; border-radius:6px; background:transparent; color:#627872; font-size:14px; }
.metric-tabs button.active { background:#e8f7f4; color:#087b78; font-weight:800; }
.metric-notice { padding:12px 14px; border-radius:6px; font-size:13px; line-height:1.5; }
.metric-notice.success { border:1px solid #9fd8cf; background:#eaf8f5; color:#087b78; }
.metric-notice.error { border:1px solid #efb7b2; background:#fff2f1; color:#a3342e; }
.metric-section { display:grid; gap:16px; padding:18px; border:1px solid #dce7e4; background:#fff; }
.section-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; }
.section-head view text:first-child { font-size:18px; font-weight:800; }
.section-head view text:last-child { margin-top:5px; color:#687b76; font-size:13px; line-height:1.5; }
.version-pill { flex:none; padding:7px 10px; border:1px solid #abd8d1; border-radius:999px; background:#eaf7f4; color:#087b78; font-size:12px; font-weight:800; }
.service-selector,.order-command-row { display:grid; grid-template-columns:minmax(220px,1fr) minmax(260px,1fr); gap:10px; }
.order-command-row { grid-template-columns:minmax(220px,1fr) auto auto auto; }
.service-selector input,.order-command-row input,.metric-config-row input,.metric-config-row textarea,.review-card textarea,.select-box { box-sizing:border-box; width:100%; border:1px solid #cbdad6; border-radius:6px; background:#fff; color:#17312e; font-size:13px; }
.service-selector input,.order-command-row input,.metric-config-row input,.select-box { min-height:40px; padding:0 12px; line-height:40px; }
.metric-config-list { display:grid; gap:12px; }
.metric-config-row { display:grid; grid-template-columns:42px repeat(3,minmax(120px,1fr)) 96px 86px auto; gap:10px; align-items:end; padding:14px; border:1px solid #e0ebe8; background:#fbfdfc; }
.metric-config-row label { display:grid; gap:6px; min-width:0; }
.metric-config-row label text,.required-switch text { color:#58706b; font-size:12px; font-weight:800; }
.row-index { align-self:center; color:#087b78; font-size:15px; font-weight:900; }
.required-switch { display:flex; align-items:center; justify-content:space-between; gap:8px; min-height:40px; }
.description-field { grid-column:2 / 7; }
.metric-config-row textarea,.review-card textarea { min-height:72px; padding:10px 12px; line-height:1.45; }
.row-remove { align-self:stretch; color:#a3342e; border-color:#efc4c0; }
.section-actions { display:flex; justify-content:flex-end; gap:10px; }
.checklist-table { display:grid; border:1px solid #dbe6e3; }
.table-head,.table-row { display:grid; grid-template-columns:1.4fr 1fr 1fr 90px; gap:10px; align-items:center; padding:11px 14px; border-bottom:1px solid #e7eeec; font-size:13px; }
.table-head { background:#f2f7f5; color:#415f59; font-weight:800; }
.table-row:last-child { border-bottom:0; }
.check-result-list { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:10px; }
.check-result-card { display:grid; gap:5px; padding:13px; border-left:4px solid #0b8f9d; background:#f2faf8; }
.check-result-card.missing { border-left-color:#ffb84d; background:#fff8e8; }
.check-result-card text { color:#17312e; font-size:14px; font-weight:800; }
.check-result-card strong { color:#087b78; font-size:18px; }
.check-result-card small { color:#687b76; font-size:12px; }
.review-list { display:grid; gap:12px; }
.review-card { display:grid; grid-template-columns:minmax(140px,1fr) 170px 170px minmax(220px,1.4fr) auto; gap:10px; align-items:start; padding:14px; border:1px solid #dfe9e6; background:#fbfdfc; }
.review-card>view:first-child { display:grid; gap:5px; }
.review-card strong { font-size:16px; overflow-wrap:anywhere; }
.review-card text,.review-card small { color:#6a7d78; font-size:12px; }
.review-card small { color:#087b78; font-weight:800; }
.review-card .primary { align-self:stretch; }
@media (max-width:900px) {
  .metric-summary { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .service-selector,.order-command-row,.metric-config-row,.review-card { grid-template-columns:1fr; }
  .description-field { grid-column:auto; }
  .metric-tabs { overflow:auto; }
  .metric-tabs button { min-width:110px; }
}
</style>
