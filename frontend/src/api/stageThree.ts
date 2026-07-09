import permissionFixtures from '@/mock/phase-03/permissions-fixtures.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse, RoleCode } from '@/types/stageOne';
import type { PermissionResponse, RolePermissionRecord, RolePermissionRequest } from '@/types/stageThree';

const rolePermissions = (permissionFixtures as { rolePermissions: RolePermissionRecord[] }).rolePermissions;

function getCurrentRole(): RoleCode | null {
  return readAuthSession()?.user.roles[0] ?? null;
}

function findPermissions(roleCode: RoleCode): string[] {
  return rolePermissions.find((item) => item.roleCode === roleCode)?.permissions ?? [];
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

  return request<PermissionResponse>({
    method: 'GET',
    url: '/auth/permissions'
  });
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

  return request<PermissionResponse>({
    method: 'POST',
    url: `/admin/roles/${roleId}/permissions`,
    data: payload
  });
}
