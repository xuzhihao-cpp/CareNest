export type TrainingArticleStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';
export type TrainingReadStatus = 'UNREAD' | 'READ' | 'CONFIRMED';

export interface TrainingArticleInput {
  title: string;
  summary: string;
  contentUrl: string;
  tags: string[];
  serviceIds: string[];
  riskTags: string[];
  requiredRead: boolean;
  status: TrainingArticleStatus;
}

export interface TrainingArticle extends TrainingArticleInput {
  articleId: string;
}

export interface RecommendedTrainingArticle {
  articleId: string;
  title: string;
  summary: string;
  contentUrl: string;
  requiredRead: boolean;
  readStatus: TrainingReadStatus;
}

export type FollowUpType = 'PHONE' | 'ONLINE' | 'HOME' | 'AI' | 'CUSTOMER_SERVICE';

export interface FollowUpInput {
  elderId: string;
  orderId?: string;
  followUpType: FollowUpType;
  content: string;
  nextFollowUpAt?: string;
  needReminder: boolean;
}

export interface FollowUpRecord extends FollowUpInput {
  followUpId: string;
  createdAt?: string;
  createdReminderTaskId?: string;
}

export interface TrendPoint {
  date: string;
  value: number;
}

export interface BasicStatistics {
  cards: Record<string, string | number>;
  orderTrend: TrendPoint[];
  serviceCompletionRate: number;
  reminderDoneRate: number;
  satisfactionAvg: number;
}

export interface QualityStatistics {
  archiveCompleteRate: number;
  metricPassRate: number;
  exceptionApproveRate: number;
  scoreDistribution: Record<string, number>;
  qualityTrend: TrendPoint[];
}

export interface DemoDataStatus {
  ready: boolean;
  accounts: string[];
  scenarioCount: number;
  lastResetAt: string;
}

export interface ServiceHealthStatus {
  status: string;
  ready: boolean;
  databaseConnected: boolean;
  version: string;
  serverTime: string;
}
