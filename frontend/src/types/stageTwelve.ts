import type { PageResult } from './api';
import type { AdminOrderStatus } from './stageEleven';

export type DispatchTargetStatus = 'DISPATCHED';

export type NurseTaskStatus =
  | 'DISPATCHED'
  | 'ACCEPTED'
  | 'ON_THE_WAY'
  | 'SERVING'
  | 'WAIT_REPORT'
  | 'WAIT_CONFIRM'
  | 'COMPLETED';

export type StageTwelveScenario = 'normal' | 'empty' | 'error';

export interface DispatchRequest {
  nurseId: string;
  dispatchRemark: string;
  targetStatus: DispatchTargetStatus;
}

export interface TaskStatusRequest {
  nurseId: string;
  dispatchRemark: string;
  targetStatus: NurseTaskStatus;
}

export interface TaskActionResponse {
  orderId: string;
  orderNo: string;
  orderStatus: AdminOrderStatus;
  taskId: string;
}

export interface NurseTaskRecord {
  taskId: string;
  orderId: string;
  orderNo: string;
  nurseId: string;
  nurseName: string;
  elderId: string;
  elderName?: string;
  serviceId: string;
  serviceName?: string;
  addressId: string;
  scheduledStart: string;
  dispatchRemark: string;
  taskStatus: NurseTaskStatus;
  orderStatus: AdminOrderStatus;
}

export interface NurseTaskQuery {
  status: NurseTaskStatus | '';
  page: number;
  size: number;
}

export type NurseTaskPageResult = PageResult<NurseTaskRecord>;
