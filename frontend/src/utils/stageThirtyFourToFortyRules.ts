import type {
  CareMetricConfigItem,
  CareMetricEvidenceType,
  CareMetricStatus,
  CareMetricType,
  EvidenceAuditStatus,
  EvidenceReviewRequest,
  ExceptionProofRequest,
  ExceptionProofStatus,
  MetricChecklistItem,
  ProofReasonType,
  ProofReviewRequest,
  ScoreDecision
} from '@/types/stageThirtyFourToForty';

export const CARE_METRIC_TYPES: CareMetricType[] = ['PRE_SERVICE', 'SERVICE_PROCESS', 'POST_SERVICE'];
export const CARE_METRIC_EVIDENCE_TYPES: CareMetricEvidenceType[] = ['NONE', 'PHOTO', 'FILE', 'TEXT', 'VITAL_SIGN'];
export const CARE_METRIC_STATUSES: CareMetricStatus[] = [
  'PENDING',
  'SUBMITTED',
  'PASS',
  'MISSING',
  'PENDING_PROOF',
  'EXEMPT_APPROVED',
  'EXEMPT_REJECTED'
];
export const EVIDENCE_AUDIT_STATUSES: EvidenceAuditStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'NEED_MORE'];
export const EVIDENCE_REVIEW_TARGETS: Array<Exclude<EvidenceAuditStatus, 'PENDING'>> = [
  'APPROVED',
  'REJECTED',
  'NEED_MORE'
];
export const PROOF_STATUSES: ExceptionProofStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];
export const PROOF_REVIEW_TARGETS: Array<Exclude<ExceptionProofStatus, 'PENDING'>> = ['APPROVED', 'REJECTED'];
export const PROOF_REASON_TYPES: ProofReasonType[] = [
  'FORGOT',
  'NOT_REQUIRED',
  'ELDER_REFUSED',
  'OBJECTIVE_IMPOSSIBLE',
  'OTHER'
];
export const SCORE_DECISIONS: ScoreDecision[] = ['NO_DEDUCTION', 'DEDUCT'];

export const CARE_METRIC_TYPE_LABELS: Record<CareMetricType, string> = {
  PRE_SERVICE: '服务前核对',
  SERVICE_PROCESS: '服务过程',
  POST_SERVICE: '服务后追踪'
};

export const CARE_METRIC_EVIDENCE_LABELS: Record<CareMetricEvidenceType, string> = {
  NONE: '无需留档',
  PHOTO: '照片',
  FILE: '附件',
  TEXT: '文字说明',
  VITAL_SIGN: '生命体征'
};

export const CARE_METRIC_STATUS_LABELS: Record<CareMetricStatus, string> = {
  PENDING: '未提交',
  SUBMITTED: '已提交',
  PASS: '已达标',
  MISSING: '未完成',
  PENDING_PROOF: '证明待审',
  EXEMPT_APPROVED: '豁免通过',
  EXEMPT_REJECTED: '豁免驳回'
};

export const EVIDENCE_AUDIT_STATUS_LABELS: Record<EvidenceAuditStatus, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  NEED_MORE: '需补材料'
};

export const PROOF_STATUS_LABELS: Record<ExceptionProofStatus, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回'
};

export const PROOF_REASON_LABELS: Record<ProofReasonType, string> = {
  FORGOT: '护理人员遗漏',
  NOT_REQUIRED: '本次不适用',
  ELDER_REFUSED: '长辈拒绝配合',
  OBJECTIVE_IMPOSSIBLE: '客观无法完成',
  OTHER: '其他原因'
};

export const SCORE_DECISION_LABELS: Record<ScoreDecision, string> = {
  NO_DEDUCTION: '不扣分',
  DEDUCT: '按规则扣分'
};

export function createEmptyMetricConfigItem(): CareMetricConfigItem {
  return {
    metricCode: '',
    metricName: '',
    metricType: 'SERVICE_PROCESS',
    required: true,
    evidenceType: 'PHOTO',
    scoreWeight: 10,
    description: ''
  };
}

export function sanitizeMetricConfigItems(items: CareMetricConfigItem[]): CareMetricConfigItem[] {
  return items.map((item) => ({
    metricCode: item.metricCode.trim(),
    metricName: item.metricName.trim(),
    metricType: item.metricType,
    required: Boolean(item.required),
    evidenceType: item.evidenceType,
    scoreWeight: Number(item.scoreWeight),
    description: item.description?.trim() || ''
  }));
}

export function validateMetricConfigItems(items: CareMetricConfigItem[]) {
  if (items.length === 0) return '请至少配置一个护理指标。';
  if (items.length > 100) return '单个服务项目最多配置 100 个护理指标。';
  const sanitized = sanitizeMetricConfigItems(items);
  const codes = new Set<string>();
  for (const [index, item] of sanitized.entries()) {
    const label = `第 ${index + 1} 个指标`;
    if (!item.metricCode) return `${label}缺少指标编码。`;
    if (!/^[A-Z0-9_]{2,64}$/.test(item.metricCode)) return `${label}编码只能使用大写英文、数字和下划线。`;
    if (codes.has(item.metricCode)) return `指标编码 ${item.metricCode} 重复。`;
    codes.add(item.metricCode);
    if (!item.metricName) return `${label}缺少指标名称。`;
    if (item.metricName.length > 128) return `${label}名称不能超过 128 个字符。`;
    if (!CARE_METRIC_TYPES.includes(item.metricType)) return `${label}阶段类型不在冻结字典内。`;
    if (!CARE_METRIC_EVIDENCE_TYPES.includes(item.evidenceType)) return `${label}留档类型不在冻结字典内。`;
    if (!Number.isFinite(item.scoreWeight) || item.scoreWeight < 0 || item.scoreWeight > 100) {
      return `${label}分值权重必须在 0 到 100 之间。`;
    }
    if (item.description && item.description.length > 500) return `${label}说明不能超过 500 个字符。`;
  }
  return '';
}

export function evidenceNeedsFile(evidenceType: CareMetricEvidenceType) {
  return evidenceType === 'PHOTO' || evidenceType === 'FILE';
}

export function validateEvidenceSubmission(
  metric: MetricChecklistItem | null,
  evidenceType: CareMetricEvidenceType,
  fileId: string,
  description: string
) {
  if (!metric) return '请先选择一个订单留档清单项。';
  if (metric.status === 'PASS') return '该指标已经达标，不需要重复提交留档。';
  if (evidenceType === 'NONE') return '无需留档的指标不能提交留档材料。';
  if (!CARE_METRIC_EVIDENCE_TYPES.includes(evidenceType)) return '留档类型不在冻结字典内。';
  if (evidenceNeedsFile(evidenceType) && !fileId.trim()) return '照片或附件留档必须先完成文件上传并取得文件凭证。';
  if (evidenceType === 'TEXT' && !description.trim()) return '文字留档必须填写说明。';
  if (description.trim().length > 500) return '留档说明不能超过 500 个字符。';
  return '';
}

export function validateEvidenceReview(payload: EvidenceReviewRequest) {
  if (!EVIDENCE_REVIEW_TARGETS.includes(payload.auditStatus)) return '请选择有效的留档审核结论。';
  if ((payload.auditStatus === 'REJECTED' || payload.auditStatus === 'NEED_MORE') && !payload.reviewComment?.trim()) {
    return '驳回或要求补材料时必须填写审核意见。';
  }
  if ((payload.reviewComment ?? '').trim().length > 500) return '审核意见不能超过 500 个字符。';
  return '';
}

export function validateExceptionProofSubmission(metric: MetricChecklistItem | null, payload: ExceptionProofRequest) {
  if (!metric) return '请先选择一个未完成的指标。';
  if (metric.status !== 'MISSING') return '只有校验结果为未完成的指标才能提交原因证明。';
  if (!PROOF_REASON_TYPES.includes(payload.reasonType)) return '原因类型不在冻结字典内。';
  if (!payload.reasonText.trim()) return '请填写未完成原因。';
  if (payload.reasonText.trim().length > 500) return '未完成原因不能超过 500 个字符。';
  if (payload.fileIds.length === 0 || payload.fileIds.some((item) => !item.trim())) return '原因证明必须至少上传一个文件凭证。';
  if (payload.fileIds.length > 10) return '单次原因证明最多提交 10 个文件凭证。';
  return '';
}

export function validateProofReview(payload: ProofReviewRequest) {
  if (!PROOF_REVIEW_TARGETS.includes(payload.reviewResult)) return '请选择有效的豁免审核结论。';
  if (payload.reviewResult === 'APPROVED' && payload.scoreDecision !== 'NO_DEDUCTION') {
    return '豁免通过必须选择不扣分。';
  }
  if (payload.reviewResult === 'REJECTED' && payload.scoreDecision !== 'DEDUCT') {
    return '豁免驳回必须选择按规则扣分。';
  }
  if (payload.reviewResult === 'REJECTED' && !payload.reviewComment?.trim()) {
    return '豁免驳回必须填写审核意见。';
  }
  if ((payload.reviewComment ?? '').trim().length > 500) return '审核意见不能超过 500 个字符。';
  return '';
}

export function stageThirtyFourToFortyError(code: number, area: 'CONFIG' | 'CHECKLIST' | 'EVIDENCE' | 'CHECK' | 'PROOF') {
  if (code === 401) return '登录状态已失效，请重新登录。';
  if (code === 403) return '当前账号没有执行该护理质控操作的权限。';
  if (code === 404) return area === 'CONFIG' ? '服务项目或指标配置不存在。' : '订单、指标或留档记录不存在。';
  if (code === 409) return '当前记录状态已经变化，请刷新后再处理。';
  if (code === 422 || code === 400) return '提交内容不符合护理质控规则，请检查后重试。';
  return '护理质控服务暂时无法处理，请稍后重试。';
}

export function compactBusinessId(value: string) {
  const trimmed = value.trim();
  if (trimmed.length <= 12) return trimmed;
  return `${trimmed.slice(0, 4)}...${trimmed.slice(-6)}`;
}
