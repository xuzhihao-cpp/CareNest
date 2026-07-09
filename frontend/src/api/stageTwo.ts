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
import type { AuthMenu, AuthSession, AuthUser, DemoAccount, LoginRequest, LoginResponse } from '@/types/stageTwo';

const accounts = (authFixtures as { accounts: DemoAccount[] }).accounts;

function toUser(account: DemoAccount): AuthUser {
  return {
    userId: account.userId,
    displayName: account.displayName,
    roles: account.roles,
    menus: account.menus
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

  const body = await request<LoginResponse>({
    method: 'POST',
    url: '/auth/login',
    data: payload
  });
  if (body.code === 0) {
    writeAuthSession({
      token: body.data.token,
      user: {
        userId: body.data.userId,
        displayName: body.data.displayName,
        roles: body.data.roles,
        menus: body.data.menus
      }
    });
  }
  return body;
}

export async function logout(): Promise<ApiResponse<Record<string, never>>> {
  if (isMockEnabled()) {
    clearAuthSession();
    return success({}, 'mock-phase-02-logout');
  }

  const response = await request<Record<string, never>>({
    method: 'POST',
    url: '/auth/logout'
  });
  clearAuthSession();
  return response;
}

export async function getCurrentUser(): Promise<ApiResponse<AuthUser>> {
  if (isMockEnabled()) {
    const session = readAuthSession();
    if (!session) {
      return failure(401, '未登录', {} as AuthUser, 'mock-phase-02-me-unauthorized');
    }
    return success(session.user, 'mock-phase-02-me');
  }

  return request<AuthUser>({
    method: 'GET',
    url: '/auth/me'
  });
}

export async function getAuthMenus(): Promise<ApiResponse<{ menus: AuthMenu[] }>> {
  if (isMockEnabled()) {
    const session = readAuthSession();
    if (!session) {
      return failure(401, '未登录', { menus: [] }, 'mock-phase-02-menus-unauthorized');
    }
    return success({ menus: session.user.menus }, 'mock-phase-02-menus');
  }

  return request<{ menus: AuthMenu[] }>({
    method: 'GET',
    url: '/auth/menus'
  });
}

export function getRoleHomePath(roleCode: RoleCode, menus: AuthMenu[]) {
  const menu = menus.find((item) => item.path.includes(roleCode.toLowerCase())) ?? menus[0];
  return menu?.path ?? '/pages/elder/index';
}
