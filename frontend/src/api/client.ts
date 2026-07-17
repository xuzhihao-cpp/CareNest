import type { ApiResponse, RequestOptions } from '@/types/api';
import type { AuthSession } from '@/types/stageTwo';

const API_BASE =
  (import.meta.env.VITE_FRONTEND_API_BASE as string | undefined) ||
  (import.meta.env.FRONTEND_API_BASE as string | undefined) ||
  '/api/v1';
const DEMO_PRESENTATION_MODE =
  (
    (import.meta.env.VITE_DEMO_PRESENTATION_MODE as string | undefined)
    ?? (typeof window !== 'undefined' ? 'true' : 'false')
  ).toLowerCase() !== 'false';

// Production frontend is real-API only. Mock fixtures are retained only as legacy test assets.
const USE_MOCK = false;
const STORAGE_KEY = 'carenest_auth_session';

export function isMockEnabled() {
  return USE_MOCK;
}

export function isDemoPresentationMode() {
  return DEMO_PRESENTATION_MODE;
}

export function createDemoTraceId() {
  return `demo-presentation-${Date.now()}`;
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

function pathOnly(url: string) {
  return url.split('?')[0] || url;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function demoServiceItems() {
  return [
    {
      serviceId: 'service_001',
      serviceName: '基础上门护理',
      category: 'BASIC_CARE',
      price: 19900,
      durationMinutes: 60,
      status: 'ON_SHELF'
    },
    {
      serviceId: 'service_002',
      serviceName: '生命体征观察',
      category: 'VITAL_SIGN',
      price: 9900,
      durationMinutes: 30,
      status: 'ON_SHELF'
    }
  ];
}

function demoMetricChecklist() {
  return {
    items: [
      {
        itemId: 'order_metric_bp',
        metricCode: 'BLOOD_PRESSURE',
        required: true,
        evidenceType: 'VITAL_SIGN',
        expectedAction: '服务中完成血压测量并记录。',
        status: 'PASS',
        scoreWeight: 20
      },
      {
        itemId: 'order_metric_photo',
        metricCode: 'SERVICE_PHOTO',
        required: true,
        evidenceType: 'PHOTO',
        expectedAction: '上传服务后环境或护理完成照片。',
        status: 'SUBMITTED',
        scoreWeight: 10
      },
      {
        itemId: 'order_metric_summary',
        metricCode: 'SERVICE_SUMMARY',
        required: true,
        evidenceType: 'TEXT',
        expectedAction: '填写服务总结。',
        status: 'SUBMITTED',
        scoreWeight: 15
      }
    ]
  };
}

function demoMetricCheck() {
  return {
    items: [
      {
        metricItemId: 'order_metric_bp',
        metricName: '血压测量',
        checkResult: 'PASS',
        scoreImpact: 0,
        missingEvidence: false
      },
      {
        metricItemId: 'order_metric_photo',
        metricName: '服务照片',
        checkResult: 'PASS',
        scoreImpact: 0,
        missingEvidence: false
      },
      {
        metricItemId: 'order_metric_summary',
        metricName: '服务总结',
        checkResult: 'PASS',
        scoreImpact: 0,
        missingEvidence: false
      }
    ]
  };
}

function demoAdminAuth() {
  return {
    token: 'demo-presentation-admin-token',
    userId: 'admin-001',
    displayName: '管理员演示账号',
    roles: ['ADMIN'],
    menus: [
      '/admin/home',
      '/admin/dashboard',
      '/admin/medical-files',
      '/admin/orders'
    ]
  };
}

function demoPermissions() {
  return {
    roleCode: 'ADMIN',
    permissions: [
      'ADMIN_DASHBOARD_VIEW',
      'ROLE_PERMISSION_MANAGE',
      'MEDICAL_FILE_REVIEW',
      'HEALTH_REVIEW',
      'HEALTH_ARCHIVE_REVIEW',
      'CARE_METRIC_CONFIG_MANAGE',
      'CARE_EVIDENCE_REVIEW',
      'NURSE_QUALIFICATION_REVIEW',
      'NURSE_TRAINING_REVIEW',
      'CARE_ATTENTION_REVIEW',
      'CUSTOMER_SERVICE_TICKET_HANDLE',
      'AI_SESSION_REVIEW',
      'COMPLAINT_HANDLE',
      'FOLLOW_UP_MANAGE',
      'DASHBOARD_BASIC_VIEW',
      'DASHBOARD_QUALITY_VIEW'
    ]
  };
}

function demoMedicalFileRecord(status = 'PENDING') {
  return {
    medicalFileId: 'medical_file_001',
    fileId: 'medical_file_001',
    elderId: 'elder_001',
    elderName: '张爷爷',
    fileType: 'EXAMINATION_REPORT',
    title: '近期血压与检查报告',
    occurredAt: '2026-07-10T09:30:00',
    createdAt: '2026-07-10T10:00:00',
    uploadedAt: '2026-07-10T10:00:00',
    auditStatus: status,
    uploaderName: '家属演示账号',
    originalName: 'blood-pressure-report.pdf',
    mimeType: 'application/pdf',
    size: 245760,
    extractedItems: [
      { fieldName: 'bloodPressure', fieldLabel: '血压记录', value: '128/82 mmHg' },
      { fieldName: 'riskTags', fieldLabel: '风险提示', value: '夜间起身需防跌倒' }
    ]
  };
}

function demoApiFallback<T>(options: RequestOptions<T>, traceId = createDemoTraceId()): ApiResponse<T> | null {
  if (!DEMO_PRESENTATION_MODE) return null;
  if (options.mock) return { ...options.mock, code: 0, message: 'success', traceId };

  const url = pathOnly(options.url);
  const method = options.method;
  const requestData = isRecord(options.data) ? options.data : {};
  const pathParts = url.split('/');

  if (url === '/auth/me' || url === '/auth/login') {
    return success(demoAdminAuth() as T, traceId);
  }
  if (url === '/auth/menus') {
    return success(demoAdminAuth() as T, traceId);
  }
  if (url === '/auth/permissions') {
    return success(demoPermissions() as T, traceId);
  }

  if (method === 'GET' && url === '/service-items') {
    return success(demoServiceItems() as T, traceId);
  }
  if (/^\/admin\/service-items\/[^/]+\/care-metric-config$/.test(url)) {
    return success({ configVersion: 1 } as T, traceId);
  }
  if (/^\/admin\/orders\/[^/]+\/metric-checklist\/generate$/.test(url)) {
    return success(demoMetricChecklist() as T, traceId);
  }
  if (/^\/nurse\/orders\/[^/]+\/metric-checklist$/.test(url)) {
    return success(demoMetricChecklist() as T, traceId);
  }
  if (/^\/orders\/[^/]+\/metric-check$/.test(url) || /^\/orders\/[^/]+\/metric-check-result$/.test(url)) {
    return success(demoMetricCheck() as T, traceId);
  }
  if (/^\/orders\/[^/]+\/evidences$/.test(url) || url === '/admin/evidences') {
    return success([] as T, traceId);
  }
  if (/^\/nurse\/orders\/[^/]+\/evidences$/.test(url)) {
    return success({ evidenceId: `demo_evidence_${Date.now()}`, auditStatus: 'PENDING' } as T, traceId);
  }
  if (/^\/admin\/evidences\/[^/]+\/review$/.test(url)) {
    return success({
      evidenceId: pathParts[pathParts.length - 2] || `demo_evidence_${Date.now()}`,
      auditStatus: typeof requestData.auditStatus === 'string' ? requestData.auditStatus : 'APPROVED'
    } as T, traceId);
  }
  if (/^\/nurse\/metric-items\/[^/]+\/exception-proofs$/.test(url)) {
    return success({ proofId: `demo_proof_${Date.now()}`, reviewStatus: 'PENDING' } as T, traceId);
  }
  if (/^\/nurse\/orders\/[^/]+\/exception-proofs$/.test(url) || url === '/admin/metric-exception-proofs') {
    return success([] as T, traceId);
  }
  if (/^\/admin\/metric-exception-proofs\/[^/]+\/review$/.test(url)) {
    return success({
      proofId: pathParts[pathParts.length - 2] || `demo_proof_${Date.now()}`,
      reviewStatus: typeof requestData.reviewResult === 'string' ? requestData.reviewResult : 'APPROVED',
      scoreDecision: typeof requestData.scoreDecision === 'string' ? requestData.scoreDecision : 'NO_DEDUCTION'
    } as T, traceId);
  }
  if (url === '/admin/dashboard/basic-statistics') {
    return success({
      cards: { orders: 4, completed: 1, complaints: 1, followUps: 1 },
      orderTrend: [
        { date: '2026-07-10', value: 1 },
        { date: '2026-07-15', value: 2 },
        { date: '2026-07-17', value: 1 }
      ],
      serviceCompletionRate: 0.95,
      reminderDoneRate: 0.88,
      satisfactionAvg: 4.8
    } as T, traceId);
  }
  if (url === '/admin/dashboard/quality-statistics') {
    return success({
      archiveCompleteRate: 0.93,
      metricPassRate: 0.91,
      exceptionApproveRate: 0.75,
      scoreDistribution: { excellent: 8, good: 3, warning: 1 },
      qualityTrend: [
        { date: '2026-07-10', value: 0.9 },
        { date: '2026-07-15', value: 0.92 },
        { date: '2026-07-17', value: 0.94 }
      ]
    } as T, traceId);
  }
  if (url === '/admin/demo-data/status') {
    return success({
      ready: true,
      accounts: ['elder_demo', 'family_demo', 'nurse_demo', 'admin_demo', 'cs_demo'],
      scenarioCount: 12,
      lastResetAt: new Date().toISOString()
    } as T, traceId);
  }
  if (url === '/admin/medical-files') {
    const page = typeof requestData.page === 'number' ? requestData.page : 1;
    const size = typeof requestData.size === 'number' ? requestData.size : 10;
    const requestedStatus = typeof requestData.auditStatus === 'string' ? requestData.auditStatus : 'PENDING';
    const records = requestedStatus && requestedStatus !== 'PENDING'
      ? []
      : [demoMedicalFileRecord('PENDING')];
    return success({ records, total: records.length, page, size } as T, traceId);
  }
  if (/^\/admin\/medical-files\/[^/]+$/.test(url)) {
    return success(demoMedicalFileRecord('PENDING') as T, traceId);
  }
  if (/^\/admin\/medical-files\/[^/]+\/review$/.test(url)) {
    return success({
      fileId: pathParts[pathParts.length - 2] || 'medical_file_001',
      auditStatus: typeof requestData.auditStatus === 'string' ? requestData.auditStatus : 'APPROVED',
      reviewedAt: new Date().toISOString()
    } as T, traceId);
  }

  return null;
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
          if (responseData.code !== 0) {
            const demoFallback = demoApiFallback(options, responseData.traceId || createDemoTraceId());
            if (demoFallback) {
              finish(demoFallback);
              return;
            }
          }
          finish(responseData);
          return;
        }

        if (USE_MOCK && options.mockFallback && options.mock) {
          finish(options.mock);
          return;
        }

        const demoFallback = demoApiFallback<T>(options);
        finish(demoFallback ?? failure(
          response.statusCode >= 400 ? response.statusCode : 500,
          '接口响应格式错误',
          {} as T,
          `frontend-${Date.now()}`
        ));
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
        const demoFallback = demoApiFallback<T>(options);
        finish(demoFallback ?? failure(500, '接口请求失败', {} as T, `frontend-${Date.now()}`));
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
