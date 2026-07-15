import type { components } from './generated/user-api';

type Schemas = components['schemas'];

export type StageEighteenScenario = 'normal' | 'empty' | 'error';

export type HealthStatusResponse = Schemas['HealthResponse'];
export type DemoDataStatusResponse = Schemas['DemoDataStatusResponse'];

export interface StageEighteenFlowStep {
  stepId: string;
  label: string;
  ownerRole: 'ELDER' | 'FAMILY' | 'NURSE' | 'ADMIN' | 'ALL';
  sourceStage: string;
  status: 'READY' | 'PENDING';
}
