import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const frontendRoot = fileURLToPath(new URL('..', import.meta.url));
const requests = [];
const responses = [];

globalThis.uni = {
  getStorageSync() {
    return { token: 'stage-31-token', user: { userId: 'nurse-001', roles: ['NURSE'], menus: [] } };
  },
  request(options) {
    requests.push(options);
    const response = responses.shift();
    if (!response) throw new Error(`Missing response for ${options.method} ${options.url}`);
    options.success({ statusCode: response.statusCode ?? 200, data: response.body });
    return { abort() {} };
  }
};

const vite = await createServer({
  root: frontendRoot,
  configFile: false,
  server: { middlewareMode: true, hmr: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } }
});
const api = await vite.ssrLoadModule('/src/api/stageThirtyOne.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageThirtyOneRules.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-31-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

const pendingNotice = {
  noticeId: 'notice-a',
  level: 'CRITICAL',
  content: '协助起身时请全程搀扶，并留意头晕情况。',
  source: 'HEALTH_ARCHIVE',
  requiredAck: true,
  acknowledged: false,
  acknowledgedAt: null
};

const informationNotice = {
  noticeId: 'notice-b',
  level: 'INFO',
  content: '本次服务结束后请记录血压测量结果。',
  source: 'SERVICE_ITEM',
  requiredAck: false,
  acknowledged: false,
  acknowledgedAt: null
};

test('reads assigned-order notices through the exact frozen endpoint', async () => {
  enqueue(success({ items: [pendingNotice, informationNotice] }));
  const response = await api.getAttentionNotices('order/031');
  const request = requests.shift();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/nurse/orders/order%2F031/attention-notices');
  assert.equal(request.header.Authorization, 'Bearer stage-31-token');
  assert.equal(response.data.items.length, 2);
  assert.equal(response.data.items[0].acknowledgedAt, '');
});

test('acknowledges only distinct selected notices and returns the latest state', async () => {
  const acknowledged = {
    ...pendingNotice,
    acknowledged: true,
    acknowledgedAt: '2099-07-22T08:45:00'
  };
  enqueue(success({ items: [acknowledged, informationNotice] }));
  const response = await api.acknowledgeAttentionNotices('order-031', {
    noticeIds: ['notice-a', 'notice-a', ' notice-b ']
  });
  const request = requests.shift();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/nurse/orders/order-031/attention-notices/ack');
  assert.deepEqual(request.data, { noticeIds: ['notice-a', 'notice-b'] });
  assert.equal(response.data.items[0].acknowledged, true);
});

test('rejects malformed, duplicated and technical-enum drift responses', async () => {
  for (const items of [
    [{ ...pendingNotice, level: 'DANGER' }],
    [{ ...pendingNotice, source: 'FRONTEND_CUSTOM' }],
    [{ ...pendingNotice, acknowledged: true, acknowledgedAt: null }],
    [pendingNotice, { ...pendingNotice }]
  ]) {
    enqueue(success({ items }));
    assert.equal((await api.getAttentionNotices('order-031')).code, 502);
    requests.shift();
  }
  assert.equal((await api.acknowledgeAttentionNotices('order-031', { noticeIds: [] })).code, 422);
  assert.equal(requests.length, 0);
});

test('preserves permission, conflict, validation and service failures', async () => {
  for (const code of [401, 403, 404, 409, 422, 500]) {
    enqueue({ code, message: 'failed', traceId: 'stage-31-test', data: {} }, code);
    const response = await api.getAttentionNotices('order-031');
    requests.shift();
    assert.equal(response.code, code);
    assert.deepEqual(response.data, { items: [] });
  }
});

test('does not start a request when its abort signal is already canceled', async () => {
  const controller = new AbortController();
  controller.abort();
  const requestCount = requests.length;
  const response = await api.getAttentionNotices('order-031', controller.signal);
  assert.equal(response.code, 499);
  assert.equal(requests.length, requestCount);
});

test('groups business levels, maps sources and blocks start until every required item is confirmed', () => {
  const items = [pendingNotice, informationNotice];
  const groups = rules.groupAttentionNotices(items);
  assert.deepEqual(groups.map((group) => group.label), ['高风险', '提示']);
  assert.equal(rules.ATTENTION_SOURCE_LABELS.HEALTH_ARCHIVE, '健康档案');
  assert.equal(rules.formatShanghaiDateTime('2026-07-16T00:30:00Z'), '2026-07-16 08:30');
  assert.equal(rules.formatShanghaiDateTime('2026-07-16T08:30:00'), '2026-07-16 08:30');
  assert.equal(rules.pendingRequiredNotices(items).length, 1);
  assert.deepEqual(rules.selectedPendingNoticeIds(items, ['notice-a', 'notice-b']), ['notice-a']);
  assert.equal(rules.canStartServiceAfterAttention({ loaded: true, hasReadError: false, taskStatus: 'ON_THE_WAY', items }), false);
  assert.equal(rules.canStartServiceAfterAttention({
    loaded: true,
    hasReadError: false,
    taskStatus: 'ON_THE_WAY',
    items: [{ ...pendingNotice, acknowledged: true, acknowledgedAt: '2099-07-22T08:45:00' }]
  }), true);
  assert.equal(rules.canStartServiceAfterAttention({ loaded: true, hasReadError: false, taskStatus: 'ACCEPTED', items: [] }), false);
});

test('wires stage 31 into the real nurse and admin flows without runtime mock or technical display', async () => {
  const [panel, summary, nurseApp, adminApp, adminOrders, stageThirteen] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/components/StageThirtyOneAttentionPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyFivePreServiceSummary.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/nurse/NurseApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/admin/AdminApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageElevenAdminOrdersPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageThirteen.ts'), 'utf8')
  ]);
  const template = panel.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  assert.match(summary, /StageThirtyOneAttentionPanel/);
  assert.match(nurseApp, /服务前核对并开始/);
  assert.match(nurseApp, /startServiceAfterAttention/);
  assert.match(nurseApp, /response\.code === 409 \|\| response\.code === 422/);
  assert.match(adminApp, /CARE_ATTENTION_REVIEW/);
  assert.match(adminApp, /服务前审阅/);
  assert.match(adminOrders, /canReviewAttentionNotices/);
  assert.match(adminOrders, /read-only/);
  assert.match(adminOrders, /全部可审阅/);
  assert.match(adminOrders, /props\.roleCode === 'CUSTOMER_SERVICE' \? '' : 'WAIT_DISPATCH'/);
  assert.match(panel, /new AbortController\(\)/);
  assert.match(panel, /readController\?\.abort\(\)/);
  assert.match(panel, /ackController\?\.abort\(\)/);
  assert.doesNotMatch(stageThirteen, /isMockEnabled|@\/mock|mock\s*:/);
  assert.doesNotMatch(template, /\{\{\s*item\.noticeId|\{\{\s*item\.source|INFO|WARNING|CRITICAL|HEALTH_ARCHIVE|MEDICAL_FILE|SERVICE_ITEM|ORDER_CONTEXT/);
});
