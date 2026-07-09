import type { PageResult } from './api';

export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN';

export type CareLevel = 'LEVEL_1' | 'LEVEL_2' | 'LEVEL_3';

export type RelationType = 'SON' | 'DAUGHTER' | 'SPOUSE' | 'OTHER';

export type ElderProfileScenario = 'normal' | 'empty' | 'error';

export interface EmergencyContact {
  contactName: string;
  contactPhone: string;
  relationType: RelationType;
}

export interface ElderProfileRequest {
  name: string;
  gender: Gender;
  birthDate: string;
  careLevel: CareLevel;
  emergencyContacts: EmergencyContact[];
}

export interface ElderProfileResponse {
  elderId: string;
  profileVersion: number;
}

export interface ElderProfileDetail extends ElderProfileRequest, ElderProfileResponse {}

export type FamilyElderPageResult = PageResult<ElderProfileDetail>;
