export type HealthFeedbackType = 'PAIN' | 'DIZZINESS' | 'SLEEP' | 'DIET' | 'MENTAL_STATE';
export type HealthFeedbackSeverity = 'LOW' | 'MEDIUM' | 'HIGH';
export type HealthFeedbackInputType = 'BUTTON' | 'TEXT' | 'VOICE';

export interface HealthFeedbackRequest {
  feedbackType: HealthFeedbackType;
  severity: HealthFeedbackSeverity;
  content: string;
  inputType: HealthFeedbackInputType;
  fileId: string | null;
}

export interface HealthFeedbackCreateResult {
  feedbackId: string;
  createdAt: string;
}

export interface HealthFeedbackRecord extends HealthFeedbackCreateResult {
  elderId: string;
  elderName?: string;
  feedbackType: HealthFeedbackType;
  severity: HealthFeedbackSeverity;
  content: string;
  inputType: HealthFeedbackInputType;
  voiceUrl?: string;
}

export interface HealthFeedbackQuery {
  page: number;
  size: number;
  feedbackType: HealthFeedbackType | '';
  severity: HealthFeedbackSeverity | '';
  dateFrom: string;
  dateTo: string;
}

export interface HealthFeedbackPageResult {
  records: HealthFeedbackRecord[];
  total: number;
  page: number;
  size: number;
}

export interface SelectedVoiceFile {
  path: string;
  name: string;
  size: number;
  mimeType: string;
  durationSeconds?: number;
}

export interface HealthFeedbackPlaybackSource {
  playbackUrl: string;
  revokeOnRelease: boolean;
}
