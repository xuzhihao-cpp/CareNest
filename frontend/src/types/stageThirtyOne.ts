export type AttentionNoticeLevel = 'INFO' | 'WARNING' | 'CRITICAL';

export type AttentionNoticeSource =
  | 'HEALTH_ARCHIVE'
  | 'MEDICAL_FILE'
  | 'SERVICE_ITEM'
  | 'ORDER_CONTEXT';

export interface AttentionNoticeRecord {
  noticeId: string;
  level: AttentionNoticeLevel;
  content: string;
  source: AttentionNoticeSource;
  requiredAck: boolean;
  acknowledged: boolean;
  acknowledgedAt: string;
}

export interface AttentionNoticeResult {
  items: AttentionNoticeRecord[];
}

export interface AttentionNoticeAckRequest {
  noticeIds: string[];
}

export interface AttentionNoticeGroup {
  level: AttentionNoticeLevel;
  label: string;
  items: AttentionNoticeRecord[];
}
