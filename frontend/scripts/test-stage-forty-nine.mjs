import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const root = fileURLToPath(new URL('..', import.meta.url));
const requests = [];
const responses = [];
globalThis.uni = {
  getStorageSync: () => ({ token: 'stage-49-token', user: { roles: ['ADMIN'] } }),
  request(options) { requests.push(options); const next = responses.shift(); options.success({ statusCode: 200, data: next }); }
};
const vite = await createServer({ root, configFile: false, server: { middlewareMode: true, hmr: false }, optimizeDeps: { noDiscovery: true, include: [] }, resolve: { alias: { '@': path.join(root, 'src') } } });
const api = await vite.ssrLoadModule('/src/api/stageFortyNineToFiftyFive.ts');
after(async () => { await vite.close(); delete globalThis.uni; });
const ok = (data) => ({ code: 0, message: 'success', traceId: 'stage49', data });

test('phase 49 reads the real article route and accepts the deployed compact response', async () => {
  responses.push(ok([{ articleId: 'article-1', status: 'DRAFT' }]));
  const response = await api.getTrainingArticles();
  const request = requests.shift();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/admin/training-articles');
  assert.equal(response.data[0].status, 'DRAFT');
});

test('phase 49 sends the complete article payload and reuses the frozen publish route', async () => {
  const payload = { title: '安全护理', summary: '摘要', contentUrl: '', tags: ['安全'], serviceIds: [], riskTags: ['跌倒风险'], requiredRead: true, status: 'DRAFT' };
  responses.push(ok({ articleId: 'article-2', status: 'DRAFT' }));
  await api.createTrainingArticle(payload);
  assert.deepEqual(requests.shift().data, payload);
  responses.push(ok({ articleId: 'article-2', status: 'PUBLISHED' }));
  await api.changeTrainingArticleStatus('article-2', { ...payload, status: 'PUBLISHED' });
  assert.equal(requests.shift().url, '/api/v1/admin/training-articles/article-2/publish');
});

test('phase 49 rejects malformed records instead of inventing article data', async () => {
  responses.push(ok([{ articleId: '', status: 'PUBLISHED' }]));
  const response = await api.getTrainingArticles();
  assert.equal(response.code, 502);
  assert.deepEqual(response.data, []);
});
