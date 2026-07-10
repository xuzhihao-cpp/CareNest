import familyOrdersEmptyMock from '@/mock/phase-10/family-orders-empty.json';
import familyOrdersErrorMock from '@/mock/phase-10/family-orders-error.json';
import familyOrdersMock from '@/mock/phase-10/family-orders.json';
import { getFamilyBindings } from '@/api/stageSix';
import { getServiceItems } from '@/api/stageEight';
import { getServiceAddresses } from '@/api/stageNine';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  FamilyOrderDetail,
  FamilyOrderPageResult,
  FamilyOrderRequest,
  FamilyOrderResponse,
  OrderScenario
} from '@/types/stageTen';

const familyOrdersPath = '/family/orders';
const orderDetailPath = (orderId: string) => `/orders/${orderId}`;
export const STAGE_TEN_STORAGE_KEY = 'carenest_stage_10_family_orders';

function seedOrders(): FamilyOrderDetail[] {
  return [
    {
      orderId: 'order_001',
      orderNo: 'NO202607100001',
      orderStatus: 'WAIT_DISPATCH',
      elderId: 'elder_001',
      serviceId: 'service_001',
      addressId: 'address_001',
      scheduledStart: '2026-07-10T09:00',
      preferredNurseId: '',
      remark: '阶段10预约下单演示订单'
    }
  ];
}

function readOrders(): FamilyOrderDetail[] {
  const stored = uni.getStorageSync(STAGE_TEN_STORAGE_KEY);
  return stored ? (stored as FamilyOrderDetail[]) : seedOrders();
}

function writeOrders(records: FamilyOrderDetail[]) {
  uni.setStorageSync(STAGE_TEN_STORAGE_KEY, records);
}

function toResponse(record: FamilyOrderDetail): FamilyOrderResponse {
  return {
    orderId: record.orderId,
    orderNo: record.orderNo,
    orderStatus: record.orderStatus
  };
}

function toPage(records: FamilyOrderDetail[]): FamilyOrderPageResult {
  return {
    records: records.map(toResponse),
    total: records.length,
    page: 1,
    size: 10
  };
}

function requireFamily<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-10-unauthorized');
  }
  if (!session.user.roles.includes('FAMILY')) {
    return failure(403, '无权限', emptyData, 'mock-10-forbidden');
  }
  return null;
}

function validatePayload(payload: FamilyOrderRequest) {
  return (
    payload.elderId.length > 0 &&
    payload.serviceId.length > 0 &&
    payload.addressId.length > 0 &&
    payload.scheduledStart.length > 0
  );
}

function nextOrderNo(count: number) {
  return `NO20260710${String(count + 1).padStart(4, '0')}`;
}

export function getStageTenEndpointSummary() {
  return ['POST /api/v1/family/orders', 'GET /api/v1/family/orders', 'GET /api/v1/orders/{orderId}'];
}

export async function getFamilyOrders(
  scenario: OrderScenario = 'normal'
): Promise<ApiResponse<FamilyOrderPageResult>> {
  if (isMockEnabled()) {
    const denied = requireFamily(toPage([]));
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return familyOrdersEmptyMock as ApiResponse<FamilyOrderPageResult>;
    }
    if (scenario === 'error') {
      return familyOrdersErrorMock as ApiResponse<FamilyOrderPageResult>;
    }
    return success(toPage(readOrders()), 'mock-10-family-orders');
  }

  return request<FamilyOrderPageResult>({
    method: 'GET',
    url: familyOrdersPath,
    mock: familyOrdersMock as ApiResponse<FamilyOrderPageResult>
  });
}

export async function getOrderDetail(orderId: string): Promise<ApiResponse<FamilyOrderResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as FamilyOrderResponse);
    if (denied) {
      return denied;
    }
    const found = readOrders().find((item) => item.orderId === orderId);
    if (!found) {
      return failure(404, '数据不存在', {} as FamilyOrderResponse, 'mock-10-order-not-found');
    }
    return success(toResponse(found), 'mock-10-order-detail');
  }

  return request<FamilyOrderResponse>({
    method: 'GET',
    url: orderDetailPath(orderId)
  });
}

export async function createFamilyOrder(payload: FamilyOrderRequest): Promise<ApiResponse<FamilyOrderResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as FamilyOrderResponse);
    if (denied) {
      return denied;
    }
    if (!validatePayload(payload)) {
      return failure(422, '业务规则不满足', {} as FamilyOrderResponse, 'mock-10-order-invalid');
    }

    const [bindingResponse, serviceResponse, addressResponse] = await Promise.all([
      getFamilyBindings(),
      getServiceItems('normal'),
      getServiceAddresses(payload.elderId)
    ]);
    const hasBindingScope =
      bindingResponse.code === 0 &&
      bindingResponse.data.some(
        (item) =>
          item.elderId === payload.elderId &&
          item.bindingStatus === 'ACTIVE' &&
          item.scopeCodes.includes('ORDER_CREATE')
      );
    const serviceOnShelf =
      serviceResponse.code === 0 && serviceResponse.data.records.some((item) => item.serviceId === payload.serviceId);
    const addressBelongs =
      addressResponse.code === 0 && addressResponse.data.some((item) => item.addressId === payload.addressId);

    if (!hasBindingScope || !serviceOnShelf || !addressBelongs) {
      return failure(403, '无权限或业务规则不满足', {} as FamilyOrderResponse, 'mock-10-order-forbidden');
    }

    const records = readOrders();
    const created: FamilyOrderDetail = {
      ...payload,
      orderId: `order-${String(records.length + 1).padStart(3, '0')}`,
      orderNo: nextOrderNo(records.length),
      orderStatus: 'WAIT_DISPATCH'
    };
    writeOrders([created, ...records]);
    return success(toResponse(created), 'mock-10-order-create');
  }

  return request<FamilyOrderResponse>({
    method: 'POST',
    url: familyOrdersPath,
    data: payload
  });
}

export function resetStageTenMockRecords() {
  writeOrders(seedOrders());
}
