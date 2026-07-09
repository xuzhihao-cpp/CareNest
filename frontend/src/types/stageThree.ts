import type { RoleCode } from './stageOne';

export interface PermissionResponse {
  roleCode: RoleCode;
  permissions: string[];
}

export interface RolePermissionRequest {
  permissionCodes: string[];
}

export interface RolePermissionRecord {
  roleCode: RoleCode;
  permissions: string[];
}

