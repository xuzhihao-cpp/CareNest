import type { BindingResponse } from '@/types/stageSix';
import type {
  HealthFeedbackInputType,
  HealthFeedbackRequest,
  HealthFeedbackSeverity,
  HealthFeedbackType
} from '@/types/stageTwentyTwo';

export const HEALTH_FEEDBACK_TYPES: HealthFeedbackType[] = [
  'PAIN', 'DIZZINESS', 'SLEEP', 'DIET', 'MENTAL_STATE'
];
export const HEALTH_FEEDBACK_SEVERITIES: HealthFeedbackSeverity[] = ['LOW', 'MEDIUM', 'HIGH'];
export const HEALTH_FEEDBACK_INPUT_TYPES: HealthFeedbackInputType[] = ['BUTTON', 'TEXT', 'VOICE'];
export const VOICE_FILE_EXTENSIONS = ['mp3', 'm4a', 'wav', 'aac', 'webm', 'ogg'] as const;

const voiceMimeByExtension: Record<string, string[]> = {
  mp3: ['audio/mpeg', 'audio/mp3'],
  m4a: ['audio/mp4', 'audio/x-m4a'],
  wav: ['audio/wav', 'audio/x-wav', 'audio/wave'],
  aac: ['audio/aac'],
  webm: ['audio/webm'],
  ogg: ['audio/ogg']
};

export function voiceFileExtension(name: string) {
  const index = name.lastIndexOf('.');
  return index >= 0 ? name.slice(index + 1).toLowerCase() : '';
}

export function normalizeVoiceMimeType(mimeType: string) {
  return mimeType.split(';', 1)[0]?.trim().toLowerCase() ?? '';
}

export function validateVoiceFileDescriptor(
  file: { name: string; size: number; mimeType: string; durationSeconds?: number },
  maxBytes: number,
  maxMb: number
) {
  const extension = voiceFileExtension(file.name);
  const normalizedMimeType = normalizeVoiceMimeType(file.mimeType);
  if (!VOICE_FILE_EXTENSIONS.includes(extension as (typeof VOICE_FILE_EXTENSIONS)[number])) {
    return '当前设备生成的录音格式暂不支持，请更换浏览器后重新录音。';
  }
  if (!normalizedMimeType || !(voiceMimeByExtension[extension] ?? []).includes(normalizedMimeType)) {
    return '无法确认本次录音的真实格式，请重新录音。';
  }
  if (file.size <= 0) return '不能上传空语音文件。';
  if (file.size > maxBytes) return `语音文件不能超过 ${maxMb} MB。`;
  if (file.durationSeconds !== undefined && file.durationSeconds > 60) return '单次语音不能超过 60 秒。';
  return '';
}

export function hasValidFeedbackFileId(value: unknown): value is { fileId: string } {
  if (!value || typeof value !== 'object') return false;
  const fileId = (value as { fileId?: unknown }).fileId;
  return typeof fileId === 'string' && Boolean(fileId.trim());
}

export function resolveHealthFeedbackInputType(content: string, hasVoice: boolean): HealthFeedbackInputType {
  if (hasVoice) return 'VOICE';
  return content.trim() ? 'TEXT' : 'BUTTON';
}

export function validateHealthFeedback(payload: HealthFeedbackRequest) {
  if (!HEALTH_FEEDBACK_TYPES.includes(payload.feedbackType)) return '请选择需要反馈的身体情况。';
  if (!HEALTH_FEEDBACK_SEVERITIES.includes(payload.severity)) return '请选择当前感受程度。';
  if (!HEALTH_FEEDBACK_INPUT_TYPES.includes(payload.inputType)) return '反馈输入方式无法识别。';
  if (payload.content.trim().length > 512) return '补充说明不能超过 512 个字。';
  if (payload.inputType === 'VOICE' && !payload.fileId?.trim()) return '语音尚未完成上传，请稍后重试。';
  if (payload.inputType !== 'VOICE' && payload.fileId) return '当前反馈不应携带语音附件。';
  return '';
}

export function canViewFamilyHealthFeedback(binding?: Pick<BindingResponse, 'bindingStatus' | 'scopeCodes'> | null) {
  return Boolean(binding?.bindingStatus === 'ACTIVE' && binding.scopeCodes.includes('HEALTH_VIEW'));
}

export function sameElderResource(left: string, right: string) {
  return left.replace(/[-_]/g, '').toLowerCase() === right.replace(/[-_]/g, '').toLowerCase();
}

export function isCurrentFeedbackRequest(
  requestSequence: number,
  latestSequence: number,
  requestedElderId: string,
  selectedElderId: string
) {
  return requestSequence === latestSequence && sameElderResource(requestedElderId, selectedElderId);
}

export type HealthFeedbackVoiceAccess =
  | { mode: 'PROTECTED_SAME_ORIGIN'; url: string }
  | { mode: 'SIGNED_TRUSTED_ORIGIN'; url: string }
  | { mode: 'REJECTED'; url: '' };

export function classifyHealthFeedbackVoiceUrl(
  sourceUrl: string,
  pageOrigin: string,
  trustedOrigins: string[]
): HealthFeedbackVoiceAccess {
  try {
    const base = new URL(pageOrigin);
    const target = new URL(sourceUrl, `${base.origin}/`);
    if (target.protocol !== 'http:' && target.protocol !== 'https:') return { mode: 'REJECTED', url: '' };
    if (target.origin === base.origin) return { mode: 'PROTECTED_SAME_ORIGIN', url: target.href };
    if (target.protocol !== 'https:') return { mode: 'REJECTED', url: '' };
    const allowedOrigins = new Set(trustedOrigins.flatMap((value) => {
      try {
        return [new URL(value).origin];
      } catch {
        return [];
      }
    }));
    return allowedOrigins.has(target.origin)
      ? { mode: 'SIGNED_TRUSTED_ORIGIN', url: target.href }
      : { mode: 'REJECTED', url: '' };
  } catch {
    return { mode: 'REJECTED', url: '' };
  }
}

export function severityPriority(value: HealthFeedbackSeverity) {
  return value === 'HIGH' ? 3 : value === 'MEDIUM' ? 2 : 1;
}
