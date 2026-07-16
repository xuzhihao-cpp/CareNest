import { request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  FamilyOrderPageResult,
  FamilyOrderRequest,
  FamilyOrderResponse,
  OrderScenario
} from '@/types/stageTen';

const familyOrdersPath = '/family/orders';
const orderDetailPath = (orderId: string) => `/orders/${encodeURIComponent(orderId)}`;

// Compatibility export for legacy scenario modules; stage 10 no longer reads or writes this key.
export const STAGE_TEN_STORAGE_KEY = 'carenest_stage_10_family_orders';

export function getStageTenEndpointSummary() {
  return ['POST /api/v1/family/orders', 'GET /api/v1/family/orders', 'GET /api/v1/orders/{orderId}'];
}

export async function getFamilyOrders(
  _scenario: OrderScenario = 'normal'
): Promise<ApiResponse<FamilyOrderPageResult>> {
  return request<FamilyOrderPageResult>({
    method: 'GET',
    url: familyOrdersPath
  });
}

export async function getOrderDetail(orderId: string): Promise<ApiResponse<FamilyOrderResponse>> {
  return request<FamilyOrderResponse>({
    method: 'GET',
    url: orderDetailPath(orderId)
  });
}

export async function createFamilyOrder(payload: FamilyOrderRequest): Promise<ApiResponse<FamilyOrderResponse>> {
  return request<FamilyOrderResponse>({
    method: 'POST',
    url: familyOrdersPath,
    data: payload
  });
}
