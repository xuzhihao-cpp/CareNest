import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { PreServiceHealthSummary } from '@/types/stageTwentyFive';

const preServiceSummaryPath = (orderId: string) =>
  `/nurse/orders/${encodeURIComponent(orderId)}/pre-service-health-summary`;

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function isString(value: unknown): value is string {
  return typeof value === 'string';
}

function isOptionalString(value: unknown) {
  return value === undefined || isString(value);
}

function isStringArray(value: unknown) {
  return Array.isArray(value) && value.every(isString);
}

function isValidElderProfile(value: unknown) {
  if (!isObject(value)) return false;
  if (![value.elderName, value.displayName, value.name, value.gender, value.birthDate, value.careLevel].every(isOptionalString)) return false;
  if (value.age !== undefined && (typeof value.age !== 'number' || !Number.isFinite(value.age))) return false;
  if (value.carePoints !== undefined && !isStringArray(value.carePoints)) return false;
  if (value.carePlan !== undefined) {
    if (!isObject(value.carePlan)) return false;
    if (![value.carePlan.careGoals, value.carePlan.dailyCare, value.carePlan.precautions].every(isOptionalString)) return false;
  }
  return true;
}

function isValidRiskTag(value: unknown) {
  return isObject(value) && isString(value.tagCode) && isString(value.tagName);
}

function isValidMedication(value: unknown) {
  if (!isObject(value)) return false;
  const frequencies = ['ONCE_DAILY', 'TWICE_DAILY', 'THREE_TIMES_DAILY', 'EVERY_OTHER_DAY', 'WEEKLY', 'AS_NEEDED'];
  return isString(value.medicationName)
    && isString(value.frequency) && frequencies.includes(value.frequency)
    && isStringArray(value.timePoints)
    && isString(value.startDate)
    && [value.dosage, value.endDate, value.remark].every(isOptionalString);
}

function isValidDisease(value: unknown) {
  if (!isObject(value)) return false;
  const statuses = ['ACTIVE', 'MONITORING', 'STABLE', 'RESOLVED'];
  return isString(value.diseaseName)
    && isString(value.status) && statuses.includes(value.status)
    && [value.diagnosedAt, value.remark].every(isOptionalString);
}

function isValidAllergy(value: unknown) {
  if (!isObject(value)) return false;
  const severities = ['MILD', 'MODERATE', 'SEVERE'];
  return isString(value.allergenName)
    && isString(value.severity) && severities.includes(value.severity)
    && [value.reaction, value.remark].every(isOptionalString);
}

function isValidMedicalFile(value: unknown) {
  if (!isObject(value)) return false;
  const fileTypes = ['PRESCRIPTION', 'EXAMINATION_REPORT', 'DISCHARGE_SUMMARY', 'MEDICAL_RECORD'];
  return isString(value.title)
    && isString(value.fileType) && fileTypes.includes(value.fileType)
    && [value.occurredAt, value.summary, value.previewUrl].every(isOptionalString);
}

function isValidRecentReport(value: unknown) {
  if (!isObject(value) || !isString(value.summary)) return false;
  if (![value.serviceName, value.occurredAt, value.generatedAt, value.nursingAdvice].every(isOptionalString)) return false;
  return value.vitalSigns === undefined || isStringArray(value.vitalSigns);
}

function isValidSummary(value: unknown): value is PreServiceHealthSummary {
  return isObject(value)
    && isValidElderProfile(value.elderProfile)
    && Array.isArray(value.riskTags) && value.riskTags.every(isValidRiskTag)
    && Array.isArray(value.medications) && value.medications.every(isValidMedication)
    && Array.isArray(value.diseases) && value.diseases.every(isValidDisease)
    && Array.isArray(value.allergies) && value.allergies.every(isValidAllergy)
    && Array.isArray(value.approvedMedicalFiles) && value.approvedMedicalFiles.every(isValidMedicalFile)
    && Array.isArray(value.recentReports) && value.recentReports.every(isValidRecentReport);
}

export async function getPreServiceHealthSummary(
  orderId: string
): Promise<ApiResponse<PreServiceHealthSummary>> {
  const response = await request<PreServiceHealthSummary>({
    method: 'GET',
    url: preServiceSummaryPath(orderId)
  });
  if (response.code !== 0) return { ...response, data: {} as PreServiceHealthSummary };
  if (!isValidSummary(response.data)) {
    return failure(
      502,
      '服务前健康摘要响应不完整',
      {} as PreServiceHealthSummary,
      response.traceId
    );
  }
  return response;
}
