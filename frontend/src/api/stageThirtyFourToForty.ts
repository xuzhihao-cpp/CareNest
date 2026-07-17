import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  CareMetricConfigRequest,
  CareMetricEvidenceType,
  CareMetricStatus,
  ConfigVersionResponse,
  EvidenceAuditStatus,
  EvidenceRequest,
  EvidenceResponse,
  EvidenceReviewRequest,
  ExceptionProofRequest,
  ExceptionProofResponse,
  ExceptionProofStatus,
  MetricCheckResponse,
  MetricChecklistResponse,
  ProofReviewRequest,
  ProofReviewResponse,
  ScoreDecision
} from '@/types/stageThirtyFourToForty';
import {
  CARE_METRIC_EVIDENCE_TYPES,
  CARE_METRIC_STATUSES,
  EVIDENCE_AUDIT_STATUSES,
  PROOF_STATUSES,
  PROOF_REASON_TYPES,
  SCORE_DECISIONS,
  validateEvidenceReview,
  validateMetricConfigItems,
  validateProofReview
} from '@/utils/stageThirtyFourToFortyRules';

const emptyConfigVersion: ConfigVersionResponse = { configVersion: 0 };
const emptyChecklist: MetricChecklistResponse = { items: [] };
const emptyMetricCheck: MetricCheckResponse = { items: [] };
const emptyEvidence: EvidenceResponse = { evidenceId: '', auditStatus: 'PENDING' };
const emptyProof: ExceptionProofResponse = { proofId: '', reviewStatus: 'PENDING' };
const emptyProofReview: ProofReviewResponse = { proofId: '', reviewStatus: 'PENDING', scoreDecision: 'DEDUCT' };

const metricConfigPath = (serviceId: string) =>
  `/admin/service-items/${encodeURIComponent(serviceId)}/care-metric-config`;
const generateChecklistPath = (orderId: string) =>
  `/admin/orders/${encodeURIComponent(orderId)}/metric-checklist/generate`;
const nurseChecklistPath = (orderId: string) =>
  `/nurse/orders/${encodeURIComponent(orderId)}/metric-checklist`;
const nurseEvidencePath = (orderId: string) =>
  `/nurse/orders/${encodeURIComponent(orderId)}/evidences`;
const orderEvidencePath = (orderId: string) =>
  `/orders/${encodeURIComponent(orderId)}/evidences`;
const evidenceReviewPath = (evidenceId: string) =>
  `/admin/evidences/${encodeURIComponent(evidenceId)}/review`;
const metricCheckPath = (orderId: string) =>
  `/orders/${encodeURIComponent(orderId)}/metric-check`;
const metricCheckResultPath = (orderId: string) =>
  `/orders/${encodeURIComponent(orderId)}/metric-check-result`;
const exceptionProofPath = (metricItemId: string) =>
  `/nurse/metric-items/${encodeURIComponent(metricItemId)}/exception-proofs`;
const nurseExceptionProofsPath = (orderId: string) =>
  `/nurse/orders/${encodeURIComponent(orderId)}/exception-proofs`;
const proofReviewPath = (proofId: string) =>
  `/admin/metric-exception-proofs/${encodeURIComponent(proofId)}/review`;

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function asNumber(value: unknown) {
  const numeric = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : Number.NaN;
  return Number.isFinite(numeric) ? numeric : null;
}

function isCareMetricEvidenceType(value: unknown): value is CareMetricEvidenceType {
  return typeof value === 'string' && CARE_METRIC_EVIDENCE_TYPES.includes(value as CareMetricEvidenceType);
}

function isCareMetricStatus(value: unknown): value is CareMetricStatus {
  return typeof value === 'string' && CARE_METRIC_STATUSES.includes(value as CareMetricStatus);
}

function isEvidenceAuditStatus(value: unknown): value is EvidenceAuditStatus {
  return typeof value === 'string' && EVIDENCE_AUDIT_STATUSES.includes(value as EvidenceAuditStatus);
}

function isProofStatus(value: unknown): value is ExceptionProofStatus {
  return typeof value === 'string' && PROOF_STATUSES.includes(value as ExceptionProofStatus);
}

function isScoreDecision(value: unknown): value is ScoreDecision {
  return typeof value === 'string' && SCORE_DECISIONS.includes(value as ScoreDecision);
}

function validConfigVersion(value: unknown): value is ConfigVersionResponse {
  return isRecord(value) && Number.isInteger(value.configVersion);
}

function normalizeChecklist(value: unknown): MetricChecklistResponse | null {
  if (!isRecord(value) || !Array.isArray(value.items)) return null;
  const items = value.items.map((item) => {
    if (!isRecord(item)) return null;
    const scoreWeight = asNumber(item.scoreWeight);
    if (
      typeof item.itemId !== 'string'
      || !item.itemId.trim()
      || typeof item.metricCode !== 'string'
      || !item.metricCode.trim()
      || typeof item.required !== 'boolean'
      || !isCareMetricEvidenceType(item.evidenceType)
      || !isCareMetricStatus(item.status)
      || scoreWeight === null
    ) {
      return null;
    }
    return {
      itemId: item.itemId.trim(),
      metricCode: item.metricCode.trim(),
      required: item.required,
      evidenceType: item.evidenceType,
      expectedAction: typeof item.expectedAction === 'string' ? item.expectedAction : '',
      status: item.status,
      scoreWeight
    };
  });
  return items.some((item) => item === null) ? null : { items: items as MetricChecklistResponse['items'] };
}

function normalizeEvidence(value: unknown): EvidenceResponse | null {
  if (!isRecord(value)) return null;
  const evidenceType = isCareMetricEvidenceType(value.evidenceType) ? value.evidenceType : undefined;
  return typeof value.evidenceId === 'string'
    && value.evidenceId.trim().length > 0
    && isEvidenceAuditStatus(value.auditStatus)
    ? {
        evidenceId: value.evidenceId.trim(),
        auditStatus: value.auditStatus,
        metricName: typeof value.metricName === 'string' ? value.metricName.trim() : undefined,
        evidenceType,
        description: typeof value.description === 'string' ? value.description.trim() : undefined,
        fileId: typeof value.fileId === 'string' && value.fileId.trim() ? value.fileId.trim() : undefined,
        submittedAt: typeof value.submittedAt === 'string' ? value.submittedAt : undefined
      }
    : null;
}

function normalizeEvidenceList(value: unknown): EvidenceResponse[] | null {
  if (!Array.isArray(value)) return null;
  const records = value.map(normalizeEvidence);
  return records.some((item) => item === null) ? null : records as EvidenceResponse[];
}

function normalizeMetricCheck(value: unknown): MetricCheckResponse | null {
  if (!isRecord(value) || !Array.isArray(value.items)) return null;
  const items = value.items.map((item) => {
    if (!isRecord(item)) return null;
    const scoreImpact = asNumber(item.scoreImpact);
    if (
      typeof item.metricItemId !== 'string'
      || !item.metricItemId.trim()
      || typeof item.metricName !== 'string'
      || !item.metricName.trim()
      || !isCareMetricStatus(item.checkResult)
      || scoreImpact === null
      || typeof item.missingEvidence !== 'boolean'
    ) {
      return null;
    }
    return {
      metricItemId: item.metricItemId.trim(),
      metricName: item.metricName.trim(),
      checkResult: item.checkResult,
      scoreImpact,
      missingEvidence: item.missingEvidence
    };
  });
  return items.some((item) => item === null) ? null : { items: items as MetricCheckResponse['items'] };
}

function normalizeProof(value: unknown): ExceptionProofResponse | null {
  if (!isRecord(value)) return null;
  return typeof value.proofId === 'string'
    && value.proofId.trim().length > 0
    && isProofStatus(value.reviewStatus)
    ? { proofId: value.proofId.trim(), reviewStatus: value.reviewStatus }
    : null;
}

function normalizeProofList(value: unknown): ExceptionProofResponse[] | null {
  if (!Array.isArray(value)) return null;
  const records = value.map(normalizeProof);
  return records.some((item) => item === null) ? null : records as ExceptionProofResponse[];
}

function normalizeProofReview(value: unknown): ProofReviewResponse | null {
  if (!isRecord(value)) return null;
  return typeof value.proofId === 'string'
    && value.proofId.trim().length > 0
    && isProofStatus(value.reviewStatus)
    && isScoreDecision(value.scoreDecision)
    ? {
        proofId: value.proofId.trim(),
        reviewStatus: value.reviewStatus,
        scoreDecision: value.scoreDecision
      }
    : null;
}

function normalizeProofReviewList(value: unknown): ProofReviewResponse[] | null {
  if (!Array.isArray(value)) return null;
  const records = value.map(normalizeProofReview);
  return records.some((item) => item === null) ? null : records as ProofReviewResponse[];
}

function sanitizeConfigRequest(payload: CareMetricConfigRequest): CareMetricConfigRequest {
  return {
    items: payload.items.map((item) => ({
      ...item,
      metricCode: item.metricCode.trim(),
      metricName: item.metricName.trim(),
      scoreWeight: Number(item.scoreWeight),
      description: item.description?.trim() || ''
    }))
  };
}

export async function getCareMetricConfig(serviceId: string): Promise<ApiResponse<ConfigVersionResponse>> {
  const normalizedId = serviceId.trim();
  if (!normalizedId) return failure(422, '请选择服务项目。', emptyConfigVersion, 'frontend-stage-34-validation');
  const response = await request<unknown>({ method: 'GET', url: metricConfigPath(normalizedId) });
  if (response.code !== 0) return { ...response, data: emptyConfigVersion };
  return validConfigVersion(response.data)
    ? { ...response, data: response.data }
    : failure(502, '护理指标配置响应不完整。', emptyConfigVersion, response.traceId);
}

export async function saveCareMetricConfig(
  serviceId: string,
  payload: CareMetricConfigRequest
): Promise<ApiResponse<ConfigVersionResponse>> {
  const normalizedId = serviceId.trim();
  if (!normalizedId) return failure(422, '请选择服务项目。', emptyConfigVersion, 'frontend-stage-34-validation');
  const validationError = validateMetricConfigItems(payload.items);
  if (validationError) return failure(422, validationError, emptyConfigVersion, 'frontend-stage-34-validation');
  const response = await request<unknown>({
    method: 'PUT',
    url: metricConfigPath(normalizedId),
    data: sanitizeConfigRequest(payload)
  });
  if (response.code !== 0) return { ...response, data: emptyConfigVersion };
  return validConfigVersion(response.data)
    ? { ...response, data: response.data }
    : failure(502, '护理指标配置响应不完整。', emptyConfigVersion, response.traceId);
}

export async function generateMetricChecklist(orderId: string): Promise<ApiResponse<MetricChecklistResponse>> {
  const normalizedId = orderId.trim();
  if (!normalizedId) return failure(422, '请输入订单编号。', emptyChecklist, 'frontend-stage-35-validation');
  const response = await request<unknown>({ method: 'POST', url: generateChecklistPath(normalizedId) });
  if (response.code !== 0) return { ...response, data: emptyChecklist };
  const data = normalizeChecklist(response.data);
  return data ? { ...response, data } : failure(502, '订单留档清单响应不完整。', emptyChecklist, response.traceId);
}

export async function getNurseMetricChecklist(orderId: string): Promise<ApiResponse<MetricChecklistResponse>> {
  const normalizedId = orderId.trim();
  if (!normalizedId) return failure(422, '订单信息不完整。', emptyChecklist, 'frontend-stage-35-validation');
  const response = await request<unknown>({ method: 'GET', url: nurseChecklistPath(normalizedId) });
  if (response.code !== 0) return { ...response, data: emptyChecklist };
  const data = normalizeChecklist(response.data);
  return data ? { ...response, data } : failure(502, '订单留档清单响应不完整。', emptyChecklist, response.traceId);
}

export async function submitCareEvidence(
  orderId: string,
  payload: EvidenceRequest
): Promise<ApiResponse<EvidenceResponse>> {
  const normalizedId = orderId.trim();
  if (!normalizedId) return failure(422, '订单信息不完整。', emptyEvidence, 'frontend-stage-36-validation');
  const response = await request<unknown>({
    method: 'POST',
    url: nurseEvidencePath(normalizedId),
    data: {
      metricItemId: payload.metricItemId?.trim() || undefined,
      fileId: payload.fileId?.trim() || undefined,
      evidenceType: payload.evidenceType,
      description: payload.description?.trim() || ''
    }
  });
  if (response.code !== 0) return { ...response, data: emptyEvidence };
  const data = normalizeEvidence(response.data);
  return data ? { ...response, data } : failure(502, '护理留档响应不完整。', emptyEvidence, response.traceId);
}

export async function getOrderEvidences(orderId: string): Promise<ApiResponse<EvidenceResponse[]>> {
  const normalizedId = orderId.trim();
  if (!normalizedId) return failure(422, '订单信息不完整。', [], 'frontend-stage-36-validation');
  const response = await request<unknown>({ method: 'GET', url: orderEvidencePath(normalizedId) });
  if (response.code !== 0) return { ...response, data: [] };
  const data = normalizeEvidenceList(response.data);
  return data ? { ...response, data } : failure(502, '护理留档列表响应不完整。', [], response.traceId);
}

export async function getAdminEvidences(): Promise<ApiResponse<EvidenceResponse[]>> {
  const response = await request<unknown>({ method: 'GET', url: '/admin/evidences' });
  if (response.code !== 0) return { ...response, data: [] };
  const data = normalizeEvidenceList(response.data);
  return data ? { ...response, data } : failure(502, '留档审核队列响应不完整。', [], response.traceId);
}

export async function reviewCareEvidence(
  evidenceId: string,
  payload: EvidenceReviewRequest
): Promise<ApiResponse<EvidenceResponse>> {
  const normalizedId = evidenceId.trim();
  if (!normalizedId) return failure(422, '留档记录不完整。', emptyEvidence, 'frontend-stage-37-validation');
  const validationError = validateEvidenceReview(payload);
  if (validationError) return failure(422, validationError, emptyEvidence, 'frontend-stage-37-validation');
  const response = await request<unknown>({
    method: 'POST',
    url: evidenceReviewPath(normalizedId),
    data: {
      auditStatus: payload.auditStatus,
      reviewComment: payload.reviewComment?.trim() || ''
    }
  });
  if (response.code !== 0) return { ...response, data: emptyEvidence };
  const data = normalizeEvidence(response.data);
  return data ? { ...response, data } : failure(502, '留档审核响应不完整。', emptyEvidence, response.traceId);
}

export async function runMetricCheck(orderId: string): Promise<ApiResponse<MetricCheckResponse>> {
  const normalizedId = orderId.trim();
  if (!normalizedId) return failure(422, '订单信息不完整。', emptyMetricCheck, 'frontend-stage-38-validation');
  const response = await request<unknown>({ method: 'POST', url: metricCheckPath(normalizedId) });
  if (response.code !== 0) return { ...response, data: emptyMetricCheck };
  const data = normalizeMetricCheck(response.data);
  return data ? { ...response, data } : failure(502, '指标完成校验响应不完整。', emptyMetricCheck, response.traceId);
}

export async function getMetricCheckResult(orderId: string): Promise<ApiResponse<MetricCheckResponse>> {
  const normalizedId = orderId.trim();
  if (!normalizedId) return failure(422, '订单信息不完整。', emptyMetricCheck, 'frontend-stage-38-validation');
  const response = await request<unknown>({ method: 'GET', url: metricCheckResultPath(normalizedId) });
  if (response.code !== 0) return { ...response, data: emptyMetricCheck };
  const data = normalizeMetricCheck(response.data);
  return data ? { ...response, data } : failure(502, '指标完成校验响应不完整。', emptyMetricCheck, response.traceId);
}

export async function submitMetricExceptionProof(
  metricItemId: string,
  payload: ExceptionProofRequest
): Promise<ApiResponse<ExceptionProofResponse>> {
  const normalizedId = metricItemId.trim();
  if (!normalizedId) return failure(422, '指标信息不完整。', emptyProof, 'frontend-stage-39-validation');
  if (!PROOF_REASON_TYPES.includes(payload.reasonType)) {
    return failure(422, '原因类型不在冻结字典内。', emptyProof, 'frontend-stage-39-validation');
  }
  if (!payload.reasonText.trim()) {
    return failure(422, '请填写未完成原因。', emptyProof, 'frontend-stage-39-validation');
  }
  if (payload.reasonText.trim().length > 500) {
    return failure(422, '未完成原因不能超过 500 个字符。', emptyProof, 'frontend-stage-39-validation');
  }
  if (payload.fileIds.length === 0 || payload.fileIds.some((item) => !item.trim()) || payload.fileIds.length > 10) {
    return failure(422, '原因证明文件凭证不完整。', emptyProof, 'frontend-stage-39-validation');
  }
  const response = await request<unknown>({
    method: 'POST',
    url: exceptionProofPath(normalizedId),
    data: {
      reasonType: payload.reasonType,
      reasonText: payload.reasonText.trim(),
      fileIds: payload.fileIds.map((item) => item.trim()).filter(Boolean)
    }
  });
  if (response.code !== 0) return { ...response, data: emptyProof };
  const data = normalizeProof(response.data);
  return data ? { ...response, data } : failure(502, '未完成原因证明响应不完整。', emptyProof, response.traceId);
}

export async function getNurseExceptionProofs(orderId: string): Promise<ApiResponse<ExceptionProofResponse[]>> {
  const normalizedId = orderId.trim();
  if (!normalizedId) return failure(422, '订单信息不完整。', [], 'frontend-stage-39-validation');
  const response = await request<unknown>({ method: 'GET', url: nurseExceptionProofsPath(normalizedId) });
  if (response.code !== 0) return { ...response, data: [] };
  const data = normalizeProofList(response.data);
  return data ? { ...response, data } : failure(502, '未完成原因证明列表响应不完整。', [], response.traceId);
}

export async function getAdminExceptionProofs(): Promise<ApiResponse<ProofReviewResponse[]>> {
  const response = await request<unknown>({ method: 'GET', url: '/admin/metric-exception-proofs' });
  if (response.code !== 0) return { ...response, data: [] };
  const data = normalizeProofReviewList(response.data);
  return data ? { ...response, data } : failure(502, '豁免审核队列响应不完整。', [], response.traceId);
}

export async function reviewMetricExceptionProof(
  proofId: string,
  payload: ProofReviewRequest
): Promise<ApiResponse<ProofReviewResponse>> {
  const normalizedId = proofId.trim();
  if (!normalizedId) return failure(422, '证明记录不完整。', emptyProofReview, 'frontend-stage-40-validation');
  const validationError = validateProofReview(payload);
  if (validationError) return failure(422, validationError, emptyProofReview, 'frontend-stage-40-validation');
  const response = await request<unknown>({
    method: 'POST',
    url: proofReviewPath(normalizedId),
    data: {
      reviewResult: payload.reviewResult,
      reviewComment: payload.reviewComment?.trim() || '',
      scoreDecision: payload.scoreDecision
    }
  });
  if (response.code !== 0) return { ...response, data: emptyProofReview };
  const data = normalizeProofReview(response.data);
  return data ? { ...response, data } : failure(502, '豁免审核响应不完整。', emptyProofReview, response.traceId);
}
