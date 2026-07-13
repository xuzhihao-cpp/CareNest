import type {
  AllergyItem,
  CarePlanContent,
  ChronicDiseaseItem,
  HealthRiskTag,
  MedicationItem
} from '@/types/stageNineteen';
import type { MedicalFileType } from '@/types/stageTwenty';

export interface PreServiceElderProfile {
  elderName?: string;
  displayName?: string;
  name?: string;
  gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';
  birthDate?: string;
  age?: number;
  careLevel?: string;
  carePlan?: Partial<CarePlanContent>;
  carePoints?: string[];
}

export interface ApprovedMedicalFileSummary {
  title: string;
  fileType: MedicalFileType;
  occurredAt?: string;
  summary?: string;
  previewUrl?: string;
}

export interface RecentServiceReportSummary {
  serviceName?: string;
  occurredAt?: string;
  generatedAt?: string;
  summary: string;
  nursingAdvice?: string;
  vitalSigns?: string[];
}

export interface PreServiceHealthSummary {
  elderProfile: PreServiceElderProfile;
  riskTags: HealthRiskTag[];
  medications: MedicationItem[];
  diseases: ChronicDiseaseItem[];
  allergies: AllergyItem[];
  approvedMedicalFiles: ApprovedMedicalFileSummary[];
  recentReports: RecentServiceReportSummary[];
}
