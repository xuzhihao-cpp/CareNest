<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  approveElderBinding,
  createFamilyBinding,
  getElderBindings,
  getFamilyBindings,
  getStageSixEndpointSummary,
  revokeFamilyBinding,
  updateFamilyBindingScopes
} from '@/api/stageSix';
import { isMockEnabled } from '@/api/client';
import { displayScopeLabel } from '@/utils/displayLabels';
import type { ApiResponse } from '@/types/api';
import type { RoleCode } from '@/types/stageOne';
import type { AuthUser } from '@/types/stageTwo';
import type {
  BindingRequest,
  BindingResponse,
  BindingScenario,
  BindingScopeCode,
  BindingStatus,
  RelationType
} from '@/types/stageSix';

type BindingDisplay = BindingResponse & {
  pendingScopeCodes?: string[];
  scopeUpdatePending?: boolean;
};

const props = defineProps<{
  roleCode: RoleCode;
  authUser: AuthUser | null;
}>();

const relationOptions: Array<{ value: RelationType; label: string }> = [
  { value: 'SON', label: '儿子' },
  { value: 'DAUGHTER', label: '女儿' },
  { value: 'SPOUSE', label: '配偶' },
  { value: 'OTHER', label: '其他' }
];

const scopeOptions: Array<{ value: BindingScopeCode; label: string }> = [
  { value: 'HEALTH_VIEW', label: '健康查看' },
  { value: 'HEALTH_EDIT', label: '健康编辑' },
  { value: 'ORDER_CREATE', label: '代下单' },
  { value: 'REPORT_VIEW', label: '报告查看' },
  { value: 'REPORT_CONFIRM', label: '报告确认' },
  { value: 'ARCHIVE_EDIT', label: '归档编辑' }
];

const statusLabels: Record<string, string> = {
  PENDING: '待确认',
  ACTIVE: '已生效',
  REJECTED: '已拒绝',
  REVOKED: '已撤销',
  EXPIRED: '已过期'
};

const form = ref<BindingRequest>({
  elderInviteCode: 'elder_001',
  relationType: 'DAUGHTER',
  scopeCodes: ['HEALTH_VIEW', 'REPORT_VIEW']
});
const records = ref<BindingDisplay[]>([]);
const loading = ref(false);
const message = ref('');
const error = ref('');
const lastTraceId = ref('');
const lastResponse = ref<ApiResponse<BindingResponse> | null>(null);
const endpoints = getStageSixEndpointSummary();
const mockEnabled = isMockEnabled();

const activeCount = computed(() => records.value.filter((item) => item.bindingStatus === 'ACTIVE').length);
const pendingCount = computed(() => records.value.filter((item) => item.bindingStatus === 'PENDING').length);
const canFamilyOperate = computed(() => props.authUser?.roles.includes('FAMILY') && props.roleCode === 'FAMILY');
const canElderApprove = computed(() => props.authUser?.roles.includes('ELDER') && props.roleCode === 'ELDER');
const currentBinding = computed(() => {
  const sameElder = records.value.filter((record) => record.elderId === form.value.elderInviteCode);
  return sameElder.find((record) => record.bindingStatus === 'PENDING')
    ?? sameElder.find((record) => record.bindingStatus === 'ACTIVE')
    ?? null;
});
const submitBindingLabel = computed(() => currentBinding.value ? '更新绑定' : '提交绑定');

function syncFormFromCurrentBinding() {
  const binding = currentBinding.value;
  if (!binding) {
    return;
  }
  form.value.relationType = binding.relationType;
  form.value.scopeCodes = [
    ...(binding.scopeUpdatePending ? binding.pendingScopeCodes ?? binding.scopeCodes : binding.scopeCodes)
  ] as BindingScopeCode[];
}

function labelRelation(value: string) {
  return relationOptions.find((item) => item.value === value)?.label ?? value;
}

function labelScope(value: string) {
  return scopeOptions.find((item) => item.value === value)?.label ?? displayScopeLabel(value);
}

function statusClass(value: string) {
  if (value === 'ACTIVE') {
    return 'tag-teal';
  }
  if (value === 'PENDING') {
    return 'tag-amber';
  }
  return 'tag-coral';
}

function toggleScope(scopeCode: BindingScopeCode) {
  const exists = form.value.scopeCodes.includes(scopeCode);
  form.value.scopeCodes = exists
    ? form.value.scopeCodes.filter((item) => item !== scopeCode)
    : [...form.value.scopeCodes, scopeCode];
}

function applyResponse(response: ApiResponse<BindingResponse>, successText: string) {
  lastResponse.value = response;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    message.value = successText;
    error.value = '';
  } else {
    message.value = '';
    error.value = `${response.code} ${response.message}`;
  }
}

async function loadBindings(scenario: BindingScenario = 'normal') {
  if (!canFamilyOperate.value && !canElderApprove.value) {
    return;
  }
  loading.value = true;
  const response = canFamilyOperate.value
    ? await getFamilyBindings(scenario)
    : await getElderBindings(scenario);
  loading.value = false;
  lastTraceId.value = response.traceId;
  if (response.code === 0) {
    records.value = response.data.filter((record) => record.bindingStatus !== 'REVOKED');
    error.value = '';
    message.value =
      scenario === 'empty' ? '已切换为空数据 mock' : scenario === 'normal' ? '绑定列表已加载' : message.value;
  } else {
    records.value = [];
    error.value = `${response.code} ${response.message}`;
    message.value = '';
  }
}

async function submitBinding() {
  if (currentBinding.value) {
    const response = await updateFamilyBindingScopes(currentBinding.value.bindingId, form.value);
    applyResponse(response, currentBinding.value.bindingStatus === 'PENDING'
      ? '待确认绑定已更新'
      : '已提交绑定变更，等待长辈确认');
    await loadBindings();
    return;
  }
  const response = await createFamilyBinding(form.value);
  applyResponse(response, '已提交绑定申请，等待长辈确认');
  await loadBindings();
}

async function revokeBinding(record: BindingDisplay) {
  const response = await revokeFamilyBinding(record.bindingId, {
    elderInviteCode: form.value.elderInviteCode,
    relationType: record.relationType,
    scopeCodes: record.scopeCodes
  });
  applyResponse(response, '绑定已撤销');
  await loadBindings();
}

async function approvePending(target: BindingDisplay) {
  const response = await approveElderBinding(target.bindingId, {
    elderInviteCode: target.elderId,
    relationType: target.relationType,
    scopeCodes: target.scopeCodes
  });
  applyResponse(response, '长辈端已确认绑定');
  await loadBindings();
}

onMounted(() => {
  loadBindings();
});

watch(() => form.value.elderInviteCode, () => {
  syncFormFromCurrentBinding();
});

watch(records, () => {
  syncFormFromCurrentBinding();
}, { deep: true });
</script>

<template>
  <view class="stage-six-panel glass-panel" aria-label="阶段6长辈家属绑定管理">
    <view class="section-title">
      <text>⑥</text>
      <text>长辈与家属绑定</text>
    </view>

    <view class="stage-six-summary">
      <view>
        <text class="section-mini">records / total / page / size</text>
        <text class="permission-main">{{ records.length }} / {{ activeCount }} / {{ pendingCount }}</text>
        <text class="auth-meta">全部绑定 / ACTIVE / PENDING</text>
      </view>
      <view>
        <text class="section-mini">traceId</text>
        <text class="permission-main">{{ lastTraceId || 'mock-6' }}</text>
        <text class="auth-meta">统一返回 { code, message, data, traceId }</text>
      </view>
    </view>

    <view class="stage-six-endpoints">
      <text v-for="item in endpoints" :key="item" class="tag tag-blue">{{ item }}</text>
    </view>

    <view v-if="roleCode === 'FAMILY'" class="binding-form">
      <label class="field">
        <text>长辈邀请码</text>
        <input v-model="form.elderInviteCode" class="input" placeholder="elder_001" />
      </label>

      <view class="binding-options">
        <text class="section-mini">与长辈的关系</text>
        <view class="segmented-row">
          <button
            v-for="item in relationOptions"
            :key="item.value"
            class="choice-button"
            :class="{ active: form.relationType === item.value }"
            type="button"
            @click="form.relationType = item.value"
          >
            <text>{{ item.label }}</text>
          </button>
        </view>
      </view>

      <view class="binding-options">
        <text class="section-mini">绑定范围</text>
        <view class="segmented-row">
          <button
            v-for="item in scopeOptions"
            :key="item.value"
            class="choice-button scope"
            :class="{ active: form.scopeCodes.includes(item.value) }"
            type="button"
            @click="toggleScope(item.value)"
          >
            <text>{{ item.label }}</text>
          </button>
        </view>
      </view>

      <view class="binding-actions">
        <button class="hero-action" type="button" :disabled="loading || form.scopeCodes.length === 0" @click="submitBinding">
          <text>{{ submitBindingLabel }}</text>
        </button>
        <button class="ghost-action" type="button" @click="loadBindings('normal')">
          <text>刷新绑定</text>
        </button>
        <button v-if="mockEnabled" class="ghost-action" type="button" @click="loadBindings('empty')">
          <text>空数据 mock</text>
        </button>
        <button v-if="mockEnabled" class="ghost-action" type="button" @click="loadBindings('error')">
          <text>错误 mock</text>
        </button>
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
        <text class="empty-title">暂无绑定记录</text>
        <text class="empty-desc">当前账号没有可显示的绑定记录。</text>
      </view>
    </view>

    <view v-else class="binding-list">
      <view v-for="record in records" :key="record.bindingId" class="binding-row">
        <view class="binding-row-main">
          <text class="flow-label">{{ record.elderName }}</text>
          <text class="flow-time">
            关系：{{ labelRelation(record.relationType) }}
          </text>
          <view class="permission-tags">
            <text v-for="scope in record.scopeCodes" :key="scope" class="tag tag-blue">{{ labelScope(scope) }}</text>
          </view>
          <view v-if="record.scopeUpdatePending" class="permission-tags pending-scope-tags">
            <text class="tag tag-amber">待长辈确认的绑定变更</text>
            <text v-for="scope in record.pendingScopeCodes ?? []" :key="`pending-${scope}`" class="tag tag-amber">
              {{ labelScope(scope) }}
            </text>
          </view>
        </view>
        <view class="binding-row-side">
          <text class="tag" :class="statusClass(record.bindingStatus)">
            {{ statusLabels[record.bindingStatus] ?? record.bindingStatus }}
          </text>
          <button
            v-if="roleCode === 'ELDER' && record.bindingStatus === 'PENDING'"
            class="hero-action"
            type="button"
            @click="approvePending(record)"
          >
            <text>确认绑定</text>
          </button>
          <button
            v-if="roleCode === 'ELDER' && record.scopeUpdatePending"
            class="hero-action"
            type="button"
            @click="approvePending(record)"
          >
            <text>确认绑定变更</text>
          </button>
          <button
            v-if="roleCode === 'FAMILY' && record.bindingStatus !== 'REVOKED'"
            class="ghost-action danger-lite"
            type="button"
            @click="revokeBinding(record)"
          >
            <text>撤销绑定</text>
          </button>
        </view>
      </view>
    </view>

    <view v-if="lastResponse" class="contract-response">
      <text class="section-mini">最近一次操作响应 DTO</text>
      <text>{{ lastResponse.code }} / {{ lastResponse.message }} / {{ lastResponse.traceId }}</text>
      <text v-if="lastResponse.code === 0">
        {{ lastResponse.data.bindingId }} · {{ lastResponse.data.bindingStatus }} ·
        {{ lastResponse.data.scopeCodes.join(',') }}
      </text>
    </view>
  </view>
</template>
