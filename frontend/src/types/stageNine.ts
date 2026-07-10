import type { components } from './generated/user-api';

type Schemas = components['schemas'];

export type ServiceAddressScenario = 'normal' | 'empty' | 'error';

export type ServiceAddressRequest = Schemas['ServiceAddressRequest'];
export type ServiceAddressResponse = Schemas['ServiceAddressResponse'];

export interface ServiceAddressDetail extends ServiceAddressRequest, ServiceAddressResponse {
  elderId: string;
}

export type ServiceAddressListResult = Schemas['ServiceAddressResponse'][];
