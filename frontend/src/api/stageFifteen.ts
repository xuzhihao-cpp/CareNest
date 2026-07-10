import reportEmptyMock from '@/mock/phase-15/service-report-empty.json';
import reportErrorMock from '@/mock/phase-15/service-report-error.json';
import reportMock from '@/mock/phase-15/service-report.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import { STAGE_FOURTEEN_RECORDS_STORAGE_KEY } from '@/api/stageFourteen';
import type { ApiResponse } from '@/types/api';
import type { CareExecutionRecord } from '@/types/stageFourteen';
import type { ServiceReportRecord, ServiceReportResponse, StageFifteenScenario } from '@/types/stageFifteen';
import type { RoleCode } from '@/types/stageOne';

const generatePath = (orderId: string) => `/orders/${orderId}/service-report/generate`;
const reportPath = (orderId: string) => `/orders/${orderId}/service-report`;
export const STAGE_FIFTEEN_REPORTS_STORAGE_KEY = 'carenest_stage_15_service_reports';

function readExecutionRecords(): CareExecutionRecord[] {
  const stored = uni.getStorageSync(STAGE_FOURTEEN_RECORDS_STORAGE_KEY);
  return stored ? (stored as CareExecutionRecord[]) : [];
}

function readReports(): ServiceReportRecord[] {
  const stored = uni.getStorageSync(STAGE_FIFTEEN_REPORTS_STORAGE_KEY);
  return stored ? (stored as ServiceReportRecord[]) : [];
}

function writeReports(records: ServiceReportRecord[]) {
  uni.setStorageSync(STAGE_FIFTEEN_REPORTS_STORAGE_KEY, records);
}

function requireReportViewer<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-15-unauthorized');
  }
  const allowedRoles: RoleCode[] = ['ADMIN', 'NURSE', 'FAMILY', 'ELDER'];
  const allowed = allowedRoles.some((role) => session.user.roles.includes(role));
  if (!allowed) {
    return failure(403, '无权限', emptyData, 'mock-15-forbidden');
  }
  return null;
}

function currentUserId() {
  return readAuthSession()?.user.userId ?? 'system';
}

function nextReportId(records: ServiceReportRecord[]) {
  return `report-${String(records.length + 1).padStart(3, '0')}`;
}

function emptyReport(): ServiceReportResponse {
  return {
    reportId: '',
    orderId: '',
    summary: '',
    vitalSigns: [],
    serviceRecords: [],
    nursingAdvice: ''
  };
}

function toResponse(record: ServiceReportRecord): ServiceReportResponse {
  return {
    reportId: record.reportId,
    orderId: record.orderId,
    summary: record.summary,
    vitalSigns: record.vitalSigns,
    serviceRecords: record.serviceRecords,
    nursingAdvice: record.nursingAdvice
  };
}

function buildReport(orderId: string): ServiceReportRecord | null {
  const records = readExecutionRecords().filter((item) => item.orderId === orderId);
  if (records.length === 0) {
    return null;
  }

  const serviceRecords = records
    .filter((item) => item.recordType === 'SERVICE_RECORD')
    .map((item) => item.content);
  const vitalSigns = records
    .filter((item) => item.recordType === 'VITAL_SIGN')
    .flatMap((item) => item.content.split(/[，,]/).map((value) => value.trim()).filter(Boolean));
  const nursingAdvice = records.map((item) => item.nursingAdvice).filter(Boolean).join('；');
  const abnormalCount = records.filter((item) => item.abnormalFlag).length;
  const summary =
    abnormalCount > 0
      ? `已完成护理执行记录，发现 ${abnormalCount} 项异常，需要家属或长辈确认。`
      : '已完成护理执行记录，生命体征平稳，可进入报告确认。';
  const reports = readReports();
  return {
    reportId: nextReportId(reports),
    orderId,
    summary,
    vitalSigns,
    serviceRecords,
    nursingAdvice,
    generatedBy: currentUserId(),
    generatedAt: '2026-07-10T10:30'
  };
}

function upsertReport(record: ServiceReportRecord) {
  const reports = readReports();
  const index = reports.findIndex((item) => item.orderId === record.orderId);
  if (index >= 0) {
    reports[index] = {
      ...record,
      reportId: reports[index].reportId
    };
  } else {
    reports.unshift(record);
  }
  writeReports(reports);
}

export function getStageFifteenEndpointSummary() {
  return [
    'POST /api/v1/orders/{orderId}/service-report/generate',
    'GET /api/v1/orders/{orderId}/service-report'
  ];
}

export async function generateServiceReport(orderId: string): Promise<ApiResponse<ServiceReportResponse>> {
  if (isMockEnabled()) {
    const denied = requireReportViewer(emptyReport());
    if (denied) {
      return denied;
    }
    const report = buildReport(orderId);
    if (!report) {
      return failure(404, '数据不存在', emptyReport(), 'mock-15-report-source-not-found');
    }
    upsertReport(report);
    const saved = readReports().find((item) => item.orderId === orderId) ?? report;
    return success(toResponse(saved), 'mock-15-service-report-generate');
  }

  return request<ServiceReportResponse>({
    method: 'POST',
    url: generatePath(orderId),
    mock: reportMock as ApiResponse<ServiceReportResponse>
  });
}

export async function getServiceReport(
  orderId: string,
  scenario: StageFifteenScenario = 'normal'
): Promise<ApiResponse<ServiceReportResponse>> {
  if (isMockEnabled()) {
    const denied = requireReportViewer(emptyReport());
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return reportEmptyMock as ApiResponse<ServiceReportResponse>;
    }
    if (scenario === 'error') {
      return reportErrorMock as ApiResponse<ServiceReportResponse>;
    }
    const report = readReports().find((item) => item.orderId === orderId);
    if (!report) {
      return reportEmptyMock as ApiResponse<ServiceReportResponse>;
    }
    return success(toResponse(report), 'mock-15-service-report');
  }

  return request<ServiceReportResponse>({
    method: 'GET',
    url: reportPath(orderId),
    mock: reportMock as ApiResponse<ServiceReportResponse>
  });
}
