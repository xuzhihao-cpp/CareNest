export type StageImplementationStatus = 'READY' | 'PENDING';

export interface StageImplementationMetadata {
  stage: string;
  status: StageImplementationStatus;
  backendModule: string;
}

export const stageImplementationStatus: StageImplementationMetadata[] = [
  { stage: '6', status: 'READY', backendModule: 'backend-user' },
  { stage: '7', status: 'READY', backendModule: 'backend-user' },
  { stage: '8', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '9', status: 'READY', backendModule: 'backend-user' },
  { stage: '10', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '11', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '12', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '13', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '14', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '15', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '16', status: 'READY', backendModule: 'backend-user' },
  { stage: '17', status: 'READY', backendModule: 'backend-care-admin' },
  { stage: '18', status: 'READY', backendModule: 'backend-user' }
];
