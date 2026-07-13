import type {
  MedicalFileReviewDecision,
  MedicalFileReviewStatus,
  MedicalFileReviewStatusWire
} from '@/types/stageTwentyOne';

export const MEDICAL_FILE_REVIEW_PERMISSION_ALIASES = [
  'MEDICAL_FILE_REVIEW',
  'HEALTH_REVIEW',
  'health:review'
] as const;

export function normalizeReviewStatus(value: MedicalFileReviewStatusWire | string): MedicalFileReviewStatus | null {
  if (value === 'PENDING' || value === 'PENDING_REVIEW') return 'PENDING';
  if (value === 'NEED_MORE' || value === 'NEEDS_SUPPLEMENT') return 'NEED_MORE';
  if (value === 'APPROVED' || value === 'REJECTED') return value;
  return null;
}

export function canEnterMedicalFileReview(
  roles: string[],
  permissionCodes: string[]
) {
  const roleAllowed = roles.includes('ADMIN') || roles.includes('CUSTOMER_SERVICE');
  if (!roleAllowed) return false;
  return MEDICAL_FILE_REVIEW_PERMISSION_ALIASES.some((code) => permissionCodes.includes(code));
}

export function getLocalCalendarDate(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function reviewCommentRequired(decision: MedicalFileReviewDecision) {
  return decision === 'REJECTED' || decision === 'NEED_MORE';
}

export function validateMedicalFileReview(
  decision: MedicalFileReviewDecision,
  reviewComment: string
) {
  const comment = reviewComment.trim();
  if (reviewCommentRequired(decision) && !comment) return '驳回或要求补充时必须填写审核意见。';
  if (comment.length > 255) return '审核意见不能超过 255 个字。';
  return '';
}

export function validateArchiveExtraction(
  extractToArchive: boolean,
  availableItemCount: number,
  selectedItemCount: number
) {
  if (!extractToArchive) return '';
  if (availableItemCount === 0) return '当前资料没有可进入档案审核流程的建议项。';
  if (selectedItemCount === 0) return '请至少选择一项进入档案审核流程的建议。';
  return '';
}

export function isCurrentMedicalFileSelection(requestedFileId: string, selectedFileId: string) {
  return requestedFileId === selectedFileId;
}

export async function refreshReviewedMedicalFile<T>(
  fileId: string,
  refreshList: () => Promise<unknown>,
  refreshDetail: (reviewedFileId: string) => Promise<T>
) {
  await refreshList();
  return refreshDetail(fileId);
}

export function canReviewMedicalFile(status: MedicalFileReviewStatus) {
  return status === 'PENDING';
}
