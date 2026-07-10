import type { PageResult } from './api';

export type ServiceAddressScenario = 'normal' | 'empty' | 'error';

export interface ServiceAddressRequest {
  contactName: string;
  contactPhone: string;
  regionCode: string;
  detailAddress: string;
  isDefault: boolean;
}

export interface ServiceAddressResponse {
  addressId: string;
  fullAddress: string;
  isDefault: boolean;
}

export interface ServiceAddressDetail extends ServiceAddressRequest, ServiceAddressResponse {
  elderId: string;
}

export type ServiceAddressPageResult = PageResult<ServiceAddressResponse>;
