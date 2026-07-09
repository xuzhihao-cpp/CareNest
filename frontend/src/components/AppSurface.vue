<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getApiBase, isMockEnabled } from '@/api/client';
import { mockServerPaths } from '@/api/mockServerPaths';
import { getHealth, getRoutes, getVersion } from '@/api/stageOne';
import { getAuthMenus, getCurrentUser, logout } from '@/api/stageTwo';
import { getAuthPermissions, updateRolePermissions } from '@/api/stageThree';
import { getHomeEndpoint, getHomeSummary } from '@/api/stageFour';
import type { HealthResponse, RoleCode, RouteEntry, StageOneSnapshot, VersionResponse } from '@/types/stageOne';
import type { AuthMenu, AuthUser } from '@/types/stageTwo';
import type { HomeQuickAction, HomeSummaryResponse } from '@/types/stageFour';
import EmptyState from './EmptyState.vue';

type Accent = 'teal' | 'mint' | 'amber' | 'coral' | 'blue';

interface VisualProfile {
  terminal: 'mobile' | 'admin';
  subtitle: string;
  heroTitle: string;
  heroMeta: string;
  primaryAction: string;
  primarySymbol: string;
  primaryPermission: string;
  highlights: Array<{
    label: string;
    value: string;
    meta: string;
    accent: Accent;
  }>;
  flowTitle: string;
  flowItems: Array<{
    label: string;
    time: string;
    status: string;
    accent: Accent;
  }>;
}

const props = defineProps<{
  roleCode: RoleCode;
}>();

const routes = getRoutes();
const currentRoute = computed<RouteEntry>(() => routes.find((item) => item.roleCode === props.roleCode) ?? routes[0]);
const snapshot = ref<StageOneSnapshot | null>(null);
const errorMessage = ref('');
const authUser = ref<AuthUser | null>(null);
const authMenus = ref<AuthMenu[]>([]);
const authMessage = ref('');
const permissions = ref<string[]>([]);
const permissionRoleCode = ref<RoleCode | null>(null);
const permissionMessage = ref('');
const adminPermissionProbe = ref('');
const homeSummary = ref<HomeSummaryResponse | null>(null);
const homeMessage = ref('');
const openedAction = ref<HomeQuickAction | null>(null);

const health = computed<HealthResponse | null>(() => snapshot.value?.health.data ?? null);
const version = computed<VersionResponse | null>(() => snapshot.value?.version.data ?? null);
const homeEndpoint = computed(() => getHomeEndpoint(props.roleCode));
const requestMode = computed(() => (isMockEnabled() ? 'mock' : 'real'));
const isRoleAllowed = computed(() => !!authUser.value && authUser.value.roles.includes(props.roleCode));
const visibleRoutes = computed(() => {
  if (!authUser.value) {
    return [];
  }
  return routes.filter((item) => authUser.value?.roles.includes(item.roleCode));
});
const permissionSet = computed(() => new Set(permissions.value));
const canUsePrimaryAction = computed(() => permissionSet.value.has(visualProfile.value.primaryPermission));
const canManageRolePermissions = computed(() => permissionSet.value.has('role:permission:update'));
const visibleQuickActions = computed(() =>
  (homeSummary.value?.quickActions ?? []).filter((item) => permissionSet.value.has(item.permissionCode))
);
const accessDeniedTitle = computed(() => (authUser.value ? '403 无权限' : '401 未登录'));
const accessDeniedDescription = computed(() => {
  if (!authUser.value) {
    return '请先登录后访问对应端页面。';
  }
  return `${authUser.value.displayName} 当前角色为 ${authUser.value.roles.join(' / ')}，不能访问 ${props.roleCode} 页面。`;
});

const roleProfiles: Record<RoleCode, VisualProfile> = {
  ELDER: {
    terminal: 'mobile',
    subtitle: '移动端 · 简单易用 · 安心陪伴',
    heroTitle: '上午好，张奶奶',
    heroMeta: '今日提醒 4 项 · 护理员预计 09:45 到达',
    primaryAction: '一键求助',
    primarySymbol: '☎',
    primaryPermission: 'emergency:create',
    highlights: [
      { label: '08:30', value: '吃药', meta: '已完成', accent: 'teal' },
      { label: '09:30', value: '测血压', meta: '待完成', accent: 'blue' },
      { label: '10:30', value: '活动一下', meta: '待完成', accent: 'amber' }
    ],
    flowTitle: '今日照护',
    flowItems: [
      { label: '护理员已出发', time: '09:00', status: '服务中', accent: 'teal' },
      { label: '预计到达', time: '09:45', status: '待确认', accent: 'amber' },
      { label: '语音播报', time: '随时', status: '可用', accent: 'mint' }
    ]
  },
  FAMILY: {
    terminal: 'mobile',
    subtitle: '移动端 · 远程守护 · 及时掌握',
    heroTitle: '张奶奶健康状态稳定',
    heroMeta: '血压 128/78 · 心率 72 · 今日提醒完成 3/5',
    primaryAction: '去确认',
    primarySymbol: '✓',
    primaryPermission: 'report:confirm',
    highlights: [
      { label: '服务进度', value: '护理员已出发', meta: '09:00', accent: 'teal' },
      { label: '待处理', value: '报告确认', meta: '2 项', accent: 'amber' },
      { label: '健康摘要', value: '良好', meta: '更新于 09:30', accent: 'mint' }
    ],
    flowTitle: '服务时间线',
    flowItems: [
      { label: '护理员出发', time: '09:00', status: '已完成', accent: 'teal' },
      { label: '预计到达', time: '09:45', status: '服务中', accent: 'teal' },
      { label: '报告确认', time: '服务后', status: '待确认', accent: 'amber' }
    ]
  },
  NURSE: {
    terminal: 'mobile',
    subtitle: '移动端 · 任务执行 · 标准流程',
    heroTitle: '我的任务',
    heroMeta: '待接单 3 · 服务中 2 · 待补资料 1',
    primaryAction: '提交报告',
    primarySymbol: '↑',
    primaryPermission: 'report:submit',
    highlights: [
      { label: '当前任务', value: '上门照护', meta: '09:45-10:30', accent: 'teal' },
      { label: '必填清单', value: '4/6', meta: '待补 2 项', accent: 'amber' },
      { label: '服务留痕', value: '已上传', meta: '3 张', accent: 'mint' }
    ],
    flowTitle: '服务流程',
    flowItems: [
      { label: '到达签到', time: '09:45', status: '已完成', accent: 'teal' },
      { label: '健康监测', time: '10:00', status: '服务中', accent: 'teal' },
      { label: '报告提交', time: '服务后', status: '待补资料', accent: 'amber' }
    ]
  },
  ADMIN: {
    terminal: 'admin',
    subtitle: '管理工作台 · 运营监管 · 质量控制',
    heroTitle: '运营看板',
    heroMeta: '订单 1,248 · 完成率 95.6% · 待审核 28',
    primaryAction: '处理队列',
    primarySymbol: '▦',
    primaryPermission: 'admin:queue:process',
    highlights: [
      { label: '服务完成率', value: '95.6%', meta: '较昨日 +2.1%', accent: 'teal' },
      { label: '质控通过率', value: '92.3%', meta: '较昨日 +1.8%', accent: 'blue' },
      { label: '异常工单', value: '3', meta: '需优先处理', accent: 'coral' }
    ],
    flowTitle: '质控队列',
    flowItems: [
      { label: '报告审核', time: '8 项', status: '待审核', accent: 'amber' },
      { label: '指标豁免', time: '6 项', status: '待确认', accent: 'amber' },
      { label: '异常投诉', time: '3 项', status: '异常', accent: 'coral' }
    ]
  }
};

const visualProfile = computed(() => roleProfiles[props.roleCode]);

function goRoute(item: RouteEntry) {
  const url = `/pages${item.routePath}/index`;
  uni.reLaunch({ url });
}

function goLogin() {
  uni.redirectTo({ url: '/pages/login/index' });
}

async function handleLogout() {
  await logout();
  uni.redirectTo({ url: '/pages/login/index' });
}

function openQuickAction(action: HomeQuickAction) {
  openedAction.value = action;
}

onMounted(async () => {
  try {
    const [healthResponse, versionResponse, meResponse, menusResponse, permissionsResponse] = await Promise.all([
      getHealth(),
      getVersion(),
      getCurrentUser(),
      getAuthMenus(),
      getAuthPermissions()
    ]);
    snapshot.value = {
      health: healthResponse,
      version: versionResponse,
      routes
    };
    if (meResponse.code === 0) {
      authUser.value = meResponse.data;
    } else {
      authMessage.value = `${meResponse.code} ${meResponse.message}`;
    }
    if (menusResponse.code === 0) {
      authMenus.value = menusResponse.data.menus;
    }
    if (permissionsResponse.code === 0) {
      permissionRoleCode.value = permissionsResponse.data.roleCode;
      permissions.value = permissionsResponse.data.permissions;
    } else {
      permissionMessage.value = `${permissionsResponse.code} ${permissionsResponse.message}`;
    }

    const probeResponse = await updateRolePermissions('ADMIN', {
      permissionCodes: ['role:permission:update']
    });
    adminPermissionProbe.value = `${probeResponse.code} ${probeResponse.message}`;

    if (meResponse.code === 0 && meResponse.data.roles.includes(props.roleCode)) {
      const homeResponse = await getHomeSummary({
        role: props.roleCode,
        currentUserId: meResponse.data.userId
      });
      if (homeResponse.code === 0) {
        homeSummary.value = homeResponse.data;
      } else {
        homeMessage.value = `${homeResponse.code} ${homeResponse.message}`;
      }
    }
  } catch {
    errorMessage.value = '接口请求失败';
  }
});
</script>

<template>
  <view class="app-shell" :class="`terminal-${visualProfile.terminal}`">
    <aside class="side-nav glass-panel" aria-label="四端角色入口">
      <view class="brand">
        <text class="brand-mark">✦</text>
        <text>CareNest</text>
      </view>
      <view class="nav-list">
        <button
          v-for="item in visibleRoutes"
          :key="item.roleCode"
          class="nav-button"
          :class="{ active: item.roleCode === props.roleCode }"
          type="button"
          @click="goRoute(item)"
        >
          <text>{{ item.entryLabel }}</text>
          <text>›</text>
        </button>
        <button v-if="!authUser" class="nav-button active" type="button" @click="goLogin">
          <text>登录入口</text>
          <text>›</text>
        </button>
      </view>
      <view class="nav-foot">
        <text>◇</text>
        <text>阶段3权限拦截</text>
      </view>
    </aside>

    <main class="workspace">
      <view class="workspace-header">
        <view>
          <text class="eyebrow">{{ currentRoute.roleCode }}</text>
          <text class="title">{{ currentRoute.appTitle }}</text>
          <text class="subtitle">{{ visualProfile.subtitle }}</text>
        </view>
        <view class="status-strip" aria-label="接口状态">
          <text class="status-pill" :class="health?.status === 'UP' ? 'status-up' : 'status-wait'">
            ⌁ {{ health?.status ?? 'LOADING' }}
          </text>
        </view>
      </view>

      <view class="auth-panel glass-panel" aria-label="阶段2登录态">
        <view class="auth-user">
          <text class="section-mini">当前用户</text>
          <text class="auth-name">{{ authUser?.displayName ?? '未登录' }}</text>
          <text class="auth-meta">
            {{ authUser ? authUser.roles.join(' / ') : authMessage || '401 未登录' }}
          </text>
        </view>
        <view class="auth-menu-list">
          <text v-for="menu in authMenus" :key="menu.name" class="tag tag-teal">{{ menu.name }}</text>
          <text v-if="authUser && !isRoleAllowed" class="tag tag-coral">403 无权限</text>
          <text v-if="!authUser" class="tag tag-amber">401 未登录</text>
        </view>
        <button v-if="authUser" class="ghost-action" type="button" @click="handleLogout">
          <text>退出</text>
        </button>
        <button v-else class="ghost-action" type="button" @click="goLogin">
          <text>去登录</text>
        </button>
      </view>

      <view class="permission-panel glass-panel" aria-label="阶段3权限契约">
        <view class="section-title">
          <text>▣</text>
          <text>权限拦截 MVP</text>
        </view>
        <view class="permission-grid">
          <view>
            <text class="section-mini">GET /api/v1/auth/permissions</text>
            <text class="permission-main">{{ permissionRoleCode ?? '未获取角色' }}</text>
            <text class="auth-meta">{{ permissionMessage || `已加载 ${permissions.length} 项权限` }}</text>
          </view>
          <view>
            <text class="section-mini">POST /api/v1/admin/roles/{roleId}/permissions</text>
            <text class="permission-main" :class="{ denied: adminPermissionProbe.startsWith('403') }">
              {{ adminPermissionProbe || '待校验' }}
            </text>
            <text class="auth-meta">普通用户访问管理端接口应返回 403</text>
          </view>
        </view>
        <view class="permission-tags">
          <text v-for="item in permissions" :key="item" class="tag tag-blue">{{ item }}</text>
        </view>
        <button v-if="canManageRolePermissions" class="ghost-action" type="button">
          <text>保存角色权限</text>
        </button>
      </view>

      <view class="request-layer-panel glass-panel" aria-label="阶段5请求层">
        <view class="section-title">
          <text>⇄</text>
          <text>请求层与 mock 开关</text>
        </view>
        <view class="request-layer-grid">
          <text>request(method,url,data)</text>
          <text>API_BASE {{ getApiBase() }}</text>
          <text>mode {{ requestMode }}</text>
          <text>mock paths {{ mockServerPaths.length }}</text>
        </view>
        <view class="permission-tags">
          <text class="tag tag-teal">ApiResponse&lt;T&gt;</text>
          <text class="tag tag-blue">PageResult&lt;T&gt;</text>
          <text class="tag tag-amber">FileUploadResult</text>
        </view>
      </view>

      <view v-if="!authUser || !isRoleAllowed" class="access-denied glass-panel" aria-label="页面访问拦截">
        <text class="access-code">{{ accessDeniedTitle }}</text>
        <text class="access-title">页面访问已拦截</text>
        <text class="access-desc">{{ accessDeniedDescription }}</text>
        <button class="hero-action" type="button" @click="goLogin">
          <text>重新登录</text>
          <text>›</text>
        </button>
      </view>

      <template v-else>
      <view class="hero-board glass-panel" aria-label="阶段1四端可视化入口">
        <view class="hero-copy">
          <text class="hero-kicker">CareNest</text>
          <text class="hero-title">{{ visualProfile.heroTitle }}</text>
          <text class="hero-meta">{{ visualProfile.heroMeta }}</text>
          <text class="hero-meta stage-four-meta">
            {{ homeEndpoint }} · 待办 {{ homeSummary?.todoCount ?? 0 }} 项
          </text>
        </view>
        <button
          v-if="canUsePrimaryAction"
          class="hero-action"
          :class="{ danger: props.roleCode === 'ELDER' }"
          type="button"
        >
          <text>{{ visualProfile.primarySymbol }}</text>
          <text>{{ visualProfile.primaryAction }}</text>
        </button>
        <text v-else class="tag tag-coral">按钮无权限</text>
      </view>

      <view class="quick-grid" aria-label="阶段4首页卡片">
        <view v-for="item in homeSummary?.cards ?? []" :key="item.key" class="quick-card glass-panel">
          <text class="quick-label">{{ item.label }}</text>
          <text class="quick-value">{{ item.value }}{{ item.unit }}</text>
          <text class="tag tag-blue">{{ item.trend }}</text>
        </view>
      </view>

      <view v-if="homeMessage" class="error-banner" role="alert">
        <text>{{ homeMessage }}</text>
      </view>

      <view class="content-grid">
        <view class="flow-panel glass-panel">
          <view class="section-title">
            <text>◷</text>
            <text>{{ visualProfile.flowTitle }}</text>
          </view>
          <view class="flow-list">
            <view v-for="item in visualProfile.flowItems" :key="item.label" class="flow-row">
              <text class="flow-dot" :class="`dot-${item.accent}`"></text>
              <view class="flow-main">
                <text class="flow-label">{{ item.label }}</text>
                <text class="flow-time">{{ item.time }}</text>
              </view>
              <text class="tag" :class="`tag-${item.accent}`">{{ item.status }}</text>
            </view>
          </view>
        </view>

        <view class="contract-panel glass-panel">
          <view class="section-title">
            <text>▥</text>
            <text>接口契约验证</text>
          </view>
          <view class="metric-grid" aria-label="阶段1接口响应">
            <view class="metric-panel">
              <view class="metric-icon teal">
                <text>♡</text>
              </view>
              <view>
                <text class="metric-path">GET /api/v1/health</text>
                <text class="metric-value">{{ health?.appName ?? 'CareNest' }}</text>
                <text class="metric-meta">{{ health?.version ?? '0.1.0' }}</text>
              </view>
            </view>
            <view class="metric-panel">
              <view class="metric-icon amber">
                <text>◷</text>
              </view>
              <view>
                <text class="metric-path">GET /api/v1/version</text>
                <text class="metric-value">{{ version?.apiPrefix ?? '/api/v1' }}</text>
                <text class="metric-meta">{{ version?.gitCommit ?? 'local-kickoff' }}</text>
              </view>
            </view>
            <view class="metric-panel">
              <view class="metric-icon teal">
                <text>⌂</text>
              </view>
              <view>
                <text class="metric-path">GET {{ homeEndpoint }}</text>
                <text class="metric-value">todo {{ homeSummary?.todoCount ?? 0 }}</text>
                <text class="metric-meta">mock-4</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="stage-strip home-actions glass-panel" aria-label="阶段4快捷入口">
        <button
          v-for="action in visibleQuickActions"
          :key="action.key"
          class="action-button"
          type="button"
          @click="openQuickAction(action)"
        >
          <text>{{ action.label }}</text>
        </button>
      </view>

      <view v-if="openedAction" class="action-placeholder glass-panel" aria-label="阶段4空列表占位">
        <text class="access-code">占位入口</text>
        <text class="access-title">{{ openedAction.label }}</text>
        <text class="access-desc">{{ openedAction.path }}</text>
        <text class="tag tag-teal">{{ openedAction.permissionCode }}</text>
      </view>

      <EmptyState :title="currentRoute.emptyStateTitle" :description="currentRoute.emptyStateDescription" />
      </template>

      <view v-if="errorMessage" class="error-banner" role="alert">
        <text>{{ errorMessage }}</text>
      </view>
    </main>
  </view>
</template>
