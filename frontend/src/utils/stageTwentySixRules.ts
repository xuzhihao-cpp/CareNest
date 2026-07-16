import type {
  QualificationApplicationRecord,
  QualificationAuditStatus,
  QualificationCertificateFile,
  QualificationFileDraft,
  QualificationPermissionContext,
  QualificationSkillOption
} from '@/types/stageTwentySix';

export const QUALIFICATION_SKILL_DICTIONARY_CODE = 'nurseServiceSkill';
export const QUALIFICATION_MAX_FILES = 3;
export const QUALIFICATION_MAX_FILE_SIZE_MB = 20;
export const QUALIFICATION_MAX_FILE_SIZE_BYTES = QUALIFICATION_MAX_FILE_SIZE_MB * 1024 * 1024;

const statusLabels: Record<QualificationAuditStatus, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  NEED_MORE: '需补充材料'
};

const trainingLabels: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '未通过',
  NEED_MORE: '需补充',
  NOT_STARTED: '尚未参加',
  EXPIRED: '已过期'
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function stringArray(value: unknown) {
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string' || !item.trim())) return null;
  return Array.from(new Set(value.map((item) => item.trim())));
}

export function normalizeQualificationAuditStatus(value: unknown): QualificationAuditStatus | null {
  return value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED' || value === 'NEED_MORE'
    ? value
    : null;
}

export function qualificationStatusLabel(status: QualificationAuditStatus) {
  return statusLabels[status];
}

export function qualificationNurseStatusLabel(status: QualificationAuditStatus) {
  return status === 'REJECTED' ? '未通过' : statusLabels[status];
}

export function qualificationTrainingStatusLabel(status: string) {
  return trainingLabels[status] ?? '状态待同步';
}

export function canSubmitQualification(status: QualificationAuditStatus | null) {
  return status === null || status === 'REJECTED' || status === 'NEED_MORE';
}

export function canSubmitQualificationByPermission(context: QualificationPermissionContext) {
  return context.roleCode === 'NURSE' && context.permissions.includes('NURSE_QUALIFICATION_SUBMIT');
}

export function canReviewQualificationByPermission(context: QualificationPermissionContext) {
  return (context.roleCode === 'ADMIN' || context.roleCode === 'CUSTOMER_SERVICE')
    && context.permissions.includes('NURSE_QUALIFICATION_REVIEW');
}

export function qualificationTrainingErrorMessage(code: number) {
  if (code === 404) return '';
  if (code === 401) return '登录状态已失效，暂时无法读取培训结果。';
  if (code === 403) return '当前账号没有查看培训结果的权限。';
  if (code === 502) return '培训结果内容不完整，请稍后刷新。';
  return '培训结果暂时无法读取，请稍后重试。';
}

export function qualificationSkillDictionaryErrorMessage(code: number) {
  return code === 0
    ? ''
    : '护理技能名称暂时无法读取，申请列表仍可查看，请稍后刷新恢复完整信息。';
}

export function sanitizeMaskedId(value: string) {
  const characters = value.toUpperCase().replace(/[^\dX]/g, '');
  let result = '';
  for (const character of characters) {
    if (result.length < 3 && /\d/.test(character)) {
      result += character;
    } else if (result.length === 3 && /[\dX]/.test(character)) {
      result += character;
    }
    if (result.length === 4) break;
  }
  return result;
}

export function maskIdentityLastFour(value: string) {
  return `${'*'.repeat(14)}${value.toUpperCase()}`;
}

export function sanitizeCertificateNo(value: string) {
  return value.toUpperCase().replace(/[^A-Z0-9-]/g, '').slice(0, 40);
}

export function sanitizeRealName(value: string) {
  return value.replace(/[^\p{L}·. '\-]/gu, '').slice(0, 32);
}

export function validateQualificationForm(input: {
  realName: string;
  idNoLastFour: string;
  certificateNo: string;
  serviceSkillCodes: string[];
  availableSkillCodes: string[];
  files: QualificationFileDraft[];
}) {
  const realName = input.realName.trim();
  if (realName.length < 2 || realName.length > 32) return '请填写 2 至 32 个字符的真实姓名。';
  if (!/^[\p{L}][\p{L}·. '\-]{1,31}$/u.test(realName)) return '姓名仅支持中文、字母及常用姓名分隔符。';
  if (!/^\d{3}[\dX]$/.test(input.idNoLastFour)) return '请填写证件号后 4 位，末位可以是数字或 X。';
  if (!/^[A-Z0-9][A-Z0-9-]{3,39}$/.test(input.certificateNo)) return '证书号应为 4 至 40 位字母、数字或短横线。';
  if (!input.availableSkillCodes.length) return '护理技能字典暂不可用，当前无法提交。';
  if (!input.serviceSkillCodes.length) return '请至少选择一项护理技能。';
  if (input.serviceSkillCodes.some((code) => !input.availableSkillCodes.includes(code))) return '护理技能已发生变化，请刷新后重新选择。';
  if (!input.files.length) return '请至少上传一份资质证明。';
  if (input.files.length > QUALIFICATION_MAX_FILES) return `资质证明最多上传 ${QUALIFICATION_MAX_FILES} 份。`;
  return '';
}

export function qualificationFileSize(size: number) {
  if (!Number.isFinite(size) || size <= 0) return '大小未知';
  if (size < 1024 * 1024) return `${Math.max(1, Math.round(size / 1024))} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function qualificationDateTime(value: string) {
  if (!value) return '尚未记录';
  return value.replace('T', ' ').slice(0, 16);
}

function normalizeCertificateFile(value: unknown): QualificationCertificateFile | null {
  if (!isRecord(value)) return null;
  const fileId = stringValue(value.fileId);
  const originalName = stringValue(value.originalName);
  const mimeType = stringValue(value.mimeType);
  const size = value.size;
  if (!fileId || !originalName || !mimeType || typeof size !== 'number' || size < 0 || typeof value.previewable !== 'boolean') return null;
  return { fileId, originalName, mimeType, size, previewable: value.previewable };
}

export function normalizeQualificationApplication(value: unknown): QualificationApplicationRecord | null {
  if (!isRecord(value)) return null;
  const auditStatus = normalizeQualificationAuditStatus(value.auditStatus);
  const serviceSkillCodes = stringArray(value.serviceSkillCodes);
  if (!Array.isArray(value.certificateFiles)) return null;
  const certificateFiles = value.certificateFiles.map(normalizeCertificateFile);
  if (certificateFiles.some((item) => item === null)) return null;
  const applicationId = stringValue(value.applicationId);
  const nurseId = stringValue(value.nurseId);
  const nurseName = stringValue(value.nurseName);
  const realName = stringValue(value.realName);
  const idNoMasked = stringValue(value.idNoMasked);
  const certificateNoMasked = stringValue(value.certificateNoMasked);
  const submittedAt = stringValue(value.submittedAt);
  if (!applicationId || !nurseId || !nurseName || !auditStatus || !realName || !idNoMasked || !certificateNoMasked || !serviceSkillCodes || !submittedAt) return null;
  return {
    applicationId,
    nurseId,
    nurseName,
    auditStatus,
    realName,
    idNoMasked,
    certificateNoMasked,
    certificateFiles: certificateFiles as QualificationCertificateFile[],
    serviceSkillCodes,
    reviewComment: stringValue(value.reviewComment),
    submittedAt,
    reviewedAt: stringValue(value.reviewedAt)
  };
}

export function normalizeQualificationSkillOptions(value: unknown): QualificationSkillOption[] | null {
  if (!isRecord(value) || !Array.isArray(value.items)) return null;
  const options = value.items
    .filter((item) => isRecord(item) && item.enabled !== false)
    .map((item) => {
      if (!isRecord(item)) return null;
      const optionValue = stringValue(item.value);
      const label = stringValue(item.label);
      if (!optionValue || !label) return null;
      return { value: optionValue, label, sort: typeof item.sort === 'number' ? item.sort : 0 };
    });
  if (!options.length || options.some((item) => item === null)) return null;
  const unique = new Map((options as QualificationSkillOption[]).map((item) => [item.value, item]));
  return Array.from(unique.values()).sort((left, right) => left.sort - right.sort);
}
