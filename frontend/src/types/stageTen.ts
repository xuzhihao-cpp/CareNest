import type { PageResult } from './api';
import type { AdminOrderStatus } from './stageEleven';

export type OrderStatus = AdminOrderStatus;

export type OrderScenario = 'normal' | 'empty' | 'error';

export interface FamilyOrderRequest {
  elderId: string;
  serviceId: string;
  addressId: string;
  scheduledStart: string;
  preferredNurseId: string;
  remark: string;
}

export interface FamilyOrderResponse {
  orderId: string;
  orderNo: string;
  orderStatus: OrderStatus;
  elderId: string;
  serviceName?: string;
  serviceAddress?: string;
  scheduledStart: string;
  scheduledEnd?: string;
  servicePrice?: number;
  contactName?: string;
  contactPhone?: string;
  remark: string;
}

export interface FamilyOrderDetail extends FamilyOrderRequest, FamilyOrderResponse {}

export type FamilyOrderPageResult = PageResult<FamilyOrderResponse>;
