export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface FileUploadResult {
  fileId: string;
  url: string;
  type: string;
  auditStatus: string;
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export interface RequestOptions<T> {
  method: HttpMethod;
  url: string;
  data?: object | string | ArrayBuffer;
  mock?: ApiResponse<T>;
  mockFallback?: boolean;
  headers?: Record<string, string>;
}

export interface MockServerPath {
  method: HttpMethod;
  url: string;
  mockFile: string;
  responseShape: string;
}
