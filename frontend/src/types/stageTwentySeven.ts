import type { QualificationApplicationRecord, QualificationAuditStatus } from './stageTwentySix';

export type QualificationReviewDecision = 'APPROVED' | 'REJECTED' | 'NEED_MORE';

export interface QualificationReviewRequest {
  auditStatus: QualificationReviewDecision;
  reviewComment: string;
}

export interface QualificationReviewResult {
  nurseId: string;
  qualificationStatus: QualificationAuditStatus;
}

export interface CompletedQualificationReview {
  applicationId: string;
  nurseName: string;
  decision: QualificationReviewDecision;
  record: QualificationApplicationRecord;
}
