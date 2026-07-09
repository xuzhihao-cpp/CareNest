import type { PageResult } from './api';

export type BindingStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REVOKED' | 'EXPIRED';

export type BindingScopeCode =
  | 'HEALTH_VIEW'
  | 'HEALTH_EDIT'
  | 'ORDER_CREATE'
  | 'REPORT_VIEW'
  | 'REPORT_CONFIRM'
  | 'ARCHIVE_EDIT';

export type RelationType = 'SON' | 'DAUGHTER' | 'SPOUSE' | 'OTHER';

export interface BindingRequest {
  elderInviteCode: string;
  relationType: RelationType;
  scopeCodes: BindingScopeCode[];
}

export interface BindingResponse {
  bindingId: string;
  elderId: string;
  elderName: string;
  relationType: RelationType;
  bindingStatus: BindingStatus;
  scopeCodes: BindingScopeCode[];
}

export interface BindingScopeUpdateRequest {
  elderInviteCode: string;
  relationType: RelationType;
  scopeCodes: BindingScopeCode[];
}

export type BindingPageResult = PageResult<BindingResponse>;

export type BindingScenario = 'normal' | 'empty' | 'error';

