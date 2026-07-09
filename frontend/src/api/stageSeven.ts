import familyEldersEmptyMock from '@/mock/phase-07/family-elders-empty.json';
import familyEldersErrorMock from '@/mock/phase-07/family-elders-error.json';
import familyEldersMock from '@/mock/phase-07/family-elders.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  ElderProfileDetail,
  ElderProfileRequest,
  ElderProfileResponse,
  ElderProfileScenario,
  FamilyElderPageResult
} from '@/types/stageSeven';

const familyEldersPath = '/family/elders';
const elderProfilePath = (elderId: string) => `/elders/${elderId}/profile`;

let elderProfiles: ElderProfileDetail[] = [
  ...(familyEldersMock as ApiResponse<FamilyElderPageResult>).data.records
];

function toPage(records: ElderProfileDetail[]): FamilyElderPageResult {
  return {
    records,
    total: records.length,
    page: 1,
    size: 10
  };
}

function requireFamily<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-7-unauthorized');
  }
  if (!session.user.roles.includes('FAMILY')) {
    return failure(403, '无权限', emptyData, 'mock-7-forbidden');
  }
  return null;
}

function requireElderOrFamily<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-7-unauthorized');
  }
  if (!session.user.roles.includes('ELDER') && !session.user.roles.includes('FAMILY')) {
    return failure(403, '无权限', emptyData, 'mock-7-forbidden');
  }
  return null;
}

export function getStageSevenEndpointSummary() {
  return [
    'GET /api/v1/elders/{elderId}/profile',
    'PUT /api/v1/elders/{elderId}/profile',
    'GET /api/v1/family/elders'
  ];
}

export async function getFamilyElders(
  scenario: ElderProfileScenario = 'normal'
): Promise<ApiResponse<FamilyElderPageResult>> {
  if (isMockEnabled()) {
    const denied = requireFamily(toPage([]));
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return familyEldersEmptyMock as ApiResponse<FamilyElderPageResult>;
    }
    if (scenario === 'error') {
      return familyEldersErrorMock as ApiResponse<FamilyElderPageResult>;
    }
    return success(toPage(elderProfiles), 'mock-7-family-elders');
  }

  return request<FamilyElderPageResult>({
    method: 'GET',
    url: familyEldersPath,
    mock: familyEldersMock as ApiResponse<FamilyElderPageResult>,
    mockFallback: true
  });
}

export async function getElderProfile(elderId: string): Promise<ApiResponse<ElderProfileDetail>> {
  if (isMockEnabled()) {
    const denied = requireElderOrFamily({} as ElderProfileDetail);
    if (denied) {
      return denied;
    }
    const found = elderProfiles.find((item) => item.elderId === elderId);
    if (!found) {
      return failure(404, '数据不存在', {} as ElderProfileDetail, 'mock-7-profile-not-found');
    }
    return success(found, 'mock-7-elder-profile');
  }

  return request<ElderProfileDetail>({
    method: 'GET',
    url: elderProfilePath(elderId)
  });
}

export async function updateElderProfile(
  elderId: string,
  payload: ElderProfileRequest
): Promise<ApiResponse<ElderProfileResponse>> {
  if (isMockEnabled()) {
    const denied = requireFamily({} as ElderProfileResponse);
    if (denied) {
      return denied;
    }
    if (!payload.name || !payload.birthDate || payload.emergencyContacts.length === 0) {
      return failure(422, '业务规则不满足', {} as ElderProfileResponse, 'mock-7-profile-invalid');
    }
    const found = elderProfiles.find((item) => item.elderId === elderId);
    if (!found) {
      return failure(404, '数据不存在', {} as ElderProfileResponse, 'mock-7-profile-update-not-found');
    }
    Object.assign(found, payload, { profileVersion: found.profileVersion + 1 });
    return success(
      {
        elderId: found.elderId,
        profileVersion: found.profileVersion
      },
      'mock-7-elder-profile-update'
    );
  }

  return request<ElderProfileResponse>({
    method: 'PUT',
    url: elderProfilePath(elderId),
    data: payload
  });
}

export function resetStageSevenMockRecords() {
  elderProfiles = [...(familyEldersMock as ApiResponse<FamilyElderPageResult>).data.records];
}

