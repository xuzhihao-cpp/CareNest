import type { QualificationAuditStatus, QualificationFileDraft } from '@/types/stageTwentySix';
import { QUALIFICATION_MAX_FILES } from '@/utils/stageTwentySixRules';

export interface PersistedQualificationFile {
  fileId: string;
  name: string;
  size: number;
  mimeType: string;
}

interface QualificationRetryStorage {
  getStorageSync(key: string): unknown;
  setStorageSync(key: string, value: unknown): void;
  removeStorageSync(key: string): void;
}

const STORAGE_PREFIX = 'carenest_phase26_uploaded_files';

export function qualificationRetryStorageKey(userId: string) {
  return `${STORAGE_PREFIX}:${userId || 'anonymous'}`;
}

export function readQualificationRetryFiles(
  storage: QualificationRetryStorage,
  userId: string
): PersistedQualificationFile[] {
  const stored = storage.getStorageSync(qualificationRetryStorageKey(userId));
  if (!Array.isArray(stored)) return [];
  return stored.filter((item): item is PersistedQualificationFile => {
    if (!item || typeof item !== 'object') return false;
    const value = item as Partial<PersistedQualificationFile>;
    return typeof value.fileId === 'string' && Boolean(value.fileId.trim())
      && typeof value.name === 'string' && Boolean(value.name.trim())
      && typeof value.size === 'number' && value.size > 0
      && typeof value.mimeType === 'string' && Boolean(value.mimeType.trim());
  }).slice(0, QUALIFICATION_MAX_FILES);
}

export function writeQualificationRetryFiles(
  storage: QualificationRetryStorage,
  userId: string,
  files: QualificationFileDraft[]
) {
  const uploaded = files
    .filter((file) => Boolean(file.uploadedFileId))
    .map<PersistedQualificationFile>((file) => ({
      fileId: file.uploadedFileId,
      name: file.name,
      size: file.size,
      mimeType: file.mimeType || 'application/octet-stream'
    }));
  const key = qualificationRetryStorageKey(userId);
  if (uploaded.length) storage.setStorageSync(key, uploaded);
  else storage.removeStorageSync(key);
}

export function clearQualificationRetryFiles(
  storage: QualificationRetryStorage,
  userId: string
) {
  storage.removeStorageSync(qualificationRetryStorageKey(userId));
}

export function shouldClearQualificationRetry(status: QualificationAuditStatus) {
  return status === 'PENDING' || status === 'APPROVED';
}

export function reconcileQualificationRetryFiles(
  storage: QualificationRetryStorage,
  userId: string,
  status: QualificationAuditStatus
) {
  if (!shouldClearQualificationRetry(status)) return false;
  clearQualificationRetryFiles(storage, userId);
  return true;
}
