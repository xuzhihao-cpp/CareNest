import type { MedicalFileAuditStatus } from '@/types/stageTwenty';

export const MEDICAL_FILE_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png'] as const;
export const MEDICAL_FILE_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png'] as const;

const mimeByExtension: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png'
};

export interface MedicalFileDescriptor {
  name: string;
  size: number;
  mimeType?: string;
}

export interface MedicalFileAccessBinding {
  bindingStatus: string;
  scopeCodes: string[];
}

export function medicalFileExtension(name: string) {
  const index = name.lastIndexOf('.');
  return index >= 0 ? name.slice(index + 1).toLowerCase() : '';
}

export function validateMedicalFileDescriptor(
  file: MedicalFileDescriptor,
  maxFileSizeBytes: number,
  maxFileSizeMb: number
) {
  const extension = medicalFileExtension(file.name);
  if (!MEDICAL_FILE_EXTENSIONS.includes(extension as (typeof MEDICAL_FILE_EXTENSIONS)[number])) {
    return '请选择 PDF、JPG 或 PNG 文件。';
  }
  if (!file.mimeType || !MEDICAL_FILE_MIME_TYPES.includes(file.mimeType as (typeof MEDICAL_FILE_MIME_TYPES)[number])) {
    return '无法确认文件的真实类型，请重新导出为 PDF、JPG 或 PNG 后再上传。';
  }
  if (mimeByExtension[extension] !== file.mimeType) {
    return '文件扩展名与实际类型不一致，请检查文件后重新选择。';
  }
  if (file.size <= 0) return '不能上传空文件，请重新选择。';
  if (file.size > maxFileSizeBytes) return `文件超过 ${maxFileSizeMb} MB，请压缩后重新选择。`;
  return '';
}

export function validateMedicalFileSignature(extension: string, bytes: Uint8Array) {
  const normalized = extension.toLowerCase();
  const isPdf = bytes.length >= 5
    && bytes[0] === 0x25 && bytes[1] === 0x50 && bytes[2] === 0x44
    && bytes[3] === 0x46 && bytes[4] === 0x2d;
  const isJpeg = bytes.length >= 3
    && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  const pngSignature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  const isPng = bytes.length >= pngSignature.length
    && pngSignature.every((value, index) => bytes[index] === value);

  if (normalized === 'pdf' && isPdf) return '';
  if ((normalized === 'jpg' || normalized === 'jpeg') && isJpeg) return '';
  if (normalized === 'png' && isPng) return '';
  return '文件内容与所选格式不一致，请选择原始 PDF 或图片文件。';
}

export function normalizeMedicalFileAuditStatus(value: string): MedicalFileAuditStatus | null {
  if (value === 'PENDING' || value === 'PENDING_REVIEW') return 'PENDING_REVIEW';
  if (value === 'NEEDS_SUPPLEMENT' || value === 'NEED_MORE') return 'NEED_MORE';
  if (value === 'APPROVED' || value === 'REJECTED') return value;
  return null;
}

export function canViewMedicalFiles(binding?: MedicalFileAccessBinding | null) {
  return Boolean(binding?.bindingStatus === 'ACTIVE' && binding.scopeCodes.includes('HEALTH_VIEW'));
}

export function canUploadMedicalFiles(binding?: MedicalFileAccessBinding | null) {
  return canViewMedicalFiles(binding) && Boolean(binding?.scopeCodes.includes('HEALTH_EDIT'));
}

export function nextMedicalFileSubmitStep(uploadedFileId: string) {
  return uploadedFileId.trim() ? 'REGISTER_ONLY' : 'UPLOAD_AND_REGISTER';
}

export function canDiscardSelectedMedicalFile(uploadedFileId: string, isSubmitting: boolean) {
  return !uploadedFileId.trim() && !isSubmitting;
}

export function hasValidUploadedFileId(value: unknown): value is { fileId: string } {
  if (!value || typeof value !== 'object') return false;
  const fileId = (value as { fileId?: unknown }).fileId;
  return typeof fileId === 'string' && Boolean(fileId.trim());
}
