import nurseTasksEmptyMock from '@/mock/phase-13/nurse-tasks-empty.json';
import nurseTasksErrorMock from '@/mock/phase-13/nurse-tasks-error.json';
import nurseTasksMock from '@/mock/phase-13/nurse-tasks.json';
import { failure, isMockEnabled, readAuthSession, request, success } from '@/api/client';
import {
  STAGE_TWELVE_ORDER_STORAGE_KEY,
  STAGE_TWELVE_TASKS_STORAGE_KEY
} from '@/api/stageTwelve';
import type { ApiResponse } from '@/types/api';
import type { AdminOrderRecord } from '@/types/stageEleven';
import type { NurseTaskRecord } from '@/types/stageTwelve';
import type {
  NurseTaskDetailRecord,
  StageThirteenScenario,
  StageThirteenTaskPageResult,
  StageThirteenTaskQuery
} from '@/types/stageThirteen';

const nurseTasksPath = '/nurse/tasks';
const nurseTaskDetailPath = (taskId: string) => `/nurse/tasks/${taskId}`;

const defaultQuery: StageThirteenTaskQuery = {
  status: '',
  page: 1,
  size: 10
};

function seedTasks(): NurseTaskDetailRecord[] {
  return [...(nurseTasksMock as ApiResponse<StageThirteenTaskPageResult>).data.records];
}

function readStageTwelveTasks(): NurseTaskRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_TASKS_STORAGE_KEY);
  return stored ? (stored as NurseTaskRecord[]) : [];
}

function readOrderOverrides(): AdminOrderRecord[] {
  const stored = uni.getStorageSync(STAGE_TWELVE_ORDER_STORAGE_KEY);
  return stored ? (stored as AdminOrderRecord[]) : [];
}

function requireTaskViewer<T>(emptyData: T): ApiResponse<T> | null {
  const session = readAuthSession();
  if (!session) {
    return failure(401, '未登录', emptyData, 'mock-13-unauthorized');
  }
  if (!session.user.roles.includes('NURSE') && !session.user.roles.includes('ADMIN')) {
    return failure(403, '无权限', emptyData, 'mock-13-forbidden');
  }
  return null;
}

function timelineFromOrder(task: NurseTaskRecord, order?: AdminOrderRecord): NurseTaskDetailRecord['statusTimeline'] {
  if (order?.statusLogs.length) {
    return order.statusLogs.map((log, index) => ({
      status: log.toStatus,
      label: log.changeReason,
      at: `日志 ${index + 1}`
    }));
  }
  return [
    {
      status: task.taskStatus,
      label: task.dispatchRemark || '护理任务状态',
      at: task.scheduledStart.slice(0, 10)
    }
  ];
}

function toDetail(task: NurseTaskRecord): NurseTaskDetailRecord {
  const order = readOrderOverrides().find((item) => item.orderId === task.orderId);
  const orderSnapshotStatus = order?.orderStatus ?? task.orderStatus;
  return {
    ...task,
    orderSnapshotStatus,
    statusConsistent: task.orderStatus === orderSnapshotStatus && task.taskStatus === orderSnapshotStatus,
    statusTimeline: timelineFromOrder(task, order)
  };
}

function readRecords(): NurseTaskDetailRecord[] {
  const stageTwelveTasks = readStageTwelveTasks().map(toDetail);
  return stageTwelveTasks.length > 0 ? stageTwelveTasks : seedTasks();
}

function visibleRecords(records: NurseTaskDetailRecord[]) {
  const session = readAuthSession();
  if (session?.user.roles.includes('NURSE')) {
    return records.filter((item) => item.nurseId === session.user.userId);
  }
  return records;
}

function matchQuery(record: NurseTaskDetailRecord, query: StageThirteenTaskQuery) {
  return !query.status || record.taskStatus === query.status;
}

function toPage(records: NurseTaskDetailRecord[], query: StageThirteenTaskQuery): StageThirteenTaskPageResult {
  return {
    records,
    total: records.length,
    page: query.page,
    size: query.size
  };
}

export function getStageThirteenEndpointSummary() {
  return ['GET /api/v1/nurse/tasks', 'GET /api/v1/nurse/tasks/{taskId}'];
}

export async function getNurseTasks(
  query: Partial<StageThirteenTaskQuery> = {},
  scenario: StageThirteenScenario = 'normal'
): Promise<ApiResponse<StageThirteenTaskPageResult>> {
  const nextQuery = { ...defaultQuery, ...query };
  if (isMockEnabled()) {
    const denied = requireTaskViewer(toPage([], nextQuery));
    if (denied) {
      return denied;
    }
    if (scenario === 'empty') {
      return nurseTasksEmptyMock as ApiResponse<StageThirteenTaskPageResult>;
    }
    if (scenario === 'error') {
      return nurseTasksErrorMock as ApiResponse<StageThirteenTaskPageResult>;
    }
    return success(
      toPage(visibleRecords(readRecords()).filter((record) => matchQuery(record, nextQuery)), nextQuery),
      'mock-13-nurse-tasks'
    );
  }

  return request<StageThirteenTaskPageResult>({
    method: 'GET',
    url: nurseTasksPath,
    data: nextQuery,
    mock: nurseTasksMock as ApiResponse<StageThirteenTaskPageResult>
  });
}

export async function getNurseTaskDetail(taskId: string): Promise<ApiResponse<StageThirteenTaskPageResult>> {
  if (isMockEnabled()) {
    const denied = requireTaskViewer(toPage([], defaultQuery));
    if (denied) {
      return denied;
    }
    const found = visibleRecords(readRecords()).find((item) => item.taskId === taskId);
    if (!found) {
      return failure(404, '数据不存在', toPage([], defaultQuery), 'mock-13-task-not-found');
    }
    return success(toPage([found], defaultQuery), 'mock-13-nurse-task-detail');
  }

  return request<StageThirteenTaskPageResult>({
    method: 'GET',
    url: nurseTaskDetailPath(taskId)
  });
}
