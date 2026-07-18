import { failure, request, success } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  NurseReminderDeleteResponse,
  NurseReminderItem,
  NurseReminderPageResult,
  NurseReminderQuery,
  NurseReminderStatus,
  NurseReminderUpsertRequest
} from '@/types/stageNurseReminders';

const defaultQuery: NurseReminderQuery = {
  status: '',
  page: 1,
  size: 20
};

const statusCodes: NurseReminderStatus[] = ['PENDING', 'DONE', 'SNOOZED', 'MISSED', 'NEED_HELP'];

function validStatus(value: unknown): value is NurseReminderStatus {
  return statusCodes.includes(String(value) as NurseReminderStatus);
}

function normalizeItem(value: unknown): NurseReminderItem | null {
  if (!value || typeof value !== 'object') {
    return null;
  }
  const item = value as Partial<NurseReminderItem>;
  if (
    !item.reminderId ||
    !item.elderId ||
    !item.elderName ||
    !item.reminderType ||
    !item.title ||
    !item.scheduledAt ||
    !validStatus(item.reminderStatus)
  ) {
    return null;
  }
  return {
    reminderId: item.reminderId,
    elderId: item.elderId,
    elderName: item.elderName,
    reminderType: item.reminderType as NurseReminderItem['reminderType'],
    title: item.title,
    content: item.content ?? null,
    scheduledAt: item.scheduledAt,
    reminderStatus: item.reminderStatus,
    sourceType: item.sourceType ?? null,
    sourceId: item.sourceId ?? null,
    createdBy: item.createdBy ?? null,
    createdByName: item.createdByName ?? null
  };
}

function normalizePage(page: number, size: number): NurseReminderPageResult {
  return { records: [], total: 0, page, size };
}

function normalizeDeleteResponse(value: unknown): NurseReminderDeleteResponse | null {
  if (!value || typeof value !== 'object') {
    return null;
  }
  const response = value as Partial<NurseReminderDeleteResponse>;
  return response.reminderId ? { reminderId: response.reminderId } : null;
}

const reminderPath = (elderId: string) => `/nurse/elders/${encodeURIComponent(elderId)}/reminders`;
const reminderDetailPath = (elderId: string, reminderId: string) =>
  `${reminderPath(elderId)}/${encodeURIComponent(reminderId)}`;

export async function getNurseReminders(
  elderId: string,
  query: Partial<NurseReminderQuery> = {}
): Promise<ApiResponse<NurseReminderPageResult>> {
  const nextQuery = { ...defaultQuery, ...query };
  const response = await request<NurseReminderPageResult>({
    method: 'GET',
    url: reminderPath(elderId),
    data: {
      page: nextQuery.page,
      size: nextQuery.size,
      ...(nextQuery.status ? { status: nextQuery.status } : {})
    }
  });
  if (response.code !== 0) {
    return { ...response, data: normalizePage(nextQuery.page, nextQuery.size) };
  }
  if (!response.data || !Array.isArray(response.data.records)) {
    return failure(502, '提醒列表响应不完整', normalizePage(nextQuery.page, nextQuery.size), response.traceId);
  }
  const records = response.data.records.map(normalizeItem);
  if (records.some((item) => !item)) {
    return failure(502, '提醒列表响应不完整', normalizePage(nextQuery.page, nextQuery.size), response.traceId);
  }
  return success({ ...response.data, records: records as NurseReminderItem[] }, response.traceId);
}

export async function createNurseReminder(
  elderId: string,
  payload: NurseReminderUpsertRequest
): Promise<ApiResponse<NurseReminderItem>> {
  const response = await request<NurseReminderItem>({
    method: 'POST',
    url: reminderPath(elderId),
    data: payload
  });
  if (response.code !== 0) {
    return response;
  }
  const item = normalizeItem(response.data);
  return item ? success(item, response.traceId) : failure(502, '提醒保存响应不完整', {} as NurseReminderItem, response.traceId);
}

export async function updateNurseReminder(
  elderId: string,
  reminderId: string,
  payload: NurseReminderUpsertRequest
): Promise<ApiResponse<NurseReminderItem>> {
  const response = await request<NurseReminderItem>({
    method: 'PUT',
    url: reminderDetailPath(elderId, reminderId),
    data: payload
  });
  if (response.code !== 0) {
    return response;
  }
  const item = normalizeItem(response.data);
  return item ? success(item, response.traceId) : failure(502, '提醒保存响应不完整', {} as NurseReminderItem, response.traceId);
}

export async function deleteNurseReminder(
  elderId: string,
  reminderId: string
): Promise<ApiResponse<NurseReminderDeleteResponse>> {
  const response = await request<NurseReminderDeleteResponse>({
    method: 'DELETE',
    url: reminderDetailPath(elderId, reminderId)
  });
  if (response.code !== 0) {
    return response;
  }
  const item = normalizeDeleteResponse(response.data);
  return item ? success(item, response.traceId) : failure(502, '提醒删除响应不完整', {} as NurseReminderDeleteResponse, response.traceId);
}
