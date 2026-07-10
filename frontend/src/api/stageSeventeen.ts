import orderChangeEmptyMock from '@/mock/phase-17/order-change-empty.json';
import orderChangeErrorMock from '@/mock/phase-17/order-change-error.json';
import orderChangeMock from '@/mock/phase-17/order-change.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import { getStageSixBindingSnapshot } from '@/api/stageSix';
import { STAGE_TEN_STORAGE_KEY } from '@/api/stageTen';
import {
  STAGE_TWELVE_ORDER_STORAGE_KEY,
  STAGE_TWELVE_TASKS_STORAGE_KEY
} from '@/api/stageTwelve';
import type { ApiResponse } from '@/types/api';
import type { AdminOrderRecord, AdminOrderStatus, OrderStatusLogRecord } from '@/types/stageEleven';
import type { FamilyOrderDetail } from '@/types/stageTen';
import type { NurseTaskRecord } from '@/types/stageTwelve';
import type {
  OrderChangeRequest,
  OrderChangeResponse,
  StageSeventeenScenario
} from '@/types/stageSeventeen';

const familyCancelPath = (orderId: string) => `/family/orders/${orderId}/cancel`;
const familyReschedulePath = (orderId: string) => `/family/orders/${orderId}/reschedule`;
const adminCancelPath = (orderId: string) => `/admin/orders/${orderId}/cancel`;

function emptyChange(): OrderChangeResponse {
  return {
    orderId: '',
    orderStatus: 'WAIT_DISPATCH',
    scheduledStart: ''
  };
}

function seedFamilyOrders(): FamilyOrderDetail[] {
  return [
    {
      orderId: 'order-001',
      orderNo: 'NO202607100001',
      orderStatus: 'WAIT_DISPATCH',
      elderId: 'elder-001',
      serviceId: 'service-001',
      addressId: 'address-001',
      scheduledStart: '2026-07-10T09:00',
      preferredNurseId: '',
      remark: '阶段17取消改期演示订单'
    }
  ];
}

function readFamilyOrders(): FamilyOrderDetail[] {
  const stored = uni.getStorageSync(STAGE_TEN_STORAGE_KEY);
  return stored ? (stored as FamilyOrderDetail[]) : seedFamilyOrders();
}

function writeFamilyOrders(records: FamilyOrderDetail[]) {
  uni.setStorageSync(STAGE_TEN_STORAGE_KEY, records);
}

function readAdminOrders(): AdminOrderRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY);
  return stored ? (stored as AdminOrderRecord[]) : [];
}

function writeAdminOrders(records: AdminOrderRecord[]) {
  uni.setStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY, records);
}

function readTasks(): NurseTaskRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY);
  return stored ? (stored as NurseTaskRecord[]) : [];
}

function writeTasks(records: NurseTaskRecord[]) {
  uni.setStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY, records);
}

function statusLog(
  orderId: string,
  index: number,
  fromStatus: AdminOrderStatus | '',
  toStatus: AdminOrderStatus,
  changedBy: string,
  changeReason: string
): OrderStatusLogRecord {
  return {
    statusLogId: `order-status-log-${String(index + 1).padStart(3, '0')}`,
    orderId,
    fromStatus,
    toStatus,
    changedBy,
    changeReason
  };
}

function toAdminOrder(order: FamilyOrderDetail): AdminOrderRecord {
  return {
    orderId: order.orderId,
    orderNo: order.orderNo,
    orderStatus: order.orderStatus,
    elderId: order.elderId,
    serviceId: order.serviceId,
    addressId: order.addressId,
    scheduledStart: order.scheduledStart,
    statusLogs: [
      statusLog(order.orderId, 0, '', order.orderStatus, 'family-001', '阶段17生成管理端同步记录')
    ]
  };
}

function requireFamilyPermission(order: FamilyOrderDetail): ApiResponse<OrderChangeResponse> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyChange(), 'mock-17-unauthorized');
  }
  if (!session.user.roles.includes('FAMILY')) {
    return failure(403, '无权限', emptyChange(), 'mock-17-family-forbidden');
  }
  const allowed = getStageSixBindingSnapshot().some(
    (binding) =>
      binding.elderId === order.elderId &&
      binding.bindingStatus === 'ACTIVE' &&
      binding.scopeCodes.includes('ORDER_CREATE')
  );
  if (!allowed) {
    return failure(403, '无权限', emptyChange(), 'mock-17-family-binding-scope-forbidden');
  }
  return null;
}

function requireAdminPermission(): ApiResponse<OrderChangeResponse> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyChange(), 'mock-17-unauthorized');
  }
  if (!session.user.roles.includes('ADMIN')) {
    return failure(403, '无权限', emptyChange(), 'mock-17-admin-forbidden');
  }
  return null;
}

function validatePayload(payload: OrderChangeRequest, action: 'cancel' | 'reschedule') {
  if (!payload.reason.trim()) {
    return false;
  }
  return action === 'cancel' || payload.newScheduledStart.trim().length > 0;
}

function canChangeOrder(status: AdminOrderStatus) {
  return ['WAIT_DISPATCH', 'DISPATCHED', 'ACCEPTED', 'ON_THE_WAY'].includes(status);
}

function upsertAdminOrder(
  order: FamilyOrderDetail,
  nextStatus: AdminOrderStatus,
  scheduledStart: string,
  operatorId: string,
  reason: string
) {
  const adminOrders = readAdminOrders();
  const found = adminOrders.find((item) => item.orderId === order.orderId) ?? toAdminOrder(order);
  const next: AdminOrderRecord = {
    ...found,
    orderStatus: nextStatus,
    scheduledStart,
    statusLogs: [
      ...found.statusLogs,
      statusLog(found.orderId, found.statusLogs.length, found.orderStatus, nextStatus, operatorId, reason)
    ]
  };
  writeAdminOrders([next, ...adminOrders.filter((item) => item.orderId !== order.orderId)]);
}

function syncTasks(orderId: string, nextStatus: AdminOrderStatus, scheduledStart: string) {
  const tasks = readTasks();
  writeTasks(
    tasks.map((task) =>
      task.orderId === orderId
        ? {
            ...task,
            orderStatus: nextStatus,
            taskStatus:
              nextStatus === 'DISPATCHED' ||
              nextStatus === 'ACCEPTED' ||
              nextStatus === 'ON_THE_WAY' ||
              nextStatus === 'SERVING' ||
              nextStatus === 'WAIT_REPORT' ||
              nextStatus === 'WAIT_CONFIRM' ||
              nextStatus === 'COMPLETED'
                ? nextStatus
                : task.taskStatus,
            scheduledStart
          }
        : task
    )
  );
}

function applyFamilyOrderChange(
  orderId: string,
  payload: OrderChangeRequest,
  action: 'cancel' | 'reschedule',
  operatorId: string
): ApiResponse<OrderChangeResponse> {
  if (!validatePayload(payload, action)) {
    return failure(422, '业务规则不满足', emptyChange(), 'mock-17-order-change-invalid');
  }
  const orders = readFamilyOrders();
  const order = orders.find((item) => item.orderId === orderId);
  if (!order) {
    return orderChangeEmptyMock as ApiResponse<OrderChangeResponse>;
  }
  const denied = requireFamilyPermission(order);
  if (denied) {
    return denied;
  }
  if (!canChangeOrder(order.orderStatus)) {
    return failure(409, '状态冲突', emptyChange(), 'mock-17-order-status-conflict');
  }

  const nextStatus: AdminOrderStatus = action === 'cancel' ? 'CANCELED' : order.orderStatus;
  const scheduledStart = action === 'reschedule' ? payload.newScheduledStart : order.scheduledStart;
  const nextOrder: FamilyOrderDetail = {
    ...order,
    orderStatus: nextStatus,
    scheduledStart,
    remark: payload.reason
  };
  writeFamilyOrders(orders.map((item) => (item.orderId === orderId ? nextOrder : item)));
  upsertAdminOrder(nextOrder, nextStatus, scheduledStart, operatorId, payload.reason);
  syncTasks(orderId, nextStatus, scheduledStart);

  return success(
    {
      orderId,
      orderStatus: nextStatus,
      scheduledStart
    },
    action === 'cancel' ? 'mock-17-family-order-cancel' : 'mock-17-family-order-reschedule'
  );
}

function applyAdminCancel(
  orderId: string,
  payload: OrderChangeRequest
): ApiResponse<OrderChangeResponse> {
  const denied = requireAdminPermission();
  if (denied) {
    return denied;
  }
  if (!validatePayload(payload, 'cancel')) {
    return failure(422, '业务规则不满足', emptyChange(), 'mock-17-admin-cancel-invalid');
  }

  const familyOrders = readFamilyOrders();
  const familyOrder = familyOrders.find((item) => item.orderId === orderId);
  const adminOrder = readAdminOrders().find((item) => item.orderId === orderId);
  const base = familyOrder ?? (adminOrder ? {
    orderId: adminOrder.orderId,
    orderNo: adminOrder.orderNo,
    orderStatus: adminOrder.orderStatus,
    elderId: adminOrder.elderId,
    serviceId: adminOrder.serviceId,
    addressId: adminOrder.addressId,
    scheduledStart: adminOrder.scheduledStart,
    preferredNurseId: '',
    remark: payload.reason
  } satisfies FamilyOrderDetail : null);

  if (!base) {
    return orderChangeEmptyMock as ApiResponse<OrderChangeResponse>;
  }
  if (!canChangeOrder(base.orderStatus)) {
    return failure(409, '状态冲突', emptyChange(), 'mock-17-admin-order-status-conflict');
  }

  const nextOrder: FamilyOrderDetail = {
    ...base,
    orderStatus: 'CANCELED',
    remark: payload.reason
  };
  writeFamilyOrders(
    familyOrder
      ? familyOrders.map((item) => (item.orderId === orderId ? nextOrder : item))
      : [nextOrder, ...familyOrders]
  );
  upsertAdminOrder(nextOrder, 'CANCELED', nextOrder.scheduledStart, readAuthSession()?.user.userId ?? 'admin-001', payload.reason);
  syncTasks(orderId, 'CANCELED', nextOrder.scheduledStart);

  return success(
    {
      orderId,
      orderStatus: 'CANCELED',
      scheduledStart: nextOrder.scheduledStart
    },
    'mock-17-admin-order-cancel'
  );
}

export function getStageSeventeenEndpointSummary() {
  return [
    'POST /api/v1/family/orders/{orderId}/cancel',
    'POST /api/v1/family/orders/{orderId}/reschedule',
    'POST /api/v1/admin/orders/{orderId}/cancel'
  ];
}

export function resetStageSeventeenMockRecords() {
  const orders = seedFamilyOrders();
  writeFamilyOrders(orders);
  writeAdminOrders(orders.map(toAdminOrder));
}

export async function cancelFamilyOrder(
  orderId: string,
  payload: OrderChangeRequest,
  scenario: StageSeventeenScenario = 'normal'
): Promise<ApiResponse<OrderChangeResponse>> {
  if (isMockEnabled()) {
    if (scenario === 'empty') {
      return orderChangeEmptyMock as ApiResponse<OrderChangeResponse>;
    }
    if (scenario === 'error') {
      return orderChangeErrorMock as ApiResponse<OrderChangeResponse>;
    }
    return applyFamilyOrderChange(orderId, payload, 'cancel', readAuthSession()?.user.userId ?? 'family-001');
  }

  return request<OrderChangeResponse>({
    method: 'POST',
    url: familyCancelPath(orderId),
    data: payload,
    mock: orderChangeMock as ApiResponse<OrderChangeResponse>
  });
}

export async function rescheduleFamilyOrder(
  orderId: string,
  payload: OrderChangeRequest,
  scenario: StageSeventeenScenario = 'normal'
): Promise<ApiResponse<OrderChangeResponse>> {
  if (isMockEnabled()) {
    if (scenario === 'empty') {
      return orderChangeEmptyMock as ApiResponse<OrderChangeResponse>;
    }
    if (scenario === 'error') {
      return orderChangeErrorMock as ApiResponse<OrderChangeResponse>;
    }
    return applyFamilyOrderChange(orderId, payload, 'reschedule', readAuthSession()?.user.userId ?? 'family-001');
  }

  return request<OrderChangeResponse>({
    method: 'POST',
    url: familyReschedulePath(orderId),
    data: payload,
    mock: orderChangeMock as ApiResponse<OrderChangeResponse>
  });
}

export async function cancelAdminOrder(
  orderId: string,
  payload: OrderChangeRequest,
  scenario: StageSeventeenScenario = 'normal'
): Promise<ApiResponse<OrderChangeResponse>> {
  if (isMockEnabled()) {
    if (scenario === 'empty') {
      return orderChangeEmptyMock as ApiResponse<OrderChangeResponse>;
    }
    if (scenario === 'error') {
      return orderChangeErrorMock as ApiResponse<OrderChangeResponse>;
    }
    return applyAdminCancel(orderId, payload);
  }

  return request<OrderChangeResponse>({
    method: 'POST',
    url: adminCancelPath(orderId),
    data: payload,
    mock: orderChangeMock as ApiResponse<OrderChangeResponse>
  });
}
