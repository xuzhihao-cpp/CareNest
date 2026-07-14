import serviceAddressesEmptyMock from '@/mock/phase-09/service-addresses-empty.json';
import serviceAddressesErrorMock from '@/mock/phase-09/service-addresses-error.json';
import serviceAddressesMock from '@/mock/phase-09/service-addresses.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  ServiceAddressDetail,
  ServiceAddressListResult,
  ServiceAddressRequest,
  ServiceAddressResponse,
  ServiceAddressScenario
} from '@/types/stageNine';

const elderServiceAddressesPath = (elderId: string) => `/elders/${elderId}/service-addresses`;
const serviceAddressPath = (addressId: string) => `/service-addresses/${addressId}`;
const STORAGE_KEY = 'carenest_stage_09_service_addresses';

function seedDetails(): ServiceAddressDetail[] {
  return [
    {
      elderId: 'elder-001',
      addressId: 'address-001',
      contactName: '张小明',
      contactPhone: '13800000002',
      regionCode: '310101',
      detailAddress: '人民路100号1单元201',
      fullAddress: '310101 人民路100号1单元201',
      isDefault: true
    },
    {
      elderId: 'elder-001',
      addressId: 'address-002',
      contactName: '张小敏',
      contactPhone: '13800001111',
      regionCode: '310104',
      detailAddress: '康复路88号3号楼502',
      fullAddress: '310104 康复路88号3号楼502',
      isDefault: false
    },
    {
      elderId: 'elder-002',
      addressId: 'address-003',
      contactName: '李芳',
      contactPhone: '13700003333',
      regionCode: '310105',
      detailAddress: '长宁路66号2单元301',
      fullAddress: '310105 长宁路66号2单元301',
      isDefault: true
    }
  ];
}

function readDetails(): ServiceAddressDetail[] {
  const stored = uni.getStorageSync(STORAGE_KEY);
  return stored ? (stored as ServiceAddressDetail[]) : seedDetails();
}

function writeDetails(records: ServiceAddressDetail[]) {
  uni.setStorageSync(STORAGE_KEY, records);
}

function toResponse(record: ServiceAddressDetail): ServiceAddressResponse {
  return {
    addressId: record.addressId,
    contactName: record.contactName,
    contactPhone: record.contactPhone,
    regionCode: record.regionCode,
    detailAddress: record.detailAddress,
    fullAddress: record.fullAddress,
    isDefault: record.isDefault
  };
}

function toList(records: ServiceAddressDetail[]): ServiceAddressListResult {
  return records.map(toResponse);
}

function buildFullAddress(payload: ServiceAddressRequest) {
  return `${payload.regionCode.trim()} ${payload.detailAddress.trim()}`;
}

function sanitizePayload(payload: ServiceAddressRequest): ServiceAddressRequest {
  return {
    contactName: payload.contactName.trim(),
    contactPhone: payload.contactPhone.trim(),
    regionCode: payload.regionCode.trim(),
    detailAddress: payload.detailAddress.trim(),
    isDefault: Boolean(payload.isDefault)
  };
}

function validatePayload(payload: ServiceAddressRequest) {
  return (
    payload.contactName.length > 0 &&
    payload.contactPhone.length > 0 &&
    payload.regionCode.length > 0 &&
    payload.detailAddress.length > 0
  );
}

function requireFamily<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-9-unauthorized');
  }
  if (!session.user.roles.includes('FAMILY')) {
    return failure(403, '无权限', emptyData, 'mock-9-forbidden');
  }
  return null;
}

function normalizeDefault(records: ServiceAddressDetail[], elderId: string, defaultAddressId: string) {
  records.forEach((item) => {
    if (item.elderId === elderId) {
      item.isDefault = item.addressId === defaultAddressId;
    }
  });
}

export function getStageNineEndpointSummary() {
  return [
    'GET /api/v1/elders/{elderId}/service-addresses',
    'POST /api/v1/elders/{elderId}/service-addresses',
    'PUT /api/v1/service-addresses/{addressId}',
    'DELETE /api/v1/service-addresses/{addressId}'
  ];
}

export async function getServiceAddresses(
  elderId: string,
  scenario: ServiceAddressScenario = 'normal'
): Promise<ApiResponse<ServiceAddressListResult>> {
  if (isMockEnabled()) {
    const denied = requireFamily<ServiceAddressListResult>([]);
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return serviceAddressesEmptyMock as ApiResponse<ServiceAddressListResult>;
    }
    if (scenario === 'error') {
      return serviceAddressesErrorMock as ApiResponse<ServiceAddressListResult>;
    }
    return success(toList(readDetails().filter((item) => item.elderId === elderId)), 'mock-9-service-addresses');
  }

  return request<ServiceAddressListResult>({
    method: 'GET',
    url: elderServiceAddressesPath(elderId)
  });
}

export async function createServiceAddress(
  elderId: string,
  payload: ServiceAddressRequest
): Promise<ApiResponse<ServiceAddressResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as ServiceAddressResponse);
    if (denied) {
      return denied;
    }
    const nextPayload = sanitizePayload(payload);
    if (!validatePayload(nextPayload)) {
      return failure(422, '业务规则不满足', {} as ServiceAddressResponse, 'mock-9-address-invalid');
    }
    const records = readDetails();
    const created: ServiceAddressDetail = {
      ...nextPayload,
      elderId,
      addressId: `address-${String(records.length + 1).padStart(3, '0')}`,
      fullAddress: buildFullAddress(nextPayload)
    };
    if (created.isDefault || records.filter((item) => item.elderId === elderId).length === 0) {
      created.isDefault = true;
      normalizeDefault(records, elderId, created.addressId);
    }
    writeDetails([created, ...records]);
    return success(toResponse(created), 'mock-9-address-create');
  }

  return request<ServiceAddressResponse>({
    method: 'POST',
    url: elderServiceAddressesPath(elderId),
    data: payload
  });
}

export async function updateServiceAddress(
  addressId: string,
  payload: ServiceAddressRequest
): Promise<ApiResponse<ServiceAddressResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as ServiceAddressResponse);
    if (denied) {
      return denied;
    }
    const nextPayload = sanitizePayload(payload);
    if (!validatePayload(nextPayload)) {
      return failure(422, '业务规则不满足', {} as ServiceAddressResponse, 'mock-9-address-invalid');
    }
    const records = readDetails();
    const index = records.findIndex((item) => item.addressId === addressId);
    if (index < 0) {
      return failure(404, '数据不存在', {} as ServiceAddressResponse, 'mock-9-address-update-not-found');
    }
    const updated: ServiceAddressDetail = {
      ...records[index],
      ...nextPayload,
      fullAddress: buildFullAddress(nextPayload)
    };
    records.splice(index, 1, updated);
    if (updated.isDefault) {
      normalizeDefault(records, updated.elderId, updated.addressId);
    }
    writeDetails(records);
    return success(toResponse(updated), 'mock-9-address-update');
  }

  return request<ServiceAddressResponse>({
    method: 'PUT',
    url: serviceAddressPath(addressId),
    data: payload
  });
}

export async function deleteServiceAddress(addressId: string): Promise<ApiResponse<ServiceAddressResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as ServiceAddressResponse);
    if (denied) {
      return denied;
    }
    const records = readDetails();
    const index = records.findIndex((item) => item.addressId === addressId);
    if (index < 0) {
      return failure(404, '数据不存在', {} as ServiceAddressResponse, 'mock-9-address-delete-not-found');
    }
    const [removed] = records.splice(index, 1);
    if (removed.isDefault) {
      const nextDefault = records.find((item) => item.elderId === removed.elderId);
      if (nextDefault) {
        normalizeDefault(records, removed.elderId, nextDefault.addressId);
      }
    }
    writeDetails(records);
    return success(toResponse(removed), 'mock-9-address-delete');
  }

  return request<ServiceAddressResponse>({
    method: 'DELETE',
    url: serviceAddressPath(addressId)
  });
}

export function resetStageNineMockRecords() {
  writeDetails(seedDetails());
}
