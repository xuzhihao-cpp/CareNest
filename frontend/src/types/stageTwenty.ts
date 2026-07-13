export type MedicalFileType =
  | 'PRESCRIPTION'
  | 'EXAMINATION_REPORT'
  | 'DISCHARGE_SUMMARY'
  | 'MEDICAL_RECORD';

export type MedicalFileAuditStatus =
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'NEED_MORE';

export type MedicalFileAuditStatusWire =
  | MedicalFileAuditStatus
  | 'PENDING'
  | 'NEEDS_SUPPLEMENT';

export interface SelectedMedicalFile {
  path: string;
  name: string;
  size: number;
  mimeType?: string;
  rawFile?: Blob;
  objectUrl?: string;
}

export interface FileAssetUploadResult {
  fileId: string;
  fileName?: string;
  contentType?: string;
  size?: number;
}

export interface MedicalFileRegisterRequest {
  fileId: string;
  fileType: MedicalFileType;
  title: string;
  occurredAt: string;
}

export interface MedicalFileRegisterResult {
  medicalFileId: string;
  fileId: string;
  auditStatus: MedicalFileAuditStatus;
}

export interface MedicalFileRecord extends MedicalFileRegisterResult {
  fileType: MedicalFileType;
  title: string;
  occurredAt: string;
  uploadedAt: string;
  originalFileName?: string;
  fileSize?: number;
  auditOpinion?: string;
  previewUrl?: string;
  downloadUrl?: string;
}

export type MedicalFileTypeFilter = 'ALL' | MedicalFileType;
export type MedicalFileStatusFilter = 'ALL' | MedicalFileAuditStatus;

export type UploadFlowStage = 'IDLE' | 'UPLOADING' | 'REGISTERING' | 'FAILED';
