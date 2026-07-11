import adminOrdersEmptyMock from '@/mock/phase-11/admin-orders-empty.json';
import adminOrdersErrorMock from '@/mock/phase-11/admin-orders-error.json';
import adminOrdersMock from '@/mock/phase-11/admin-orders.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { FamilyOrderDetail } from '@/types/stageTen';
import type {
  AdminOrderPageResult,
  AdminOrderQuery,
  AdminOrderRecord,
  AdminOrderScenario
} from '@/types/stageEleven';

const adminOrdersPath = '/admin/orders';
const adminOrderDetailPath = (orderId: string) => `/admin/orders/${orderId}`;
const STAGE_TEN_STORAGE_KEY = 'carenest_stage_10_family_orders';
const STAGE_TWELVE_ORDER_STORAGE_KEY = 'carenest_stage_12_order_overrides';

const defaultQuery: AdminOrderQuery = {
  page: 1,
  size: 10,
  orderStatus: '',
  keyword: '',
  dateFrom: '',
  dateTo: ''
};

type BackendOrder = Omit<AdminOrderRecord, 'statusLogs'>;
type BackendOrderPage = { records: BackendOrder[]; total: number; page: number; size: number };

function fromBackendOrder(record: BackendOrder): AdminOrderRecord {
  return { ...record, statusLogs: [] };
}

function fromBackendPage(page: BackendOrderPage): AdminOrderPageResult {
  return { ...page, records: page.records.map(fromBackendOrder) };
}

function seedRecords(): AdminOrderRecord[] {
  return [...(adminOrdersMock as ApiResponse<AdminOrderPageResult>).data.records];
}

function readStageTenOrders(): FamilyOrderDetail[] {
  const stored = uni.getStorageSync(STAGE_TEN_STORAGE_KEY);
  return stored ? (stored as FamilyOrderDetail[]) : [];
}

function readStageTwelveOrderOverrides(): AdminOrderRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY);
  return stored ? (stored as AdminOrderRecord[]) : [];
}

function fromFamilyOrder(record: FamilyOrderDetail, index: number): AdminOrderRecord {
  return {
    orderId: record.orderId,
    orderNo: record.orderNo,
    orderStatus: record.orderStatus,
    elderId: record.elderId,
    serviceId: record.serviceId,
    addressId: record.addressId,
    scheduledStart: record.scheduledStart,
    statusLogs: [
      {
        statusLogId: `order-status-log-${String(index + 1).padStart(3, '0')}`,
        orderId: record.orderId,
        fromStatus: '',
        toStatus: record.orderStatus,
        changedBy: 'family-001',
        changeReason: '家属端预约下单'
      }
    ]
  };
}

function readRecords(): AdminOrderRecord[] {
  const stageTenOrders = readStageTenOrders().map(fromFamilyOrder);
  const baseRecords = stageTenOrders.length > 0 ? stageTenOrders : seedRecords();
  const overrides = readStageTwelveOrderOverrides();
  if (overrides.length === 0) {
    return baseRecords;
  }
  const merged = baseRecords.map((record) => overrides.find((item) => item.orderId === record.orderId) ?? record);
  const baseIds = new Set(baseRecords.map((record) => record.orderId));
  return [...overrides.filter((record) => !baseIds.has(record.orderId)), ...merged];
}

function requireAdmin<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-11-unauthorized');
  }
  if (!session.user.roles.includes('ADMIN')) {
    return failure(403, '无权限', emptyData, 'mock-11-forbidden');
  }
  return null;
}

function toPage(records: AdminOrderRecord[], query: AdminOrderQuery): AdminOrderPageResult {
  return {
    records,
    total: records.length,
    page: query.page,
    size: query.size
  };
}

function matchQuery(record: AdminOrderRecord, query: AdminOrderQuery) {
  const keyword = query.keyword.trim().toLowerCase();
  const matchesStatus = !query.orderStatus || record.orderStatus === query.orderStatus;
  const matchesKeyword =
    !keyword ||
    record.orderNo.toLowerCase().includes(keyword) ||
    record.orderId.toLowerCase().includes(keyword) ||
    record.elderId.toLowerCase().includes(keyword);
  const dateValue = record.scheduledStart.slice(0, 10);
  const matchesFrom = !query.dateFrom || dateValue >= query.dateFrom;
  const matchesTo = !query.dateTo || dateValue <= query.dateTo;
  return matchesStatus && matchesKeyword && matchesFrom && matchesTo;
}

export function getStageElevenEndpointSummary() {
  return ['GET /api/v1/admin/orders', 'GET /api/v1/admin/orders/{orderId}'];
}

export async function getAdminOrders(
  query: Partial<AdminOrderQuery> = {},
  scenario: AdminOrderScenario = 'normal'
): Promise<ApiResponse<AdminOrderPageResult>> {
  const nextQuery: AdminOrderQuery = { ...defaultQuery, ...query };
  if (isMockEnabled()) {
    const denied = requireAdmin(toPage([], nextQuery));
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return adminOrdersEmptyMock as ApiResponse<AdminOrderPageResult>;
    }
    if (scenario === 'error') {
      return adminOrdersErrorMock as ApiResponse<AdminOrderPageResult>;
    }
    return success(toPage(readRecords().filter((item) => matchQuery(item, nextQuery)), nextQuery), 'mock-11-admin-orders');
  }

  const response = await request<BackendOrderPage>({
    method: 'GET',
    url: adminOrdersPath,
    data: nextQuery,
    mock: adminOrdersMock as ApiResponse<AdminOrderPageResult>
  });
  return response.code === 0
    ? success(fromBackendPage(response.data), response.traceId)
    : (response as unknown as ApiResponse<AdminOrderPageResult>);
}

export async function getAdminOrderDetail(orderId: string): Promise<ApiResponse<AdminOrderPageResult>> {
  if (isMockEnabled()) {
    const denied = requireAdmin(toPage([], defaultQuery));
    if (denied) {
      return denied;
    }
    const found = readRecords().find((item) => item.orderId === orderId);
    if (!found) {
      return failure(404, '数据不存在', toPage([], defaultQuery), 'mock-11-admin-order-not-found');
    }
    return success(toPage([found], defaultQuery), 'mock-11-admin-order-detail');
  }

  const response = await request<BackendOrder>({
    method: 'GET',
    url: adminOrderDetailPath(orderId)
  });
  return response.code === 0
    ? success(toPage([fromBackendOrder(response.data)], defaultQuery), response.traceId)
    : (response as unknown as ApiResponse<AdminOrderPageResult>);
}
