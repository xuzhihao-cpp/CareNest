import type { PageResult } from '@/types/api';

export type ReminderStatus = 'PENDING' | 'DONE' | 'SNOOZED' | 'MISSED' | 'NEED_HELP';
export type ReminderAction = 'DONE' | 'SNOOZE' | 'NEED_HELP';

export interface ReminderItem {
  reminderId: string;
  title: string;
  content: string;
  reminderAt: string;
  status: ReminderStatus;
  snoozedUntil?: string;
  completedAt?: string;
  needsHelpAt?: string;
  sourceType: string;
}

export interface ReminderActionRequest {
  action: ReminderAction;
  snoozeMinutes?: number;
  note?: string;
}

export interface ReminderRecord {
  reminderId: string;
  title: string;
  action: string;
  fromStatus: ReminderStatus;
  toStatus: ReminderStatus;
  note?: string;
  actedAt: string;
}

export type ReminderPage = PageResult<ReminderItem>;
export type ReminderRecordPage = PageResult<ReminderRecord>;
