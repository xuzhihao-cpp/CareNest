import authFixtures from '@/mock/phase-02/auth-fixtures.json';
import {
  clearAuthSession,
  failure,
  isMockEnabled,
  readAuthSession,
  request,
  success,
  writeAuthSession
} from '@/api/client';
import type { ApiResponse, RoleCode } from '@/types/stageOne';
import type {
  AuthMenu,
  AuthUser,
  BackendAuthResponse,
  DemoAccount,
  LoginRequest,
  LoginResponse,
  RawAuthMenu
} from '@/types/stageTwo';

const accounts = (authFixtures as { accounts: DemoAccount[] }).accounts;
const supportedRoles: RoleCode[] = ['ELDER', 'FAMILY', 'NURSE', 'ADMIN'];

const menuMetaByBackendPath: Record<string, Pick<AuthMenu, 'name' | 'icon'>> = {
  '/elder/home': { name: '长辈首页', icon: 'home' },
  '/elder/reminders': { name: '今日提醒', icon: 'reminder' },
  '/elder/ai': { name: 'AI 语音', icon: 'voice' },
  '/family/home': { name: '家属首页', icon: 'home' },
  '/family/elders': { name: '长辈档案', icon: 'health' },
  '/family/orders': { name: '服务订单', icon: 'order' },
  '/nurse/home': { name: '护理工作台', icon: 'workbench' },
  '/nurse/orders': { name: '任务管理', icon: 'task' },
  '/nurse/reports': { name: '服务报告', icon: 'file' },
  '/admin/home': { name: '管理首页', icon: 'dashboard' },
  '/admin/users': { name: '用户管理', icon: 'users' },
  '/admin/dashboard': { name: '数据看板', icon: 'chart' }
};

const backendPathToPagePath: Record<string, string> = {
  '/elder/home': '/pages/elder/index',
  '/elder/reminders': '/pages/elder/index?view=today-reminders',
  '/elder/ai': '/pages/elder/index?view=voice-assistant',
  '/family/home': '/pages/family/index',
  '/family/elders': '/pages/family/index?view=health-archive',
  '/family/orders': '/pages/family/index?view=orders',
  '/nurse/home': '/pages/nurse/index',
  '/nurse/orders': '/pages/nurse/index?view=task-list',
  '/nurse/reports': '/pages/nurse/index?view=submit-report',
  '/admin/home': '/pages/admin/index',
  '/admin/users': '/pages/admin/index?view=users',
  '/admin/dashboard': '/pages/admin/index?view=dashboard'
};

function isRoleCode(value: string): value is RoleCode {
  return supportedRoles.includes(value as RoleCode);
}

function normalizeRoles(roles: string[]): RoleCode[] {
  return roles.filter(isRoleCode);
}

function normalizeMenu(menu: RawAuthMenu): AuthMenu {
  if (typeof menu !== 'string') {
    return menu;
  }

  const meta = menuMetaByBackendPath[menu] ?? {
    name: menu.split('/').filter(Boolean).join(' / ') || '菜单',
    icon: 'link'
  };

  return {
    ...meta,
    path: backendPathToPagePath[menu] ?? menu
  };
}

function normalizeMenus(menus: RawAuthMenu[]): AuthMenu[] {
  return menus.map(normalizeMenu);
}

function normalizeAuthUser(data: Pick<BackendAuthResponse, 'userId' | 'displayName' | 'roles' | 'menus'>): AuthUser {
  return {
    userId: data.userId,
    displayName: data.displayName,
    roles: normalizeRoles(data.roles),
    menus: normalizeMenus(data.menus)
  };
}

function toUser(account: DemoAccount): AuthUser {
  return normalizeAuthUser(account);
}

function normalizeAuthResponse(data: BackendAuthResponse): LoginResponse {
  const user = normalizeAuthUser(data);
  return {
    ...user,
    token: data.token
  };
}

export function getDemoAccounts(): DemoAccount[] {
  return accounts;
}

export async function login(payload: LoginRequest): Promise<ApiResponse<LoginResponse>> {
  if (isMockEnabled()) {
    const account = accounts.find((item) => item.username === payload.username);
    if (!account || account.password !== payload.password) {
      return failure(401, '用户名或密码错误', {} as LoginResponse, 'mock-phase-02-login-failed');
    }

    const user = toUser(account);
    const response: LoginResponse = {
      ...user,
      token: `mock-token-${account.roles[0].toLowerCase()}`
    };
    writeAuthSession({ token: response.token, user });
    return success(response, 'mock-phase-02-login');
  }

  const body = await request<BackendAuthResponse>({
    method: 'POST',
    url: '/auth/login',
    data: payload
  });
  if (body.code === 0) {
    const normalized = normalizeAuthResponse(body.data);
    writeAuthSession({
      token: normalized.token,
      user: normalizeAuthUser(normalized)
    });
    return {
      ...body,
      data: normalized
    };
  }
  return body as unknown as ApiResponse<LoginResponse>;
}

export async function logout(): Promise<ApiResponse<Record<string, never>>> {
  if (isMockEnabled()) {
    clearAuthSession();
    return success({}, 'mock-phase-02-logout');
  }

  const response = await request<BackendAuthResponse>({
    method: 'POST',
    url: '/auth/logout'
  });
  clearAuthSession();
  return {
    ...response,
    data: {}
  };
}

export async function getCurrentUser(): Promise<ApiResponse<AuthUser>> {
  if (isMockEnabled()) {
    const session = readAuthSession();
    if (!session) {
      return failure(401, '未登录', {} as AuthUser, 'mock-phase-02-me-unauthorized');
    }
    return success(session.user, 'mock-phase-02-me');
  }

  const response = await request<BackendAuthResponse>({
    method: 'GET',
    url: '/auth/me'
  });
  if (response.code !== 0) {
    return response as unknown as ApiResponse<AuthUser>;
  }
  const normalized = normalizeAuthResponse(response.data);
  writeAuthSession({
    token: normalized.token,
    user: normalizeAuthUser(normalized)
  });
  return {
    ...response,
    data: normalized
  };
}

export async function getAuthMenus(): Promise<ApiResponse<{ menus: AuthMenu[] }>> {
  if (isMockEnabled()) {
    const session = readAuthSession();
    if (!session) {
      return failure(401, '未登录', { menus: [] }, 'mock-phase-02-menus-unauthorized');
    }
    return success({ menus: session.user.menus }, 'mock-phase-02-menus');
  }

  const response = await request<BackendAuthResponse>({
    method: 'GET',
    url: '/auth/menus'
  });
  if (response.code !== 0) {
    return response as unknown as ApiResponse<{ menus: AuthMenu[] }>;
  }
  return {
    ...response,
    data: {
      menus: normalizeMenus(response.data.menus)
    }
  };
}

export function getRoleHomePath(roleCode: RoleCode, menus: AuthMenu[]) {
  const menu = menus.find((item) => item.path.includes(roleCode.toLowerCase())) ?? menus[0];
  return menu?.path ?? '/pages/elder/index';
}
