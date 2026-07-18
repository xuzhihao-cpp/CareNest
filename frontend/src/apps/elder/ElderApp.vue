<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Bell, ClipboardList, FileHeart, HeartPulse, Home, Link2, LogOut, MessageCircleHeart, Sparkles, Type, UserRound } from '@lucide/vue';
import { getCurrentUser, logout } from '@/api/stageTwo';
import { getElderReminders } from '@/api/stageThirtyTwo';
import EmergencyAssistancePanel from '@/components/EmergencyAssistancePanel.vue';
import FloatingAiAssistant from '@/components/FloatingAiAssistant.vue';
import StageSixBindingPanel from '@/components/StageSixBindingPanel.vue';
import StageSevenProfilePanel from '@/components/StageSevenProfilePanel.vue';
import StageFifteenServiceReportPanel from '@/components/StageFifteenServiceReportPanel.vue';
import StageSixteenReportAckPanel from '@/components/StageSixteenReportAckPanel.vue';
import StageNineteenHealthArchivePanel from '@/components/StageNineteenHealthArchivePanel.vue';
import StageTwentyMedicalFilesPanel from '@/components/StageTwentyMedicalFilesPanel.vue';
import StageTwentyTwoHealthFeedbackPanel from '@/components/StageTwentyTwoHealthFeedbackPanel.vue';
import StageThirtyTwoReminderCenter from '@/components/StageThirtyTwoReminderCenter.vue';
import StageFortyOneAiAssistantPanel from '@/components/StageFortyOneAiAssistantPanel.vue';
import type { AuthUser } from '@/types/stageTwo';

type PrimaryView = 'home' | 'reminders' | 'ai' | 'account';
type HomeView = 'health' | 'feedback' | 'assistance';
type AccountView = 'profile' | 'medical' | 'binding' | 'reports';

const primaryView = ref<PrimaryView>('home');
const homeView = ref<HomeView>('health');
const accountView = ref<AccountView>('profile');
const user = ref<AuthUser | null>(null);
const pendingReminders = ref(0);
const largeText = ref(true);
const allowed = computed(() => Boolean(user.value?.roles.includes('ELDER')));
const pageTitle = computed(() => ({ home: '我的照护', reminders: '提醒', ai: 'AI 照护助手', account: '我的' }[primaryView.value]));
const elderTextModeStorageKey = 'carenest_elder_large_text';

function loadDisplayPreference() {
  const stored = uni.getStorageSync(elderTextModeStorageKey);
  largeText.value = typeof stored === 'boolean' ? stored : true;
}

function toggleLargeText() {
  largeText.value = !largeText.value;
  uni.setStorageSync(elderTextModeStorageKey, largeText.value);
}

async function loadUser() {
  const response = await getCurrentUser();
  if (response.code !== 0) return;
  user.value = response.data;
  const pages = getCurrentPages();
  const page = pages[pages.length - 1] as { options?: Record<string, string> } | undefined;
  const requested = page?.options?.view;
  if (requested === 'health-feedback') homeView.value = 'feedback';
  if (requested === 'emergency') homeView.value = 'assistance';
  if (requested === 'medical-files') { primaryView.value = 'account'; accountView.value = 'medical'; }
}

async function loadReminderCount() {
  const response = await getElderReminders(1, 50);
  if (response.code === 0) pendingReminders.value = response.data.records.filter((item) => ['PENDING', 'NEED_HELP', 'SNOOZED'].includes(item.status)).length;
}

async function selectPrimary(next: PrimaryView) {
  primaryView.value = next;
  if (next === 'reminders') pendingReminders.value = 0;
  await nextTickScrollTop();
}

function nextTickScrollTop() {
  return new Promise<void>((resolve) => uni.pageScrollTo({ scrollTop: 0, duration: 160, complete: () => resolve() }));
}

async function signOut() {
  await logout();
  uni.redirectTo({ url: '/pages/login/index' });
}

onMounted(async () => {
  loadDisplayPreference();
  await loadUser();
  if (allowed.value) await loadReminderCount();
});
</script>

<template>
  <view class="elder-shell" :class="{ 'large-text': largeText }">
    <header class="shell-header">
      <view>
        <text class="brand">CareNest</text>
        <text class="page-title">{{ pageTitle }}</text>
      </view>
      <view class="header-tools">
        <button
          class="text-mode-button"
          type="button"
          :aria-pressed="largeText"
          :aria-label="largeText ? '切换为标准字体' : '开启大字模式'"
          @click="toggleLargeText"
        >
          <Type :size="20" aria-hidden="true" />
          <text>{{ largeText ? '标准字' : '大字' }}</text>
        </button>
        <view class="identity" aria-label="当前用户">
          <UserRound :size="18" :stroke-width="2" aria-hidden="true" />
          <text>{{ user?.displayName || '长辈用户' }}</text>
        </view>
      </view>
    </header>

    <main class="shell-main">
      <view v-if="!allowed" class="access-state">请使用长辈账号登录后访问。</view>
      <transition v-else name="view-shift" mode="out-in">
        <view :key="primaryView" class="primary-view">
          <template v-if="primaryView === 'home'">
            <section class="welcome-band">
              <text class="welcome-kicker">今天也要照顾好自己</text>
              <text class="welcome-title">需要什么，点一下就好</text>
              <view class="quick-actions">
                <button type="button" :class="{ active: homeView === 'health' }" @click="homeView='health'">
                  <HeartPulse :size="23" aria-hidden="true" /><text>健康摘要</text>
                </button>
                <button type="button" :class="{ active: homeView === 'feedback' }" @click="homeView='feedback'">
                  <MessageCircleHeart :size="23" aria-hidden="true" /><text>健康反馈</text>
                </button>
                <button type="button" :class="{ active: homeView === 'assistance' }" @click="homeView='assistance'">
                  <FileHeart :size="23" aria-hidden="true" /><text>请求协助</text>
                </button>
              </view>
            </section>
            <StageNineteenHealthArchivePanel v-if="homeView === 'health'" role-code="ELDER" :auth-user="user" />
            <StageTwentyTwoHealthFeedbackPanel v-if="homeView === 'feedback'" role-code="ELDER" :auth-user="user" @open-profile="primaryView='account';accountView='profile'" @open-assistance="homeView='assistance'" />
            <EmergencyAssistancePanel v-if="homeView === 'assistance'" @back-feedback="homeView='feedback'" />
          </template>

          <StageThirtyTwoReminderCenter v-else-if="primaryView === 'reminders'" @close="selectPrimary('home')" />
          <StageFortyOneAiAssistantPanel v-else-if="primaryView === 'ai'" role-code="ELDER" />

          <template v-else>
            <nav class="account-nav" aria-label="我的功能">
              <button type="button" :class="{ active: accountView === 'profile' }" @click="accountView='profile'"><UserRound :size="19" aria-hidden="true" />档案</button>
              <button type="button" :class="{ active: accountView === 'medical' }" @click="accountView='medical'"><FileHeart :size="19" aria-hidden="true" />病历</button>
              <button type="button" :class="{ active: accountView === 'binding' }" @click="accountView='binding'"><Link2 :size="19" aria-hidden="true" />绑定</button>
              <button type="button" :class="{ active: accountView === 'reports' }" @click="accountView='reports'"><ClipboardList :size="19" aria-hidden="true" />报告</button>
            </nav>
            <StageSevenProfilePanel v-if="accountView === 'profile'" role-code="ELDER" :auth-user="user" />
            <StageTwentyMedicalFilesPanel v-if="accountView === 'medical'" role-code="ELDER" :auth-user="user" />
            <StageSixBindingPanel v-if="accountView === 'binding'" role-code="ELDER" :auth-user="user" />
            <template v-if="accountView === 'reports'"><StageFifteenServiceReportPanel role-code="ELDER" :auth-user="user" /><StageSixteenReportAckPanel role-code="ELDER" :auth-user="user" /></template>
            <button class="sign-out" type="button" @click="signOut"><LogOut :size="20" aria-hidden="true" />退出登录</button>
          </template>
        </view>
      </transition>
    </main>

    <nav v-if="allowed" class="bottom-nav" aria-label="主要导航">
      <button type="button" :class="{ active: primaryView === 'home' }" :aria-current="primaryView === 'home' ? 'page' : undefined" @click="selectPrimary('home')"><Home :size="24" aria-hidden="true" /><text>首页</text></button>
      <button type="button" :class="{ active: primaryView === 'reminders' }" :aria-current="primaryView === 'reminders' ? 'page' : undefined" @click="selectPrimary('reminders')"><view class="nav-icon"><Bell :size="24" aria-hidden="true" /><text v-if="pendingReminders" class="nav-badge">{{ pendingReminders > 9 ? '9+' : pendingReminders }}</text></view><text>提醒</text></button>
      <button type="button" :class="{ active: primaryView === 'ai' }" :aria-current="primaryView === 'ai' ? 'page' : undefined" @click="selectPrimary('ai')"><Sparkles :size="24" aria-hidden="true" /><text>AI</text></button>
      <button type="button" :class="{ active: primaryView === 'account' }" :aria-current="primaryView === 'account' ? 'page' : undefined" @click="selectPrimary('account')"><UserRound :size="24" aria-hidden="true" /><text>我的</text></button>
    </nav>
    <FloatingAiAssistant v-if="allowed && primaryView === 'home'" />
  </view>
</template>

<style scoped>
.elder-shell{overflow-x:clip}
.elder-shell{min-height:100vh;box-sizing:border-box;background:#f4f6f3;color:#1f3029;padding-bottom:calc(78px + env(safe-area-inset-bottom));}.shell-header{position:sticky;top:0;z-index:20;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:17px 16px 14px;background:rgba(255,255,255,.96);border-bottom:1px solid #e4e9e5;backdrop-filter:blur(14px)}.brand,.page-title{display:block;letter-spacing:0}.brand{color:#2c7461;font-size:12px;font-weight:800}.page-title{margin-top:3px;font-size:24px;font-weight:750}.header-tools{display:flex;align-items:center;justify-content:flex-end;gap:8px;min-width:0}.text-mode-button{display:flex;align-items:center;justify-content:center;gap:5px;min-width:76px;min-height:44px;margin:0;padding:0 9px;border:1px solid #cbdad3;border-radius:7px;background:#fff;color:#2a6d59;font-size:13px;font-weight:700;line-height:1.2}.text-mode-button[aria-pressed="true"]{border-color:#4c8d78;background:#e9f4ef;color:#215f4c}.identity{display:flex;align-items:center;gap:6px;max-width:112px;color:#506159;font-size:13px}.identity text{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.shell-main{min-height:calc(100vh - 150px);padding:18px 16px 28px}.primary-view{min-width:0}.welcome-band{padding:8px 2px 22px}.welcome-kicker,.welcome-title{display:block}.welcome-kicker{color:#5e7168;font-size:14px}.welcome-title{margin-top:5px;font-size:22px;font-weight:750}.quick-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:18px}.quick-actions button{display:flex;min-width:0;min-height:76px;flex-direction:column;align-items:center;justify-content:center;gap:8px;margin:0;padding:9px 6px;border:1px solid #dfe6e1;border-radius:8px;background:#fff;color:#53645c;font-size:13px;line-height:1.2}.quick-actions button.active{border-color:#78a999;background:#edf5f1;color:#245e4e;font-weight:700}.account-nav{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin:0 0 18px;border-bottom:1px solid #dde5df}.account-nav button{display:flex;min-width:0;min-height:58px;align-items:center;justify-content:center;gap:5px;margin:0;padding:0 4px;border:0;border-bottom:3px solid transparent;border-radius:0;background:transparent;color:#697970;font-size:13px}.account-nav button.active{border-bottom-color:#2d7c68;color:#245f50;font-weight:700}.sign-out{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;min-height:48px;margin-top:20px;border:1px solid #e3c7c2;border-radius:7px;background:#fff8f6;color:#93463d;font-size:15px}.bottom-nav{position:fixed;right:0;bottom:0;left:0;z-index:30;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));height:calc(70px + env(safe-area-inset-bottom));padding:4px max(8px,calc((100vw - 440px)/2)) env(safe-area-inset-bottom);border-top:1px solid #dfe6e1;background:rgba(255,255,255,.98);box-shadow:0 -8px 24px rgba(33,65,53,.06);box-sizing:border-box}.bottom-nav button{display:flex;min-width:0;min-height:60px;flex-direction:column;align-items:center;justify-content:center;gap:3px;margin:0;padding:0;border:0;border-radius:7px;background:transparent;color:#78847e;font-size:12px;line-height:1}.bottom-nav button.active{color:#236651;font-weight:750}.bottom-nav button.active :deep(svg){stroke-width:2.5}.nav-icon{position:relative;display:grid;place-items:center;width:28px;height:26px}.nav-badge{position:absolute;top:-5px;right:-8px;display:grid;place-items:center;min-width:17px;height:17px;padding:0 3px;border:2px solid #fff;border-radius:9px;background:#d15a4d;color:#fff;font-size:9px;font-weight:800;box-sizing:border-box}.access-state{padding:28px 18px;border-left:4px solid #d15a4d;background:#fff;color:#7e433d}.view-shift-enter-active,.view-shift-leave-active{transition:opacity .16s ease,transform .16s ease}.view-shift-enter-from{opacity:0;transform:translateY(7px)}.view-shift-leave-to{opacity:0;transform:translateY(-4px)}.shell-main :deep(.glass-panel),.shell-main :deep(.health-archive-panel),.shell-main :deep(.medical-files-panel){min-width:0;margin-top:0;border-radius:8px;box-shadow:none}.shell-main :deep(.stage-six-summary),.shell-main :deep(.stage-seven-summary),.shell-main :deep(.stage-fifteen-summary),.shell-main :deep(.stage-sixteen-summary),.shell-main :deep(.profile-workbench),.shell-main :deep(.service-report-workbench),.shell-main :deep(.report-toolbar),.shell-main :deep(.contact-grid){grid-template-columns:minmax(0,1fr)!important}
.large-text .page-title{font-size:28px}.large-text .brand{font-size:14px}.large-text .identity{display:none}.large-text .text-mode-button{min-width:96px;min-height:50px;font-size:16px}.large-text .welcome-kicker{font-size:18px}.large-text .welcome-title{font-size:28px;line-height:1.35}.large-text .quick-actions button{min-height:94px;font-size:17px}.large-text .account-nav button{min-height:70px;font-size:16px}.large-text .sign-out{min-height:58px;font-size:18px}.large-text .bottom-nav{height:calc(78px + env(safe-area-inset-bottom))}.large-text .bottom-nav button{min-height:68px;font-size:15px}.large-text .shell-main :deep(button),.large-text .shell-main :deep(input),.large-text .shell-main :deep(textarea),.large-text .shell-main :deep(picker){min-height:52px;font-size:18px!important;line-height:1.45!important}.large-text .shell-main :deep(text),.large-text .shell-main :deep(p),.large-text .shell-main :deep(label),.large-text .shell-main :deep(span){font-size:18px!important;line-height:1.55!important}.large-text .shell-main :deep(strong),.large-text .shell-main :deep(h2),.large-text .shell-main :deep(h3){font-size:20px!important;line-height:1.45!important}.large-text .shell-main :deep(.panel-title),.large-text .shell-main :deep(.next-title),.large-text .shell-main :deep(.ai-title),.large-text .shell-main :deep(h1){font-size:26px!important;line-height:1.35!important}
@media(min-width:768px){.elder-shell{width:440px;margin:0 auto;box-shadow:0 0 0 1px #e0e5e1,0 18px 48px rgba(31,52,41,.1)}}@media(max-width:370px){.shell-header{padding-right:12px;padding-left:12px}.text-mode-button{min-width:70px}.large-text .text-mode-button{min-width:88px}}@media(prefers-reduced-motion:reduce){.view-shift-enter-active,.view-shift-leave-active{transition:none}}
.elder-shell{--elder-header-height:92px;--app-shell-header-height:var(--elder-header-height)}
.elder-shell .shell-header{position:fixed;top:0;right:0;left:0;z-index:40;min-height:var(--elder-header-height);box-sizing:border-box;background:rgba(255,255,255,.97)}
.elder-shell .shell-main{padding-top:calc(var(--elder-header-height) + 18px)}
.elder-shell .welcome-band,.elder-shell .account-nav{position:sticky;top:var(--elder-header-height);z-index:25;background:#f4f6f3}
.elder-shell .welcome-band{padding-top:16px;box-shadow:0 8px 12px -14px rgba(31,52,41,.45)}
.elder-shell .account-nav{padding-top:8px;box-shadow:0 8px 12px -14px rgba(31,52,41,.45)}
.elder-shell .shell-main :deep(.ai-header){position:sticky;top:var(--elder-header-height);z-index:25;padding-top:14px;background:#f4f6f3;box-shadow:0 8px 12px -14px rgba(31,52,41,.45)}
.large-text.elder-shell{--elder-header-height:108px}
@media(min-width:768px){.elder-shell .shell-header{left:50%;right:auto;width:440px;transform:translateX(-50%)}}
</style>
