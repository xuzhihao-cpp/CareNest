import type { PageResult } from './api';

export type ServiceStatus = 'ON_SHELF' | 'OFF_SHELF';

export type ServiceItemScenario = 'normal' | 'empty' | 'error';

export interface ServiceItemRequest {
  serviceName: string;
  category: string;
  price: number;
  durationMinutes: number;
  status: ServiceStatus;
}

export interface ServiceItemResponse extends ServiceItemRequest {
  serviceId: string;
}

export type ServiceItemPageResult = PageResult<ServiceItemResponse>;
