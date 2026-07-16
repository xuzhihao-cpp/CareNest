import type { AdminOrderStatus } from './stageEleven';
import type { NurseRecommendationRecord } from './stageTwentyNine';

export interface PreferredNurseRequest {
  preferredNurseId: string;
}

export interface PreferredNurseResponse {
  orderId: string;
  preferredNurseId: string;
  recommendReason: string;
}

export interface PreferredNursePresentation extends PreferredNurseResponse {
  nurseName: string;
}

export interface AdminPreferenceSource {
  orderId: string;
  orderStatus: AdminOrderStatus;
  preferredNurseId?: string;
  preferredNurseName?: string;
  preferredNurseReason?: string;
}

export interface PreferenceResolution {
  presentation: PreferredNursePresentation | null;
  unresolved: boolean;
}

export type RecommendationSelection = Pick<
  NurseRecommendationRecord,
  'nurseId' | 'nurseName' | 'recommendReason' | 'available'
>;
