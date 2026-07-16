import type { SelectedMedicalFile } from './stageTwenty';

export type QualificationAuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEED_MORE';
export type QualificationStatusFilter = 'ALL' | QualificationAuditStatus;

export interface QualificationCertificateFile {
  fileId: string;
  originalName: string;
  mimeType: string;
  size: number;
  previewable: boolean;
}

export interface QualificationApplicationRecord {
  applicationId: string;
  nurseId: string;
  nurseName: string;
  auditStatus: QualificationAuditStatus;
  realName: string;
  idNoMasked: string;
  certificateNoMasked: string;
  certificateFiles: QualificationCertificateFile[];
  serviceSkillCodes: string[];
  reviewComment: string;
  submittedAt: string;
  reviewedAt: string;
}

export interface QualificationApplicationSubmitRequest {
  realName: string;
  idNoMasked: string;
  certificateNo: string;
  certificateFileIds: string[];
  serviceSkillCodes: string[];
}

export interface QualificationApplicationSubmitResult {
  applicationId: string;
  auditStatus: QualificationAuditStatus;
}

export interface QualificationApplicationPage {
  records: QualificationApplicationRecord[];
  total: number;
  page: number;
  size: number;
}

export interface QualificationSkillOption {
  value: string;
  label: string;
  sort: number;
}

export interface QualificationTrainingOverview {
  nurseName: string;
  qualificationStatus: QualificationAuditStatus;
  trainingStatus: QualificationAuditStatus | 'EXPIRED';
  trainingBatch: string;
  passedAt: string;
  expiredAt: string;
  remark: string;
}

export interface QualificationFileDraft extends SelectedMedicalFile {
  clientKey: string;
  uploadedFileId: string;
  progress: number;
  uploadState: 'READY' | 'UPLOADING' | 'UPLOADED' | 'FAILED';
  uploadError: string;
}

export interface QualificationPermissionContext {
  roleCode: string;
  permissions: string[];
}
