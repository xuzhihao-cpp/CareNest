export type StageEighteenScenario = 'normal' | 'empty' | 'error';

export interface StageEighteenStatusResponse {
  ready: boolean;
  accounts: number;
  scenarioCount: number;
}

export interface StageEighteenFlowStep {
  stepId: string;
  label: string;
  ownerRole: 'ELDER' | 'FAMILY' | 'NURSE' | 'ADMIN' | 'ALL';
  sourceStage: string;
  status: 'READY' | 'MOCK_ONLY' | 'PENDING';
}
