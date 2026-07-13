import type { AllergySeverity, DiseaseStatus, MedicationFrequency } from '@/types/stageNineteen';
import type {
  ApprovedMedicalFileSummary,
  PreServiceElderProfile,
  RecentServiceReportSummary
} from '@/types/stageTwentyFive';

export const DISEASE_STATUS_LABELS: Record<DiseaseStatus, string> = {
  ACTIVE: '治疗中',
  MONITORING: '持续观察',
  STABLE: '情况稳定',
  RESOLVED: '已恢复'
};

export const MEDICATION_FREQUENCY_LABELS: Record<MedicationFrequency, string> = {
  ONCE_DAILY: '每日一次',
  TWICE_DAILY: '每日两次',
  THREE_TIMES_DAILY: '每日三次',
  EVERY_OTHER_DAY: '隔日一次',
  WEEKLY: '每周一次',
  AS_NEEDED: '按需使用'
};

export const ALLERGY_SEVERITY_LABELS: Record<AllergySeverity, string> = {
  MILD: '轻度',
  MODERATE: '中度',
  SEVERE: '重度'
};

export const MEDICAL_FILE_TYPE_LABELS: Record<string, string> = {
  PRESCRIPTION: '处方',
  EXAMINATION_REPORT: '检查报告',
  DISCHARGE_SUMMARY: '出院小结',
  MEDICAL_RECORD: '门诊病历'
};

export const CARE_LEVEL_LABELS: Record<string, string> = {
  LEVEL_1: '一级照护',
  LEVEL_2: '二级照护',
  LEVEL_3: '三级照护'
};

export function elderProfileName(profile: PreServiceElderProfile, fallback = '服务对象') {
  return profile.elderName?.trim()
    || profile.displayName?.trim()
    || profile.name?.trim()
    || fallback;
}

export function elderProfileSummary(profile: PreServiceElderProfile) {
  const parts = [
    typeof profile.age === 'number' && profile.age >= 0 ? `${profile.age} 岁` : '',
    profile.careLevel ? CARE_LEVEL_LABELS[profile.careLevel] ?? profile.careLevel : ''
  ].filter(Boolean);
  return parts.join(' · ');
}

export function carePointList(profile: PreServiceElderProfile) {
  const points = (profile.carePoints ?? []).map((item) => item.trim()).filter(Boolean);
  const plan = profile.carePlan;
  if (plan?.dailyCare?.trim()) points.push(`日常照护：${plan.dailyCare.trim()}`);
  if (plan?.precautions?.trim()) points.push(`注意事项：${plan.precautions.trim()}`);
  if (plan?.careGoals?.trim()) points.push(`照护目标：${plan.careGoals.trim()}`);
  return [...new Set(points)];
}

export function medicalFileTypeLabel(record: ApprovedMedicalFileSummary) {
  return MEDICAL_FILE_TYPE_LABELS[record.fileType] ?? '病历资料';
}

export function reportTitle(record: RecentServiceReportSummary) {
  return record.serviceName?.trim() || '近期护理服务';
}

export function formatBusinessDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16);
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).replace(/\//g, '-');
}

export function preServiceSummaryError(code: number) {
  if (code === 401) return '登录状态已失效，请重新登录后查看。';
  if (code === 403) return '您不是该订单的服务护理人员，无法查看这份健康摘要。';
  if (code === 404) return '暂未找到该订单的服务前健康摘要。';
  if (code === 409) return '当前任务状态暂不允许查看服务前健康摘要，请刷新任务后重试。';
  if (code === 502) return '健康摘要内容不完整，请联系平台维护人员。';
  return '服务前健康摘要暂时无法读取，请稍后重试。';
}
