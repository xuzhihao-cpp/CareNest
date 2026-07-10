<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import StageEightServiceItemsPanel from '@/components/StageEightServiceItemsPanel.vue';
import StageElevenAdminOrdersPanel from '@/components/StageElevenAdminOrdersPanel.vue';
import StageTwelveDispatchPanel from '@/components/StageTwelveDispatchPanel.vue';
import StageFifteenServiceReportPanel from '@/components/StageFifteenServiceReportPanel.vue';
import StageSeventeenOrderChangePanel from '@/components/StageSeventeenOrderChangePanel.vue';
import StageEighteenIntegrationPanel from '@/components/StageEighteenIntegrationPanel.vue';
import type { AuthUser } from '@/types/stageTwo';

type AdminView = 'overview' | 'services' | 'orders' | 'reports' | 'system';
const view = ref<AdminView>('overview');
const user = ref<AuthUser | null>(null);
const allowed = computed(() => user.value?.roles.includes('ADMIN'));
const nav: Array<{ key: AdminView; label: string }> = [
  { key: 'overview', label: '运营概览' }, { key: 'services', label: '服务项目' },
  { key: 'orders', label: '订单调度' }, { key: 'reports', label: '服务报告' }, { key: 'system', label: '系统检查' }
];
async function loadUser() { const response = await getCurrentUser(); if (response.code === 0) user.value = response.data; }
async function signOut() { await logout(); uni.redirectTo({ url: '/pages/login/index' }); }
onMounted(loadUser);
</script>

<template>
  <view class="admin-app">
    <aside class="admin-nav">
      <view class="brand"><text class="brand-mark">C</text><text>CareNest</text></view>
      <view class="nav-group"><button v-for="item in nav" :key="item.key" :class="{ active: view === item.key }" type="button" @click="view=item.key">{{ item.label }}</button></view>
      <view class="nav-user"><text>{{ user?.displayName || '管理员' }}</text><button type="button" @click="signOut">退出登录</button></view>
    </aside>
    <main class="admin-main">
      <view class="admin-top"><view><text class="eyebrow">OPERATIONS</text><text class="page-title">{{ nav.find((item) => item.key === view)?.label }}</text></view><text class="today">今日运营中心</text></view>
      <view v-if="!allowed" class="admin-access">请使用管理员账号登录。</view>
      <template v-else>
        <view v-if="view === 'overview'" class="overview">
          <view class="metric"><text>待派订单</text><strong>12</strong><small>需要安排护理员</small></view><view class="metric"><text>服务进行中</text><strong>28</strong><small>今日实时追踪</small></view><view class="metric"><text>待确认报告</text><strong>9</strong><small>家属与长辈待处理</small></view>
          <view class="overview-note"><text>工作提示</text><strong>优先处理待派订单与异常服务记录</strong><button type="button" @click="view='orders'">进入订单调度</button></view>
        </view>
        <StageEightServiceItemsPanel v-if="view === 'services'" role-code="ADMIN" :auth-user="user" />
        <template v-if="view === 'orders'"><StageElevenAdminOrdersPanel role-code="ADMIN" :auth-user="user" /><StageTwelveDispatchPanel role-code="ADMIN" :auth-user="user" /><StageSeventeenOrderChangePanel role-code="ADMIN" :auth-user="user" /></template>
        <StageFifteenServiceReportPanel v-if="view === 'reports'" role-code="ADMIN" :auth-user="user" />
        <StageEighteenIntegrationPanel v-if="view === 'system'" role-code="ADMIN" :auth-user="user" />
      </template>
    </main>
  </view>
</template>

<style scoped>
.admin-app { min-height:100vh; display:grid; grid-template-columns:224px minmax(0,1fr); background:#f4f7f6; color:#18312d; }.admin-nav { display:flex; flex-direction:column; padding:26px 16px; background:#123d39; color:#eaf5f1; }.brand { display:flex; align-items:center; gap:10px; padding:0 10px 34px; font-size:22px; font-weight:700; }.brand-mark { display:grid; place-items:center; width:30px; height:30px; border-radius:7px; background:#34b3a5; color:#103a36; }.nav-group { display:grid; gap:6px; }.nav-group button { margin:0; padding:12px 14px; border:0; border-radius:6px; background:transparent; color:#b9d3cf; text-align:left; font-size:14px; }.nav-group button.active { background:#245a55; color:#fff; font-weight:700; }.nav-user { margin-top:auto; display:grid; gap:9px; padding:14px 10px 0; border-top:1px solid rgba(236,255,250,.15); color:#c3dad5; font-size:13px; }.nav-user button { width:max-content; margin:0; padding:0; border:0; background:transparent; color:#79cfc5; font-size:13px; }.admin-main { min-width:0; padding:30px 38px 56px; }.admin-top { display:flex; align-items:flex-end; justify-content:space-between; margin-bottom:28px; }.eyebrow,.page-title { display:block; }.eyebrow { color:#23877d; font-size:11px; font-weight:700; letter-spacing:1.6px; }.page-title { margin-top:8px; font-size:30px; font-weight:700; }.today { color:#748681; font-size:14px; }.overview { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; }.metric,.overview-note,.admin-access { display:grid; gap:8px; padding:22px; border:1px solid #dce7e4; border-radius:8px; background:#fff; }.metric text,.metric small { color:#6e817d; font-size:13px; }.metric strong { color:#173c37; font-size:34px; }.overview-note { grid-column:1 / -1; grid-template-columns:1fr auto; align-items:center; }.overview-note text { color:#23877d; font-size:13px; font-weight:700; }.overview-note strong { grid-column:1; font-size:18px; }.overview-note button { grid-column:2; grid-row:1 / 3; margin:0; border:0; border-radius:6px; background:#147d72; color:#fff; padding:11px 16px; }.admin-main :deep(.glass-panel) { border-radius:8px; box-shadow:none; }.admin-main :deep(.stage-eight-panel),.admin-main :deep(.stage-eleven-panel),.admin-main :deep(.stage-twelve-panel),.admin-main :deep(.stage-fifteen-panel),.admin-main :deep(.stage-seventeen-panel),.admin-main :deep(.stage-eighteen-panel) { margin-bottom:18px; }
@media (max-width:800px) { .admin-app { grid-template-columns:1fr; }.admin-nav { padding:16px; }.brand { padding-bottom:16px; }.nav-group { grid-template-columns:repeat(3,minmax(0,1fr)); overflow:auto; }.nav-group button { white-space:nowrap; }.nav-user { display:none; }.admin-main { padding:22px 16px 40px; }.overview { grid-template-columns:1fr; }.overview-note { grid-column:auto; }.admin-top { align-items:center; }.today { display:none; } }
</style>
