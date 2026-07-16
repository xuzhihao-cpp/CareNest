import type {
  QualificationApplicationRecord,
  QualificationAuditStatus
} from './stageTwentySix';

export type TrainingReviewStatus = QualificationAuditStatus;
export type TrainingDisplayStatus = TrainingReviewStatus | 'EXPIRED';

export interface TrainingReviewRequest {
  status: TrainingReviewStatus;
  trainingBatch: string;
  expiredAt: string;
  remark: string;
}

export interface TrainingReviewResult {
  nurseId: string;
  trainingStatus: TrainingReviewStatus;
  expiredAt: string;
}

export interface CompletedTrainingReview {
  nurseName: string;
  qualification: QualificationApplicationRecord;
  result: TrainingReviewResult;
}
