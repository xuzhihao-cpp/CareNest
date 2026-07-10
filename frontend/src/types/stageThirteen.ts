import type { PageResult } from './api';
import type { NurseTaskQuery, NurseTaskRecord } from './stageTwelve';

export type StageThirteenScenario = 'normal' | 'empty' | 'error';

export type StageThirteenTaskQuery = NurseTaskQuery;

export interface NurseTaskDetailRecord extends NurseTaskRecord {
  orderSnapshotStatus: string;
  statusConsistent: boolean;
  statusTimeline: Array<{
    status: string;
    label: string;
    at: string;
  }>;
}

export type StageThirteenTaskPageResult = PageResult<NurseTaskDetailRecord>;
