import type { ApiResponse, RequestOptions } from '@/types/api';
import type { AuthSession } from '@/types/stageTwo';

const API_BASE =
  (import.meta.env.VITE_FRONTEND_API_BASE as string | undefined) ||
  (import.meta.env.FRONTEND_API_BASE as string | undefined) ||
  '/api/v1';

// Production frontend is real-API only. Mock fixtures are retained only as legacy test assets.
const USE_MOCK = false;
const STORAGE_KEY = 'carenest_auth_session';

export function isMockEnabled() {
  return USE_MOCK;
}

export function getApiBase() {
  return API_BASE;
}

export function success<T>(data: T, traceId: string): ApiResponse<T> {
  return {
    code: 0,
    message: 'success',
    data,
    traceId
  };
}

export function failure<T>(code: number, message: string, data: T, traceId: string): ApiResponse<T> {
  return {
    code,
    message,
    data,
    traceId
  };
}

export function readAuthSession(): AuthSession | null {
  const raw = uni.getStorageSync(STORAGE_KEY);
  return raw ? (raw as AuthSession) : null;
}

export function writeAuthSession(session: AuthSession) {
  uni.setStorageSync(STORAGE_KEY, session);
}

export function clearAuthSession() {
  uni.removeStorageSync(STORAGE_KEY);
}

export function uploadMultipart<T>(options: {
  url: string;
  filePath: string;
  fileName: string;
  blob?: Blob;
  formData?: Record<string, string>;
}): Promise<ApiResponse<T>> {
  const session = readAuthSession();
  if (options.blob && typeof fetch === 'function' && typeof FormData !== 'undefined') {
    const formData = new FormData();
    formData.append('audio', options.blob, options.fileName);
    Object.entries(options.formData ?? {}).forEach(([key, value]) => formData.append(key, value));
    return fetch(`${API_BASE}${options.url}`, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        ...(session ? { Authorization: `Bearer ${session.token}` } : {})
      },
      body: formData
    }).then(async (response) => {
      const payload = parseResponsePayload(await response.text());
      return isApiResponse<T>(payload)
        ? payload
        : failure(response.status, '接口响应格式错误', {} as T, `frontend-${Date.now()}`);
    }).catch(() => failure(500, '语音上传失败', {} as T, `frontend-${Date.now()}`));
  }
  return new Promise((resolve) => {
    uni.uploadFile({
      url: `${API_BASE}${options.url}`,
      filePath: options.filePath,
      name: 'audio',
      formData: options.formData,
      header: {
        Accept: 'application/json',
        ...(session ? { Authorization: `Bearer ${session.token}` } : {})
      },
      success: (response) => {
        const payload = parseResponsePayload(response.data);
        if (isApiResponse<T>(payload)) {
          resolve(payload);
          return;
        }
        resolve(failure(response.statusCode >= 400 ? response.statusCode : 500, '接口响应格式错误', {} as T, `frontend-${Date.now()}`));
      },
      fail: () => resolve(failure(500, '语音上传失败', {} as T, `frontend-${Date.now()}`))
    });
  });
}

function isApiResponse<T>(payload: unknown): payload is ApiResponse<T> {
  if (!payload || typeof payload !== 'object') {
    return false;
  }
  const value = payload as Partial<ApiResponse<T>>;
  return typeof value.code === 'number' && typeof value.message === 'string' && 'traceId' in value;
}

function parseResponsePayload(payload: unknown) {
  if (typeof payload !== 'string') {
    return payload;
  }
  try {
    return JSON.parse(payload) as unknown;
  } catch {
    return payload;
  }
}

export async function request<T>(options: RequestOptions<T>): Promise<ApiResponse<T>> {
  if (USE_MOCK && options.mock) {
    return options.mock;
  }

  const session = readAuthSession();

  if (options.signal?.aborted) {
    return failure(499, '请求已取消', {} as T, `frontend-${Date.now()}`);
  }

  return new Promise<ApiResponse<T>>((resolve) => {
    let settled = false;
    let requestTask: { abort: () => void } | null = null;
    const finish = (response: ApiResponse<T>) => {
      if (settled) return;
      settled = true;
      options.signal?.removeEventListener('abort', abortRequest);
      resolve(response);
    };
    const abortRequest = () => {
      requestTask?.abort();
      finish(failure(499, '请求已取消', {} as T, `frontend-${Date.now()}`));
    };

    requestTask = uni.request({
      url: `${API_BASE}${options.url}`,
      method: options.method,
      data: options.data as string | Record<string, unknown> | ArrayBuffer | undefined,
      header: {
        Accept: 'application/json',
        ...(options.method === 'POST' || options.method === 'PUT' ? { 'Content-Type': 'application/json' } : {}),
        ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
        ...(options.headers ?? {})
      },
      success: (response) => {
        const responseData = parseResponsePayload(response.data);
        if (isApiResponse<T>(responseData)) {
          if (USE_MOCK && options.mockFallback && options.mock && responseData.code !== 0) {
            finish(options.mock);
            return;
          }
          finish(responseData);
          return;
        }

        if (USE_MOCK && options.mockFallback && options.mock) {
          finish(options.mock);
          return;
        }

        finish(
          failure(
            response.statusCode >= 400 ? response.statusCode : 500,
            '接口响应格式错误',
            {} as T,
            `frontend-${Date.now()}`
          )
        );
      },
      fail: () => {
        if (options.signal?.aborted) {
          finish(failure(499, '请求已取消', {} as T, `frontend-${Date.now()}`));
          return;
        }
        if (USE_MOCK && options.mockFallback && options.mock) {
          finish(options.mock);
          return;
        }
        finish(failure(500, '接口请求失败', {} as T, `frontend-${Date.now()}`));
      }
    });

    if (settled) return;
    if (options.signal?.aborted) {
      abortRequest();
    } else {
      options.signal?.addEventListener('abort', abortRequest, { once: true });
    }
  });
}
