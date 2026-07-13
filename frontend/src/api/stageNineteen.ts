import { request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  HealthArchiveResponse,
  HealthArchiveUpdateRequest,
  HealthArchiveUpdateResult,
  MedicationCreateRequest,
  MedicationCreateResult
} from '@/types/stageNineteen';

const healthArchivePath = (elderId: string) =>
  `/elders/${encodeURIComponent(elderId)}/health-archive`;

const medicationsPath = (elderId: string) =>
  `/elders/${encodeURIComponent(elderId)}/medications`;

export function resolveElderResourceId(userId: string) {
  return userId
    .replace(/^user_/, '')
    .replace(/^elder-/, 'elder_');
}

export function getHealthArchive(elderId: string): Promise<ApiResponse<HealthArchiveResponse>> {
  return request<HealthArchiveResponse>({
    method: 'GET',
    url: healthArchivePath(elderId)
  });
}

export function updateHealthArchive(
  elderId: string,
  payload: HealthArchiveUpdateRequest
): Promise<ApiResponse<HealthArchiveUpdateResult>> {
  return request<HealthArchiveUpdateResult>({
    method: 'PUT',
    url: healthArchivePath(elderId),
    data: payload
  });
}

// The full archive editor always saves through PUT for one version-checked transaction.
// This endpoint is reserved for a separate quick-add interaction and is intentionally
// not mixed into the full editor save flow.
export function quickAddHealthArchiveMedication(
  elderId: string,
  payload: MedicationCreateRequest
): Promise<ApiResponse<MedicationCreateResult>> {
  return request<MedicationCreateResult>({
    method: 'POST',
    url: medicationsPath(elderId),
    data: payload
  });
}
