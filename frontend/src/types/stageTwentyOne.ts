import type { MedicalFileType } from '@/types/stageTwenty';

export type MedicalFileReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEED_MORE';
export type MedicalFileReviewStatusWire = MedicalFileReviewStatus | 'PENDING_REVIEW' | 'NEEDS_SUPPLEMENT';
export type MedicalFileReviewDecision = 'APPROVED' | 'REJECTED' | 'NEED_MORE';

export interface AdminMedicalFileQuery {
  page: number;
  size: number;
  auditStatus: MedicalFileReviewStatus | '';
  fileType: MedicalFileType | '';
  keyword: string;
  dateFrom: string;
  dateTo: string;
}

export interface AdminMedicalFileRecord {
  medicalFileId: string;
  fileId: string;
  elderId: string;
  elderName: string;
  fileType: MedicalFileType;
  title: string;
  occurredAt?: string;
  createdAt: string;
  auditStatus: MedicalFileReviewStatus;
  reviewComment?: string;
  reviewedAt?: string;
  uploaderName?: string;
}

export interface MedicalFileExtractedItem {
  fieldName: string;
  fieldLabel: string;
  value: string;
}

export interface AdminMedicalFileDetail extends AdminMedicalFileRecord {
  originalName?: string;
  mimeType?: string;
  size?: number;
  previewUrl?: string;
  downloadUrl?: string;
  extractedItems?: MedicalFileExtractedItem[];
}

export interface AdminMedicalFilePageResult {
  records: AdminMedicalFileRecord[];
  total: number;
  page: number;
  size: number;
}

export interface MedicalFileReviewRequest {
  auditStatus: MedicalFileReviewDecision;
  reviewComment: string;
  extractToArchive: boolean;
  extractedItems: MedicalFileExtractedItem[];
}

export interface MedicalFileReviewResult {
  fileId: string;
  auditStatus: MedicalFileReviewStatus;
  reviewedAt: string;
}
