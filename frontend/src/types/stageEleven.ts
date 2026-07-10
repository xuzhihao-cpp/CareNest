import type { PageResult } from './api';

export type AdminOrderStatus =
  | 'WAIT_DISPATCH'
  | 'DISPATCHED'
  | 'ACCEPTED'
  | 'ON_THE_WAY'
  | 'SERVING'
  | 'WAIT_REPORT'
  | 'WAIT_CONFIRM'
  | 'COMPLETED'
  | 'CANCELED';

export type AdminOrderScenario = 'normal' | 'empty' | 'error';

export interface AdminOrderQuery {
  page: number;
  size: number;
  orderStatus: AdminOrderStatus | '';
  keyword: string;
  dateFrom: string;
  dateTo: string;
}

export interface OrderStatusLogRecord {
  statusLogId: string;
  orderId: string;
  fromStatus: AdminOrderStatus | '';
  toStatus: AdminOrderStatus;
  changedBy: string;
  changeReason: string;
}

export interface AdminOrderRecord {
  orderId: string;
  orderNo: string;
  orderStatus: AdminOrderStatus;
  elderId: string;
  serviceId: string;
  serviceName?: string;
  addressId: string;
  scheduledStart: string;
  contactName?: string;
  contactPhone?: string;
  remark?: string;
  statusLogs: OrderStatusLogRecord[];
}

export type AdminOrderPageResult = PageResult<AdminOrderRecord>;
