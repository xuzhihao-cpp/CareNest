<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  ClipboardList,
  FileHeart,
  HeartPulse,
  Home,
  Link2,
  LogOut,
  MapPin,
  MessageCircleHeart,
  ShoppingBag,
  Sparkles,
  UserRound
} from '@lucide/vue';
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
import StageFortyFiveFamilyFeedbackPanel from '@/components/StageFortyFiveFamilyFeedbackPanel.vue';
import type { AuthUser } from '@/types/stageTwo';

type FamilyPrimaryView = 'home' | 'services' | 'ai' | 'reports' | 'account';
type FamilyTab = 'profile' | 'health' | 'feedback' | 'medical' | 'binding' | 'address' | 'orders' | 'reports' | 'service-feedback' | 'follow-ups' | 'ai';

const primaryView = ref<FamilyPrimaryView>('home');
const activeTab = ref<FamilyTab>('profile');
const user = ref<AuthUser | null>(null);
const allowed = computed(() => Boolean(user.value?.roles.includes('FAMILY')));
const pageTitle = computed(() => ({
  home: '家庭照护',
  services: '服务预约',
  ai: 'AI 照护助手',
  reports: '服务跟进',
  account: '我的'
}[primaryView.value]));

function scrollTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 160 });
}

function selectPrimary(next: FamilyPrimaryView) {
  primaryView.value = next;
  if (next === 'home' && !['profile', 'health', 'feedback'].includes(activeTab.value)) activeTab.value = 'profile';
  if (next === 'services' && !['address', 'orders'].includes(activeTab.value)) activeTab.value = 'orders';
  if (next === 'ai') activeTab.value = 'ai';
  if (next === 'reports' && !['reports', 'service-feedback', 'follow-ups'].includes(activeTab.value)) activeTab.value = 'reports';
  if (next === 'account' && !['binding', 'medical'].includes(activeTab.value)) activeTab.value = 'binding';
  scrollTop();
}

function selectTab(tab: FamilyTab) {
  activeTab.value = tab;
  scrollTop();
}

async function loadUser() {
  const response = await getCurrentUser();
  if (response.code !== 0) return;
  user.value = response.data;
  const pages = getCurrentPages();
  const page = pages[pages.length - 1] as { options?: Record<string, string> } | undefined;
  const requested = page?.options?.view;
  if (requested === 'health-archive') { primaryView.value = 'home'; activeTab.value = 'health'; }
  if (requested === 'health-feedback') { primaryView.value = 'home'; activeTab.value = 'feedback'; }
  if (requested === 'medical-files') { primaryView.value = 'account'; activeTab.value = 'medical'; }
}

async function signOut() {
  await logout();
  uni.redirectTo({ url: '/pages/login/index' });
}

onMounted(loadUser);
</script>

<template>
  <view class="family-shell">
    <header class="shell-header">
      <view>
        <text class="brand">CareNest</text>
        <text class="page-title">{{ pageTitle }}</text>
      </view>
      <view class="identity" aria-label="当前用户">
        <UserRound :size="18" :stroke-width="2" aria-hidden="true" />
        <text>{{ user?.displayName || '家属用户' }}</text>
      </view>
    </header>

    <main class="shell-main">
      <view v-if="!allowed" class="access-state">请使用家属账号登录后访问。</view>
      <transition v-else name="view-shift" mode="out-in">
        <view :key="primaryView" class="primary-view">
          <template v-if="primaryView === 'home'">
            <section class="welcome-band">
              <text class="welcome-kicker">陪伴长辈，照护更安心</text>
              <text class="welcome-title">今天想先了解什么？</text>
              <view class="quick-actions">
                <button type="button" :class="{ active: activeTab === 'profile' }" @click="selectTab('profile')">
                  <UserRound :size="23" aria-hidden="true" /><text>长辈档案</text>
                </button>
                <button type="button" :class="{ active: activeTab === 'health' }" @click="selectTab('health')">
                  <HeartPulse :size="23" aria-hidden="true" /><text>健康档案</text>
                </button>
                <button type="button" :class="{ active: activeTab === 'feedback' }" @click="selectTab('feedback')">
                  <MessageCircleHeart :size="23" aria-hidden="true" /><text>健康反馈</text>
                </button>
              </view>
            </section>
            <StageSevenProfilePanel v-if="activeTab === 'profile'" role-code="FAMILY" :auth-user="user" />
            <StageNineteenHealthArchivePanel v-if="activeTab === 'health'" role-code="FAMILY" :auth-user="user" />
            <StageTwentyTwoHealthFeedbackPanel v-if="activeTab === 'feedback'" role-code="FAMILY" :auth-user="user" />
          </template>

          <template v-else-if="primaryView === 'services'">
            <nav class="section-nav two-columns" aria-label="服务功能">
              <button type="button" :class="{ active: activeTab === 'orders' }" @click="selectTab('orders')"><ShoppingBag :size="19" aria-hidden="true" />服务订单</button>
              <button type="button" :class="{ active: activeTab === 'address' }" @click="selectTab('address')"><MapPin :size="19" aria-hidden="true" />服务地址</button>
            </nav>
            <StageNineServiceAddressPanel v-if="activeTab === 'address'" role-code="FAMILY" :auth-user="user" />
            <template v-if="activeTab === 'orders'"><StageTenOrderPanel role-code="FAMILY" :auth-user="user" /><StageSeventeenOrderChangePanel role-code="FAMILY" :auth-user="user" /></template>
          </template>

          <StageFortyOneAiAssistantPanel v-else-if="primaryView === 'ai'" role-code="FAMILY" />

          <template v-else-if="primaryView === 'reports'">
            <nav class="section-nav three-columns" aria-label="服务跟进功能">
              <button type="button" :class="{ active: activeTab === 'reports' }" @click="selectTab('reports')"><ClipboardList :size="19" aria-hidden="true" />服务报告</button>
              <button type="button" :class="{ active: activeTab === 'service-feedback' }" @click="selectTab('service-feedback')"><MessageCircleHeart :size="19" aria-hidden="true" />评价投诉</button>
              <button type="button" :class="{ active: activeTab === 'follow-ups' }" @click="selectTab('follow-ups')"><HeartPulse :size="19" aria-hidden="true" />随访记录</button>
            </nav>
            <template v-if="activeTab === 'reports'"><StageFifteenServiceReportPanel role-code="FAMILY" :auth-user="user" /><StageSixteenReportAckPanel role-code="FAMILY" :auth-user="user" /></template>
            <StageFortyFiveFamilyFeedbackPanel v-if="activeTab === 'service-feedback'" />
            <StageFiftyOneFollowUpPanel v-if="activeTab === 'follow-ups'" mode="FAMILY" />
          </template>

          <template v-else>
            <nav class="section-nav two-columns" aria-label="我的功能">
              <button type="button" :class="{ active: activeTab === 'binding' }" @click="selectTab('binding')"><Link2 :size="19" aria-hidden="true" />绑定授权</button>
              <button type="button" :class="{ active: activeTab === 'medical' }" @click="selectTab('medical')"><FileHeart :size="19" aria-hidden="true" />病历资料</button>
            </nav>
            <StageSixBindingPanel v-if="activeTab === 'binding'" role-code="FAMILY" :auth-user="user" />
            <StageTwentyMedicalFilesPanel v-if="activeTab === 'medical'" role-code="FAMILY" :auth-user="user" />
            <button class="sign-out" type="button" @click="signOut"><LogOut :size="20" aria-hidden="true" />退出登录</button>
          </template>
        </view>
      </transition>
    </main>

    <nav v-if="allowed" class="bottom-nav" aria-label="主要导航">
      <button type="button" :class="{ active: primaryView === 'home' }" @click="selectPrimary('home')"><Home :size="23" aria-hidden="true" /><text>照护</text></button>
      <button type="button" :class="{ active: primaryView === 'services' }" @click="selectPrimary('services')"><ShoppingBag :size="23" aria-hidden="true" /><text>服务</text></button>
      <button type="button" :class="{ active: primaryView === 'ai' }" @click="selectPrimary('ai')"><Sparkles :size="23" aria-hidden="true" /><text>AI</text></button>
      <button type="button" :class="{ active: primaryView === 'reports' }" @click="selectPrimary('reports')"><ClipboardList :size="23" aria-hidden="true" /><text>跟进</text></button>
      <button type="button" :class="{ active: primaryView === 'account' }" @click="selectPrimary('account')"><UserRound :size="23" aria-hidden="true" /><text>我的</text></button>
    </nav>
  </view>
</template>

<style scoped>
.family-shell{min-height:100vh;box-sizing:border-box;background:#f4f6f3;color:#1f3029;padding-bottom:calc(78px + env(safe-area-inset-bottom));overflow-x:hidden}.shell-header{position:sticky;top:0;z-index:20;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:17px 20px 14px;background:rgba(255,255,255,.96);border-bottom:1px solid #e4e9e5;backdrop-filter:blur(14px)}.brand,.page-title{display:block;letter-spacing:0}.brand{color:#2c7461;font-size:12px;font-weight:800}.page-title{margin-top:3px;font-size:24px;font-weight:750}.identity{display:flex;align-items:center;gap:7px;max-width:46%;color:#506159;font-size:13px}.identity text{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.shell-main{min-height:calc(100vh - 150px);padding:18px 16px 28px}.primary-view{min-width:0}.welcome-band{padding:8px 2px 22px}.welcome-kicker,.welcome-title{display:block}.welcome-kicker{color:#5e7168;font-size:14px}.welcome-title{margin-top:5px;font-size:22px;font-weight:750}.quick-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:18px}.quick-actions button{display:flex;min-width:0;min-height:76px;flex-direction:column;align-items:center;justify-content:center;gap:8px;margin:0;padding:9px 6px;border:1px solid #dfe6e1;border-radius:8px;background:#fff;color:#53645c;font-size:13px;line-height:1.2}.quick-actions button.active{border-color:#78a999;background:#edf5f1;color:#245e4e;font-weight:700}.section-nav{display:grid;margin:0 0 18px;border-bottom:1px solid #dde5df}.section-nav.two-columns{grid-template-columns:repeat(2,minmax(0,1fr))}.section-nav.three-columns{grid-template-columns:repeat(3,minmax(0,1fr))}.section-nav button{display:flex;min-width:0;min-height:58px;align-items:center;justify-content:center;gap:5px;margin:0;padding:0 5px;border:0;border-bottom:3px solid transparent;border-radius:0;background:transparent;color:#697970;font-size:13px;line-height:1.25}.section-nav button.active{border-bottom-color:#2d7c68;color:#245f50;font-weight:700}.sign-out{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;min-height:48px;margin-top:20px;border:1px solid #e3c7c2;border-radius:7px;background:#fff8f6;color:#93463d;font-size:15px}.bottom-nav{position:fixed;right:0;bottom:0;left:0;z-index:30;display:grid;grid-template-columns:repeat(5,minmax(0,1fr));height:calc(70px + env(safe-area-inset-bottom));padding:4px max(6px,calc((100vw - 440px)/2)) env(safe-area-inset-bottom);border-top:1px solid #dfe6e1;background:rgba(255,255,255,.98);box-shadow:0 -8px 24px rgba(33,65,53,.06);box-sizing:border-box}.bottom-nav button{display:flex;min-width:0;min-height:60px;flex-direction:column;align-items:center;justify-content:center;gap:3px;margin:0;padding:0;border:0;border-radius:7px;background:transparent;color:#78847e;font-size:11px;line-height:1}.bottom-nav button.active{color:#236651;font-weight:750}.access-state{padding:28px 18px;border-left:4px solid #d15a4d;background:#fff;color:#7e433d}.view-shift-enter-active,.view-shift-leave-active{transition:opacity .16s ease,transform .16s ease}.view-shift-enter-from{opacity:0;transform:translateY(7px)}.view-shift-leave-to{opacity:0;transform:translateY(-4px)}.shell-main :deep(.glass-panel),.shell-main :deep(.health-archive-panel),.shell-main :deep(.medical-files-panel),.shell-main :deep(.health-feedback-panel){min-width:0;margin-top:0;border-radius:8px;box-shadow:none}.shell-main :deep(.stage-six-panel),.shell-main :deep(.stage-seven-panel),.shell-main :deep(.stage-nine-panel),.shell-main :deep(.stage-ten-panel),.shell-main :deep(.stage-fifteen-panel),.shell-main :deep(.stage-sixteen-panel),.shell-main :deep(.stage-seventeen-panel){min-width:0;margin-top:0;padding:16px}.shell-main :deep(.stage-six-summary),.shell-main :deep(.stage-seven-summary),.shell-main :deep(.stage-nine-summary),.shell-main :deep(.stage-ten-summary),.shell-main :deep(.stage-fifteen-summary),.shell-main :deep(.stage-sixteen-summary),.shell-main :deep(.stage-seventeen-summary),.shell-main :deep(.profile-workbench),.shell-main :deep(.address-workbench),.shell-main :deep(.order-workbench),.shell-main :deep(.contact-grid),.shell-main :deep(.service-report-workbench),.shell-main :deep(.report-toolbar){grid-template-columns:minmax(0,1fr)!important}.shell-main :deep(.permission-main),.shell-main :deep(.flow-label),.shell-main :deep(.flow-time),.shell-main :deep(.auth-meta),.shell-main :deep(.tag),.shell-main :deep(text){max-width:100%;overflow-wrap:anywhere}.shell-main :deep(input),.shell-main :deep(textarea),.shell-main :deep(picker),.shell-main :deep(.input){min-width:0;max-width:100%;box-sizing:border-box}.shell-main :deep(.binding-actions){align-items:stretch}.shell-main :deep(.binding-actions button){min-width:0;flex:1 1 130px}.shell-main :deep(.address-row),.shell-main :deep(.binding-row),.shell-main :deep(.order-row){min-width:0}@media(min-width:768px){.family-shell{width:440px;margin:0 auto;box-shadow:0 0 0 1px #e0e5e1,0 18px 48px rgba(31,52,41,.1)}}@media(max-width:360px){.shell-main{padding-right:12px;padding-left:12px}.section-nav button{font-size:12px}.quick-actions button{font-size:12px}}@media(prefers-reduced-motion:reduce){.view-shift-enter-active,.view-shift-leave-active{transition:none}}
</style>
