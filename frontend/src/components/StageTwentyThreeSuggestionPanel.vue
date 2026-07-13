<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getServiceReport } from '@/api/stageFifteen';
import { getOrderServiceRecords } from '@/api/stageFourteen';
import { createHealthUpdateSuggestion } from '@/api/stageTwentyThree';
import type {
  AllergySeverity,
  DiseaseStatus,
  MedicationFrequency
} from '@/types/stageNineteen';
import type { NurseTaskDetailRecord } from '@/types/stageThirteen';
import type {
  HealthSuggestionFieldName,
  HealthSuggestionSourceOption,
  HealthSuggestionStatus,
  HealthUpdateSuggestionRequest
} from '@/types/stageTwentyThree';
import {
  HEALTH_SUGGESTION_STATUS_LABELS,
  HEALTH_SUGGESTION_FIELD_OPTIONS,
  HEALTH_SUGGESTION_SOURCE_LABELS,
  healthSuggestionSourceLoadError,
  validateHealthSuggestion
} from '@/utils/stageTwentyThreeRules';

const props = defineProps<{
  tasks: NurseTaskDetailRecord[];
  preferredOrderId?: string;
}>();

const emit = defineEmits<{
  submitted: [status: HealthSuggestionStatus];
}>();

const diseaseStatuses: Array<{ value: DiseaseStatus; label: string }> = [
  { value: 'ACTIVE', label: '治疗中' },
  { value: 'MONITORING', label: '持续观察' },
  { value: 'STABLE', label: '情况稳定' },
  { value: 'RESOLVED', label: '已恢复' }
];
const medicationFrequencies: Array<{ value: MedicationFrequency; label: string }> = [
  { value: 'ONCE_DAILY', label: '每日一次' },
  { value: 'TWICE_DAILY', label: '每日两次' },
  { value: 'THREE_TIMES_DAILY', label: '每日三次' },
  { value: 'EVERY_OTHER_DAY', label: '隔日一次' },
  { value: 'WEEKLY', label: '每周一次' },
  { value: 'AS_NEEDED', label: '按需使用' }
];
const allergySeverities: Array<{ value: AllergySeverity; label: string }> = [
  { value: 'MILD', label: '轻度' },
  { value: 'MODERATE', label: '中度' },
  { value: 'SEVERE', label: '重度' }
];
const riskOptions = [
  { tagCode: 'FALL_RISK', tagName: '跌倒风险' },
  { tagCode: 'PRESSURE_INJURY_RISK', tagName: '压疮风险' },
  { tagCode: 'CHOKING_RISK', tagName: '呛咳风险' },
  { tagCode: 'MEDICATION_RISK', tagName: '用药风险' },
  { tagCode: 'NUTRITION_RISK', tagName: '营养风险' }
];

const selectedOrderId = ref('');
const sources = ref<HealthSuggestionSourceOption[]>([]);
const selectedSourceKey = ref('');
const fieldName = ref<HealthSuggestionFieldName>('diseases');
const reason = ref('');
const loadingSources = ref(false);
const submitting = ref(false);
const error = ref('');
const successStatus = ref<HealthSuggestionStatus | null>(null);
let sourceRequestSequence = 0;

const disease = ref({ diseaseName: '', diagnosedAt: '', status: 'MONITORING' as DiseaseStatus, remark: '' });
const medication = ref({
  medicationName: '', dosage: '', frequency: 'ONCE_DAILY' as MedicationFrequency,
  timePoints: ['08:00'], startDate: localDate(), endDate: '', remark: ''
});
const allergy = ref({ allergenName: '', reaction: '', severity: 'MILD' as AllergySeverity, remark: '' });
const risk = ref({ ...riskOptions[0] });
const carePlan = ref({ careGoals: '', dailyCare: '', precautions: '' });

const orderTasks = computed(() => {
  const seen = new Set<string>();
  return props.tasks.filter((task) => {
    if (!task.orderId || seen.has(task.orderId)) return false;
    seen.add(task.orderId);
    return true;
  });
});
const selectedTask = computed(() => orderTasks.value.find((task) => task.orderId === selectedOrderId.value) ?? null);
const selectedSource = computed(() => sources.value.find((source) => sourceKey(source) === selectedSourceKey.value) ?? null);

function pad(value: number) {
  return String(value).padStart(2, '0');
}

function localDate(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间待确认';
}

function serviceName(task: NurseTaskDetailRecord) {
  return task.serviceName?.trim() || (task.serviceId && !task.serviceId.startsWith('service_') ? task.serviceId : '') || '上门护理服务';
}

function elderName(task: NurseTaskDetailRecord) {
  return task.elderName?.trim() || '本次服务长辈';
}

function sourceKey(source: Pick<HealthSuggestionSourceOption, 'sourceType' | 'sourceId'>) {
  return `${source.sourceType}:${source.sourceId}`;
}

function sourceLabel(source: HealthSuggestionSourceOption) {
  return HEALTH_SUGGESTION_SOURCE_LABELS[source.sourceType];
}

function businessError(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前护理人员不能为这笔服务提交档案建议。';
  if (code === 404) return '对应服务或建议提交服务暂不可用。';
  if (code === 409) return '相同建议已在审核中，请勿重复提交。';
  if (code === 422) return '建议内容与服务来源不匹配，请检查后重试。';
  if (code === 502) return '健康档案建议响应不完整，请联系平台维护人员。';
  return '健康档案建议暂时无法提交，请稍后重试。';
}

async function loadSources(orderId: string) {
  const sequence = ++sourceRequestSequence;
  sources.value = [];
  selectedSourceKey.value = '';
  successStatus.value = null;
  error.value = '';
  if (!orderId) return;
  loadingSources.value = true;
  const [recordResponse, reportResponse] = await Promise.all([
    getOrderServiceRecords(orderId),
    getServiceReport(orderId)
  ]);
  if (sequence !== sourceRequestSequence || orderId !== selectedOrderId.value) return;
  loadingSources.value = false;

  const nextSources: HealthSuggestionSourceOption[] = [];
  if (recordResponse.code === 0) {
    recordResponse.data.records.forEach((record) => {
      if (!record.recordId?.trim()) return;
      nextSources.push({
        sourceType: 'SERVICE_RECORD',
        sourceId: record.recordId,
        title: `服务记录 · ${formatDateTime(record.endTime || record.startTime)}`,
        summary: record.nursingAdvice?.trim() || record.content?.trim() || '本次服务记录',
        occurredAt: record.endTime || record.startTime
      });
    });
  }
  if (reportResponse.code === 0 && reportResponse.data.reportId?.trim()) {
    nextSources.push({
      sourceType: 'SERVICE_REPORT',
      sourceId: reportResponse.data.reportId,
      title: '本次服务报告',
      summary: reportResponse.data.nursingAdvice?.trim() || reportResponse.data.summary?.trim() || '已生成服务报告',
      occurredAt: selectedTask.value?.scheduledStart ?? ''
    });
  }
  sources.value = nextSources;
  selectedSourceKey.value = nextSources[0] ? sourceKey(nextSources[0]) : '';
  const sourceError = healthSuggestionSourceLoadError(recordResponse, reportResponse);
  if (sourceError) {
    error.value = sourceError;
    return;
  }
  if (!nextSources.length) {
    error.value = '请先保存服务记录或生成服务报告，再提出档案变更建议。';
  }
}

function selectTask(task: NurseTaskDetailRecord) {
  if (task.orderId === selectedOrderId.value) return;
  selectedOrderId.value = task.orderId;
  loadSources(task.orderId);
}

function selectSource(source: HealthSuggestionSourceOption) {
  selectedSourceKey.value = sourceKey(source);
  error.value = '';
  successStatus.value = null;
}

function selectField(next: HealthSuggestionFieldName) {
  fieldName.value = next;
  error.value = '';
  successStatus.value = null;
}

function pickerIndex<T extends string>(options: Array<{ value: T }>, current: T) {
  return Math.max(0, options.findIndex((option) => option.value === current));
}

function setDiseaseStatus(event: { detail: { value: number | string } }) {
  disease.value.status = diseaseStatuses[Number(event.detail.value)]?.value ?? 'MONITORING';
}

function setMedicationFrequency(event: { detail: { value: number | string } }) {
  medication.value.frequency = medicationFrequencies[Number(event.detail.value)]?.value ?? 'ONCE_DAILY';
}

function setAllergySeverity(event: { detail: { value: number | string } }) {
  allergy.value.severity = allergySeverities[Number(event.detail.value)]?.value ?? 'MILD';
}

function setMedicationTime(event: { detail: { value: string } }) {
  medication.value.timePoints = [event.detail.value];
}

function buildPayload(): HealthUpdateSuggestionRequest | null {
  const source = selectedSource.value;
  if (!source) return null;
  let newValue: HealthUpdateSuggestionRequest['newValue'];
  if (fieldName.value === 'diseases') newValue = { ...disease.value, diseaseName: disease.value.diseaseName.trim(), remark: disease.value.remark.trim() || undefined, diagnosedAt: disease.value.diagnosedAt || undefined };
  else if (fieldName.value === 'medications') newValue = { ...medication.value, medicationName: medication.value.medicationName.trim(), dosage: medication.value.dosage.trim() || undefined, endDate: medication.value.endDate || undefined, remark: medication.value.remark.trim() || undefined };
  else if (fieldName.value === 'allergies') newValue = { ...allergy.value, allergenName: allergy.value.allergenName.trim(), reaction: allergy.value.reaction.trim() || undefined, remark: allergy.value.remark.trim() || undefined };
  else if (fieldName.value === 'riskTags') newValue = { ...risk.value };
  else newValue = { careGoals: carePlan.value.careGoals.trim(), dailyCare: carePlan.value.dailyCare.trim(), precautions: carePlan.value.precautions.trim() };
  return {
    fieldName: fieldName.value,
    newValue,
    sourceType: source.sourceType,
    sourceId: source.sourceId,
    reason: reason.value.trim()
  };
}

async function submitSuggestion() {
  const payload = buildPayload();
  if (!selectedOrderId.value || !payload) {
    error.value = '请选择本次建议所依据的服务记录或报告。';
    return;
  }
  const validationError = validateHealthSuggestion(payload);
  if (validationError) {
    error.value = validationError;
    return;
  }
  submitting.value = true;
  error.value = '';
  successStatus.value = null;
  const response = await createHealthUpdateSuggestion(selectedOrderId.value, payload);
  submitting.value = false;
  if (response.code !== 0) {
    error.value = businessError(response.code);
    return;
  }
  successStatus.value = response.data.status;
  reason.value = '';
  emit('submitted', response.data.status);
}

function chooseInitialOrder() {
  const preferred = props.preferredOrderId && orderTasks.value.some((task) => task.orderId === props.preferredOrderId)
    ? props.preferredOrderId
    : orderTasks.value[0]?.orderId ?? '';
  if (!preferred) return;
  if (preferred !== selectedOrderId.value) selectedOrderId.value = preferred;
  loadSources(preferred);
}

watch(() => props.preferredOrderId, (next) => {
  if (next && orderTasks.value.some((task) => task.orderId === next)) {
    selectedOrderId.value = next;
    loadSources(next);
  }
});
watch(() => props.tasks.map((task) => task.orderId).join('|'), () => {
  if (!selectedOrderId.value || !orderTasks.value.some((task) => task.orderId === selectedOrderId.value)) chooseInitialOrder();
});
onMounted(chooseInitialOrder);
</script>

<template>
  <view class="suggestion-panel">
    <view class="panel-heading">
      <text>建议更新健康档案</text>
      <text>建议会交由管理端审核，不会直接修改长辈档案。</text>
    </view>

    <view v-if="successStatus" class="success-message">
      <text>已提交管理端审核，未立即修改档案</text>
      <text>当前状态：{{ HEALTH_SUGGESTION_STATUS_LABELS[successStatus] }}</text>
    </view>
    <view v-if="error" class="error-message">{{ error }}</view>

    <view class="form-section">
      <view class="section-heading"><text>选择相关服务</text><text>{{ orderTasks.length }} 项</text></view>
      <view v-if="orderTasks.length === 0" class="empty-state">暂无可用于提出建议的护理服务。</view>
      <button
        v-for="task in orderTasks"
        :key="task.orderId"
        class="choice-card"
        :class="{ selected: selectedOrderId === task.orderId }"
        type="button"
        @click="selectTask(task)"
      >
        <text class="choice-title">{{ serviceName(task) }}</text>
        <text>{{ elderName(task) }} · {{ formatDateTime(task.scheduledStart) }}</text>
      </button>
    </view>

    <view v-if="selectedTask" class="form-section">
      <view class="section-heading"><text>建议依据</text><text>{{ loadingSources ? '读取中' : `${sources.length} 项` }}</text></view>
      <button
        v-for="source in sources"
        :key="sourceKey(source)"
        class="source-card"
        :class="{ selected: selectedSourceKey === sourceKey(source) }"
        type="button"
        @click="selectSource(source)"
      >
        <view><text class="choice-title">{{ source.title }}</text><text>{{ source.summary }}</text></view>
        <text class="source-type">{{ sourceLabel(source) }}</text>
      </button>
    </view>

    <template v-if="selectedSource">
      <view class="form-section">
        <view class="section-heading"><text>建议更新的内容</text><text>请选择一项</text></view>
        <view class="field-options">
          <button
            v-for="option in HEALTH_SUGGESTION_FIELD_OPTIONS"
            :key="option.value"
            class="field-option"
            :class="{ selected: fieldName === option.value }"
            type="button"
            @click="selectField(option.value)"
          >{{ option.label }}</button>
        </view>
        <text class="field-help">{{ HEALTH_SUGGESTION_FIELD_OPTIONS.find((option) => option.value === fieldName)?.help }}</text>
      </view>

      <view class="form-section value-form">
        <template v-if="fieldName === 'diseases'">
          <label class="field"><text>疾病名称</text><input v-model="disease.diseaseName" maxlength="80" placeholder="例如：高血压" /></label>
          <view class="field"><text>当前状态</text><picker :range="diseaseStatuses" range-key="label" :value="pickerIndex(diseaseStatuses, disease.status)" @change="setDiseaseStatus"><view class="picker-value">{{ diseaseStatuses.find((item) => item.value === disease.status)?.label }}</view></picker></view>
          <view class="field"><text>确诊日期（选填）</text><picker mode="date" :end="localDate()" :value="disease.diagnosedAt" @change="disease.diagnosedAt = $event.detail.value"><view class="picker-value">{{ disease.diagnosedAt || '选择日期' }}</view></picker></view>
          <label class="field"><text>补充说明（选填）</text><textarea v-model="disease.remark" maxlength="300" placeholder="记录病情变化或观察情况" /></label>
        </template>

        <template v-else-if="fieldName === 'medications'">
          <label class="field"><text>药物名称</text><input v-model="medication.medicationName" maxlength="80" placeholder="例如：阿司匹林" /></label>
          <label class="field"><text>每次用量（选填）</text><input v-model="medication.dosage" maxlength="50" placeholder="例如：半片" /></label>
          <view class="field"><text>用药频次</text><picker :range="medicationFrequencies" range-key="label" :value="pickerIndex(medicationFrequencies, medication.frequency)" @change="setMedicationFrequency"><view class="picker-value">{{ medicationFrequencies.find((item) => item.value === medication.frequency)?.label }}</view></picker></view>
          <view class="field"><text>用药时间</text><picker mode="time" :value="medication.timePoints[0]" @change="setMedicationTime"><view class="picker-value">{{ medication.timePoints[0] }}</view></picker></view>
          <view class="date-grid">
            <view class="field"><text>开始日期</text><picker mode="date" :value="medication.startDate" @change="medication.startDate = $event.detail.value"><view class="picker-value">{{ medication.startDate }}</view></picker></view>
            <view class="field"><text>结束日期（选填）</text><picker mode="date" :start="medication.startDate" :value="medication.endDate" @change="medication.endDate = $event.detail.value"><view class="picker-value">{{ medication.endDate || '长期使用' }}</view></picker></view>
          </view>
          <label class="field"><text>补充说明（选填）</text><textarea v-model="medication.remark" maxlength="300" placeholder="记录用药调整的具体情况" /></label>
        </template>

        <template v-else-if="fieldName === 'allergies'">
          <label class="field"><text>过敏原</text><input v-model="allergy.allergenName" maxlength="80" placeholder="例如：青霉素" /></label>
          <label class="field"><text>过敏反应（选填）</text><input v-model="allergy.reaction" maxlength="120" placeholder="例如：皮疹、呼吸急促" /></label>
          <view class="field"><text>严重程度</text><picker :range="allergySeverities" range-key="label" :value="pickerIndex(allergySeverities, allergy.severity)" @change="setAllergySeverity"><view class="picker-value">{{ allergySeverities.find((item) => item.value === allergy.severity)?.label }}</view></picker></view>
          <label class="field"><text>补充说明（选填）</text><textarea v-model="allergy.remark" maxlength="300" placeholder="补充发生场景和处置情况" /></label>
        </template>

        <template v-else-if="fieldName === 'riskTags'">
          <text class="risk-heading">选择需要关注的健康风险</text>
          <view class="risk-options">
            <button v-for="item in riskOptions" :key="item.tagCode" type="button" :class="{ selected: risk.tagCode === item.tagCode }" @click="risk = { ...item }">{{ item.tagName }}</button>
          </view>
        </template>

        <template v-else>
          <label class="field"><text>照护目标</text><textarea v-model="carePlan.careGoals" maxlength="300" placeholder="说明希望达到的照护目标" /></label>
          <label class="field"><text>日常护理</text><textarea v-model="carePlan.dailyCare" maxlength="500" placeholder="说明建议采用的日常护理方式" /></label>
          <label class="field"><text>注意事项</text><textarea v-model="carePlan.precautions" maxlength="500" placeholder="说明需要重点观察或避免的情况" /></label>
        </template>

        <label class="field reason-field"><text>提出建议的原因</text><textarea v-model="reason" maxlength="255" placeholder="结合本次服务记录，说明为什么建议调整档案" /><text class="counter">{{ reason.length }}/255</text></label>
        <button class="submit-button" type="button" :disabled="submitting" @click="submitSuggestion">{{ submitting ? '正在提交' : '提交管理端审核' }}</button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.suggestion-panel { display:grid; gap:20rpx; color:#18312d; }
.panel-heading { display:grid; gap:8rpx; padding:22rpx; border:1rpx solid #b8d9d4; border-radius:8rpx; background:#f1faf7; }
.panel-heading text:first-child { font-size:31rpx; font-weight:700; }.panel-heading text:last-child { color:#637975; font-size:23rpx; line-height:1.6; }
.success-message,.error-message { display:grid; gap:7rpx; padding:20rpx; border-radius:8rpx; font-size:24rpx; }
.success-message { border:1rpx solid #9ed8cc; background:#e7f7f2; color:#0b665d; }.success-message text:first-child { font-weight:700; }
.error-message { border:1rpx solid #f0b9b4; background:#fff1ef; color:#ad3831; }
.form-section { display:grid; gap:13rpx; padding:22rpx; border:1rpx solid #dce8e5; border-radius:8rpx; background:#fff; }
.section-heading { display:flex; align-items:center; justify-content:space-between; gap:12rpx; }.section-heading text:first-child { font-size:28rpx; font-weight:700; }.section-heading text:last-child { color:#748681; font-size:22rpx; }
.choice-card,.source-card { display:grid; gap:8rpx; width:100%; margin:0; padding:18rpx; border:1rpx solid #d8e4e1; border-radius:7rpx; background:#fff; color:#687d79; text-align:left; font-size:23rpx; line-height:1.45; }
.choice-card.selected,.source-card.selected { border-color:#72c4b9; background:#eaf8f4; }.choice-title { display:block; color:#193732; font-size:27rpx; font-weight:700; }
.source-card { grid-template-columns:minmax(0,1fr) auto; align-items:center; }.source-card > view { display:grid; gap:7rpx; min-width:0; }.source-type { padding:5rpx 10rpx; border-radius:5rpx; background:#edf5f3; color:#39716a; font-size:20rpx; }
.field-options,.risk-options { display:flex; flex-wrap:wrap; gap:10rpx; }.field-option,.risk-options button { width:auto; margin:0; padding:0 18rpx; height:64rpx; line-height:64rpx; border:1rpx solid #cddbd8; border-radius:6rpx; background:#fff; color:#465e5a; font-size:23rpx; }.field-option.selected,.risk-options button.selected { border-color:#70c2b7; background:#e4f6f2; color:#08756a; font-weight:700; }
.field-help { color:#6f817e; font-size:22rpx; line-height:1.55; }.value-form { gap:18rpx; }.field { position:relative; display:grid; gap:9rpx; }.field > text:first-child,.risk-heading { color:#354f4b; font-size:24rpx; font-weight:600; }
.field input,.field textarea,.picker-value { box-sizing:border-box; width:100%; border:1rpx solid #d5e0de; border-radius:7rpx; background:#fbfdfc; padding:16rpx; color:#18312d; font-size:25rpx; }.field textarea { min-height:132rpx; }.picker-value { min-height:70rpx; }
.date-grid { display:grid; grid-template-columns:1fr 1fr; gap:12rpx; }.reason-field textarea { padding-bottom:42rpx; }.counter { position:absolute; right:14rpx; bottom:12rpx; color:#83918f; font-size:20rpx; }
.submit-button { width:100%; margin:4rpx 0 0; border:0; border-radius:7rpx; background:#0f766e; color:#fff; font-size:27rpx; font-weight:700; }.submit-button[disabled] { opacity:.55; }
.empty-state { padding:18rpx; border-radius:7rpx; background:#f2f6f5; color:#71837f; font-size:23rpx; }
@media (max-width:390px) { .date-grid { grid-template-columns:1fr; }.source-card { grid-template-columns:1fr; }.source-type { width:max-content; } }
</style>
