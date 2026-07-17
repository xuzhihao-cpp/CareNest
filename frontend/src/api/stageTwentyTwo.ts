import { failure, getApiBase, readAuthSession, request, success } from '@/api/client';
import type { ApiResponse, PageResult } from '@/types/api';
import type { FileAssetUploadResult } from '@/types/stageTwenty';
import type {
  HealthFeedbackCreateResult,
  HealthFeedbackInputType,
  HealthFeedbackPlaybackSource,
  HealthFeedbackPageResult,
  HealthFeedbackQuery,
  HealthFeedbackRecord,
  HealthFeedbackRequest,
  HealthFeedbackSeverity,
  HealthFeedbackType,
  SelectedVoiceFile
} from '@/types/stageTwentyTwo';
import {
  hasValidFeedbackFileId,
  classifyHealthFeedbackVoiceUrl,
  HEALTH_FEEDBACK_INPUT_TYPES,
  HEALTH_FEEDBACK_SEVERITIES,
  HEALTH_FEEDBACK_TYPES
} from '@/utils/stageTwentyTwoRules';

const filesPath = '/files';
const elderFeedbackPath = '/elder/health-feedback';
const familyFeedbackPath = (elderId: string) =>
  `/family/elders/${encodeURIComponent(elderId)}/health-feedback`;

interface HealthFeedbackWireRecord {
  feedbackId: string;
  elderId?: string;
  elderName?: string;
  feedbackType: string;
  severity: string;
  content?: string;
  inputType: string;
  fileId?: string;
  voiceUrl?: string;
  audioUrl?: string;
  fileUrl?: string;
  createdAt: string;
}

type HealthFeedbackListPayload = HealthFeedbackWireRecord[] | PageResult<HealthFeedbackWireRecord>;

function isApiResponse<T>(value: unknown): value is ApiResponse<T> {
  if (!value || typeof value !== 'object') return false;
  const response = value as Partial<ApiResponse<T>>;
  return typeof response.code === 'number' && typeof response.message === 'string' && 'data' in response;
}

function parseUploadPayload(payload: unknown) {
  if (typeof payload !== 'string') return payload;
  try {
    return JSON.parse(payload) as unknown;
  } catch {
    return payload;
  }
}

function normalizeFeedbackRecord(record: HealthFeedbackWireRecord): HealthFeedbackRecord | null {
  if (!record.feedbackId?.trim() || !record.createdAt) return null;
  if (!HEALTH_FEEDBACK_TYPES.includes(record.feedbackType as HealthFeedbackType)) return null;
  if (!HEALTH_FEEDBACK_SEVERITIES.includes(record.severity as HealthFeedbackSeverity)) return null;
  if (!HEALTH_FEEDBACK_INPUT_TYPES.includes(record.inputType as HealthFeedbackInputType)) return null;
  return {
    feedbackId: record.feedbackId,
    elderId: record.elderId ?? '',
    elderName: record.elderName,
    feedbackType: record.feedbackType as HealthFeedbackType,
    severity: record.severity as HealthFeedbackSeverity,
    content: record.content?.trim() ?? '',
    inputType: record.inputType as HealthFeedbackInputType,
    voiceUrl: record.voiceUrl || record.audioUrl || record.fileUrl,
    createdAt: record.createdAt
  };
}

function cleanQuery(query: HealthFeedbackQuery) {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== ''));
}

function resolveVoiceResourceUrl(sourceUrl: string) {
  if (/^https?:\/\//i.test(sourceUrl) || sourceUrl.startsWith('/api/')) return sourceUrl;
  return `${getApiBase()}/${sourceUrl.replace(/^\/+/, '')}`;
}

function currentPageOrigin() {
  return typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
}

function trustedMediaOrigins() {
  return String(import.meta.env.VITE_MEDIA_TRUSTED_ORIGINS || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

export async function getAuthorizedHealthFeedbackVoice(
  sourceUrl: string
): Promise<ApiResponse<HealthFeedbackPlaybackSource>> {
  const session = readAuthSession();
  const emptySource = { playbackUrl: '', revokeOnRelease: false };
  if (!session) return failure(401, '未登录', emptySource, `frontend-${Date.now()}`);
  const access = classifyHealthFeedbackVoiceUrl(
    resolveVoiceResourceUrl(sourceUrl),
    currentPageOrigin(),
    trustedMediaOrigins()
  );
  if (access.mode === 'REJECTED') {
    return failure(403, '语音来源不受信任', emptySource, `frontend-${Date.now()}`);
  }
  if (access.mode === 'SIGNED_TRUSTED_ORIGIN') {
    return success({ playbackUrl: access.url, revokeOnRelease: false }, `frontend-${Date.now()}`);
  }
  try {
    const response = await fetch(access.url, {
      headers: {
        Accept: 'audio/*',
        Authorization: `Bearer ${session.token}`
      }
    });
    if (!response.ok) {
      return failure(response.status, '语音暂时无法读取', emptySource, `frontend-${Date.now()}`);
    }
    const blob = await response.blob();
    if (!blob.size || (blob.type && !blob.type.toLowerCase().startsWith('audio/'))) {
      return failure(502, '语音响应格式错误', emptySource, `frontend-${Date.now()}`);
    }
    return success({ playbackUrl: URL.createObjectURL(blob), revokeOnRelease: true }, `frontend-${Date.now()}`);
  } catch {
    return failure(500, '语音暂时无法读取', emptySource, `frontend-${Date.now()}`);
  }
}

export async function uploadHealthFeedbackVoice(
  file: SelectedVoiceFile,
  onProgress: (progress: number) => void
): Promise<ApiResponse<FileAssetUploadResult>> {
  const session = readAuthSession();
  if (file.blob) {
    const formData = new FormData();
    formData.append('file', file.blob, file.name);
    onProgress(20);
    try {
      const response = await fetch(`${getApiBase()}${filesPath}`, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          ...(session ? { Authorization: `Bearer ${session.token}` } : {})
        },
        body: formData
      });
      const payload = await response.json().catch(() => null) as unknown;
      onProgress(100);
      if (!isApiResponse<FileAssetUploadResult>(payload)) {
        return failure(response.status >= 400 ? response.status : 500, '语音上传响应格式错误', {} as FileAssetUploadResult, `frontend-${Date.now()}`);
      }
      if (payload.code === 0 && !hasValidFeedbackFileId(payload.data)) {
        return failure(502, '语音上传响应缺少文件凭证', {} as FileAssetUploadResult, payload.traceId);
      }
      return payload;
    } catch {
      return failure(500, '语音上传失败', {} as FileAssetUploadResult, `frontend-${Date.now()}`);
    }
  }
  return new Promise((resolve) => {
    const task = uni.uploadFile({
      url: `${getApiBase()}${filesPath}`,
      filePath: file.path,
      name: 'file',
      header: {
        Accept: 'application/json',
        ...(session ? { Authorization: `Bearer ${session.token}` } : {})
      },
      success(response) {
        const payload = parseUploadPayload(response.data);
        if (isApiResponse<FileAssetUploadResult>(payload)) {
          if (payload.code === 0 && !hasValidFeedbackFileId(payload.data)) {
            resolve(failure(502, '语音上传响应缺少文件凭证', {} as FileAssetUploadResult, payload.traceId));
            return;
          }
          resolve(payload);
          return;
        }
        resolve(failure(
          response.statusCode >= 400 ? response.statusCode : 500,
          '语音上传响应格式错误',
          {} as FileAssetUploadResult,
          `frontend-${Date.now()}`
        ));
      },
      fail() {
        resolve(failure(500, '语音上传失败', {} as FileAssetUploadResult, `frontend-${Date.now()}`));
      }
    });
    task.onProgressUpdate((event) => onProgress(Math.max(0, Math.min(100, event.progress))));
  });
}

export async function createElderHealthFeedback(
  payload: HealthFeedbackRequest
): Promise<ApiResponse<HealthFeedbackCreateResult>> {
  const response = await request<HealthFeedbackCreateResult>({
    method: 'POST',
    url: elderFeedbackPath,
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as HealthFeedbackCreateResult };
  if (!response.data?.feedbackId?.trim() || !response.data.createdAt || !response.data.aiAdvice?.trim()) {
    return failure(502, '健康反馈响应不完整', {} as HealthFeedbackCreateResult, response.traceId);
  }
  return response;
}

export async function getFamilyHealthFeedback(
  elderId: string,
  query: HealthFeedbackQuery
): Promise<ApiResponse<HealthFeedbackPageResult>> {
  const response = await request<HealthFeedbackListPayload>({
    method: 'GET',
    url: familyFeedbackPath(elderId),
    data: cleanQuery(query)
  });
  if (response.code !== 0) {
    return { ...response, data: { records: [], total: 0, page: query.page, size: query.size } };
  }
  if (!response.data || (!Array.isArray(response.data) && !Array.isArray(response.data.records))) {
    return failure(502, '健康反馈列表响应不完整', { records: [], total: 0, page: query.page, size: query.size }, response.traceId);
  }
  const wireRecords = Array.isArray(response.data) ? response.data : response.data.records;
  const records = wireRecords.map(normalizeFeedbackRecord);
  if (records.some((record) => record === null)) {
    return failure(502, '健康反馈列表响应不完整', { records: [], total: 0, page: query.page, size: query.size }, response.traceId);
  }
  return {
    ...response,
    data: {
      records: records as HealthFeedbackRecord[],
      total: Array.isArray(response.data) ? records.length : response.data.total,
      page: Array.isArray(response.data) ? query.page : response.data.page,
      size: Array.isArray(response.data) ? query.size : response.data.size
    }
  };
}
