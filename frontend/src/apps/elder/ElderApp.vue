<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import StageSixBindingPanel from '@/components/StageSixBindingPanel.vue';
import StageSevenProfilePanel from '@/components/StageSevenProfilePanel.vue';
import StageFifteenServiceReportPanel from '@/components/StageFifteenServiceReportPanel.vue';
import StageSixteenReportAckPanel from '@/components/StageSixteenReportAckPanel.vue';
import type { AuthUser } from '@/types/stageTwo';

type ElderTab = 'profile' | 'binding' | 'reports';
const activeTab = ref<ElderTab>('profile');
const user = ref<AuthUser | null>(null);
const allowed = computed(() => user.value?.roles.includes('ELDER'));
async function loadUser() { const response = await getCurrentUser(); if (response.code === 0) user.value = response.data; }
async function signOut() { await logout(); uni.redirectTo({ url: '/pages/login/index' }); }
onMounted(loadUser);
</script>

<template>
  <view class="elder-app">
    <view class="elder-header"><view><text class="kicker">ELDER CARE</text><text class="title">{{ user?.displayName || '我的照护' }}</text></view><button type="button" class="logout" @click="signOut">退出</button></view>
    <view v-if="!allowed" class="access-card"><text>请使用长辈账号登录后访问。</text></view>
    <template v-else>
      <view class="tabs"><button :class="{ active: activeTab === 'profile' }" type="button" @click="activeTab='profile'">我的档案</button><button :class="{ active: activeTab === 'binding' }" type="button" @click="activeTab='binding'">绑定确认</button><button :class="{ active: activeTab === 'reports' }" type="button" @click="activeTab='reports'">服务报告</button></view>
      <view class="tab-content"><StageSevenProfilePanel v-if="activeTab==='profile'" role-code="ELDER" :auth-user="user" /><StageSixBindingPanel v-if="activeTab==='binding'" role-code="ELDER" :auth-user="user" /><template v-if="activeTab==='reports'"><StageFifteenServiceReportPanel role-code="ELDER" :auth-user="user" /><StageSixteenReportAckPanel role-code="ELDER" :auth-user="user" /></template></view>
    </template>
  </view>
</template>

<style scoped>
.elder-app { min-height:100vh; padding:24rpx; box-sizing:border-box; background:#f7f7f4; color:#27352f; overflow-x:hidden; }.elder-header { display:flex; align-items:center; justify-content:space-between; padding:12rpx 4rpx 28rpx; }.kicker,.title { display:block; }.kicker { color:#2d7c68; font-size:20rpx; font-weight:700; letter-spacing:2rpx; }.title { margin-top:8rpx; font-size:42rpx; font-weight:700; }.logout { margin:0; border:0; background:transparent; color:#61756d; font-size:26rpx; }.tabs { display:flex; margin:0 -24rpx 18rpx; padding:0 16rpx; background:#fff; border-top:1rpx solid #e7ebe5; border-bottom:1rpx solid #e7ebe5; }.tabs button { flex:1; margin:0; padding:20rpx 8rpx 16rpx; border:0; border-radius:0; background:transparent; color:#74807a; font-size:27rpx; }.tabs button.active { color:#2d7c68; border-bottom:4rpx solid #2d7c68; font-weight:700; }.access-card { padding:30rpx; background:#fff1ef; color:#9f382e; border-radius:10rpx; }.tab-content { min-width:0; }.tab-content :deep(.glass-panel) { min-width:0; border-radius:10rpx; box-shadow:none; }.tab-content :deep(.stage-six-summary),.tab-content :deep(.stage-seven-summary),.tab-content :deep(.stage-fifteen-summary),.tab-content :deep(.stage-sixteen-summary),.tab-content :deep(.profile-workbench),.tab-content :deep(.service-report-workbench),.tab-content :deep(.report-toolbar),.tab-content :deep(.contact-grid) { grid-template-columns:minmax(0,1fr) !important; }.tab-content :deep(.permission-main),.tab-content :deep(.flow-label),.tab-content :deep(.flow-time),.tab-content :deep(.auth-meta),.tab-content :deep(.tag) { max-width:100%; overflow-wrap:anywhere; word-break:break-word; white-space:normal; }
@media (min-width:768px) { .elder-app { width:440px; margin:0 auto; box-shadow:0 0 0 1px #e0e5dd,0 16px 48px rgba(31,52,41,.1); } }
</style>
