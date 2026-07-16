import { request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  AdminOrderPageResult,
  AdminOrderQuery,
  AdminOrderRecord,
  AdminOrderScenario
} from '@/types/stageEleven';

const adminOrdersPath = '/admin/orders';
const adminOrderDetailPath = (orderId: string) => `/admin/orders/${encodeURIComponent(orderId)}`;

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

function toDetailPage(record: BackendOrder): AdminOrderPageResult {
  return { records: [fromBackendOrder(record)], total: 1, page: 1, size: 1 };
}

export function getStageElevenEndpointSummary() {
  return ['GET /api/v1/admin/orders', 'GET /api/v1/admin/orders/{orderId}'];
}

export async function getAdminOrders(
  query: Partial<AdminOrderQuery> = {},
  _scenario: AdminOrderScenario = 'normal'
): Promise<ApiResponse<AdminOrderPageResult>> {
  const nextQuery: AdminOrderQuery = { ...defaultQuery, ...query };
  const response = await request<BackendOrderPage>({
    method: 'GET',
    url: adminOrdersPath,
    data: nextQuery
  });
  return response.code === 0
    ? success(fromBackendPage(response.data), response.traceId)
    : (response as unknown as ApiResponse<AdminOrderPageResult>);
}

export async function getAdminOrderDetail(orderId: string): Promise<ApiResponse<AdminOrderPageResult>> {
  const response = await request<BackendOrder>({
    method: 'GET',
    url: adminOrderDetailPath(orderId)
  });
  return response.code === 0
    ? success(toDetailPage(response.data), response.traceId)
    : (response as unknown as ApiResponse<AdminOrderPageResult>);
}
