import permissionFixtures from '@/mock/phase-03/permissions-fixtures.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse, RoleCode } from '@/types/stageOne';
import type { PermissionResponse, RolePermissionRecord, RolePermissionRequest } from '@/types/stageThree';

const rolePermissions = (permissionFixtures as { rolePermissions: RolePermissionRecord[] }).rolePermissions;
const permissionAliases: Record<string, string[]> = {
  ELDER_REMINDER_VIEW: ['elder:home:view', 'reminder:update'],
  ELDER_AI_CHAT: ['elder:home:view', 'emergency:create'],
  FAMILY_ELDER_VIEW: ['family:home:view', 'health:view', 'report:confirm'],
  FAMILY_ORDER_CREATE: ['order:create'],
  NURSE_ORDER_VIEW: ['nurse:workbench:view', 'task:accept'],
  NURSE_REPORT_CREATE: ['report:submit', 'evidence:upload'],
  ADMIN_DASHBOARD_VIEW: ['admin:dashboard:view', 'admin:queue:process', 'order:dispatch', 'health:review'],
  ROLE_PERMISSION_MANAGE: ['role:permission:update'],
  CUSTOMER_SERVICE_TICKET_HANDLE: ['customer-service:ticket:handle']
};

const backendPermissionByAlias: Record<string, string> = Object.entries(permissionAliases).reduce<Record<string, string>>(
  (result, [backendCode, aliases]) => {
    result[backendCode] = backendCode;
    aliases.forEach((alias) => {
      result[alias] = backendCode;
    });
    return result;
  },
  {}
);

function getCurrentRole(): RoleCode | null {
  return readAuthSession()?.user.roles[0] ?? null;
}

function findPermissions(roleCode: RoleCode): string[] {
  return rolePermissions.find((item) => item.roleCode === roleCode)?.permissions ?? [];
}

function expandPermissions(permissions: string[]): string[] {
  return Array.from(new Set(permissions.flatMap((permission) => [permission, ...(permissionAliases[permission] ?? [])])));
}

function normalizePermissionResponse(response: ApiResponse<PermissionResponse>): ApiResponse<PermissionResponse> {
  if (response.code !== 0) {
    return response;
  }
  return {
    ...response,
    data: {
      ...response.data,
      permissions: expandPermissions(response.data.permissions)
    }
  };
}

function toBackendPermissionCodes(permissionCodes: string[]): string[] {
  return Array.from(new Set(permissionCodes.map((code) => backendPermissionByAlias[code] ?? code)));
}

export async function getAuthPermissions(): Promise<ApiResponse<PermissionResponse>> {
  if (isMockEnabled()) {
    const roleCode = getCurrentRole();
    if (!roleCode) {
      return failure(401, '未登录', {} as PermissionResponse, 'mock-phase-03-permissions-unauthorized');
    }
    return success(
      {
        roleCode,
        permissions: findPermissions(roleCode)
      },
      'mock-phase-03-permissions'
    );
  }

  const response = await request<PermissionResponse>({
    method: 'GET',
    url: '/auth/permissions'
  });
  return normalizePermissionResponse(response);
}

export async function updateRolePermissions(
  roleId: string,
  payload: RolePermissionRequest
): Promise<ApiResponse<PermissionResponse>> {
  if (isMockEnabled()) {
    const roleCode = getCurrentRole();
    if (!roleCode) {
      return failure(401, '未登录', {} as PermissionResponse, 'mock-phase-03-role-permissions-unauthorized');
    }
    if (roleCode !== 'ADMIN' || !findPermissions(roleCode).includes('role:permission:update')) {
      return failure(403, '无权限', {} as PermissionResponse, 'mock-phase-03-role-permissions-forbidden');
    }
    return success(
      {
        roleCode: roleId as RoleCode,
        permissions: payload.permissionCodes
      },
      'mock-phase-03-role-permissions'
    );
  }

  const response = await request<PermissionResponse>({
    method: 'POST',
    url: `/admin/roles/${roleId}/permissions`,
    data: {
      permissionCodes: toBackendPermissionCodes(payload.permissionCodes)
    }
  });
  return normalizePermissionResponse(response);
}
