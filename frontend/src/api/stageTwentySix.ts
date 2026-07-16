import { failure, getApiBase, readAuthSession, request } from '@/api/client';
import { uploadMedicalFileAsset } from '@/api/stageTwenty';
import type { ApiResponse } from '@/types/api';
import type { FileAssetUploadResult } from '@/types/stageTwenty';
import type {
  QualificationApplicationPage,
  QualificationApplicationRecord,
  QualificationApplicationSubmitRequest,
  QualificationApplicationSubmitResult,
  QualificationAuditStatus,
  QualificationPermissionContext,
  QualificationSkillOption,
  QualificationStatusFilter,
  QualificationTrainingOverview
} from '@/types/stageTwentySix';
import {
  normalizeQualificationApplication,
  normalizeQualificationAuditStatus,
  normalizeQualificationSkillOptions,
  QUALIFICATION_SKILL_DICTIONARY_CODE
} from '@/utils/stageTwentySixRules';
import { normalizeTrainingDisplayStatus } from '@/utils/stageTwentyEightRules';

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

export async function getQualificationPermissions(): Promise<ApiResponse<QualificationPermissionContext>> {
  const response = await request<unknown>({ method: 'GET', url: '/auth/permissions' });
  if (response.code !== 0) return { ...response, data: { roleCode: '', permissions: [] } };
  if (!isRecord(response.data)) return failure(502, '权限信息响应不完整', { roleCode: '', permissions: [] }, response.traceId);
  const permissions = Array.isArray(response.data.permissions)
    ? response.data.permissions
    : Array.isArray(response.data.permissionCodes) ? response.data.permissionCodes : null;
  if (!permissions || permissions.some((item) => typeof item !== 'string')) {
    return failure(502, '权限信息响应不完整', { roleCode: '', permissions: [] }, response.traceId);
  }
  return {
    ...response,
    data: {
      roleCode: stringValue(response.data.roleCode),
      permissions: Array.from(new Set((permissions as string[]).map((item) => item.trim()).filter(Boolean)))
    }
  };
}

export async function getQualificationSkillOptions(): Promise<ApiResponse<QualificationSkillOption[]>> {
  const response = await request<unknown>({
    method: 'GET',
    url: `/dictionaries/${encodeURIComponent(QUALIFICATION_SKILL_DICTIONARY_CODE)}`
  });
  if (response.code !== 0) return { ...response, data: [] };
  const options = normalizeQualificationSkillOptions(response.data);
  if (!options) return failure(502, '护理技能字典响应不完整', [], response.traceId);
  return { ...response, data: options };
}

export function uploadQualificationFile(
  filePath: string,
  onProgress: (progress: number) => void
): Promise<ApiResponse<FileAssetUploadResult>> {
  return uploadMedicalFileAsset(filePath, onProgress);
}

export async function submitQualificationApplication(
  payload: QualificationApplicationSubmitRequest
): Promise<ApiResponse<QualificationApplicationSubmitResult>> {
  const response = await request<unknown>({
    method: 'POST',
    url: '/nurse/qualification-applications',
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as QualificationApplicationSubmitResult };
  if (!isRecord(response.data)) return failure(502, '资质申请提交响应不完整', {} as QualificationApplicationSubmitResult, response.traceId);
  const applicationId = stringValue(response.data.applicationId);
  const auditStatus = normalizeQualificationAuditStatus(response.data.auditStatus);
  if (!applicationId || !auditStatus) return failure(502, '资质申请提交响应不完整', {} as QualificationApplicationSubmitResult, response.traceId);
  return { ...response, data: { applicationId, auditStatus } };
}

export async function getCurrentQualificationApplication(): Promise<ApiResponse<QualificationApplicationRecord>> {
  const response = await request<unknown>({ method: 'GET', url: '/nurse/qualification-applications/current' });
  if (response.code !== 0) return { ...response, data: {} as QualificationApplicationRecord };
  const record = normalizeQualificationApplication(response.data);
  if (!record) return failure(502, '当前资质申请响应不完整', {} as QualificationApplicationRecord, response.traceId);
  return { ...response, data: record };
}

export async function getQualificationApplications(input: {
  auditStatus: QualificationStatusFilter;
  page: number;
  size: number;
}): Promise<ApiResponse<QualificationApplicationPage>> {
  const query = [
    input.auditStatus === 'ALL' ? '' : `auditStatus=${encodeURIComponent(input.auditStatus)}`,
    `page=${input.page}`,
    `size=${input.size}`
  ].filter(Boolean).join('&');
  const response = await request<unknown>({
    method: 'GET',
    url: `/admin/nurse-qualification-applications?${query}`
  });
  if (response.code !== 0) return { ...response, data: { records: [], total: 0, page: input.page, size: input.size } };
  if (!isRecord(response.data) || !Array.isArray(response.data.records)) {
    return failure(502, '资质申请列表响应不完整', { records: [], total: 0, page: input.page, size: input.size }, response.traceId);
  }
  const records = response.data.records.map(normalizeQualificationApplication);
  const total = response.data.total;
  const page = response.data.page;
  const size = response.data.size;
  if (records.some((record) => record === null) || typeof total !== 'number' || typeof page !== 'number' || typeof size !== 'number') {
    return failure(502, '资质申请列表响应不完整', { records: [], total: 0, page: input.page, size: input.size }, response.traceId);
  }
  return { ...response, data: { records: records as QualificationApplicationRecord[], total, page, size } };
}

export async function getQualificationCertificatePreview(
  applicationId: string,
  fileId: string,
  signal?: AbortSignal
): Promise<{ code: number; blob: Blob | null }> {
  const path = `/admin/nurse-qualification-applications/${encodeURIComponent(applicationId)}/files/${encodeURIComponent(fileId)}/preview`;
  const target = new URL(`${getApiBase()}${path}`, window.location.origin);
  const session = readAuthSession();
  try {
    const response = await fetch(target.toString(), {
      headers: session ? { Authorization: `Bearer ${session.token}` } : {},
      signal
    });
    if (!response.ok) return { code: response.status, blob: null };
    return { code: 0, blob: await response.blob() };
  } catch {
    return { code: signal?.aborted ? 499 : 500, blob: null };
  }
}

export async function getQualificationTrainingOverview(): Promise<ApiResponse<QualificationTrainingOverview>> {
  const response = await request<unknown>({ method: 'GET', url: '/nurse/training-status' });
  if (response.code !== 0) return { ...response, data: {} as QualificationTrainingOverview };
  if (!isRecord(response.data)) return failure(502, '培训概览响应不完整', {} as QualificationTrainingOverview, response.traceId);
  const nurseId = stringValue(response.data.nurseId);
  const qualificationStatus = normalizeQualificationAuditStatus(response.data.qualificationStatus);
  const nurseName = stringValue(response.data.nurseName);
  const trainingStatus = normalizeTrainingDisplayStatus(response.data.trainingStatus);
  const trainingBatch = stringValue(response.data.trainingBatch);
  const passedAt = stringValue(response.data.passedAt);
  const expiredAt = stringValue(response.data.expiredAt);
  if (!nurseId || !nurseName || !qualificationStatus || !trainingStatus || !trainingBatch
      || ((trainingStatus === 'APPROVED' || trainingStatus === 'EXPIRED') && (!passedAt || !expiredAt))) {
    return failure(502, '培训概览响应不完整', {} as QualificationTrainingOverview, response.traceId);
  }
  return {
    ...response,
    data: {
      nurseName,
      qualificationStatus,
      trainingStatus,
      trainingBatch,
      passedAt,
      expiredAt,
      remark: stringValue(response.data.remark)
    }
  };
}
