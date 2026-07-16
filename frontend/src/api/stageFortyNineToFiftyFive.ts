import { failure, request } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  BasicStatistics,
  DemoDataStatus,
  FollowUpInput,
  FollowUpRecord,
  QualityStatistics,
  RecommendedTrainingArticle,
  ServiceHealthStatus,
  TrainingArticle,
  TrainingArticleInput,
  TrainingArticleStatus,
  TrainingReadStatus,
  TrendPoint
} from '@/types/stageFortyNineToFiftyFive';

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function text(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function numberValue(value: unknown) {
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

const articleStatuses: TrainingArticleStatus[] = ['DRAFT', 'PUBLISHED', 'OFFLINE'];
const readStatuses: TrainingReadStatus[] = ['UNREAD', 'READ', 'CONFIRMED'];

function parseArticle(value: unknown): TrainingArticle | null {
  if (!isRecord(value)) return null;
  const articleId = text(value.articleId);
  const status = text(value.status) as TrainingArticleStatus;
  if (!articleId || !articleStatuses.includes(status)) return null;
  return {
    articleId,
    status,
    title: text(value.title),
    summary: text(value.summary),
    contentUrl: text(value.contentUrl),
    tags: Array.isArray(value.tags) ? value.tags.filter((item): item is string => typeof item === 'string') : [],
    serviceIds: Array.isArray(value.serviceIds) ? value.serviceIds.filter((item): item is string => typeof item === 'string') : [],
    riskTags: Array.isArray(value.riskTags) ? value.riskTags.filter((item): item is string => typeof item === 'string') : [],
    requiredRead: value.requiredRead === true
  };
}

export async function getOperationsPermissions(): Promise<ApiResponse<string[]>> {
  const response = await request<unknown>({ method: 'GET', url: '/auth/permissions' });
  if (response.code !== 0) return { ...response, data: [] };
  if (!isRecord(response.data)) return failure(502, '账号权限信息不完整', [], response.traceId);
  const values = Array.isArray(response.data.permissions)
    ? response.data.permissions
    : Array.isArray(response.data.permissionCodes) ? response.data.permissionCodes : null;
  if (!values || values.some((item) => typeof item !== 'string')) {
    return failure(502, '账号权限信息不完整', [], response.traceId);
  }
  return { ...response, data: Array.from(new Set(values.map((item) => String(item).trim()).filter(Boolean))) };
}

export async function getTrainingArticles(signal?: AbortSignal): Promise<ApiResponse<TrainingArticle[]>> {
  const response = await request<unknown>({ method: 'GET', url: '/admin/training-articles', signal });
  if (response.code !== 0) return { ...response, data: [] };
  if (!Array.isArray(response.data)) return failure(502, '培训文章列表内容不完整', [], response.traceId);
  const records = response.data.map(parseArticle);
  return records.some((item) => item === null)
    ? failure(502, '培训文章列表内容不完整', [], response.traceId)
    : { ...response, data: records as TrainingArticle[] };
}

async function saveTrainingArticle(
  method: 'POST' | 'PUT',
  url: string,
  payload: TrainingArticleInput
): Promise<ApiResponse<TrainingArticle>> {
  const response = await request<unknown>({ method, url, data: payload });
  if (response.code !== 0) return { ...response, data: {} as TrainingArticle };
  const article = parseArticle(response.data);
  if (!article) return failure(502, '培训文章保存结果不完整', {} as TrainingArticle, response.traceId);
  return { ...response, data: { ...payload, ...article } };
}

export function createTrainingArticle(payload: TrainingArticleInput) {
  return saveTrainingArticle('POST', '/admin/training-articles', payload);
}

export function updateTrainingArticle(articleId: string, payload: TrainingArticleInput) {
  return saveTrainingArticle('PUT', `/admin/training-articles/${encodeURIComponent(articleId)}`, payload);
}

export function changeTrainingArticleStatus(articleId: string, payload: TrainingArticleInput) {
  return saveTrainingArticle('POST', `/admin/training-articles/${encodeURIComponent(articleId)}/publish`, payload);
}

function parseRecommendedArticle(value: unknown, index: number): RecommendedTrainingArticle | null {
  if (!isRecord(value)) return null;
  const articleId = text(value.articleId);
  const readStatus = text(value.readStatus) as TrainingReadStatus;
  if (!articleId || !readStatuses.includes(readStatus)) return null;
  return {
    articleId,
    readStatus,
    title: text(value.title) || `服务前学习资料 ${index + 1}`,
    summary: text(value.summary),
    contentUrl: text(value.contentUrl),
    requiredRead: value.requiredRead === true
  };
}

export async function getRecommendedTrainingArticles(orderId: string, signal?: AbortSignal) {
  const response = await request<unknown>({
    method: 'GET',
    url: `/nurse/orders/${encodeURIComponent(orderId)}/recommended-articles`,
    signal
  });
  if (response.code !== 0) return { ...response, data: [] as RecommendedTrainingArticle[] };
  if (!Array.isArray(response.data)) return failure(502, '学习资料内容不完整', [], response.traceId);
  const records = response.data.map(parseRecommendedArticle);
  return records.some((item) => item === null)
    ? failure(502, '学习资料内容不完整', [], response.traceId)
    : { ...response, data: records as RecommendedTrainingArticle[] };
}

export async function markTrainingArticleRead(articleId: string, orderId: string, readDurationSeconds: number) {
  const response = await request<unknown>({
    method: 'POST',
    url: `/nurse/articles/${encodeURIComponent(articleId)}/read`,
    data: { orderId, readDurationSeconds }
  });
  if (response.code !== 0) return { ...response, data: {} as RecommendedTrainingArticle };
  const parsed = parseRecommendedArticle(response.data, 0);
  return parsed
    ? { ...response, data: parsed }
    : failure(502, '阅读状态更新结果不完整', {} as RecommendedTrainingArticle, response.traceId);
}

export async function createFollowUp(payload: FollowUpInput): Promise<ApiResponse<FollowUpRecord>> {
  const response = await request<unknown>({ method: 'POST', url: '/admin/follow-ups', data: payload });
  if (response.code !== 0) return { ...response, data: {} as FollowUpRecord };
  if (!isRecord(response.data) || !text(response.data.followUpId)) {
    return failure(502, '随访登记结果不完整', {} as FollowUpRecord, response.traceId);
  }
  return {
    ...response,
    data: {
      ...payload,
      followUpId: text(response.data.followUpId),
      createdReminderTaskId: text(response.data.createdReminderTaskId) || undefined
    }
  };
}

export async function getElderFollowUps(elderId: string, signal?: AbortSignal): Promise<ApiResponse<FollowUpRecord[]>> {
  const response = await request<unknown>({
    method: 'GET',
    url: `/elders/${encodeURIComponent(elderId)}/follow-ups`,
    signal
  });
  if (response.code !== 0) return { ...response, data: [] };
  const source = Array.isArray(response.data)
    ? response.data
    : isRecord(response.data) && Array.isArray(response.data.records) ? response.data.records : null;
  if (!source) return failure(502, '随访记录内容不完整', [], response.traceId);
  const records = source.map((value) => {
    if (!isRecord(value) || !text(value.followUpId) || !text(value.elderId) || !text(value.followUpType)) return null;
    return {
      followUpId: text(value.followUpId), elderId: text(value.elderId), orderId: text(value.orderId) || undefined,
      followUpType: text(value.followUpType), content: text(value.content), nextFollowUpAt: text(value.nextFollowUpAt) || undefined,
      needReminder: value.needReminder === true, createdAt: text(value.createdAt) || undefined
    } as FollowUpRecord;
  });
  return records.some((item) => item === null)
    ? failure(502, '随访记录内容不完整', [], response.traceId)
    : { ...response, data: records as FollowUpRecord[] };
}

function parseTrend(value: unknown): TrendPoint[] | null {
  if (!Array.isArray(value)) return null;
  const result = value.map((item) => {
    if (!isRecord(item) || !text(item.date)) return null;
    const point = numberValue(item.value);
    return point === null ? null : { date: text(item.date), value: point };
  });
  return result.some((item) => item === null) ? null : result as TrendPoint[];
}

export async function getBasicStatistics(dateFrom: string, dateTo: string, signal?: AbortSignal): Promise<ApiResponse<BasicStatistics>> {
  const response = await request<unknown>({ method: 'GET', url: '/admin/dashboard/basic-statistics', data: { dateFrom, dateTo }, signal });
  if (response.code !== 0) return { ...response, data: {} as BasicStatistics };
  if (!isRecord(response.data) || !isRecord(response.data.cards)) return failure(502, '运营统计内容不完整', {} as BasicStatistics, response.traceId);
  const orderTrend = parseTrend(response.data.orderTrend);
  const completion = numberValue(response.data.serviceCompletionRate);
  const reminder = numberValue(response.data.reminderDoneRate);
  const satisfaction = numberValue(response.data.satisfactionAvg);
  if (!orderTrend || completion === null || reminder === null || satisfaction === null) return failure(502, '运营统计内容不完整', {} as BasicStatistics, response.traceId);
  return { ...response, data: { cards: response.data.cards as Record<string, string | number>, orderTrend, serviceCompletionRate: completion, reminderDoneRate: reminder, satisfactionAvg: satisfaction } };
}

export async function getQualityStatistics(dateFrom: string, dateTo: string, signal?: AbortSignal): Promise<ApiResponse<QualityStatistics>> {
  const response = await request<unknown>({ method: 'GET', url: '/admin/dashboard/quality-statistics', data: { dateFrom, dateTo }, signal });
  if (response.code !== 0) return { ...response, data: {} as QualityStatistics };
  if (!isRecord(response.data) || !isRecord(response.data.scoreDistribution)) return failure(502, '质量统计内容不完整', {} as QualityStatistics, response.traceId);
  const trend = parseTrend(response.data.qualityTrend);
  const archive = numberValue(response.data.archiveCompleteRate);
  const metric = numberValue(response.data.metricPassRate);
  const exception = numberValue(response.data.exceptionApproveRate);
  const distributionEntries = Object.entries(response.data.scoreDistribution).map(([key, value]) => [key, numberValue(value)] as const);
  if (!trend || archive === null || metric === null || exception === null || distributionEntries.some(([, value]) => value === null)) return failure(502, '质量统计内容不完整', {} as QualityStatistics, response.traceId);
  return { ...response, data: { archiveCompleteRate: archive, metricPassRate: metric, exceptionApproveRate: exception, scoreDistribution: Object.fromEntries(distributionEntries) as Record<string, number>, qualityTrend: trend } };
}

function parseDemoStatus(value: unknown): DemoDataStatus | null {
  if (!isRecord(value) || typeof value.ready !== 'boolean' || !Array.isArray(value.accounts) || value.accounts.some((item) => typeof item !== 'string')) return null;
  const count = numberValue(value.scenarioCount);
  if (count === null) return null;
  return { ready: value.ready, accounts: value.accounts as string[], scenarioCount: count, lastResetAt: text(value.lastResetAt) };
}

async function requestDemoStatus(method: 'GET' | 'POST') {
  const response = await request<unknown>({ method, url: method === 'GET' ? '/admin/demo-data/status' : '/admin/demo-data/reset' });
  if (response.code !== 0) return { ...response, data: {} as DemoDataStatus };
  const status = parseDemoStatus(response.data);
  return status ? { ...response, data: status } : failure(502, '演示环境状态不完整', {} as DemoDataStatus, response.traceId);
}

export function getDemoDataStatus() { return requestDemoStatus('GET'); }
export function resetDemoData() { return requestDemoStatus('POST'); }

export async function getServiceHealth(): Promise<ApiResponse<ServiceHealthStatus>> {
  const response = await request<unknown>({ method: 'GET', url: '/health' });
  if (response.code !== 0) return { ...response, data: {} as ServiceHealthStatus };
  if (!isRecord(response.data) || !text(response.data.status) || typeof response.data.dbConnected !== 'boolean') {
    return failure(502, '平台运行状态不完整', {} as ServiceHealthStatus, response.traceId);
  }
  return {
    ...response,
    data: {
      status: text(response.data.status),
      ready: typeof response.data.ready === 'boolean'
        ? response.data.ready
        : text(response.data.status) === 'UP' && response.data.dbConnected,
      databaseConnected: response.data.dbConnected, version: text(response.data.version),
      serverTime: text(response.data.serverTime)
    }
  };
}
