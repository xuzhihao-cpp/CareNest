import serviceItemsEmptyMock from '@/mock/phase-08/service-items-empty.json';
import serviceItemsErrorMock from '@/mock/phase-08/service-items-error.json';
import serviceItemsMock from '@/mock/phase-08/service-items.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  ServiceItemPageResult,
  ServiceItemRequest,
  ServiceItemResponse,
  ServiceItemScenario
} from '@/types/stageEight';

const serviceItemsPath = '/service-items';
const adminServiceItemsPath = '/admin/service-items';
const serviceItemPath = (serviceId: string) => `/service-items/${serviceId}`;
const adminServiceItemPath = (serviceId: string) => `/admin/service-items/${serviceId}`;
const STORAGE_KEY = 'carenest_stage_08_service_items';

function seedRecords(): ServiceItemResponse[] {
  return [...(serviceItemsMock as ApiResponse<ServiceItemPageResult>).data.records];
}

function readRecords(): ServiceItemResponse[] {
  const stored = uni.getStorageSync(STORAGE_KEY);
  return stored ? (stored as ServiceItemResponse[]) : seedRecords();
}

function writeRecords(records: ServiceItemResponse[]) {
  uni.setStorageSync(STORAGE_KEY, records);
}

function toPage(records: ServiceItemResponse[]): ServiceItemPageResult {
  return {
    records,
    total: records.length,
    page: 1,
    size: 10
  };
}

function requireSignedIn<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-8-unauthorized');
  }
  return null;
}

function requireAdmin<T>(emptyData: T): ApiResponse<T> | null {
  const denied = requireSignedIn(emptyData);
  if (denied) {
    return denied;
  }
  const session = readAuthSession();
  if (!session?.user.roles.includes('ADMIN')) {
    return failure(403, '无权限', emptyData, 'mock-8-forbidden');
  }
  return null;
}

function sanitizePayload(payload: ServiceItemRequest): ServiceItemRequest {
  return {
    serviceName: payload.serviceName.trim(),
    category: payload.category.trim(),
    price: Number(payload.price),
    durationMinutes: Number(payload.durationMinutes),
    status: payload.status
  };
}

function validatePayload(payload: ServiceItemRequest) {
  return (
    payload.serviceName.length > 0 &&
    payload.category.length > 0 &&
    Number.isFinite(payload.price) &&
    payload.price > 0 &&
    Number.isInteger(payload.durationMinutes) &&
    payload.durationMinutes > 0 &&
    ['ON_SHELF', 'OFF_SHELF'].includes(payload.status)
  );
}

export function getStageEightEndpointSummary() {
  return [
    'GET /api/v1/service-items',
    'GET /api/v1/service-items/{serviceId}',
    'POST /api/v1/admin/service-items',
    'PUT /api/v1/admin/service-items/{serviceId}'
  ];
}

export async function getServiceItems(
  scenario: ServiceItemScenario = 'normal',
  includeOffShelf = false
): Promise<ApiResponse<ServiceItemPageResult>> {
  if (isMockEnabled()) {
    const denied = requireSignedIn(toPage([]));
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return serviceItemsEmptyMock as ApiResponse<ServiceItemPageResult>;
    }
    if (scenario === 'error') {
      return serviceItemsErrorMock as ApiResponse<ServiceItemPageResult>;
    }
    const records = readRecords().filter((item) => includeOffShelf || item.status === 'ON_SHELF');
    return success(toPage(records), 'mock-8-service-items');
  }

  const response = await request<Array<ServiceItemResponse> | ServiceItemPageResult>({
    method: 'GET',
    url: serviceItemsPath,
    mock: serviceItemsMock as ApiResponse<ServiceItemPageResult>
  });
  if (response.code !== 0) {
    return response as ApiResponse<ServiceItemPageResult>;
  }
  const records = Array.isArray(response.data) ? response.data : response.data.records;
  return success(
    toPage(records),
    response.traceId
  );
}

export async function getServiceItem(serviceId: string): Promise<ApiResponse<ServiceItemResponse>> {
  if (isMockEnabled()) {
    const denied = requireSignedIn({} as ServiceItemResponse);
    if (denied) {
      return denied;
    }
    const found = readRecords().find((item) => item.serviceId === serviceId);
    if (!found) {
      return failure(404, '数据不存在', {} as ServiceItemResponse, 'mock-8-service-not-found');
    }
    return success(found, 'mock-8-service-item');
  }

  return request<ServiceItemResponse>({
    method: 'GET',
    url: serviceItemPath(serviceId)
  });
}

export async function createServiceItem(payload: ServiceItemRequest): Promise<ApiResponse<ServiceItemResponse>> {
  if (isMockEnabled()) {
    const denied = requireAdmin({} as ServiceItemResponse);
    if (denied) {
      return denied;
    }
    const nextPayload = sanitizePayload(payload);
    if (!validatePayload(nextPayload)) {
      return failure(422, '业务规则不满足', {} as ServiceItemResponse, 'mock-8-service-invalid');
    }
    const records = readRecords();
    const created: ServiceItemResponse = {
      ...nextPayload,
      serviceId: `service-${String(records.length + 1).padStart(3, '0')}`
    };
    writeRecords([created, ...records]);
    return success(created, 'mock-8-service-create');
  }

  return request<ServiceItemResponse>({
    method: 'POST',
    url: adminServiceItemsPath,
    data: payload
  });
}

export async function updateServiceItem(
  serviceId: string,
  payload: ServiceItemRequest
): Promise<ApiResponse<ServiceItemResponse>> {
  if (isMockEnabled()) {
    const denied = requireAdmin({} as ServiceItemResponse);
    if (denied) {
      return denied;
    }
    const nextPayload = sanitizePayload(payload);
    if (!validatePayload(nextPayload)) {
      return failure(422, '业务规则不满足', {} as ServiceItemResponse, 'mock-8-service-invalid');
    }
    const records = readRecords();
    const index = records.findIndex((item) => item.serviceId === serviceId);
    if (index < 0) {
      return failure(404, '数据不存在', {} as ServiceItemResponse, 'mock-8-service-update-not-found');
    }
    const updated: ServiceItemResponse = {
      ...records[index],
      ...nextPayload
    };
    records.splice(index, 1, updated);
    writeRecords(records);
    return success(updated, 'mock-8-service-update');
  }

  return request<ServiceItemResponse>({
    method: 'PUT',
    url: adminServiceItemPath(serviceId),
    data: payload
  });
}

export function resetStageEightMockRecords() {
  writeRecords(seedRecords());
}
