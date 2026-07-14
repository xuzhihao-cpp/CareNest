<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getFamilyBindings } from '@/api/stageSix';
import { getFamilyElders } from '@/api/stageSeven';
import {
  getHealthArchive,
  resolveElderResourceId,
  updateHealthArchive
} from '@/api/stageNineteen';
import StageTwentyFourChangeHistory from '@/components/StageTwentyFourChangeHistory.vue';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type { BindingResponse } from '@/types/stageSix';
import type { ElderProfileResponse } from '@/types/stageSeven';
import type {
  AllergyItem,
  AllergySeverity,
  ChronicDiseaseItem,
  DiseaseStatus,
  HealthArchiveDraft,
  HealthArchiveResponse,
  HealthArchiveUpdateRequest,
  MedicationFrequency,
  MedicationItem
} from '@/types/stageNineteen';

interface ArchiveChangeDetail {
  category: string;
  description: string;
}

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const diseaseStatusOptions: Array<{ value: DiseaseStatus; label: string }> = [
  { value: 'ACTIVE', label: '治疗中' },
  { value: 'MONITORING', label: '持续观察' },
  { value: 'STABLE', label: '情况稳定' },
  { value: 'RESOLVED', label: '已康复' }
];

const frequencyOptions: Array<{ value: MedicationFrequency; label: string }> = [
  { value: 'ONCE_DAILY', label: '每日一次' },
  { value: 'TWICE_DAILY', label: '每日两次' },
  { value: 'THREE_TIMES_DAILY', label: '每日三次' },
  { value: 'EVERY_OTHER_DAY', label: '隔日一次' },
  { value: 'WEEKLY', label: '每周一次' },
  { value: 'AS_NEEDED', label: '按需使用' }
];

const severityOptions: Array<{ value: AllergySeverity; label: string }> = [
  { value: 'MILD', label: '轻微' },
  { value: 'MODERATE', label: '中等' },
  { value: 'SEVERE', label: '严重' }
];

const defaultRiskOptions = [
  { value: 'FALL_RISK', label: '跌倒风险' },
  { value: 'PRESSURE_INJURY_RISK', label: '压疮风险' },
  { value: 'SWALLOWING_RISK', label: '吞咽风险' },
  { value: 'MEDICATION_RISK', label: '用药风险' },
  { value: 'WANDERING_RISK', label: '走失风险' },
  { value: 'ALLERGY_RISK', label: '过敏风险' }
];

const emptyDraft: HealthArchiveDraft = {
  archiveVersion: 0,
  diseases: [],
  medications: [],
  allergies: [],
  riskTags: [],
  carePlan: {
    careGoals: '',
    dailyCare: '',
    precautions: ''
  }
};

const profiles = ref<ElderProfileResponse[]>([]);
const bindings = ref<BindingResponse[]>([]);
const selectedElderId = ref('');
const archive = ref<HealthArchiveResponse | null>(null);
const draft = ref<HealthArchiveDraft>(clone(emptyDraft));
const loading = ref(false);
const saving = ref(false);
const editing = ref(false);
const reviewing = ref(false);
const error = ref('');
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
  const binding = selectedBinding.value;
  return Boolean(
    binding
      && binding.bindingStatus === 'ACTIVE'
      && binding.scopeCodes.includes('HEALTH_VIEW')
  );
});
const canEdit = computed(() => {
  if (!isFamily.value || !canView.value) return false;
  return Boolean(selectedBinding.value?.scopeCodes.includes('HEALTH_EDIT'));
});
const selectedElderName = computed(() =>
  selectedProfile.value?.name
  || selectedBinding.value?.elderName
  || props.authUser?.displayName
  || '长辈'
);
const riskChoices = computed(() => {
  const choices = [...defaultRiskOptions];
  for (const item of archive.value?.riskTags ?? []) {
    if (!choices.some((choice) => choice.value === item.tagCode)) {
      choices.push({ value: item.tagCode, label: item.tagName });
    }
  }
  return choices;
});
const activeRiskLabels = computed(() =>
  draft.value.riskTags.map((code) =>
    riskChoices.value.find((item) => item.value === code)?.label ?? code
  )
);
const changeDetails = computed<ArchiveChangeDetail[]>(() => {
  if (!archive.value) return [];
  return [
    ...compareNamedItems(
      '慢病记录',
      archive.value.diseases,
      draft.value.diseases,
      (item) => item.diseaseName,
      describeDiseaseChange
    ),
    ...compareNamedItems(
      '当前用药',
      archive.value.medications,
      draft.value.medications,
      (item) => item.medicationName,
      describeMedicationChange
    ),
    ...compareNamedItems(
      '过敏记录',
      archive.value.allergies,
      draft.value.allergies,
      (item) => item.allergenName,
      describeAllergyChange
    ),
    ...describeRiskChanges(archive.value.riskTags, draft.value.riskTags),
    ...describeCarePlanChanges(archive.value.carePlan, draft.value.carePlan)
  ];
});
const hasChanges = computed(() => changeDetails.value.length > 0);
const today = new Date().toISOString().slice(0, 10);

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function comparable(value?: string) {
  return (value ?? '').trim();
}

function readable(value?: string) {
  const normalized = comparable(value);
  if (!normalized) return '未记录';
  return normalized.length > 32 ? `${normalized.slice(0, 32)}...` : normalized;
}

function fieldChange(label: string, before?: string, after?: string) {
  if (comparable(before) === comparable(after)) return '';
  return `${label}由“${readable(before)}”改为“${readable(after)}”`;
}

function compareNamedItems<T>(
  category: string,
  beforeItems: T[],
  afterItems: T[],
  getName: (item: T) => string,
  describeUpdate: (before: T, after: T) => string[]
) {
  const details: ArchiveChangeDetail[] = [];
  const beforeMap = new Map(beforeItems.map((item) => [comparable(getName(item)).toLowerCase(), item]));
  const afterMap = new Map(afterItems.map((item) => [comparable(getName(item)).toLowerCase(), item]));

  for (const [key, item] of afterMap) {
    const name = readable(getName(item));
    const previous = beforeMap.get(key);
    if (!previous) {
      details.push({ category, description: `新增“${name}”` });
      continue;
    }
    const updates = describeUpdate(previous, item).filter(Boolean);
    if (updates.length > 0) details.push({ category, description: `${name}：${updates.join('；')}` });
  }

  for (const [key, item] of beforeMap) {
    if (!afterMap.has(key)) {
      details.push({ category, description: `删除“${readable(getName(item))}”` });
    }
  }
  return details;
}

function describeDiseaseChange(before: ChronicDiseaseItem, after: ChronicDiseaseItem) {
  return [
    fieldChange('状态', labelOf(diseaseStatusOptions, before.status), labelOf(diseaseStatusOptions, after.status)),
    fieldChange('确诊日期', before.diagnosedAt, after.diagnosedAt),
    fieldChange('说明', before.remark, after.remark)
  ];
}

function describeMedicationChange(before: MedicationItem, after: MedicationItem) {
  return [
    fieldChange('剂量', before.dosage, after.dosage),
    fieldChange('服用频次', labelOf(frequencyOptions, before.frequency), labelOf(frequencyOptions, after.frequency)),
    fieldChange('服用时间', before.timePoints.join('、'), after.timePoints.join('、')),
    fieldChange('开始日期', before.startDate, after.startDate),
    fieldChange('结束日期', before.endDate, after.endDate),
    fieldChange('说明', before.remark, after.remark)
  ];
}

function describeAllergyChange(before: AllergyItem, after: AllergyItem) {
  return [
    fieldChange('严重程度', labelOf(severityOptions, before.severity), labelOf(severityOptions, after.severity)),
    fieldChange('过敏反应', before.reaction, after.reaction),
    fieldChange('说明', before.remark, after.remark)
  ];
}

function describeRiskChanges(beforeItems: HealthArchiveResponse['riskTags'], afterCodes: string[]) {
  const details: ArchiveChangeDetail[] = [];
  const beforeMap = new Map(beforeItems.map((item) => [item.tagCode, item.tagName]));
  const afterSet = new Set(afterCodes);
  for (const code of afterSet) {
    if (!beforeMap.has(code)) {
      details.push({ category: '风险标签', description: `新增“${riskLabel(code)}”` });
    }
  }
  for (const [code, label] of beforeMap) {
    if (!afterSet.has(code)) {
      details.push({ category: '风险标签', description: `删除“${label}”` });
    }
  }
  return details;
}

function describeCarePlanChanges(
  before: HealthArchiveResponse['carePlan'],
  after: HealthArchiveDraft['carePlan']
) {
  const fields: Array<{ label: string; before?: string; after?: string }> = [
    { label: '照护目标', before: before.careGoals, after: after.careGoals },
    { label: '日常照护', before: before.dailyCare, after: after.dailyCare },
    { label: '注意事项', before: before.precautions, after: after.precautions }
  ];
  return fields
    .map((item) => fieldChange(item.label, item.before, item.after))
    .filter(Boolean)
    .map((description) => ({ category: '照护计划', description }));
}

function riskLabel(code: string) {
  return riskChoices.value.find((item) => item.value === code)?.label ?? code;
}

function labelOf(options: Array<{ value: string; label: string }>, value: string) {
  return options.find((item) => item.value === value)?.label ?? value;
}

function formatDate(value?: string) {
  if (!value) return '未记录';
  return value.replace('T', ' ').slice(0, 16);
}

function formatTimePoints(values: string[]) {
  return values.length > 0 ? values.join('、') : '未设置时间';
}

function applyArchive(value: HealthArchiveResponse) {
  archive.value = clone(value);
  draft.value = {
    archiveVersion: value.archiveVersion,
    diseases: clone(value.diseases ?? []),
    medications: clone(value.medications ?? []),
    allergies: clone(value.allergies ?? []),
    riskTags: (value.riskTags ?? []).map((item) => item.tagCode),
    carePlan: clone(value.carePlan ?? emptyDraft.carePlan)
  };
}

function businessError(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前绑定未获得健康档案查看或编辑权限。';
  if (code === 404) return '暂未建立健康档案，请稍后再试。';
  if (code === 409) return '档案已被其他人更新，请重新读取后再保存。';
  if (code === 422) return '填写内容不符合要求，请检查后重试。';
  return '健康档案暂时无法处理，请稍后重试。';
}

async function loadArchive(elderId = selectedElderId.value) {
  if (!elderId) return;
  if (!canView.value) {
    archive.value = null;
    draft.value = clone(emptyDraft);
    error.value = '当前绑定没有健康档案查看权限。';
    return;
  }
  loading.value = true;
  error.value = '';
  successMessage.value = '';
  const response = await getHealthArchive(elderId);
  loading.value = false;
  if (response.code === 0) {
    applyArchive(response.data);
    editing.value = false;
    reviewing.value = false;
    return;
  }
  archive.value = null;
  draft.value = clone(emptyDraft);
  error.value = businessError(response.code);
}

async function selectElder(elderId: string) {
  if (selectedElderId.value === elderId && archive.value) return;
  selectedElderId.value = elderId;
  editing.value = false;
  reviewing.value = false;
  await loadArchive(elderId);
}

async function loadContext() {
  loading.value = true;
  error.value = '';
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
      error.value = businessError(profileResponse.code !== 0 ? profileResponse.code : bindingResponse.code);
      return;
    }
    profiles.value = profileResponse.data;
    bindings.value = bindingResponse.data.filter((item) => item.bindingStatus === 'ACTIVE');
    const firstVisible = profiles.value.find((profile) => {
      const binding = bindings.value.find((item) => item.elderId === profile.elderId);
      return binding?.scopeCodes.includes('HEALTH_VIEW');
    });
    selectedElderId.value = firstVisible?.elderId ?? profiles.value[0]?.elderId ?? '';
    if (selectedElderId.value) await loadArchive();
    return;
  }

  loading.value = false;
  if (isElder.value && props.authUser?.userId) {
    selectedElderId.value = resolveElderResourceId(props.authUser.userId);
    await loadArchive();
  }
}

function startEditing() {
  if (!archive.value || !canEdit.value) return;
  applyArchive(archive.value);
  editing.value = true;
  reviewing.value = false;
  error.value = '';
  successMessage.value = '';
}

function cancelEditing() {
  if (archive.value) applyArchive(archive.value);
  editing.value = false;
  reviewing.value = false;
  error.value = '';
}

function addDisease() {
  draft.value.diseases.push({ diseaseName: '', diagnosedAt: '', status: 'MONITORING', remark: '' });
}

function addMedication() {
  draft.value.medications.push({
    medicationName: '',
    dosage: '',
    frequency: 'ONCE_DAILY',
    timePoints: ['08:00'],
    startDate: today,
    endDate: '',
    remark: ''
  });
}

function addAllergy() {
  draft.value.allergies.push({ allergenName: '', reaction: '', severity: 'MILD', remark: '' });
}

function addMedicationTime(item: MedicationItem) {
  if (item.timePoints.length < 3) item.timePoints.push('12:00');
}

function removeMedicationTime(item: MedicationItem, index: number) {
  if (item.timePoints.length > 1) item.timePoints.splice(index, 1);
}

function toggleRisk(code: string) {
  const index = draft.value.riskTags.indexOf(code);
  if (index >= 0) draft.value.riskTags.splice(index, 1);
  else draft.value.riskTags.push(code);
}

function validateDraft() {
  const diseaseNames = draft.value.diseases.map((item) => item.diseaseName.trim().toLowerCase());
  if (diseaseNames.some((name) => !name)) return '请填写每一项慢病名称。';
  if (new Set(diseaseNames).size !== diseaseNames.length) return '慢病记录中存在重复项目。';

  const medicationNames = draft.value.medications.map((item) => item.medicationName.trim().toLowerCase());
  if (medicationNames.some((name) => !name)) return '请填写每一种药物名称。';
  if (new Set(medicationNames).size !== medicationNames.length) return '当前用药中存在重复药物，请合并后保存。';
  if (draft.value.medications.some((item) => !item.startDate || item.timePoints.some((time) => !time))) {
    return '请补全用药开始日期和服用时间。';
  }
  if (draft.value.medications.some((item) => item.endDate && item.endDate < item.startDate)) {
    return '用药结束日期不能早于开始日期。';
  }

  const allergens = draft.value.allergies.map((item) => item.allergenName.trim().toLowerCase());
  if (allergens.some((name) => !name)) return '请填写每一项过敏原。';
  if (new Set(allergens).size !== allergens.length) return '过敏记录中存在重复项目。';
  return '';
}

function reviewChanges() {
  const validationError = validateDraft();
  if (validationError) {
    error.value = validationError;
    return;
  }
  if (!hasChanges.value) {
    error.value = '档案内容没有变化，无需保存。';
    return;
  }
  error.value = '';
  reviewing.value = true;
}

function buildPayload(): HealthArchiveUpdateRequest {
  return {
    archiveVersion: draft.value.archiveVersion,
    diseases: clone(draft.value.diseases),
    medications: clone(draft.value.medications),
    allergies: clone(draft.value.allergies),
    riskTags: [...draft.value.riskTags],
    carePlan: clone(draft.value.carePlan)
  };
}

async function confirmSave() {
  if (!selectedElderId.value || !canEdit.value) return;
  saving.value = true;
  error.value = '';
  const response = await updateHealthArchive(selectedElderId.value, buildPayload());
  saving.value = false;
  if (response.code !== 0) {
    reviewing.value = false;
    error.value = businessError(response.code);
    return;
  }
  const refreshed = await getHealthArchive(selectedElderId.value);
  if (refreshed.code !== 0) {
    reviewing.value = false;
    error.value = '档案已保存，但暂时无法读取最新内容，请点击刷新。';
    return;
  }
  applyArchive(refreshed.data);
  editing.value = false;
  reviewing.value = false;
  successMessage.value = '健康档案已保存，并已同步最新内容。';
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
  <view class="health-archive-panel" aria-label="健康档案">
    <view class="archive-heading">
      <view>
        <text class="archive-kicker">健康档案</text>
        <text class="archive-title">{{ isFamily ? '长辈健康信息' : '我的健康摘要' }}</text>
        <text class="archive-subtitle">
          {{ isFamily ? '集中维护慢病、用药、过敏与照护安排。' : '查看当前需要重点关注的健康信息。' }}
        </text>
      </view>
      <button class="icon-command" type="button" :disabled="loading || saving" aria-label="刷新健康档案" @click="loadContext">
        刷新
      </button>
    </view>

    <scroll-view v-if="isFamily && profiles.length" class="elder-selector" scroll-x="true" :show-scrollbar="false">
      <view class="elder-selector-row">
        <button
          v-for="profile in profiles"
          :key="profile.elderId"
          class="elder-choice"
          :class="{ active: selectedElderId === profile.elderId }"
          type="button"
          @click="selectElder(profile.elderId)"
        >
          <text class="elder-choice-name">{{ profile.name }}</text>
          <text class="elder-choice-state">
            {{ bindings.some((item) => item.elderId === profile.elderId && item.scopeCodes.includes('HEALTH_VIEW')) ? '可查看' : '未授权' }}
          </text>
        </button>
      </view>
    </scroll-view>

    <view v-if="loading" class="archive-state"><text>正在读取健康档案...</text></view>
    <view v-else-if="error && !archive" class="archive-state error-state">
      <text class="state-title">暂时无法显示</text>
      <text>{{ error }}</text>
      <button class="secondary-command" type="button" @click="loadContext">重新读取</button>
    </view>
    <view v-else-if="isFamily && profiles.length === 0" class="archive-state">
      <text class="state-title">暂无可查看的长辈</text>
      <text>绑定生效并获得健康查看权限后，健康档案会显示在这里。</text>
    </view>

    <template v-else-if="archive">
      <view class="archive-person-bar">
        <view>
          <text class="person-label">当前档案</text>
          <text class="person-name">{{ selectedElderName }}</text>
        </view>
        <text class="updated-time">{{ archive.updatedAt ? `更新于 ${formatDate(archive.updatedAt)}` : '内容已同步' }}</text>
      </view>

      <template v-if="!editing">
        <view v-if="draft.riskTags.length" class="summary-section risk-section">
          <view class="section-heading"><text>重点风险</text><text>{{ draft.riskTags.length }} 项</text></view>
          <view class="tag-list">
            <text v-for="label in activeRiskLabels" :key="label" class="risk-tag">{{ label }}</text>
          </view>
        </view>
        <view v-else class="summary-section calm-section">
          <text class="section-title-text">暂无风险标签</text>
          <text class="section-help">当前档案未记录需要特别提示的风险。</text>
        </view>

        <view class="summary-section">
          <view class="section-heading"><text>当前用药</text><text>{{ draft.medications.length }} 项</text></view>
          <view v-if="draft.medications.length" class="summary-list">
            <view v-for="item in draft.medications" :key="`${item.medicationName}-${item.startDate}`" class="summary-row">
              <text class="summary-name">{{ item.medicationName }}</text>
              <text>{{ item.dosage || '剂量未记录' }} · {{ labelOf(frequencyOptions, item.frequency) }}</text>
              <text>服用时间：{{ formatTimePoints(item.timePoints) }}</text>
            </view>
          </view>
          <text v-else class="section-help">暂无当前用药记录。</text>
        </view>

        <view class="summary-section">
          <view class="section-heading"><text>照护要点</text></view>
          <view class="care-summary">
            <view><text>日常照护</text><text>{{ draft.carePlan.dailyCare || '暂无记录' }}</text></view>
            <view><text>注意事项</text><text>{{ draft.carePlan.precautions || '暂无记录' }}</text></view>
            <view v-if="isFamily"><text>照护目标</text><text>{{ draft.carePlan.careGoals || '暂无记录' }}</text></view>
          </view>
        </view>

        <template v-if="isFamily">
          <view class="summary-section">
            <view class="section-heading"><text>慢病记录</text><text>{{ draft.diseases.length }} 项</text></view>
            <view v-if="draft.diseases.length" class="summary-list">
              <view v-for="item in draft.diseases" :key="item.diseaseName" class="summary-row compact-row">
                <text class="summary-name">{{ item.diseaseName }}</text>
                <text>{{ labelOf(diseaseStatusOptions, item.status) }} · {{ item.diagnosedAt || '确诊日期未记录' }}</text>
              </view>
            </view>
            <text v-else class="section-help">暂无慢病记录。</text>
          </view>

          <view class="summary-section">
            <view class="section-heading"><text>过敏记录</text><text>{{ draft.allergies.length }} 项</text></view>
            <view v-if="draft.allergies.length" class="summary-list">
              <view v-for="item in draft.allergies" :key="item.allergenName" class="summary-row compact-row">
                <text class="summary-name">{{ item.allergenName }}</text>
                <text>{{ labelOf(severityOptions, item.severity) }} · {{ item.reaction || '反应未记录' }}</text>
              </view>
            </view>
            <text v-else class="section-help">暂无过敏记录。</text>
          </view>

          <view class="archive-actions">
            <button class="primary-command" type="button" :disabled="!canEdit" @click="startEditing">编辑健康档案</button>
          </view>
          <text v-if="!canEdit" class="permission-note">当前绑定仅可查看，需由长辈同意健康编辑权限后才能修改。</text>
        </template>
      </template>

      <view v-else class="archive-editor">
        <view class="editor-intro">
          <text>编辑 {{ selectedElderName }} 的健康档案</text>
          <text>保存前会先展示本次变更内容。</text>
        </view>

        <view class="editor-section">
          <view class="editor-section-heading"><view><text>慢病记录</text><text>记录已确诊或持续观察的健康问题</text></view><button type="button" @click="addDisease">新增</button></view>
          <view v-for="(item, index) in draft.diseases" :key="index" class="edit-card">
            <view class="edit-card-heading"><text>慢病 {{ index + 1 }}</text><button class="danger-text" type="button" @click="draft.diseases.splice(index, 1)">删除</button></view>
            <label class="archive-field"><text>慢病名称</text><input v-model="item.diseaseName" maxlength="40" placeholder="例如：高血压" /></label>
            <view class="choice-grid">
              <button v-for="option in diseaseStatusOptions" :key="option.value" type="button" :class="{ active: item.status === option.value }" @click="item.status = option.value">{{ option.label }}</button>
            </view>
            <label class="archive-field"><text>确诊日期</text><picker mode="date" start="1900-01-01" :end="today" :value="item.diagnosedAt" @change="item.diagnosedAt = $event.detail.value"><view class="picker-field">{{ item.diagnosedAt || '选择日期（可选）' }}</view></picker></label>
            <label class="archive-field"><text>补充说明</text><input v-model="item.remark" maxlength="100" placeholder="病情控制情况等（可选）" /></label>
          </view>
          <text v-if="!draft.diseases.length" class="section-help">没有慢病记录，可按需新增。</text>
        </view>

        <view class="editor-section">
          <view class="editor-section-heading"><view><text>当前用药</text><text>药物信息仅用于照护记录，不提供用药建议</text></view><button type="button" @click="addMedication">新增</button></view>
          <view v-for="(item, index) in draft.medications" :key="index" class="edit-card">
            <view class="edit-card-heading"><text>药物 {{ index + 1 }}</text><button class="danger-text" type="button" @click="draft.medications.splice(index, 1)">删除</button></view>
            <view class="two-column">
              <label class="archive-field"><text>药物名称</text><input v-model="item.medicationName" maxlength="50" placeholder="请输入药物名称" /></label>
              <label class="archive-field"><text>每次剂量</text><input v-model="item.dosage" maxlength="30" placeholder="例如：1 片" /></label>
            </view>
            <view class="choice-grid frequency-grid">
              <button v-for="option in frequencyOptions" :key="option.value" type="button" :class="{ active: item.frequency === option.value }" @click="item.frequency = option.value">{{ option.label }}</button>
            </view>
            <view class="time-list">
              <view v-for="(_, timeIndex) in item.timePoints" :key="timeIndex" class="time-row">
                <picker mode="time" :value="item.timePoints[timeIndex]" @change="item.timePoints[timeIndex] = $event.detail.value"><view class="picker-field">{{ item.timePoints[timeIndex] }}</view></picker>
                <button v-if="item.timePoints.length > 1" type="button" aria-label="删除服用时间" @click="removeMedicationTime(item, timeIndex)">删除</button>
              </view>
              <button v-if="item.timePoints.length < 3" class="inline-command" type="button" @click="addMedicationTime(item)">增加服用时间</button>
            </view>
            <view class="two-column">
              <label class="archive-field"><text>开始日期</text><picker mode="date" start="1900-01-01" :value="item.startDate" @change="item.startDate = $event.detail.value"><view class="picker-field">{{ item.startDate || '选择开始日期' }}</view></picker></label>
              <view class="archive-field">
                <text>结束日期</text>
                <view class="end-date-control">
                  <picker mode="date" :start="item.startDate || today" :value="item.endDate" @change="item.endDate = $event.detail.value"><view class="picker-field">{{ item.endDate || '选择结束日期' }}</view></picker>
                  <button :class="['long-term-choice', { active: !item.endDate }]" type="button" @click="item.endDate = ''">长期使用</button>
                </view>
              </view>
            </view>
            <label class="archive-field"><text>用药说明</text><input v-model="item.remark" maxlength="100" placeholder="例如：饭后服用（可选）" /></label>
          </view>
          <text v-if="!draft.medications.length" class="section-help">暂无当前用药，可按需新增。</text>
        </view>

        <view class="editor-section">
          <view class="editor-section-heading"><view><text>过敏记录</text><text>记录药物、食物或其他过敏原</text></view><button type="button" @click="addAllergy">新增</button></view>
          <view v-for="(item, index) in draft.allergies" :key="index" class="edit-card">
            <view class="edit-card-heading"><text>过敏项 {{ index + 1 }}</text><button class="danger-text" type="button" @click="draft.allergies.splice(index, 1)">删除</button></view>
            <view class="two-column">
              <label class="archive-field"><text>过敏原</text><input v-model="item.allergenName" maxlength="40" placeholder="例如：青霉素" /></label>
              <label class="archive-field"><text>过敏反应</text><input v-model="item.reaction" maxlength="60" placeholder="例如：皮疹" /></label>
            </view>
            <view class="choice-grid severity-grid">
              <button v-for="option in severityOptions" :key="option.value" type="button" :class="{ active: item.severity === option.value }" @click="item.severity = option.value">{{ option.label }}</button>
            </view>
            <label class="archive-field"><text>补充说明</text><input v-model="item.remark" maxlength="100" placeholder="处置方式等（可选）" /></label>
          </view>
          <text v-if="!draft.allergies.length" class="section-help">暂无过敏记录，可按需新增。</text>
        </view>

        <view class="editor-section">
          <view class="editor-section-heading"><view><text>风险标签</text><text>选择需要护理人员重点留意的风险</text></view></view>
          <view class="risk-choice-grid">
            <button v-for="option in riskChoices" :key="option.value" type="button" :class="{ active: draft.riskTags.includes(option.value) }" @click="toggleRisk(option.value)">{{ option.label }}</button>
          </view>
        </view>

        <view class="editor-section">
          <view class="editor-section-heading"><view><text>照护计划</text><text>记录家庭照护中的目标和注意事项</text></view></view>
          <label class="archive-field"><text>照护目标</text><textarea v-model="draft.carePlan.careGoals" maxlength="300" placeholder="希望通过照护达到的目标" /></label>
          <label class="archive-field"><text>日常照护</text><textarea v-model="draft.carePlan.dailyCare" maxlength="500" placeholder="饮食、活动、监测等日常安排" /></label>
          <label class="archive-field"><text>注意事项</text><textarea v-model="draft.carePlan.precautions" maxlength="500" placeholder="需要特别留意或避免的情况" /></label>
        </view>

        <view v-if="error" class="inline-error" role="alert">{{ error }}</view>
        <view class="archive-actions two-actions">
          <button class="secondary-command" type="button" :disabled="saving" @click="cancelEditing">取消</button>
          <button class="primary-command" type="button" :disabled="saving || !hasChanges" @click="reviewChanges">核对并保存</button>
        </view>
      </view>

      <view v-if="reviewing" class="review-panel" role="dialog" aria-label="核对健康档案变更">
        <view class="review-card">
          <text class="review-title">核对本次变更</text>
          <text class="review-help">将更新以下内容，确认后会以最新档案为准。</text>
          <view class="review-list">
            <view v-for="(item, index) in changeDetails" :key="`${item.category}-${index}`" class="review-item">
              <text>{{ item.category }}</text>
              <text>{{ item.description }}</text>
            </view>
          </view>
          <view class="archive-actions two-actions">
            <button class="secondary-command" type="button" :disabled="saving" @click="reviewing = false">返回修改</button>
            <button class="primary-command" type="button" :disabled="saving" @click="confirmSave">{{ saving ? '正在保存...' : '确认保存' }}</button>
          </view>
        </view>
      </view>

      <StageTwentyFourChangeHistory
        v-if="!editing"
        :elder-id="selectedElderId"
        :refresh-key="archive.archiveVersion"
      />

      <view v-if="successMessage" class="inline-success" role="status">{{ successMessage }}</view>
      <view v-if="error && !editing" class="inline-error" role="alert">{{ error }}</view>
    </template>
  </view>
</template>

<style scoped>
.health-archive-panel { display:grid; gap:20rpx; min-width:0; color:#17312e; }
.archive-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:20rpx; padding:12rpx 4rpx 4rpx; }
.archive-kicker,.archive-title,.archive-subtitle,.person-label,.person-name,.updated-time,.state-title,.section-title-text,.section-help,.summary-name,.editor-intro text,.editor-section-heading text,.archive-field>text,.review-title,.review-help { display:block; }
.archive-kicker { color:#0f766e; font-size:22rpx; font-weight:700; }
.archive-title { margin-top:6rpx; font-size:36rpx; font-weight:800; }
.archive-subtitle { margin-top:8rpx; color:#607671; font-size:25rpx; line-height:1.55; }
.icon-command,.secondary-command,.primary-command,.inline-command,.editor-section-heading button,.time-row button { min-height:88rpx; padding:0 24rpx; border:1rpx solid #bfd4cf; border-radius:4rpx; background:#fff; color:#176d65; font-size:25rpx; font-weight:700; }
.icon-command { flex:none; min-height:80rpx; padding:0 20rpx; }
button[disabled] { opacity:.48; }
.primary-command { border-color:#137f75; background:#137f75; color:#fff; }
.elder-selector { margin:0 -24rpx; width:calc(100% + 48rpx); white-space:nowrap; }
.elder-selector-row { display:flex; gap:12rpx; padding:0 24rpx; }
.elder-choice { display:grid; gap:4rpx; min-width:190rpx; padding:18rpx 20rpx; border:1rpx solid #d8e4e1; border-radius:4rpx; background:#fff; text-align:left; }
.elder-choice.active { border-color:#67bdb4; background:#e8f7f4; }
.elder-choice-name { font-size:27rpx; font-weight:800; }
.elder-choice-state { color:#6e827d; font-size:22rpx; }
.archive-state { display:grid; gap:14rpx; padding:36rpx 28rpx; border:1rpx dashed #bfd1cd; background:#fff; color:#607671; font-size:25rpx; line-height:1.55; }
.state-title { color:#17312e; font-size:29rpx; font-weight:800; }
.error-state { border-color:#efb7b2; background:#fff6f5; color:#9d3731; }
.error-state .secondary-command { justify-self:start; margin-top:6rpx; }
.archive-person-bar { display:flex; align-items:end; justify-content:space-between; gap:20rpx; padding:22rpx; border-left:6rpx solid #168c81; background:#e9f6f3; }
.person-label { color:#657b76; font-size:22rpx; }
.person-name { margin-top:4rpx; font-size:32rpx; font-weight:800; }
.updated-time { color:#657b76; font-size:22rpx; text-align:right; }
.summary-section,.editor-section { display:grid; gap:18rpx; padding:24rpx; border:1rpx solid #dce7e4; background:#fff; }
.section-heading,.editor-section-heading,.edit-card-heading { display:flex; align-items:center; justify-content:space-between; gap:16rpx; }
.section-heading text:first-child { font-size:29rpx; font-weight:800; }
.section-heading text:last-child { color:#71837f; font-size:22rpx; }
.risk-section { border-color:#efcf9b; background:#fffaf1; }
.calm-section { border-color:#bee1db; background:#f2fbf9; }
.tag-list,.risk-choice-grid { display:flex; flex-wrap:wrap; gap:12rpx; }
.risk-tag { padding:10rpx 16rpx; border:1rpx solid #e5bb75; border-radius:4rpx; background:#fff4df; color:#865a12; font-size:24rpx; font-weight:700; }
.section-title-text { font-size:28rpx; font-weight:800; }
.section-help { color:#6b7f7a; font-size:24rpx; line-height:1.5; }
.summary-list { display:grid; gap:12rpx; }
.summary-row { display:grid; gap:7rpx; padding:18rpx; border-left:5rpx solid #62b7ad; background:#f4faf8; color:#526c67; font-size:24rpx; }
.summary-name { color:#17312e; font-size:27rpx; font-weight:800; }
.compact-row { border-left-color:#a8c4be; background:#f7f9f8; }
.care-summary { display:grid; gap:14rpx; }
.care-summary view { display:grid; gap:6rpx; padding-bottom:14rpx; border-bottom:1rpx solid #e5ecea; }
.care-summary view:last-child { padding-bottom:0; border-bottom:0; }
.care-summary text:first-child { color:#48635e; font-size:23rpx; font-weight:800; }
.care-summary text:last-child { color:#203b36; font-size:25rpx; line-height:1.6; white-space:pre-wrap; }
.archive-actions { display:flex; justify-content:flex-end; gap:12rpx; }
.archive-actions .primary-command { min-width:220rpx; }
.two-actions>* { flex:1; }
.permission-note { color:#8a641f; font-size:23rpx; line-height:1.5; }
.archive-editor { display:grid; gap:20rpx; }
.editor-intro { display:grid; gap:6rpx; padding:20rpx 22rpx; border-left:6rpx solid #168c81; background:#edf8f6; }
.editor-intro text:first-child { font-size:28rpx; font-weight:800; }
.editor-intro text:last-child { color:#607671; font-size:23rpx; }
.editor-section-heading>view { display:grid; gap:4rpx; }
.editor-section-heading text:first-child { font-size:29rpx; font-weight:800; }
.editor-section-heading text:last-child { color:#71837f; font-size:22rpx; line-height:1.4; }
.editor-section-heading button { min-height:80rpx; padding:0 18rpx; }
.edit-card { display:grid; gap:16rpx; padding:20rpx; border:1rpx solid #d8e4e1; background:#f9fbfa; }
.edit-card-heading { padding-bottom:12rpx; border-bottom:1rpx solid #e2eae8; font-size:25rpx; font-weight:800; }
.danger-text { color:#c43f37; font-size:23rpx; font-weight:700; }
.archive-field { display:grid; gap:8rpx; color:#536b66; font-size:23rpx; font-weight:700; }
.archive-field input,.picker-field,.archive-field textarea { width:100%; box-sizing:border-box; border:1rpx solid #cbdad6; border-radius:4rpx; background:#fff; color:#17312e; font-size:26rpx; font-weight:600; }
.archive-field input,.picker-field { min-height:88rpx; padding:0 18rpx; line-height:88rpx; }
.archive-field textarea { min-height:150rpx; padding:16rpx 18rpx; line-height:1.5; }
.two-column { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:14rpx; }
.choice-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10rpx; }
.choice-grid button,.risk-choice-grid button { min-height:88rpx; padding:0 12rpx; border:1rpx solid #cbdad6; border-radius:4rpx; background:#fff; color:#516863; font-size:23rpx; font-weight:700; }
.choice-grid button.active,.risk-choice-grid button.active { border-color:#63b9af; background:#e4f6f2; color:#0f766e; }
.frequency-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
.severity-grid { grid-template-columns:repeat(3,minmax(0,1fr)); }
.risk-choice-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); }
.time-list { display:grid; gap:10rpx; }
.time-row { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:10rpx; }
.time-row button { min-height:88rpx; color:#b73d36; }
.inline-command { justify-self:start; min-height:80rpx; }
.end-date-control { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:10rpx; }
.long-term-choice { width:auto; min-width:156rpx; min-height:88rpx; margin:0; padding:0 16rpx; border:1rpx solid #bfd4cf; border-radius:4rpx; background:#fff; color:#526a65; font-size:22rpx; font-weight:700; line-height:1.2; }
.long-term-choice.active { border-color:#67bcb1; background:#e5f6f2; color:#0f766e; }
.inline-error,.inline-success { padding:18rpx 20rpx; border:1rpx solid #efb7b2; background:#fff2f1; color:#a3342e; font-size:24rpx; line-height:1.5; }
.inline-success { border-color:#9fd8cf; background:#eaf8f5; color:#0f766e; }
.review-panel { position:fixed; inset:0; z-index:30; display:flex; align-items:flex-end; justify-content:center; padding:24rpx; box-sizing:border-box; background:rgba(15,34,31,.44); }
.review-card { display:grid; gap:18rpx; width:min(100%,720rpx); padding:28rpx; box-sizing:border-box; border:1rpx solid #c7d9d5; background:#fff; box-shadow:0 18rpx 50rpx rgba(16,52,46,.2); }
.review-title { font-size:32rpx; font-weight:800; }
.review-help { color:#617772; font-size:24rpx; line-height:1.5; }
.review-list { display:grid; gap:10rpx; max-height:46vh; overflow-y:auto; }
.review-item { display:grid; grid-template-columns:130rpx minmax(0,1fr); gap:14rpx; padding:14rpx 16rpx; border:1rpx solid #c9ded9; background:#f1f9f7; }
.review-item text:first-child { color:#176d65; font-size:22rpx; font-weight:800; }
.review-item text:last-child { color:#29443f; font-size:23rpx; font-weight:650; line-height:1.5; }
@media (max-width:390px) {
  .archive-heading { align-items:stretch; }
  .archive-subtitle { max-width:240px; }
  .two-column { grid-template-columns:1fr; }
  .archive-person-bar { align-items:flex-start; flex-direction:column; }
  .updated-time { text-align:left; }
}
</style>
