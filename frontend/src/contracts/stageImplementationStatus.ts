export type StageImplementationStatus = 'READY' | 'MOCK_ONLY' | 'PENDING';

export interface StageImplementationMetadata {
  stage: string;
  status: StageImplementationStatus;
  backendModule: string;
}

export const stageImplementationStatus: StageImplementationMetadata[] = [
  { stage: '6', status: 'READY', backendModule: 'backend-user' },
  { stage: '7', status: 'READY', backendModule: 'backend-user' },
  { stage: '8', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '9', status: 'READY', backendModule: 'backend-user' },
  { stage: '10', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '11', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '12', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '13', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '14', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '15', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '16', status: 'READY', backendModule: 'backend-user' },
  { stage: '17', status: 'MOCK_ONLY', backendModule: 'not implemented' },
  { stage: '18', status: 'READY', backendModule: 'backend-user' }
];
