import type { PageResult } from './api';
import type {
  AllergyItem,
  CarePlanContent,
  ChronicDiseaseItem,
  HealthRiskTag,
  MedicationItem
} from './stageNineteen';

export type HealthSuggestionFieldName =
  | 'diseases'
  | 'medications'
  | 'allergies'
  | 'riskTags'
  | 'carePlan';

export type HealthSuggestionSourceType = 'SERVICE_RECORD' | 'SERVICE_REPORT';

export type HealthReviewSourceType =
  | HealthSuggestionSourceType
  | 'MEDICAL_FILE'
  | 'REPORT_ACK'
  | 'SUGGESTION'
  | 'MANUAL';

export type HealthSuggestionStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';

export type HealthReviewTaskStatus = 'PENDING' | 'IN_REVIEW' | 'ARCHIVED' | 'REJECTED';

export type HealthSuggestionNewValue =
  | ChronicDiseaseItem
  | MedicationItem
  | AllergyItem
  | HealthRiskTag
  | CarePlanContent;

export interface HealthUpdateSuggestionRequest {
  fieldName: HealthSuggestionFieldName;
  newValue: HealthSuggestionNewValue;
  sourceType: HealthSuggestionSourceType;
  sourceId: string;
  reason: string;
}

export interface HealthUpdateSuggestionResult {
  suggestionId: string;
  status: HealthSuggestionStatus;
}

export interface HealthSuggestionSourceOption {
  sourceType: HealthSuggestionSourceType;
  sourceId: string;
  title: string;
  summary: string;
  occurredAt: string;
}

export interface AdminHealthReviewTaskRecord {
  taskId: string;
  suggestionId?: string;
  status: HealthReviewTaskStatus;
  elderName: string;
  serviceName: string;
  sourceType: HealthReviewSourceType;
  sourceSummary: string;
  fieldName: HealthSuggestionFieldName;
  currentValue: unknown;
  suggestedValue: unknown;
  reason: string;
  submittedAt: string;
}

export interface AdminHealthReviewTaskQuery {
  page: number;
  size: number;
  status: HealthReviewTaskStatus | '';
  sourceType: HealthReviewSourceType | '';
  keyword: string;
}

export type AdminHealthReviewTaskPageResult = PageResult<AdminHealthReviewTaskRecord>;
