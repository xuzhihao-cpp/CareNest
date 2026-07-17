import {
  createDemoTraceId,
  failure,
  getApiBase,
  isDemoPresentationMode,
  readAuthSession,
  request,
  success
} from '@/api/client';
import type { ApiResponse, PageResult } from '@/types/api';
import type {
  FileAssetUploadResult,
  MedicalFileAuditStatusWire,
  MedicalFileRecord,
  MedicalFileRegisterRequest,
  MedicalFileRegisterResult
} from '@/types/stageTwenty';
import { hasValidUploadedFileId, normalizeMedicalFileAuditStatus } from '@/utils/stageTwentyRules';

type MedicalFileRegisterWireResult = Omit<MedicalFileRegisterResult, 'auditStatus'> & {
  auditStatus: MedicalFileAuditStatusWire;
};
type MedicalFileWireRecord = Omit<MedicalFileRecord, 'auditStatus'> & {
  auditStatus: MedicalFileAuditStatusWire;
};
type MedicalFileListPayload = MedicalFileWireRecord[] | PageResult<MedicalFileWireRecord>;

const filesPath = '/files';
const medicalFilesPath = (elderId: string) =>
  `/elders/${encodeURIComponent(elderId)}/medical-files`;

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

function demoUploadResult(filePath: string): ApiResponse<FileAssetUploadResult> {
  return success(
    {
      fileId: `demo_file_${Date.now()}`,
      url: filePath,
      type: 'DEMO',
      auditStatus: 'PENDING'
    },
    createDemoTraceId()
  );
}

export function uploadMedicalFileAsset(
  filePath: string,
  onProgress: (progress: number) => void
): Promise<ApiResponse<FileAssetUploadResult>> {
  const session = readAuthSession();

  return new Promise((resolve) => {
    const task = uni.uploadFile({
      url: `${getApiBase()}${filesPath}`,
      filePath,
      name: 'file',
      header: {
        Accept: 'application/json',
        ...(session ? { Authorization: `Bearer ${session.token}` } : {})
      },
      success(response) {
        const payload = parseUploadPayload(response.data);
        if (isApiResponse<FileAssetUploadResult>(payload)) {
          if (isDemoPresentationMode() && payload.code !== 0) {
            resolve(demoUploadResult(filePath));
            return;
          }
          if (payload.code === 0 && !hasValidUploadedFileId(payload.data)) {
            if (isDemoPresentationMode()) {
              resolve(demoUploadResult(filePath));
              return;
            }
            resolve(
              failure(
                502,
                '文件上传成功响应缺少文件凭证',
                {} as FileAssetUploadResult,
                payload.traceId || `frontend-${Date.now()}`
              )
            );
            return;
          }
          resolve(payload);
          return;
        }
        if (isDemoPresentationMode()) {
          resolve(demoUploadResult(filePath));
          return;
        }
        resolve(
          failure(
            response.statusCode >= 400 ? response.statusCode : 500,
            '文件上传响应格式错误',
            {} as FileAssetUploadResult,
            `frontend-${Date.now()}`
          )
        );
      },
      fail() {
        if (isDemoPresentationMode()) {
          resolve(demoUploadResult(filePath));
          return;
        }
        resolve(
          failure(
            500,
            '文件上传失败',
            {} as FileAssetUploadResult,
            `frontend-${Date.now()}`
          )
        );
      }
    });
    task.onProgressUpdate((event) => onProgress(Math.max(0, Math.min(100, event.progress))));
  });
}

export async function registerMedicalFile(
  elderId: string,
  payload: MedicalFileRegisterRequest
): Promise<ApiResponse<MedicalFileRegisterResult>> {
  const response = await request<MedicalFileRegisterWireResult>({
    method: 'POST',
    url: medicalFilesPath(elderId),
    data: payload
  });
  if (response.code !== 0) return { ...response, data: {} as MedicalFileRegisterResult };
  const auditStatus = normalizeMedicalFileAuditStatus(response.data.auditStatus);
  if (!response.data.medicalFileId?.trim() || !response.data.fileId?.trim() || !auditStatus) {
    return failure(502, '病历资料登记响应不完整', {} as MedicalFileRegisterResult, response.traceId);
  }
  return { ...response, data: { ...response.data, auditStatus } };
}

export async function getMedicalFiles(elderId: string): Promise<ApiResponse<MedicalFileRecord[]>> {
  const response = await request<MedicalFileListPayload>({
    method: 'GET',
    url: medicalFilesPath(elderId)
  });
  if (response.code !== 0) return { ...response, data: [] };
  const wireRecords = Array.isArray(response.data) ? response.data : response.data.records;
  const records = wireRecords.map((record) => {
    const auditStatus = normalizeMedicalFileAuditStatus(record.auditStatus);
    return auditStatus ? { ...record, auditStatus } : null;
  });
  if (records.some((record) => record === null)) {
    return failure(502, '病历资料审核状态无法识别', [], response.traceId);
  }
  return {
    ...response,
    data: records as MedicalFileRecord[]
  };
}
