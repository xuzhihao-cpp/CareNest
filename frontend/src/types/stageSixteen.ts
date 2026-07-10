export type StageSixteenScenario = 'normal' | 'empty' | 'error';

export type ReportAckResult = 'ACCEPTED' | 'REJECTED';

export type ServiceReportStatus = 'WAIT_CONFIRM' | 'CONFIRMED' | 'REJECTED';

export interface ReportAckRequest {
  ackResult: ReportAckResult;
  satisfaction: number;
  remark: string;
  acceptedSuggestionIds: string[];
}

export interface ReportAckResponse {
  ackId: string;
  ackResult: ReportAckResult;
  reportStatus: ServiceReportStatus;
}

export interface ReportAckRecord extends ReportAckResponse {
  reportId: string;
  orderId: string;
  operatorId: string;
  operatorRole: 'ELDER' | 'FAMILY';
  satisfaction: number;
  remark: string;
  acceptedSuggestionIds: string[];
  orderStatus: 'WAIT_REPORT' | 'COMPLETED';
  createdAt: string;
}

export interface HealthInfoReviewTaskRecord {
  taskId: string;
  reportId: string;
  orderId: string;
  suggestionId: string;
  fieldName: string;
  newValue: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
}
