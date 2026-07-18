import { request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { NurseTaskRecord } from '@/types/stageTwelve';
import type {
  NurseTaskDetailRecord,
  StageThirteenScenario,
  StageThirteenTaskPageResult,
  StageThirteenTaskQuery
} from '@/types/stageThirteen';

const nurseTasksPath = '/nurse/tasks';
const nurseTaskDetailPath = (taskId: string) => `/nurse/tasks/${encodeURIComponent(taskId)}`;

const defaultQuery: StageThirteenTaskQuery = {
  status: '',
  page: 1,
  size: 10
};

type BackendTask = Omit<NurseTaskRecord, 'orderNo' | 'nurseName' | 'elderName' | 'serviceId' | 'serviceName' | 'addressId'> & {
  elderId?: string;
  nurseName?: string;
  elderName?: string;
  serviceName?: string;
};

type BackendTaskPage = {
  records: BackendTask[];
  total: number;
  page: number;
  size: number;
};

function fromBackendTask(task: BackendTask): NurseTaskDetailRecord {
  const normalized: NurseTaskRecord = {
    ...task,
    orderNo: task.orderId,
    nurseName: task.nurseName || '',
    elderId: task.elderId || '',
    elderName: task.elderName || '',
    serviceId: '',
    serviceName: task.serviceName || '',
    addressId: ''
  };
  return {
    ...normalized,
    orderSnapshotStatus: normalized.orderStatus,
    statusConsistent: normalized.taskStatus === normalized.orderStatus,
    statusTimeline: [{
      status: normalized.taskStatus,
      label: normalized.dispatchRemark || '护理任务状态已更新',
      at: normalized.scheduledStart
    }]
  };
}

function toPage(record: NurseTaskDetailRecord): StageThirteenTaskPageResult {
  return { records: [record], total: 1, page: 1, size: 1 };
}

export function getStageThirteenEndpointSummary() {
  return ['GET /api/v1/nurse/tasks', 'GET /api/v1/nurse/tasks/{taskId}'];
}

export async function getNurseTasks(
  query: Partial<StageThirteenTaskQuery> = {},
  _scenario: StageThirteenScenario = 'normal'
): Promise<ApiResponse<StageThirteenTaskPageResult>> {
  const nextQuery = { ...defaultQuery, ...query };
  const response = await request<BackendTaskPage>({
    method: 'GET',
    url: nurseTasksPath,
    data: nextQuery
  });
  return response.code === 0
    ? success({ ...response.data, records: response.data.records.map(fromBackendTask) }, response.traceId)
    : (response as unknown as ApiResponse<StageThirteenTaskPageResult>);
}

export async function getNurseTaskDetail(taskId: string): Promise<ApiResponse<StageThirteenTaskPageResult>> {
  const response = await request<BackendTask>({
    method: 'GET',
    url: nurseTaskDetailPath(taskId)
  });
  return response.code === 0
    ? success(toPage(fromBackendTask(response.data)), response.traceId)
    : (response as unknown as ApiResponse<StageThirteenTaskPageResult>);
}
