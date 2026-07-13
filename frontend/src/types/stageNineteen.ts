export type ArchiveVersion = string | number;

export type DiseaseStatus = 'ACTIVE' | 'MONITORING' | 'STABLE' | 'RESOLVED';

export type MedicationFrequency =
  | 'ONCE_DAILY'
  | 'TWICE_DAILY'
  | 'THREE_TIMES_DAILY'
  | 'EVERY_OTHER_DAY'
  | 'WEEKLY'
  | 'AS_NEEDED';

export type AllergySeverity = 'MILD' | 'MODERATE' | 'SEVERE';

export interface ChronicDiseaseItem {
  diseaseName: string;
  diagnosedAt?: string;
  status: DiseaseStatus;
  remark?: string;
}

export interface MedicationItem {
  medicationName: string;
  dosage?: string;
  frequency: MedicationFrequency;
  timePoints: string[];
  startDate: string;
  endDate?: string;
  remark?: string;
}

export interface AllergyItem {
  allergenName: string;
  reaction?: string;
  severity: AllergySeverity;
  remark?: string;
}

export interface HealthRiskTag {
  tagCode: string;
  tagName: string;
}

export interface CarePlanContent {
  careGoals: string;
  dailyCare: string;
  precautions: string;
}

export interface HealthArchiveResponse {
  elderId: string;
  archiveVersion: ArchiveVersion;
  diseases: ChronicDiseaseItem[];
  medications: MedicationItem[];
  allergies: AllergyItem[];
  riskTags: HealthRiskTag[];
  carePlan: CarePlanContent;
  updatedAt?: string;
}

export interface HealthArchiveUpdateRequest {
  archiveVersion: ArchiveVersion;
  diseases: ChronicDiseaseItem[];
  medications: MedicationItem[];
  allergies: AllergyItem[];
  riskTags: string[];
  carePlan: CarePlanContent;
}

export interface HealthArchiveUpdateResult {
  archiveVersion: ArchiveVersion;
  updatedAt?: string;
}

export type MedicationCreateRequest = MedicationItem & {
  archiveVersion: ArchiveVersion;
};

export interface MedicationCreateResult {
  archiveVersion: ArchiveVersion;
  medication: MedicationItem;
}

export interface HealthArchiveDraft {
  archiveVersion: ArchiveVersion;
  diseases: ChronicDiseaseItem[];
  medications: MedicationItem[];
  allergies: AllergyItem[];
  riskTags: string[];
  carePlan: CarePlanContent;
}
