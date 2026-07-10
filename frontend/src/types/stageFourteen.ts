import type { PageResult } from './api';
import type { AdminOrderStatus } from './stageEleven';

export type StageFourteenScenario = 'normal' | 'empty' | 'error';

export interface CareServiceRecordRequest {
  startTime: string;
  endTime: string;
  content: string;
  nursingAdvice: string;
  abnormalFlag: boolean;
}

export interface VitalSignRecordRequest {
  startTime: string;
  endTime: string;
  content: string;
  nursingAdvice: string;
  abnormalFlag: boolean;
}

export interface CareExecutionResponse {
  recordId: string;
  orderId: string;
  orderStatus: AdminOrderStatus;
}

export interface CareExecutionRecord extends CareExecutionResponse {
  recordType: 'SERVICE_RECORD' | 'VITAL_SIGN';
  taskId: string;
  nurseId: string;
  startTime: string;
  endTime: string;
  content: string;
  nursingAdvice: string;
  abnormalFlag: boolean;
}

export type CareExecutionPageResult = PageResult<CareExecutionRecord>;
