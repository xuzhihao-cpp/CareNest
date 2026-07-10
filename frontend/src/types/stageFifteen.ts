export type StageFifteenScenario = 'normal' | 'empty' | 'error';

export interface ServiceReportResponse {
  reportId: string;
  orderId: string;
  summary: string;
  vitalSigns: string[];
  serviceRecords: string[];
  nursingAdvice: string;
}

export interface ServiceReportRecord extends ServiceReportResponse {
  generatedBy: string;
  generatedAt: string;
}
