import type { ArchiveVersion } from '@/types/stageNineteen';
import type {
  HealthReviewSourceType,
  HealthReviewTaskStatus,
  HealthSuggestionFieldName
} from '@/types/stageTwentyThree';

export type HealthArchiveFieldDecision = 'APPROVE' | 'REJECT' | 'NEED_MORE';

export interface HealthReviewEvidence {
  sourceType: HealthReviewSourceType;
  title: string;
  summary: string;
  occurredAt?: string;
}

export interface HealthReviewFieldDetail {
  sourceField: string;
  targetField: HealthSuggestionFieldName;
  fieldLabel: string;
  currentValue: unknown;
  suggestedValue: unknown;
  normalizedValue: unknown;
  normalizationNote?: string;
}

export interface HealthReviewTaskDetail {
  taskId: string;
  status: HealthReviewTaskStatus;
  elderId: string;
  elderName: string;
  serviceName: string;
  submittedAt: string;
  archiveVersion: ArchiveVersion;
  evidence: HealthReviewEvidence;
  fields: HealthReviewFieldDetail[];
}

export interface HealthArchiveFieldDecisionRequest {
  sourceField: string;
  targetField: HealthSuggestionFieldName;
  normalizedValue: unknown;
  decision: HealthArchiveFieldDecision;
  comment: string;
}

export interface HealthReviewArchiveRequest {
  decisions: HealthArchiveFieldDecisionRequest[];
}

export interface HealthReviewArchiveResult {
  taskId: string;
  status: HealthReviewTaskStatus;
  archiveVersion: ArchiveVersion;
}

export interface HealthArchiveChangeLogRecord {
  changeLogId: string;
  fieldName: HealthSuggestionFieldName | '';
  fieldLabel: string;
  beforeValue: unknown;
  afterValue: unknown;
  sourceType?: HealthReviewSourceType;
  sourceSummary?: string;
  comment?: string;
  archiveVersion?: ArchiveVersion;
  changedAt: string;
}

export interface HealthArchiveChangeLogResult {
  records: HealthArchiveChangeLogRecord[];
}

export interface HealthArchiveDecisionDraft {
  sourceField: string;
  targetField: HealthSuggestionFieldName;
  normalizedValue: unknown;
  comment: string;
  fieldLabel: string;
  decision: HealthArchiveFieldDecision | '';
}
