import type { AdminOrderStatus } from './stageEleven';

export type StageSeventeenScenario = 'normal' | 'empty' | 'error';

export interface OrderChangeRequest {
  reason: string;
  newScheduledStart: string;
}

export interface OrderChangeResponse {
  orderId: string;
  orderStatus: AdminOrderStatus;
  scheduledStart: string;
}
