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

  return new Promise<ApiResponse<T>>((resolve) => {
    uni.request({
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
            resolve(options.mock);
            return;
          }
          resolve(responseData);
          return;
        }

        if (USE_MOCK && options.mockFallback && options.mock) {
          resolve(options.mock);
          return;
        }

        resolve(
          failure(
            response.statusCode >= 400 ? response.statusCode : 500,
            '接口响应格式错误',
            {} as T,
            `frontend-${Date.now()}`
          )
        );
      },
      fail: () => {
        if (USE_MOCK && options.mockFallback && options.mock) {
          resolve(options.mock);
          return;
        }
        resolve(failure(500, '接口请求失败', {} as T, `frontend-${Date.now()}`));
      }
    });
  });
}
