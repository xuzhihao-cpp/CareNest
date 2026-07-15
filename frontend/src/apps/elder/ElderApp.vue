<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import StageSixBindingPanel from '@/components/StageSixBindingPanel.vue';
import StageSevenProfilePanel from '@/components/StageSevenProfilePanel.vue';
import StageFifteenServiceReportPanel from '@/components/StageFifteenServiceReportPanel.vue';
import StageNineteenHealthArchivePanel from '@/components/StageNineteenHealthArchivePanel.vue';
import StageTwentyMedicalFilesPanel from '@/components/StageTwentyMedicalFilesPanel.vue';
import StageTwentyTwoHealthFeedbackPanel from '@/components/StageTwentyTwoHealthFeedbackPanel.vue';
import EmergencyAssistancePanel from '@/components/EmergencyAssistancePanel.vue';
import StageSixteenReportAckPanel from '@/components/StageSixteenReportAckPanel.vue';
import StageThirtyTwoReminderCenter from '@/components/StageThirtyTwoReminderCenter.vue';
import type { AuthUser } from '@/types/stageTwo';

type ElderTab = 'profile' | 'health' | 'feedback' | 'assistance' | 'medical' | 'binding' | 'reports' | 'reminders';
const activeTab = ref<ElderTab>('profile');
const user = ref<AuthUser | null>(null);
const allowed = computed(() => user.value?.roles.includes('ELDER'));
async function loadUser() { const response = await getCurrentUser(); if (response.code === 0) { user.value = response.data; const pages = getCurrentPages(); const page = pages[pages.length - 1] as { options?: Record<string, string> } | undefined; if (page?.options?.view === 'health-archive') activeTab.value = 'health'; if (page?.options?.view === 'health-feedback') activeTab.value = 'feedback'; if (page?.options?.view === 'emergency') activeTab.value = 'assistance'; if (page?.options?.view === 'medical-files') activeTab.value = 'medical'; } }
async function signOut() { await logout(); uni.redirectTo({ url: '/pages/login/index' }); }
onMounted(loadUser);
</script>

<template>
  <button v-if="allowed && activeTab !== 'reminders'" class="reminder-entry" type="button" @click="activeTab='reminders'">提醒中心</button>
  <StageThirtyTwoReminderCenter v-if="activeTab==='reminders' && allowed" @close="activeTab='profile'" />
  <view class="elder-app">
    <view class="elder-header"><view><text class="kicker">长辈服务</text><text class="title">{{ user?.displayName || '我的照护' }}</text></view><button type="button" class="logout" @click="signOut">退出登录</button></view>
    <view v-if="!allowed" class="access-card"><text>请使用长辈账号登录后访问。</text></view>
    <template v-else>
      <scroll-view class="tabs" scroll-x="true" :show-scrollbar="false"><view class="tab-row"><button :class="{ active: activeTab === 'profile' }" type="button" @click="activeTab='profile'">我的档案</button><button :class="{ active: activeTab === 'health' }" type="button" @click="activeTab='health'">健康摘要</button><button :class="{ active: activeTab === 'feedback' }" type="button" @click="activeTab='feedback'">健康反馈</button><button :class="{ active: activeTab === 'assistance' }" type="button" @click="activeTab='assistance'">协助</button><button :class="{ active: activeTab === 'medical' }" type="button" @click="activeTab='medical'">病历资料</button><button :class="{ active: activeTab === 'binding' }" type="button" @click="activeTab='binding'">绑定确认</button><button :class="{ active: activeTab === 'reports' }" type="button" @click="activeTab='reports'">服务报告</button></view></scroll-view>
      <view class="tab-content"><StageSevenProfilePanel v-if="activeTab==='profile'" role-code="ELDER" :auth-user="user" /><StageNineteenHealthArchivePanel v-if="activeTab==='health'" role-code="ELDER" :auth-user="user" /><StageTwentyTwoHealthFeedbackPanel v-if="activeTab==='feedback'" role-code="ELDER" :auth-user="user" @open-profile="activeTab='profile'" @open-assistance="activeTab='assistance'" /><EmergencyAssistancePanel v-if="activeTab==='assistance'" @open-profile="activeTab='profile'" @back-feedback="activeTab='feedback'" /><StageTwentyMedicalFilesPanel v-if="activeTab==='medical'" role-code="ELDER" :auth-user="user" /><StageSixBindingPanel v-if="activeTab==='binding'" role-code="ELDER" :auth-user="user" /><template v-if="activeTab==='reports'"><StageFifteenServiceReportPanel role-code="ELDER" :auth-user="user" /><StageSixteenReportAckPanel role-code="ELDER" :auth-user="user" /></template></view>
    </template>
  </view>
</template>

<style scoped>
.elder-app { min-height:100vh; padding:24rpx; box-sizing:border-box; background:#f7f7f4; color:#27352f; overflow-x:hidden; }.elder-header { display:flex; align-items:center; justify-content:space-between; gap:24rpx; padding:12rpx 4rpx 28rpx; }.kicker,.title { display:block; }.kicker { color:#2d7c68; font-size:20rpx; font-weight:700; letter-spacing:2rpx; }.title { margin-top:8rpx; font-size:42rpx; font-weight:700; }.logout { flex:none; min-width:150rpx; min-height:88rpx; padding:0 22rpx; border:1rpx solid #bdcec7; border-radius:6rpx !important; background:#fff; color:#315c51; box-shadow:0 4rpx 12rpx rgba(45,91,77,.08); font-size:24rpx; font-weight:700; line-height:1.2; }.logout:active { border-color:#78a99b; background:#edf7f3; color:#1f6a58; box-shadow:none; }.tabs { margin:0 -24rpx 18rpx; white-space:nowrap; background:#fff; border-top:1rpx solid #e7ebe5; border-bottom:1rpx solid #e7ebe5; }.tab-row { display:flex; padding:0 16rpx; }.tabs button { display:inline-flex; align-items:center; justify-content:center; flex:none; min-height:88rpx; margin:0; padding:0 20rpx; border:0; border-radius:0; background:transparent; color:#74807a; font-size:27rpx; line-height:1.2; }.tabs button.active { color:#2d7c68; border-bottom:4rpx solid #2d7c68; background:#f1f7f4; font-weight:700; }.access-card { padding:30rpx; background:#fff1ef; color:#9f382e; border-radius:10rpx; }.tab-content { min-width:0; }.tab-content :deep(.glass-panel) { min-width:0; border-radius:10rpx; box-shadow:none; }.tab-content :deep(.stage-six-summary),.tab-content :deep(.stage-seven-summary),.tab-content :deep(.stage-fifteen-summary),.tab-content :deep(.stage-sixteen-summary),.tab-content :deep(.profile-workbench),.tab-content :deep(.service-report-workbench),.tab-content :deep(.report-toolbar),.tab-content :deep(.contact-grid) { grid-template-columns:minmax(0,1fr) !important; }.tab-content :deep(.permission-main),.tab-content :deep(.flow-label),.tab-content :deep(.flow-time),.tab-content :deep(.auth-meta),.tab-content :deep(.tag) { max-width:100%; overflow-wrap:anywhere; word-break:break-word; white-space:normal; }.tab-content :deep(.health-archive-panel),.tab-content :deep(.medical-files-panel) { margin-top:0; }
.tab-content :deep(.health-feedback-panel) { margin-top:0; }
@media (min-width:768px) { .elder-app { width:440px; margin:0 auto; box-shadow:0 0 0 1px #e0e5dd,0 16px 48px rgba(31,52,41,.1); } }
</style>
