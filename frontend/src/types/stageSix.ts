import type { components } from './generated/user-api';

type Schemas = components['schemas'];

export type BindingStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REVOKED' | 'EXPIRED';

export type BindingScopeCode =
  | 'HEALTH_VIEW'
  | 'HEALTH_EDIT'
  | 'ORDER_CREATE'
  | 'REPORT_VIEW'
  | 'REPORT_CONFIRM'
  | 'ARCHIVE_EDIT';

export type RelationType = 'SON' | 'DAUGHTER' | 'SPOUSE' | 'OTHER';

export type BindingRequest = Schemas['BindingRequest'];
export type BindingResponse = Schemas['BindingResponse'];
export type BindingScopeUpdateRequest = Schemas['BindingRequest'];
export type BindingListResult = Schemas['BindingResponse'][];

export type BindingScenario = 'normal' | 'empty' | 'error';

