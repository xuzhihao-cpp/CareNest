import type { PageResult } from '@/types/api';

export type NurseReminderStatus = 'PENDING' | 'DONE' | 'SNOOZED' | 'MISSED' | 'NEED_HELP';

export type NurseReminderType = 'MEDICATION' | 'MEASUREMENT' | 'REHAB' | 'REVISIT' | 'FOLLOW_UP' | 'CUSTOM';

export interface NurseReminderItem {
  reminderId: string;
  elderId: string;
  elderName: string;
  reminderType: NurseReminderType;
  title: string;
  content: string | null;
  scheduledAt: string;
  reminderStatus: NurseReminderStatus;
  sourceType: string | null;
  sourceId?: string | null;
  createdBy?: string | null;
  createdByName?: string | null;
}

export interface NurseReminderUpsertRequest {
  reminderType: NurseReminderType;
  title: string;
  content?: string;
  scheduledAt: string;
  reminderStatus?: NurseReminderStatus;
}

export interface NurseReminderDeleteResponse {
  reminderId: string;
}

export interface NurseReminderQuery {
  status: NurseReminderStatus | '';
  page: number;
  size: number;
}

export type NurseReminderPageResult = PageResult<NurseReminderItem>;
