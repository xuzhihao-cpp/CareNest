<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import StageSixBindingPanel from '@/components/StageSixBindingPanel.vue';
import StageSevenProfilePanel from '@/components/StageSevenProfilePanel.vue';
import StageNineServiceAddressPanel from '@/components/StageNineServiceAddressPanel.vue';
import StageTenOrderPanel from '@/components/StageTenOrderPanel.vue';
import StageFifteenServiceReportPanel from '@/components/StageFifteenServiceReportPanel.vue';
import StageNineteenHealthArchivePanel from '@/components/StageNineteenHealthArchivePanel.vue';
import StageTwentyMedicalFilesPanel from '@/components/StageTwentyMedicalFilesPanel.vue';
import StageTwentyTwoHealthFeedbackPanel from '@/components/StageTwentyTwoHealthFeedbackPanel.vue';
import StageSixteenReportAckPanel from '@/components/StageSixteenReportAckPanel.vue';
import StageSeventeenOrderChangePanel from '@/components/StageSeventeenOrderChangePanel.vue';
import StageFiftyOneFollowUpPanel from '@/components/StageFiftyOneFollowUpPanel.vue';
import StageFortyOneAiAssistantPanel from '@/components/StageFortyOneAiAssistantPanel.vue';
import type { AuthUser } from '@/types/stageTwo';

type FamilyTab = 'profile' | 'health' | 'feedback' | 'medical' | 'binding' | 'address' | 'orders' | 'reports' | 'follow-ups' | 'ai';
const activeTab = ref<FamilyTab>('profile');
const user = ref<AuthUser | null>(null);
const tabs: Array<{ key: FamilyTab; label: string }> = [
  { key: 'profile', label: '长辈档案' },
  { key: 'health', label: '健康档案' },
  { key: 'feedback', label: '健康反馈' },
  { key: 'medical', label: '病历资料' },
  { key: 'binding', label: '绑定授权' },
  { key: 'address', label: '服务地址' },
  { key: 'orders', label: '服务订单' },
  { key: 'reports', label: '服务报告' },
  { key: 'follow-ups', label: '随访记录' }
];
tabs.push({ key: 'ai', label: 'AI 照护助手' });
const allowed = computed(() => user.value?.roles.includes('FAMILY'));

async function loadUser() {
  const response = await getCurrentUser();
  if (response.code === 0) {
    user.value = response.data;
    const pages = getCurrentPages();
    const page = pages[pages.length - 1] as { options?: Record<string, string> } | undefined;
    if (page?.options?.view === 'health-archive') activeTab.value = 'health';
    if (page?.options?.view === 'health-feedback') activeTab.value = 'feedback';
    if (page?.options?.view === 'medical-files') activeTab.value = 'medical';
  }
}

async function signOut() {
  await logout();
  uni.redirectTo({ url: '/pages/login/index' });
}

onMounted(loadUser);
</script>

<template>
  <view class="user-app">
    <view class="user-header">
      <view><text class="kicker">家属服务</text><text class="title">{{ user?.displayName || '家属服务' }}</text></view>
      <button type="button" class="logout" @click="signOut">退出登录</button>
    </view>
    <view v-if="!allowed" class="access-card"><text>请使用家属账号登录后访问。</text></view>
    <template v-else>
      <scroll-view class="tabs" scroll-x="true" :show-scrollbar="false"><view class="tab-row"><button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button></view></scroll-view>
      <view class="tab-content">
        <StageSevenProfilePanel v-if="activeTab === 'profile'" role-code="FAMILY" :auth-user="user" />
        <StageNineteenHealthArchivePanel v-if="activeTab === 'health'" role-code="FAMILY" :auth-user="user" />
        <StageTwentyTwoHealthFeedbackPanel v-if="activeTab === 'feedback'" role-code="FAMILY" :auth-user="user" />
        <StageTwentyMedicalFilesPanel v-if="activeTab === 'medical'" role-code="FAMILY" :auth-user="user" />
        <StageSixBindingPanel v-if="activeTab === 'binding'" role-code="FAMILY" :auth-user="user" />
        <StageNineServiceAddressPanel v-if="activeTab === 'address'" role-code="FAMILY" :auth-user="user" />
        <template v-if="activeTab === 'orders'"><StageTenOrderPanel role-code="FAMILY" :auth-user="user" /><StageSeventeenOrderChangePanel role-code="FAMILY" :auth-user="user" /></template>
        <template v-if="activeTab === 'reports'"><StageFifteenServiceReportPanel role-code="FAMILY" :auth-user="user" /><StageSixteenReportAckPanel role-code="FAMILY" :auth-user="user" /></template>
        <StageFiftyOneFollowUpPanel v-if="activeTab === 'follow-ups'" mode="FAMILY" />
         <StageFortyOneAiAssistantPanel v-if="activeTab === 'ai'" role-code="FAMILY" />
      </view>
    </template>
  </view>
</template>

<style scoped>
.user-app { min-height: 100vh; padding: 24rpx; box-sizing: border-box; background: #f5f8f7; color: #17312e; overflow-x: hidden; }.user-header { display:flex; align-items:center; justify-content:space-between; gap:24rpx; padding: 12rpx 4rpx 28rpx; }.kicker,.title { display:block; }.kicker { color:#0f766e; font-size:20rpx; font-weight:700; letter-spacing:2rpx; }.title { margin-top:8rpx; font-size:42rpx; font-weight:700; }.logout { flex:none; min-width:150rpx; min-height:88rpx; padding:0 22rpx; border:1rpx solid #bdcec7; border-radius:6rpx !important; background:#fff; color:#315c51; box-shadow:0 4rpx 12rpx rgba(45,91,77,.08); font-size:24rpx; font-weight:700; line-height:1.2; }.logout:active { border-color:#78a99b; background:#edf7f3; color:#1f6a58; box-shadow:none; }.tabs { margin:0 -24rpx 18rpx; white-space:nowrap; background:#fff; border-top:1rpx solid #e3ece9; border-bottom:1rpx solid #e3ece9; }.tab-row { display:flex; padding:0 16rpx; }.tab-row button { display:inline-flex; align-items:center; justify-content:center; flex:none; min-height:88rpx; margin:0; padding:0 20rpx; border:0; border-radius:0; background:transparent; color:#70817e; font-size:27rpx; line-height:1.2; }.tab-row button.active { color:#0f766e; border-bottom:4rpx solid #0f766e; background:#edf7f4; font-weight:700; }.access-card { padding:30rpx; background:#fff1ef; color:#9f382e; border-radius:10rpx; }.tab-content { min-width:0; }.tab-content :deep(.glass-panel) { min-width:0; border-radius:10rpx; box-shadow:none; }.tab-content :deep(.stage-six-panel),.tab-content :deep(.stage-seven-panel),.tab-content :deep(.stage-nine-panel),.tab-content :deep(.stage-ten-panel),.tab-content :deep(.stage-fifteen-panel),.tab-content :deep(.stage-sixteen-panel),.tab-content :deep(.stage-seventeen-panel) { margin-top:0; padding:24rpx; }.tab-content :deep(.stage-six-summary),.tab-content :deep(.stage-seven-summary),.tab-content :deep(.stage-nine-summary),.tab-content :deep(.stage-ten-summary),.tab-content :deep(.stage-fifteen-summary),.tab-content :deep(.stage-sixteen-summary),.tab-content :deep(.stage-seventeen-summary),.tab-content :deep(.profile-workbench),.tab-content :deep(.address-workbench),.tab-content :deep(.order-workbench),.tab-content :deep(.contact-grid),.tab-content :deep(.service-report-workbench),.tab-content :deep(.report-toolbar) { grid-template-columns:minmax(0,1fr) !important; }.tab-content :deep(.permission-main),.tab-content :deep(.flow-label),.tab-content :deep(.flow-time),.tab-content :deep(.auth-meta),.tab-content :deep(.contract-response text),.tab-content :deep(.tag) { max-width:100%; overflow-wrap:anywhere; word-break:break-word; white-space:normal; }.tab-content :deep(.stage-six-endpoints),.tab-content :deep(.stage-seven-endpoints),.tab-content :deep(.stage-nine-endpoints),.tab-content :deep(.stage-ten-endpoints),.tab-content :deep(.stage-fifteen-endpoints),.tab-content :deep(.stage-sixteen-endpoints),.tab-content :deep(.stage-seventeen-endpoints) { align-items:flex-start; }.tab-content :deep(.binding-actions) { align-items:stretch; }.tab-content :deep(.binding-actions button) { min-width:0; flex:1 1 180rpx; }.tab-content :deep(.input) { min-width:0; box-sizing:border-box; }.tab-content :deep(.address-row),.tab-content :deep(.binding-row),.tab-content :deep(.order-row) { min-width:0; }.tab-content :deep(.health-archive-panel),.tab-content :deep(.medical-files-panel) { margin-top:0; }
.tab-content :deep(.health-feedback-panel) { margin-top:0; }
@media (min-width:768px) { .user-app { width:440px; margin:0 auto; box-shadow:0 0 0 1px #dde8e5,0 16px 48px rgba(20,55,49,.1); } }
</style>
