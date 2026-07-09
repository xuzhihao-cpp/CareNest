import type { RoleCode } from './stageOne';

export interface HomeSummaryRequest {
  role: RoleCode;
  currentUserId: string;
}

export interface HomeCard {
  key: string;
  label: string;
  value: string;
  unit: string;
  trend: string;
}

export interface HomeQuickAction {
  key: string;
  label: string;
  path: string;
  permissionCode: string;
}

export interface HomeSummaryResponse {
  cards: HomeCard[];
  quickActions: HomeQuickAction[];
  todoCount: number;
}

