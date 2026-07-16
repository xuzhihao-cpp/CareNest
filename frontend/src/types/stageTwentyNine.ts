export interface NurseRecommendationRequest {
  elderId: string;
  serviceId: string;
  scheduledStart: string;
  addressId: string;
}

export interface NurseRecommendationRecord {
  nurseId: string;
  nurseName: string;
  score: number;
  matchedSkills: string[];
  recommendReason: string;
  available: boolean;
}

export interface NurseRecommendationResult {
  nurses: NurseRecommendationRecord[];
}
