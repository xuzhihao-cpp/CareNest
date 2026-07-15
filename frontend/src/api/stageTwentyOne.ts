import { failure, request } from '@/api/client';
import type { ApiResponse, PageResult } from '@/types/api';
import type { MedicalFileType } from '@/types/stageTwenty';
import type {
  AdminMedicalFileDetail,
  AdminMedicalFilePageResult,
  AdminMedicalFileQuery,
  AdminMedicalFileRecord,
  MedicalFileExtractedItem,
  MedicalFileReviewRequest,
  MedicalFileReviewResult,
  MedicalFileReviewStatusWire
} from '@/types/stageTwentyOne';
import { normalizeReviewStatus } from '@/utils/stageTwentyOneRules';

const adminMedicalFilesPath = '/admin/medical-files';
const adminMedicalFileDetailPath = (fileId: string) =>
  `${adminMedicalFilesPath}/${encodeURIComponent(fileId)}`;
const adminMedicalFileReviewPath = (fileId: string) =>
  `${adminMedicalFileDetailPath(fileId)}/review`;

interface ReviewPermissionWireResponse {
  roleCode?: string;
  permissions?: unknown;
}

interface MedicalFileWireRecord {
  medicalFileId?: string;
  fileId?: string;
  elderId?: string;
  elderName?: string;
  fileType?: MedicalFileType;
  title?: string;
  occurredAt?: string;
  createdAt?: string;
  uploadedAt?: string;
  auditStatus: MedicalFileReviewStatusWire;
  reviewComment?: string;
  auditOpinion?: string;
  reviewedAt?: string;
  uploaderName?: string;
}

interface MedicalFileWireDetail extends MedicalFileWireRecord {
  originalName?: string;
  originalFileName?: string;
  mimeType?: string;
  size?: number;
  fileSize?: number;
  previewUrl?: string;
  downloadUrl?: string;
  extractedItems?: MedicalFileExtractedItem[];
}

interface MedicalFileReviewWireResult {
  fileId: string;
  auditStatus: MedicalFileReviewStatusWire;
  reviewedAt: string;
}

function normalizeRecord(record: MedicalFileWireRecord): AdminMedicalFileRecord | null {
  const auditStatus = normalizeReviewStatus(record.auditStatus);
  const fileId = record.fileId?.trim() || record.medicalFileId?.trim();
  if (!fileId || !record.fileType || !auditStatus) return null;
  return {
    medicalFileId: record.medicalFileId?.trim() || fileId,
    fileId,
    elderId: record.elderId ?? '',
    elderName: record.elderName?.trim() || undefined,
    fileType: record.fileType,
    title: record.title?.trim() || '未命名病历资料',
    occurredAt: record.occurredAt,
    createdAt: record.createdAt || record.uploadedAt || undefined,
    auditStatus,
    reviewComment: record.reviewComment ?? record.auditOpinion,
    reviewedAt: record.reviewedAt,
    uploaderName: record.uploaderName
  };
}

function normalizeDetail(record: MedicalFileWireDetail): AdminMedicalFileDetail | null {
  const base = normalizeRecord(record);
  if (!base) return null;
  return {
    ...base,
    originalName: record.originalName ?? record.originalFileName,
    mimeType: record.mimeType,
    size: record.size ?? record.fileSize,
    previewUrl: record.previewUrl,
    downloadUrl: record.downloadUrl,
    extractedItems: record.extractedItems ?? []
  };
}

function toBackendQuery(query: AdminMedicalFileQuery) {
  return {
    page: query.page,
    size: query.size,
    ...(query.auditStatus ? { auditStatus: query.auditStatus } : {})
  };
}

export async function getMedicalFileReviewPermissions(): Promise<ApiResponse<string[]>> {
  const response = await request<ReviewPermissionWireResponse>({
    method: 'GET',
    url: '/auth/permissions'
  });
  if (response.code !== 0) return { ...response, data: [] };
  if (!response.data || !Array.isArray(response.data.permissions)
    || response.data.permissions.some((permission) => typeof permission !== 'string')) {
    return failure(502, '账号权限响应不完整', [], response.traceId);
  }
  return { ...response, data: response.data.permissions };
}

export async function getAdminMedicalFiles(
  query: AdminMedicalFileQuery
): Promise<ApiResponse<AdminMedicalFilePageResult>> {
  const response = await request<PageResult<MedicalFileWireRecord>>({
    method: 'GET',
    url: adminMedicalFilesPath,
    data: toBackendQuery(query)
  });
  if (response.code !== 0) return { ...response, data: { records: [], total: 0, page: query.page, size: query.size } };
  if (!response.data || !Array.isArray(response.data.records)
    || !Number.isFinite(response.data.total)
    || !Number.isFinite(response.data.page)
    || !Number.isFinite(response.data.size)) {
    return failure(502, '病历资料列表响应不完整', { records: [], total: 0, page: query.page, size: query.size }, response.traceId);
  }
  const records = response.data.records.map(normalizeRecord);
  if (records.some((record) => record === null)) {
    return failure(502, '病历资料列表响应不完整', { records: [], total: 0, page: query.page, size: query.size }, response.traceId);
  }
  return {
    ...response,
    data: {
      records: records as AdminMedicalFileRecord[],
      total: response.data.total,
      page: response.data.page,
      size: response.data.size
    }
  };
}

export async function getAdminMedicalFileDetail(
  fileId: string
): Promise<ApiResponse<AdminMedicalFileDetail>> {
  const response = await request<MedicalFileWireDetail>({
    method: 'GET',
    url: adminMedicalFileDetailPath(fileId)
  });
  if (response.code !== 0) return { ...response, data: {} as AdminMedicalFileDetail };
  const detail = normalizeDetail(response.data);
  return detail
    ? { ...response, data: detail }
    : failure(502, '病历资料详情响应不完整', {} as AdminMedicalFileDetail, response.traceId);
}

export async function reviewAdminMedicalFile(
  fileId: string,
  payload: MedicalFileReviewRequest
): Promise<ApiResponse<MedicalFileReviewResult>> {
  const response = await request<MedicalFileReviewWireResult>({
    method: 'POST',
    url: adminMedicalFileReviewPath(fileId),
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as MedicalFileReviewResult };
  const auditStatus = normalizeReviewStatus(response.data.auditStatus);
  if (!response.data.fileId?.trim() || !response.data.reviewedAt || !auditStatus) {
    return failure(502, '病历资料审核响应不完整', {} as MedicalFileReviewResult, response.traceId);
  }
  return { ...response, data: { ...response.data, auditStatus } };
}
