import familyBindingsEmptyMock from '@/mock/phase-06/family-bindings-empty.json';
import familyBindingsErrorMock from '@/mock/phase-06/family-bindings-error.json';
import familyBindingsMock from '@/mock/phase-06/family-bindings.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  BindingListResult,
  BindingRequest,
  BindingResponse,
  BindingScenario,
  BindingScopeUpdateRequest
} from '@/types/stageSix';

type BindingMockData = BindingListResult | { records: BindingResponse[] };
type BindingMockResponse = ApiResponse<BindingMockData>;

const familyBindingsPath = '/family/bindings';
const elderBindingsPath = '/elder/bindings';
const elderApprovePath = (bindingId: string) => `/elder/bindings/${bindingId}/approve`;
const familyScopesPath = (bindingId: string) => `/family/bindings/${bindingId}/scopes`;
const familyRevokePath = (bindingId: string) => `/family/bindings/${bindingId}/revoke`;

function readMockBindings(mock: BindingMockResponse): BindingResponse[] {
  const data = mock.data;
  return Array.isArray(data) ? data : data.records;
}

function normalizeMockResponse(mock: BindingMockResponse): ApiResponse<BindingListResult> {
  return { ...mock, data: readMockBindings(mock) };
}

let bindingRecords: BindingResponse[] = readMockBindings(familyBindingsMock as BindingMockResponse);

function requireFamily<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-6-unauthorized');
  }
  if (!session.user.roles.includes('FAMILY')) {
    return failure(403, '无权限', emptyData, 'mock-6-forbidden');
  }
  return null;
}

function requireElder<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-6-unauthorized');
  }
  if (!session.user.roles.includes('ELDER')) {
    return failure(403, '无权限', emptyData, 'mock-6-forbidden');
  }
  return null;
}

export function getStageSixEndpointSummary() {
  return [
    'POST /api/v1/family/bindings',
    'GET /api/v1/family/bindings',
    'GET /api/v1/elder/bindings',
    'POST /api/v1/elder/bindings/{bindingId}/approve',
    'PUT /api/v1/family/bindings/{bindingId}/scopes',
    'POST /api/v1/family/bindings/{bindingId}/revoke'
  ];
}

export async function getElderBindings(scenario: BindingScenario = 'normal'): Promise<ApiResponse<BindingListResult>> {
  if (isMockEnabled()) {
    const denied = requireElder<BindingListResult>([]);
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return normalizeMockResponse(familyBindingsEmptyMock as BindingMockResponse);
    }
    if (scenario === 'error') {
      return normalizeMockResponse(familyBindingsErrorMock as BindingMockResponse);
    }
    const session = readAuthSession();
    const elderId = session?.user.userId.replace(/^elder-/, 'elder_');
    return success(
      bindingRecords.filter((binding) => binding.elderId === elderId),
      'mock-6-elder-bindings'
    );
  }

  return request<BindingListResult>({
    method: 'GET',
    url: elderBindingsPath
  });
}

export function getStageSixBindingSnapshot(): BindingResponse[] {
  return bindingRecords;
}

export async function getFamilyBindings(scenario: BindingScenario = 'normal'): Promise<ApiResponse<BindingListResult>> {
  if (isMockEnabled()) {
    const denied = requireFamily<BindingListResult>([]);
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return normalizeMockResponse(familyBindingsEmptyMock as BindingMockResponse);
    }
    if (scenario === 'error') {
      return normalizeMockResponse(familyBindingsErrorMock as BindingMockResponse);
    }
    return success(bindingRecords, 'mock-6-family-bindings');
  }

  return request<BindingListResult>({
    method: 'GET',
    url: familyBindingsPath
  });
}

export async function createFamilyBinding(payload: BindingRequest): Promise<ApiResponse<BindingResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as BindingResponse);
    if (denied) {
      return denied;
    }
    if (!payload.elderInviteCode || payload.scopeCodes.length === 0) {
      return failure(422, '业务规则不满足', {} as BindingResponse, 'mock-6-create-invalid');
    }
    const created: BindingResponse = {
      bindingId: `binding-${String(bindingRecords.length + 1).padStart(3, '0')}`,
      elderId: `elder-${String(bindingRecords.length + 1).padStart(3, '0')}`,
      elderName: '张爷爷',
      relationType: payload.relationType,
      bindingStatus: 'PENDING',
      scopeCodes: payload.scopeCodes,
      pendingScopeCodes: [],
      scopeUpdatePending: false
    };
    bindingRecords = [created, ...bindingRecords];
    return success(created, 'mock-6-family-bindings-create');
  }

  return request<BindingResponse>({
    method: 'POST',
    url: familyBindingsPath,
    data: payload
  });
}

export async function approveElderBinding(
  bindingId: string,
  payload: BindingRequest
): Promise<ApiResponse<BindingResponse>> {
  if (isMockEnabled()) {
    const denied = requireElder({} as BindingResponse);
    if (denied) {
      return denied;
    }
    const record = bindingRecords.find((item) => item.bindingId === bindingId);
    if (!record) {
      return failure(404, '数据不存在', {} as BindingResponse, 'mock-6-approve-not-found');
    }
    if (record.bindingStatus !== 'PENDING') {
      return failure(409, '状态冲突', record, 'mock-6-approve-conflict');
    }
    record.bindingStatus = 'ACTIVE';
    return success(record, 'mock-6-elder-bindings-approve');
  }

  return request<BindingResponse>({
    method: 'POST',
    url: elderApprovePath(bindingId),
    data: payload
  });
}

export async function updateFamilyBindingScopes(
  bindingId: string,
  payload: BindingScopeUpdateRequest
): Promise<ApiResponse<BindingResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as BindingResponse);
    if (denied) {
      return denied;
    }
    const record = bindingRecords.find((item) => item.bindingId === bindingId);
    if (!record) {
      return failure(404, '数据不存在', {} as BindingResponse, 'mock-6-scopes-not-found');
    }
    if (record.bindingStatus !== 'ACTIVE') {
      return failure(409, '状态冲突', record, 'mock-6-scopes-conflict');
    }
    record.scopeCodes = payload.scopeCodes;
    record.relationType = payload.relationType;
    return success(record, 'mock-6-family-bindings-scopes');
  }

  return request<BindingResponse>({
    method: 'PUT',
    url: familyScopesPath(bindingId),
    data: payload
  });
}

export async function revokeFamilyBinding(bindingId: string, source: BindingRequest): Promise<ApiResponse<BindingResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as BindingResponse);
    if (denied) {
      return denied;
    }
    const record = bindingRecords.find((item) => item.bindingId === bindingId);
    if (!record) {
      return failure(404, '数据不存在', {} as BindingResponse, 'mock-6-revoke-not-found');
    }
    if (record.bindingStatus === 'REVOKED') {
      return failure(409, '状态冲突', record, 'mock-6-revoke-conflict');
    }
    record.relationType = source.relationType;
    record.scopeCodes = source.scopeCodes;
    record.bindingStatus = 'REVOKED';
    return success(record, 'mock-6-family-bindings-revoke');
  }

  return request<BindingResponse>({
    method: 'POST',
    url: familyRevokePath(bindingId),
    data: source
  });
}

export function resetStageSixMockRecords() {
  bindingRecords = readMockBindings(familyBindingsMock as BindingMockResponse);
}
