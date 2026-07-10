<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  getElderProfile,
  getFamilyElders,
  getStageSevenEndpointSummary,
  resetStageSevenMockRecords,
  updateElderProfile
} from '@/api/stageSeven';
import { getFamilyBindings } from '@/api/stageSix';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type {
  CareLevel,
  ElderProfileRequest,
  ElderProfileResponse,
  ElderProfileScenario,
  Gender,
  RelationType
} from '@/types/stageSeven';
import type { BindingResponse } from '@/types/stageSix';

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const genderOptions: Array<{ value: Gender; label: string }> = [
  { value: 'FEMALE', label: '女' },
  { value: 'MALE', label: '男' },
  { value: 'UNKNOWN', label: '未知' }
];

const careLevelOptions: Array<{ value: CareLevel; label: string }> = [
  { value: 'LEVEL_1', label: '一级照护' },
  { value: 'LEVEL_2', label: '二级照护' },
  { value: 'LEVEL_3', label: '三级照护' }
];

const relationOptions: Array<{ value: RelationType; label: string }> = [
  { value: 'SON', label: '儿子' },
  { value: 'DAUGHTER', label: '女儿' },
  { value: 'SPOUSE', label: '配偶' },
  { value: 'OTHER', label: '其他' }
];

const emptyForm: ElderProfileRequest = {
  name: '',
  gender: 'UNKNOWN',
  birthDate: '',
  careLevel: 'LEVEL_1',
  emergencyContacts: [
    {
      contactName: '',
      contactPhone: '',
      relationType: 'OTHER'
    }
  ]
};

const records = ref<ElderProfileResponse[]>([]);
const selectedElderId = ref('');
const form = ref<ElderProfileRequest>(cloneProfile(emptyForm));
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<ElderProfileResponse> | null>(null);
const endpoints = getStageSevenEndpointSummary();
const familyBindings = ref<BindingResponse[]>([]);

const selectedProfile = computed(() => records.value.find((item) => item.elderId === selectedElderId.value) ?? null);
const isFamily = computed(() => props.roleCode === 'FAMILY' && props.authUser?.roles.includes('FAMILY'));
const isElder = computed(() => props.roleCode === 'ELDER' && props.authUser?.roles.includes('ELDER'));
const totalContacts = computed(() => form.value.emergencyContacts.length);
const currentVersion = computed(() => selectedProfile.value?.profileVersion ?? '-');
const canEditProfile = computed(() => {
  if (isElder.value) {
    return true;
  }
  return familyBindings.value.some((binding) =>
    binding.elderId === selectedElderId.value
    && binding.bindingStatus === 'ACTIVE'
    && (binding.scopeCodes.includes('HEALTH_EDIT') || binding.scopeCodes.includes('ARCHIVE_EDIT'))
  );
});

function formatProfileUpdatedAt(profileVersion: string) {
  const timestamp = profileVersion.slice(profileVersion.indexOf(':') + 1);
  const parsed = new Date(timestamp);
  if (Number.isNaN(parsed.getTime())) {
    return '已保存';
  }
  const pad = (value: number) => String(value).padStart(2, '0');
  return `最近更新于 ${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ${pad(parsed.getHours())}:${pad(parsed.getMinutes())}`;
}

function cloneProfile<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function ensurePrimaryContact(payload: ElderProfileRequest) {
  if (payload.emergencyContacts.length === 0) {
    payload.emergencyContacts = cloneProfile(emptyForm.emergencyContacts);
  }
}

function updateEmergencyPhone(event: InputEvent & { detail?: { value?: string } }) {
  const rawValue = event.detail?.value ?? (event.target as HTMLInputElement | null)?.value ?? '';
  const phone = rawValue.replace(/\D/g, '').slice(0, 11);
  form.value.emergencyContacts[0].contactPhone = phone;
}

function selectBirthDate(event: { detail: { value: string } }) {
  form.value.birthDate = event.detail.value;
}

function applyProfile(profile: ElderProfileResponse) {
  const index = records.value.findIndex((item) => item.elderId === profile.elderId);
  if (index >= 0) {
    records.value.splice(index, 1, cloneProfile(profile));
  }
  form.value = {
    name: profile.name,
    gender: profile.gender as Gender,
    birthDate: profile.birthDate,
    careLevel: profile.careLevel as CareLevel,
    emergencyContacts: profile.emergencyContacts.length > 0
      ? profile.emergencyContacts.map((contact) => ({
          ...contact,
          relationType: (contact.relationType ?? 'OTHER') as RelationType
        }))
      : cloneProfile(emptyForm.emergencyContacts)
  };
  selectedElderId.value = profile.elderId;
  ensurePrimaryContact(form.value);
}

function labelOption(options: Array<{ value: string; label: string }>, value: string) {
  return options.find((item) => item.value === value)?.label ?? value;
}

async function selectProfile(elderId: string, successText = '档案已读取') {
  selectedElderId.value = elderId;
  const response = await getElderProfile(elderId);
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    applyProfile(response.data);
    error.value = '';
    message.value = successText;
  } else {
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function loadFamilyElders(scenario: ElderProfileScenario = 'normal') {
  if (!isFamily.value) {
    return;
  }
  loading.value = true;
  const response = await getFamilyElders(scenario);
  loading.value = false;
  lastTraceId.value = response.traceId;
  lastResponse.value = null;
  if (response.code === 0) {
    records.value = response.data;
    error.value = '';
    message.value =
      scenario === 'empty' ? '已切换为空档案 mock' : scenario === 'normal' ? '长辈档案列表已加载' : message.value;
    if (records.value.length > 0) {
      applyProfile(records.value[0]);
    } else {
      selectedElderId.value = '';
      form.value = cloneProfile(emptyForm);
    }
  } else {
    records.value = [];
    selectedElderId.value = '';
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function loadFamilyPermissions() {
  if (!isFamily.value) {
    return;
  }
  const response = await getFamilyBindings();
  familyBindings.value = response.code === 0 ? response.data : [];
}

async function loadElderProfile() {
  if (!isElder.value) {
    return;
  }
  loading.value = true;
  const response = await getElderProfile('elder_001');
  loading.value = false;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    records.value = [response.data];
    applyProfile(response.data);
    error.value = '';
    message.value = '长辈端基础档案已加载';
  } else {
    records.value = [];
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function saveProfile() {
  if (!selectedElderId.value) {
    error.value = '请先选择一位长辈';
    return;
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.value.birthDate)) {
    error.value = '请输入有效的出生日期，格式为 YYYY-MM-DD';
    return;
  }
  const response = await updateElderProfile(selectedElderId.value, form.value);
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    error.value = '';
    await selectProfile(response.data.elderId, '基础档案已保存');
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function resetNormalMock() {
  resetStageSevenMockRecords();
  await loadFamilyElders('normal');
}

onMounted(() => {
  loadFamilyPermissions();
  if (isFamily.value) {
    loadFamilyElders();
    return;
  }
  if (isElder.value) {
    loadElderProfile();
  }
});
</script>

<template>
  <view class="stage-seven-panel glass-panel" aria-label="阶段7长辈基础档案">
    <view class="section-title">
      <text>⑦</text>
      <text>长辈基础档案 MVP</text>
    </view>

    <view class="stage-seven-summary">
      <view>
        <text class="section-mini">records / profileVersion / contacts</text>
        <text class="permission-main">{{ records.length }} / {{ currentVersion }} / {{ totalContacts }}</text>
        <text class="auth-meta">基础档案列表 / 版本 / 紧急联系人</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-7' }}</text>
        <text class="auth-meta">保存后刷新仍可读取 profileVersion</text>
      </view>
    </view>

    <view class="stage-seven-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view v-if="roleCode === 'FAMILY' || roleCode === 'ELDER'" class="profile-workbench">
      <view v-if="roleCode === 'FAMILY'" class="profile-list">
        <button
          v-for="record in records"
          :key="record.elderId"
          class="profile-row"
          :class="{ active: selectedElderId === record.elderId }"
          type="button"
          @click="selectProfile(record.elderId)"
        >
          <view>
            <text class="flow-label">长辈档案</text>
            <text class="flow-time">{{ formatProfileUpdatedAt(record.profileVersion) }}</text>
          </view>
          <text class="tag tag-teal">已保存</text>
        </button>

        <view class="binding-actions">
          <button class="ghost-action test-action" type="button" :disabled="loading" @click="resetNormalMock">
            <text>正常 mock</text>
          </button>
          <button class="ghost-action test-action" type="button" @click="loadFamilyElders('empty')">
            <text>空数据 mock</text>
          </button>
          <button class="ghost-action test-action" type="button" @click="loadFamilyElders('error')">
            <text>错误 mock</text>
          </button>
        </view>
      </view>

      <view class="profile-form">
        <view v-if="!selectedElderId" class="empty-state">
          <view>
            <text class="empty-title">暂无已授权长辈</text>
            <text class="empty-desc">请先在“绑定授权”完成长辈确认，授权生效后即可编辑基础档案。</text>
          </view>
        </view>
        <template v-else>
        <label class="field">
          <text>姓名 name</text>
          <input v-model="form.name" class="input" placeholder="请输入长辈姓名" />
        </label>
        <label class="field">
          <text>出生日期 birthDate</text>
          <picker
            mode="date"
            start="1900-01-01"
            :end="new Date().toISOString().slice(0, 10)"
            :value="form.birthDate"
            @change="selectBirthDate"
          >
            <view class="input date-picker-input" :class="{ 'is-empty': !form.birthDate }">
              {{ form.birthDate || '请选择出生日期' }}
            </view>
          </picker>
        </label>

        <view class="binding-options">
          <text class="section-mini">性别 gender</text>
          <view class="segmented-row">
            <button
              v-for="item in genderOptions"
              :key="item.value"
              class="choice-button"
              :class="{ active: form.gender === item.value }"
              type="button"
              @click="form.gender = item.value"
            >
              <text>{{ item.label }}</text>
            </button>
          </view>
        </view>

        <view class="binding-options">
          <text class="section-mini">照护等级 careLevel</text>
          <view class="segmented-row">
            <button
              v-for="item in careLevelOptions"
              :key="item.value"
              class="choice-button"
              :class="{ active: form.careLevel === item.value }"
              type="button"
              @click="form.careLevel = item.value"
            >
              <text>{{ item.label }}</text>
            </button>
          </view>
        </view>

        <view class="contact-grid">
          <label class="field">
            <text>紧急联系人 contactName</text>
            <input v-model="form.emergencyContacts[0].contactName" class="input" maxlength="32" placeholder="联系人姓名" />
          </label>
          <label class="field">
            <text>联系电话 contactPhone</text>
            <input v-model="form.emergencyContacts[0].contactPhone" class="input" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="11" placeholder="11 位手机号码" @input="updateEmergencyPhone" />
          </label>
        </view>

        <view class="binding-options">
          <text class="section-mini">联系人关系 relationType</text>
          <view class="segmented-row">
            <button
              v-for="item in relationOptions"
              :key="item.value"
              class="choice-button"
              :class="{ active: form.emergencyContacts[0].relationType === item.value }"
              type="button"
              @click="form.emergencyContacts[0].relationType = item.value"
            >
              <text>{{ item.label }}</text>
            </button>
          </view>
        </view>

        <text v-if="isFamily && !canEditProfile" class="field-error">当前授权仅可查看，不能修改基础档案</text>
        <button class="hero-action" type="button" :disabled="loading || !selectedElderId || !canEditProfile" @click="saveProfile">
          <text>保存基础档案</text>
        </button>
        </template>
      </view>
    </view>

    <view v-if="selectedProfile" class="profile-saved-card">
      <view class="profile-saved-heading">
        <view class="profile-saved-title">
          <text class="section-mini">当前基础档案</text>
          <text class="permission-main">{{ form.name || '未填写姓名' }}</text>
        </view>
        <text class="tag tag-teal">已保存</text>
      </view>
      <view class="profile-saved-grid">
        <text>出生日期：{{ form.birthDate || '未填写' }}</text>
        <text>性别：{{ labelOption(genderOptions, form.gender) }}</text>
        <text>照护等级：{{ labelOption(careLevelOptions, form.careLevel) }}</text>
        <text>紧急联系人：{{ form.emergencyContacts[0]?.contactName || '未填写' }}</text>
        <text>联系电话：{{ form.emergencyContacts[0]?.contactPhone || '未填写' }}</text>
        <text>关系：{{ labelOption(relationOptions, form.emergencyContacts[0]?.relationType || 'OTHER') }}</text>
      </view>
    </view>

    <view v-if="roleCode === 'ELDER' && selectedProfile" class="profile-readonly">
      <view class="profile-detail">
        <text class="section-mini">GET /api/v1/elders/{elderId}/profile</text>
        <text class="access-title">{{ selectedProfile.elderId }}</text>
        <text class="access-desc">profileVersion {{ selectedProfile.profileVersion }}</text>
      </view>
    </view>

    <view v-if="message" class="success-banner">
      <text>{{ message }}</text>
    </view>
    <view v-if="error" class="error-banner" role="alert">
      <text>{{ error }}</text>
    </view>

    <view v-if="records.length === 0 && !error" class="empty-state">
      <text class="empty-icon">∅</text>
      <view>
        <text class="empty-title">暂无长辈基础档案</text>
        <text class="empty-desc">空数据 mock 已返回 records: []，分页结构仍保持 total / page / size。</text>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次保存响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.elderId }} · profileVersion {{ lastResponse.data.profileVersion }}
      </text>
    </view>
  </view>
</template>
