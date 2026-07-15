import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { ReminderActionRequest, ReminderItem, ReminderPage, ReminderRecordPage, ReminderStatus } from '@/types/stageThirtyTwo';

const emptyPage = (page: number, size: number): ReminderPage => ({ records: [], total: 0, page, size });
const emptyRecordPage = (page: number, size: number): ReminderRecordPage => ({ records: [], total: 0, page, size });

function validStatus(value: unknown): value is ReminderStatus {
  return ['PENDING', 'DONE', 'SNOOZED', 'MISSED', 'NEED_HELP'].includes(String(value));
}

function normalizeItem(value: unknown): ReminderItem | null {
  if (!value || typeof value !== 'object') return null;
  const item = value as Partial<ReminderItem>;
  if (!item.reminderId || !item.title || !item.content || !item.reminderAt || !validStatus(item.status) || !item.sourceType) return null;
  return { reminderId: item.reminderId, title: item.title, content: item.content, reminderAt: item.reminderAt, status: item.status, snoozedUntil: item.snoozedUntil, completedAt: item.completedAt, needsHelpAt: item.needsHelpAt, sourceType: item.sourceType };
}

export async function getElderReminders(page = 1, size = 20, status = ''): Promise<ApiResponse<ReminderPage>> {
  const response = await request<ReminderPage>({ method: 'GET', url: '/elder/reminders', data: { page, size, ...(status ? { status } : {}) } });
  if (response.code !== 0) return { ...response, data: emptyPage(page, size) };
  if (!response.data || !Array.isArray(response.data.records) || response.data.records.some((item) => !normalizeItem(item))) {
    return failure(502, '提醒列表响应不完整', emptyPage(page, size), response.traceId);
  }
  return { ...response, data: { ...response.data, records: response.data.records.map((item) => normalizeItem(item) as ReminderItem) } };
}

export async function actOnElderReminder(reminderId: string, payload: ReminderActionRequest): Promise<ApiResponse<{ reminder: ReminderItem; record: unknown }>> {
  const response = await request<{ reminder: ReminderItem; record: unknown }>({ method: 'POST', url: `/elder/reminders/${encodeURIComponent(reminderId)}/actions`, data: payload });
  if (response.code !== 0) return response;
  if (!response.data?.reminder || !normalizeItem(response.data.reminder)) return failure(502, '提醒操作响应不完整', {} as { reminder: ReminderItem; record: unknown }, response.traceId);
  return response;
}

export async function getElderReminderRecords(page = 1, size = 20): Promise<ApiResponse<ReminderRecordPage>> {
  const response = await request<ReminderRecordPage>({ method: 'GET', url: '/elder/reminders/records', data: { page, size } });
  if (response.code !== 0) return { ...response, data: emptyRecordPage(page, size) };
  if (!response.data || !Array.isArray(response.data.records)) return failure(502, '提醒执行记录响应不完整', emptyRecordPage(page, size), response.traceId);
  return response;
}
