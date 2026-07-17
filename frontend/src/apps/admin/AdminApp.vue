<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getHomeSummary } from '@/api/stageFour';
import { getCurrentUser, logout } from '@/api/stageTwo';
import { getPreferredNursePermissions } from '@/api/stageThirty';
import StageEightServiceItemsPanel from '@/components/StageEightServiceItemsPanel.vue';
import StageElevenAdminOrdersPanel from '@/components/StageElevenAdminOrdersPanel.vue';
import StageTwelveDispatchPanel from '@/components/StageTwelveDispatchPanel.vue';
import StageFifteenServiceReportPanel from '@/components/StageFifteenServiceReportPanel.vue';
import StageSeventeenOrderChangePanel from '@/components/StageSeventeenOrderChangePanel.vue';
import StageTwentyOneMedicalReviewPanel from '@/components/StageTwentyOneMedicalReviewPanel.vue';
import StageTwentyThreeReviewTaskList from '@/components/StageTwentyThreeReviewTaskList.vue';
import StageTwentySevenQualificationReviewWorkbench from '@/components/StageTwentySevenQualificationReviewWorkbench.vue';
import StageTwentyEightTrainingReviewWorkbench from '@/components/StageTwentyEightTrainingReviewWorkbench.vue';
import StageFortyNineTrainingArticleAdmin from '@/components/StageFortyNineTrainingArticleAdmin.vue';
import StageFiftyOneFollowUpPanel from '@/components/StageFiftyOneFollowUpPanel.vue';
import StageFiftyTwoFiftyThreeDashboard from '@/components/StageFiftyTwoFiftyThreeDashboard.vue';
import StageFiftyFourFiftyFiveDeliveryPanel from '@/components/StageFiftyFourFiftyFiveDeliveryPanel.vue';
import type { HomeCard } from '@/types/stageFour';
import type { AuthUser } from '@/types/stageTwo';
import StageFortyThreeCustomerServicePanel from '@/components/StageFortyThreeCustomerServicePanel.vue';
import StageFortyFiveAdminReviewPanel from '@/components/StageFortyFiveAdminReviewPanel.vue';
import StageFortyFiveToFortyEightAdminPanel from '@/components/StageFortyFiveToFortyEightAdminPanel.vue';
import StageThirtyFourToFortyAdminPanel from '@/components/StageThirtyFourToFortyAdminPanel.vue';

type AdminView = 'overview' | 'services' | 'orders' | 'reports' | 'medical-files' | 'health-review' | 'qualifications' | 'training' | 'training-articles' | 'follow-ups' | 'dashboard' | 'delivery' | 'customer-service' | 'service-supervision' | 'care-metrics';
const view = ref<AdminView>('overview');
const user = ref<AuthUser | null>(null);
const overviewCards = ref<HomeCard[]>([]);
const overviewError = ref('');
const permissions = ref<string[]>([]);
const permissionError = ref('');
const isAdmin = computed(() => Boolean(user.value?.roles.includes('ADMIN')));
const isCustomerService = computed(() => Boolean(user.value?.roles.includes('CUSTOMER_SERVICE')));
const allowed = computed(() => isAdmin.value || isCustomerService.value);
const canViewNurseRecommendations = computed(() => permissions.value.includes('NURSE_RECOMMEND_VIEW'));
const canReviewAttentionNotices = computed(() => permissions.value.includes('CARE_ATTENTION_REVIEW'));
const canManageTrainingArticles = computed(() => permissions.value.includes('TRAINING_ARTICLE_MANAGE'));
const canManageFollowUps = computed(() => permissions.value.includes('FOLLOW_UP_MANAGE'));
const canViewBasicDashboard = computed(() => permissions.value.includes('DASHBOARD_BASIC_VIEW'));
const canViewQualityDashboard = computed(() => permissions.value.includes('DASHBOARD_QUALITY_VIEW'));
const canManageDemoData = computed(() => permissions.value.includes('DEMO_DATA_MANAGE'));
const canHandleComplaints = computed(() => permissions.value.includes('COMPLAINT_HANDLE'));
const canReviewNurseAppeals = computed(() => permissions.value.includes('NURSE_APPEAL_REVIEW'));
const canManageCareMetrics = computed(() => permissions.value.includes('CARE_METRIC_CONFIG_MANAGE'));
const canReviewCareEvidence = computed(() => permissions.value.includes('CARE_EVIDENCE_REVIEW'));
const nav: Array<{ key: AdminView; label: string }> = [
  { key: 'overview', label: '运营概览' }, { key: 'services', label: '服务项目' },
  { key: 'orders', label: '订单调度' }, { key: 'reports', label: '服务报告' },
  { key: 'medical-files', label: '病历审核' }, { key: 'health-review', label: '档案建议' },
  { key: 'qualifications', label: '护理资质' }, { key: 'training', label: '培训资格' },
  { key: 'training-articles', label: '培训文章' }, { key: 'follow-ups', label: '随访管理' },
  { key: 'care-metrics', label: '护理质控' },
  { key: 'dashboard', label: '数据看板' }, { key: 'delivery', label: '交付检查' }
];
nav.push({ key: 'service-supervision', label: '服务监督' });
 const visibleNav = computed(() => {
  const permissionFilteredNav = nav.filter((item) =>
    (item.key !== 'training-articles' || canManageTrainingArticles.value)
    && (item.key !== 'follow-ups' || canManageFollowUps.value)
    && (item.key !== 'dashboard' || canViewBasicDashboard.value || canViewQualityDashboard.value)
    && (item.key !== 'delivery' || canManageDemoData.value)
    && (item.key !== 'service-supervision' || canHandleComplaints.value || canReviewNurseAppeals.value)
    && (item.key !== 'care-metrics' || canManageCareMetrics.value || canReviewCareEvidence.value)
  );
  if (!isCustomerService.value || isAdmin.value) return permissionFilteredNav;
  const customerServiceNav = permissionFilteredNav.filter((item) =>
    item.key === 'customer-service'
    || item.key === 'medical-files'
    || item.key === 'health-review'
    || item.key === 'qualifications'
    || item.key === 'training'
    || (item.key === 'training-articles' && canManageTrainingArticles.value)
    || (item.key === 'follow-ups' && canManageFollowUps.value)
    || (item.key === 'dashboard' && (canViewBasicDashboard.value || canViewQualityDashboard.value))
    || (item.key === 'delivery' && canManageDemoData.value)
    || (item.key === 'service-supervision' && (canHandleComplaints.value || canReviewNurseAppeals.value))
    || (item.key === 'care-metrics' && (canManageCareMetrics.value || canReviewCareEvidence.value))
  );
  return canReviewAttentionNotices.value
    ? [{ key: 'orders' as AdminView, label: '服务前审阅' }, ...customerServiceNav]
    : customerServiceNav;
});
 nav.push({ key: 'customer-service', label: '客服工单' });
async function loadUser() { const response = await getCurrentUser(); if (response.code === 0) user.value = response.data; }
async function loadPermissions() {
  permissionError.value = '';
  const response = await getPreferredNursePermissions();
  permissions.value = response.code === 0 ? response.data : [];
  if (response.code !== 0) permissionError.value = '账号权限暂时无法读取，护理推荐、派单、服务前注意事项审阅和护理质控入口已关闭。';
}
async function loadOverview() {
  if (!user.value) return;
  overviewError.value = '';
  const response = await getHomeSummary({ role: 'ADMIN', currentUserId: user.value.userId });
  if (response.code === 0) {
    overviewCards.value = response.data.cards;
    return;
  }
  overviewError.value = response.message || '运营数据加载失败';
}
async function initialize() {
  await loadUser();
  if (!allowed.value) return;
  await loadPermissions();
  const pages = getCurrentPages();
  const page = pages[pages.length - 1] as { options?: Record<string, string> } | undefined;
  const requestedView = page?.options?.view;
  if (requestedView === 'training') {
    view.value = 'training';
    return;
  }
  if (requestedView === 'care-metrics') {
    view.value = 'care-metrics';
    return;
  }
  if (requestedView === 'qualifications') {
    view.value = 'qualifications';
    return;
  }
  if (requestedView === 'health-review') {
    view.value = 'health-review';
    return;
  }
  if (requestedView === 'medical-files' || (isCustomerService.value && !isAdmin.value)) {
    view.value = 'medical-files';
    return;
  }
  await loadOverview();
}
async function signOut() { await logout(); uni.redirectTo({ url: '/pages/login/index' }); }
onMounted(initialize);
</script>

<template>
  <view class="admin-app">
    <aside class="admin-nav">
      <view class="brand"><text class="brand-mark">C</text><text>CareNest</text></view>
      <view class="nav-group"><button v-for="item in visibleNav" :key="item.key" :class="{ active: view === item.key }" type="button" @click="view=item.key">{{ item.label }}</button></view>
      <view class="nav-user"><text>{{ user?.displayName || '管理员' }}</text><button type="button" @click="signOut">退出登录</button></view>
    </aside>
    <main class="admin-main">
      <view class="admin-top"><view><text class="eyebrow">运营管理</text><text class="page-title">{{ visibleNav.find((item) => item.key === view)?.label }}</text></view><text class="today">今日运营中心</text></view>
      <view v-if="!allowed" class="admin-access">当前账号无权进入管理工作台。</view>
      <template v-else>
        <view v-if="view === 'overview' && isAdmin" class="overview">
          <view v-for="card in overviewCards" :key="card.key" class="metric"><text>{{ card.label }}</text><strong>{{ card.value }}</strong><small>{{ card.trend }}</small></view>
          <view v-if="overviewError" class="overview-error">{{ overviewError }}</view>
          <view class="overview-note"><text>工作提示</text><strong>优先处理待派订单与异常服务记录</strong><button type="button" @click="view='orders'">进入订单调度</button></view>
        </view>
        <StageEightServiceItemsPanel v-if="view === 'services' && isAdmin" role-code="ADMIN" :auth-user="user" />
        <template v-if="view === 'orders' && (isAdmin || canReviewAttentionNotices)">
          <view v-if="permissionError" class="admin-permission-note">{{ permissionError }}</view>
          <StageElevenAdminOrdersPanel
            :role-code="isCustomerService && !isAdmin ? 'CUSTOMER_SERVICE' : 'ADMIN'"
            :auth-user="user"
            :can-view-recommendations="canViewNurseRecommendations"
            :can-review-attention-notices="canReviewAttentionNotices"
          />
          <StageTwelveDispatchPanel v-if="isAdmin" role-code="ADMIN" :auth-user="user" :can-view-recommendations="canViewNurseRecommendations" />
          <StageSeventeenOrderChangePanel v-if="isAdmin" role-code="ADMIN" :auth-user="user" />
        </template>
        <StageFifteenServiceReportPanel v-if="view === 'reports' && isAdmin" role-code="ADMIN" :auth-user="user" />
        <StageTwentyOneMedicalReviewPanel v-if="view === 'medical-files'" :role-code="isCustomerService && !isAdmin ? 'CUSTOMER_SERVICE' : 'ADMIN'" :auth-user="user" />
        <StageFortyThreeCustomerServicePanel v-if="view === 'customer-service'" />
        <StageFortyFiveAdminReviewPanel v-if="view === 'service-supervision' && (canHandleComplaints || canReviewNurseAppeals)" />
        <StageFortyFiveToFortyEightAdminPanel v-if="view === 'service-supervision' && (canHandleComplaints || canReviewNurseAppeals)" />
        <StageThirtyFourToFortyAdminPanel v-if="view === 'care-metrics' && (canManageCareMetrics || canReviewCareEvidence)" :permissions="permissions" />
        <StageTwentyThreeReviewTaskList v-if="view === 'health-review'" :role-code="isCustomerService && !isAdmin ? 'CUSTOMER_SERVICE' : 'ADMIN'" :auth-user="user" />
        <StageTwentySevenQualificationReviewWorkbench v-if="view === 'qualifications'" />
        <StageTwentyEightTrainingReviewWorkbench v-if="view === 'training'" />
        <StageFortyNineTrainingArticleAdmin v-if="view === 'training-articles' && canManageTrainingArticles" />
        <StageFiftyOneFollowUpPanel v-if="view === 'follow-ups' && canManageFollowUps" mode="ADMIN" />
        <StageFiftyTwoFiftyThreeDashboard
          v-if="view === 'dashboard' && (canViewBasicDashboard || canViewQualityDashboard)"
          :can-view-basic="canViewBasicDashboard"
          :can-view-quality="canViewQualityDashboard"
        />
        <StageFiftyFourFiftyFiveDeliveryPanel v-if="view === 'delivery' && canManageDemoData" />
      </template>
    </main>
  </view>
</template>

<style scoped>
.admin-app { min-height:100vh; display:grid; grid-template-columns:224px minmax(0,1fr); background:#f4f7f6; color:#18312d; }.admin-nav { display:flex; flex-direction:column; padding:26px 16px; background:#123d39; color:#eaf5f1; }.brand { display:flex; align-items:center; gap:10px; padding:0 10px 34px; font-size:22px; font-weight:700; }.brand-mark { display:grid; place-items:center; width:30px; height:30px; border-radius:7px; background:#34b3a5; color:#103a36; }.nav-group { display:grid; gap:6px; }.nav-group button { justify-content:flex-start; margin:0; padding:12px 14px; border:0; border-radius:6px; background:transparent; color:#b9d3cf; text-align:left; font-size:14px; }.nav-group button.active { background:#245a55; color:#fff; font-weight:700; }.nav-user { margin-top:auto; display:grid; gap:9px; padding:14px 10px 0; border-top:1px solid rgba(236,255,250,.15); color:#c3dad5; font-size:13px; }.nav-user button { width:max-content; margin:0; padding:0; border:0; background:transparent; color:#79cfc5; font-size:13px; }.admin-main { min-width:0; padding:30px 38px 56px; }.admin-top { display:flex; align-items:flex-end; justify-content:space-between; margin-bottom:28px; }.eyebrow,.page-title { display:block; }.eyebrow { color:#23877d; font-size:11px; font-weight:700; letter-spacing:1.6px; }.page-title { margin-top:8px; font-size:30px; font-weight:700; }.today { color:#748681; font-size:14px; }.overview { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; }.metric,.overview-note,.admin-access { display:grid; gap:8px; padding:22px; border:1px solid #dce7e4; border-radius:8px; background:#fff; }.metric text,.metric small { color:#6e817d; font-size:13px; }.metric strong { color:#173c37; font-size:34px; }.overview-error { grid-column:1 / -1; padding:14px 16px; border:1px solid #f0b8b3; border-radius:6px; background:#fff2f0; color:#a53630; }.overview-note { grid-column:1 / -1; grid-template-columns:1fr auto; align-items:center; }.overview-note text { color:#23877d; font-size:13px; font-weight:700; }.overview-note strong { grid-column:1; font-size:18px; }.overview-note button { grid-column:2; grid-row:1 / 3; margin:0; border:0; border-radius:6px; background:#147d72; color:#fff; padding:11px 16px; }.admin-main :deep(.glass-panel) { border-radius:8px; box-shadow:none; }.admin-main :deep(.stage-eight-panel),.admin-main :deep(.stage-eleven-panel),.admin-main :deep(.stage-twelve-panel),.admin-main :deep(.stage-fifteen-panel),.admin-main :deep(.stage-seventeen-panel),.admin-main :deep(.stage-eighteen-panel),.admin-main :deep(.medical-review-panel),.admin-main :deep(.health-review-panel) { margin-bottom:18px; }
.admin-main :deep(.qualification-review-workbench) { margin-bottom:18px; }
.admin-main :deep(.training-review-workbench) { margin-bottom:18px; }
.admin-main :deep(.care-metric-admin-panel) { margin-bottom:18px; }
.admin-permission-note { margin-bottom:18px; padding:14px 16px; border-left:4px solid #c98e34; background:#fff8e8; color:#755417; font-size:13px; line-height:1.6; }
@media (max-width:800px) { .admin-app { grid-template-columns:1fr; }.admin-nav { padding:16px; }.brand { padding-bottom:16px; }.nav-group { grid-template-columns:repeat(3,minmax(0,1fr)); overflow:auto; }.nav-group button { white-space:nowrap; }.nav-user { display:none; }.admin-main { padding:22px 16px 40px; }.overview { grid-template-columns:1fr; }.overview-note { grid-column:auto; }.admin-top { align-items:center; }.today { display:none; } }
</style>
