export type CareMetricType = 'PRE_SERVICE' | 'SERVICE_PROCESS' | 'POST_SERVICE';
export type CareMetricEvidenceType = 'NONE' | 'PHOTO' | 'FILE' | 'TEXT' | 'VITAL_SIGN';
export type CareMetricStatus =
  | 'PENDING'
  | 'SUBMITTED'
  | 'PASS'
  | 'MISSING'
  | 'PENDING_PROOF'
  | 'EXEMPT_APPROVED'
  | 'EXEMPT_REJECTED';
export type EvidenceAuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEED_MORE';
export type ExceptionProofStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type ProofReasonType =
  | 'FORGOT'
  | 'NOT_REQUIRED'
  | 'ELDER_REFUSED'
  | 'OBJECTIVE_IMPOSSIBLE'
  | 'OTHER';
export type ScoreDecision = 'NO_DEDUCTION' | 'DEDUCT';

export interface CareMetricConfigItem {
  metricCode: string;
  metricName: string;
  metricType: CareMetricType;
  required: boolean;
  evidenceType: CareMetricEvidenceType;
  scoreWeight: number;
  description?: string;
}

export interface CareMetricConfigRequest {
  items: CareMetricConfigItem[];
}

export interface ConfigVersionResponse {
  configVersion: number;
}

export interface MetricChecklistItem {
  itemId: string;
  metricCode: string;
  required: boolean;
  evidenceType: CareMetricEvidenceType;
  expectedAction: string;
  status: CareMetricStatus;
  scoreWeight: number;
}

export interface MetricChecklistResponse {
  items: MetricChecklistItem[];
}

export interface EvidenceRequest {
  metricItemId?: string;
  fileId?: string;
  evidenceType: CareMetricEvidenceType;
  description?: string;
}

export interface EvidenceResponse {
  evidenceId: string;
  auditStatus: EvidenceAuditStatus;
}

export interface EvidenceReviewRequest {
  auditStatus: Exclude<EvidenceAuditStatus, 'PENDING'>;
  reviewComment?: string;
}

export interface MetricCheckItem {
  metricItemId: string;
  metricName: string;
  checkResult: CareMetricStatus;
  scoreImpact: number;
  missingEvidence: boolean;
}

export interface MetricCheckResponse {
  items: MetricCheckItem[];
}

export interface ExceptionProofRequest {
  reasonType: ProofReasonType;
  reasonText: string;
  fileIds: string[];
}

export interface ExceptionProofResponse {
  proofId: string;
  reviewStatus: ExceptionProofStatus;
}

export interface ProofReviewRequest {
  reviewResult: Exclude<ExceptionProofStatus, 'PENDING'>;
  reviewComment?: string;
  scoreDecision: ScoreDecision;
}

export interface ProofReviewResponse {
  proofId: string;
  reviewStatus: ExceptionProofStatus;
  scoreDecision: ScoreDecision;
}
