import type { ApiResponse } from '@/types/api';
import type { components } from '@/types/generated/user-api';
import type { approveElderBinding, getElderBindings } from '@/api/stageSix';
import type { BindingListResult, BindingRequest, BindingResponse } from '@/types/stageSix';
import type { ElderProfileResponse, FamilyElderListResult } from '@/types/stageSeven';
import type { ServiceAddressListResult } from '@/types/stageNine';
import type { DemoDataStatusResponse, HealthStatusResponse } from '@/types/stageEighteen';

type Equal<Left, Right> =
  (<Value>() => Value extends Left ? 1 : 2) extends
  (<Value>() => Value extends Right ? 1 : 2)
    ? true
    : false;

type Assert<Condition extends true> = Condition;
type Schemas = components['schemas'];

type BindingListMatchesBackend = Assert<Equal<BindingListResult, Schemas['BindingResponse'][]>>;
type ElderListMatchesBackend = Assert<Equal<FamilyElderListResult, Schemas['ElderProfileResponse'][]>>;
type AddressListMatchesBackend = Assert<Equal<ServiceAddressListResult, Schemas['ServiceAddressResponse'][]>>;
type ElderProfileMatchesBackend = Assert<Equal<ElderProfileResponse, Schemas['ElderProfileResponse']>>;
type HealthMatchesBackend = Assert<Equal<HealthStatusResponse, Schemas['HealthResponse']>>;
type DemoStatusMatchesBackend = Assert<Equal<DemoDataStatusResponse, Schemas['DemoDataStatusResponse']>>;
type ApproveBindingMatchesBackend = Assert<
  Equal<
    typeof approveElderBinding,
    (bindingId: string, payload: BindingRequest) => Promise<ApiResponse<BindingResponse>>
  >
>;
type ElderBindingListMatchesBackend = Assert<
  Equal<typeof getElderBindings, (scenario?: 'normal' | 'empty' | 'error') => Promise<ApiResponse<BindingListResult>>>
>;

export type ImplementedUserApiContractAssertions =
  | BindingListMatchesBackend
  | ElderListMatchesBackend
  | AddressListMatchesBackend
  | ElderProfileMatchesBackend
  | HealthMatchesBackend
  | DemoStatusMatchesBackend
  | ElderBindingListMatchesBackend
  | ApproveBindingMatchesBackend;
