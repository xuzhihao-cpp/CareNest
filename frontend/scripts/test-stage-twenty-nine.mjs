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
    return { token: 'stage-29-token', user: { userId: 'family-001', roles: ['FAMILY'], menus: [] } };
  },
  request(options) {
    requests.push(options);
    const response = responses.shift();
    if (!response) throw new Error(`Missing response for ${options.method} ${options.url}`);
    options.success({ statusCode: response.statusCode ?? 200, data: response.body });
  }
};

const vite = await createServer({
  root: frontendRoot,
  configFile: false,
  server: { middlewareMode: true, hmr: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } }
});
const api = await vite.ssrLoadModule('/src/api/stageTwentyNine.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageTwentyNineRules.ts');
const { createLatestRequestGate } = await vite.ssrLoadModule('/src/utils/latestRequestGate.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-29-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

const recommendation = {
  nurseId: 'nurse-reco-a-029',
  nurseName: '李护士',
  score: 96.5,
  matchedSkills: ['BASIC_CARE', 'VITAL_SIGN'],
  recommendReason: '资质和培训有效，匹配基础照护、生命体征观察，综合评分较高。',
  available: true
};

test('requests pre-order recommendations with the exact frozen Shanghai-time payload', async () => {
  enqueue(success({ nurses: [recommendation] }));
  const response = await api.recommendNurses({
    elderId: 'elder-001',
    serviceId: 'service-001',
    addressId: 'address-001',
    scheduledStart: '2099-07-22T09:00'
  });
  const request = requests.shift();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/orders/recommend-nurses');
  assert.equal(request.header.Authorization, 'Bearer stage-29-token');
  assert.deepEqual(request.data, {
    elderId: 'elder-001',
    serviceId: 'service-001',
    addressId: 'address-001',
    scheduledStart: '2099-07-22T09:00:00+08:00'
  });
  assert.equal(response.data.nurses[0].nurseName, '李护士');
  assert.equal(response.data.nurses[0].score, 96.5);
});

test('reads order recommendations without exposing order data in query parameters', async () => {
  enqueue(success({ nurses: [recommendation] }));
  const response = await api.getOrderRecommendations('order/029');
  const request = requests.shift();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/orders/order%2F029/recommendations');
  assert.equal(response.data.nurses.length, 1);
});

test('rejects incomplete, duplicated and malformed recommendation responses', async () => {
  enqueue(success({ nurses: [{ ...recommendation, nurseName: '' }] }));
  assert.equal((await api.getOrderRecommendations('order-029')).code, 502);
  requests.shift();

  enqueue(success({ nurses: [recommendation, { ...recommendation }] }));
  assert.equal((await api.getOrderRecommendations('order-029')).code, 502);
  requests.shift();

  enqueue(success({ nurses: [{ ...recommendation, score: 101 }] }));
  assert.equal((await api.getOrderRecommendations('order-029')).code, 502);
  requests.shift();

  enqueue(success({ nurses: [{ ...recommendation, recommendReason: '匹配技能 BASIC_CARE' }] }));
  assert.equal((await api.getOrderRecommendations('order-029')).code, 502);
  requests.shift();

  const localValidation = await api.recommendNurses({
    elderId: '', serviceId: 'service-001', addressId: 'address-001', scheduledStart: '2099-07-22T09:00'
  });
  assert.equal(localValidation.code, 422);
  assert.equal(requests.length, 0);
});

test('preserves real permission and service failures without fallback recommendations', async () => {
  for (const code of [401, 403, 404, 409, 422, 500]) {
    enqueue({ code, message: 'failed', traceId: 'stage-29-test', data: {} }, code);
    const response = await api.getOrderRecommendations('order-029');
    requests.shift();
    assert.equal(response.code, code);
    assert.deepEqual(response.data.nurses, []);
  }
});

test('keys every recommendation request and requires backend-provided Chinese reasons', () => {
  const base = {
    elderId: 'elder-001', serviceId: 'service-001', addressId: 'address-001', scheduledStart: '2099-07-22T09:00'
  };
  assert.equal(rules.recommendationConditionsComplete(base), true);
  assert.equal(rules.recommendationConditionsComplete(
    { ...base, scheduledStart: '2026-07-15T08:59' },
    new Date('2026-07-15T09:00:00+08:00')
  ), false);
  assert.notEqual(
    rules.recommendationConditionKey(base),
    rules.recommendationConditionKey({ ...base, scheduledStart: '2099-07-22T10:00' })
  );
  assert.equal(rules.recommendationReasonIsBusinessReadable(recommendation.recommendReason), true);
  assert.equal(rules.recommendationReasonIsBusinessReadable('匹配技能 BASIC_CARE'), false);
  assert.equal(rules.recommendationReasonIsBusinessReadable('High score'), false);
  assert.equal(rules.recommendationScoreText(96.5), '96.5');
  assert.match(rules.recommendationErrorMessage(403, 'conditions'), /预约服务权限/);
  assert.match(rules.recommendationErrorMessage(403, 'order'), /无权查看/);
  assert.match(rules.recommendationSkillDictionaryErrorMessage(500), /推荐结果仍可查看/);
});

test('drops stale elder-address and admin-order responses after rapid switching', async () => {
  const addressGate = createLatestRequestGate();
  let currentElderId = 'elder-a';
  let visibleAddresses = [];
  let addressError = '';
  const elderA = deferred();
  const elderB = deferred();

  async function applyAddresses(elderId, pending) {
    const ticket = addressGate.begin(elderId);
    const response = await pending.promise;
    if (!addressGate.isCurrent(ticket, currentElderId)) return;
    visibleAddresses = response.code === 0 ? response.records : [];
    addressError = response.code === 0 ? '' : response.message;
  }

  const staleAddressRun = applyAddresses('elder-a', elderA);
  currentElderId = 'elder-b';
  const currentAddressRun = applyAddresses('elder-b', elderB);
  elderB.resolve({ code: 0, records: ['address-b'] });
  await currentAddressRun;
  elderA.resolve({ code: 500, message: 'stale elder error' });
  await staleAddressRun;
  assert.deepEqual(visibleAddresses, ['address-b']);
  assert.equal(addressError, '');

  const detailGate = createLatestRequestGate();
  let selectedOrderId = 'order-a';
  let visibleOrder = null;
  let detailError = '';
  const orderA = deferred();
  const orderB = deferred();

  async function applyDetail(orderId, pending) {
    const ticket = detailGate.begin(orderId);
    const response = await pending.promise;
    if (!detailGate.isCurrent(ticket, selectedOrderId)) return;
    visibleOrder = response.code === 0 ? response.record : null;
    detailError = response.code === 0 ? '' : response.message;
  }

  const staleDetailRun = applyDetail('order-a', orderA);
  selectedOrderId = 'order-b';
  const currentDetailRun = applyDetail('order-b', orderB);
  orderB.resolve({ code: 0, record: { orderId: 'order-b', serviceName: '康复陪护' } });
  await currentDetailRun;
  orderA.resolve({ code: 403, message: 'stale order error' });
  await staleDetailRun;
  assert.equal(visibleOrder.orderId, 'order-b');
  assert.equal(detailError, '');
});

test('keeps only the newest recommendation after service, address and time changes', async () => {
  const gate = createLatestRequestGate();
  const base = {
    elderId: 'elder-001', serviceId: 'service-a', addressId: 'address-a', scheduledStart: '2099-07-22T09:00'
  };
  const conditions = [
    base,
    { ...base, serviceId: 'service-b' },
    { ...base, serviceId: 'service-b', addressId: 'address-b' },
    { ...base, serviceId: 'service-b', addressId: 'address-b', scheduledStart: '2099-07-22T10:00' }
  ];
  const pending = conditions.map(() => deferred());
  let currentKey = rules.recommendationConditionKey(conditions[0]);
  let visibleNurses = [];
  let visibleError = '';

  async function applyRecommendation(condition, delayed) {
    const requestedKey = rules.recommendationConditionKey(condition);
    const ticket = gate.begin(requestedKey);
    const response = await delayed.promise;
    if (!gate.isCurrent(ticket, currentKey)) return;
    visibleNurses = response.code === 0 ? response.nurses : [];
    visibleError = response.code === 0 ? '' : response.message;
  }

  const runs = conditions.map((condition, index) => {
    currentKey = rules.recommendationConditionKey(condition);
    return applyRecommendation(condition, pending[index]);
  });
  pending[3].resolve({ code: 0, nurses: [{ nurseName: '当前条件护理员' }] });
  await runs[3];
  pending[0].resolve({ code: 500, message: 'stale first error' });
  pending[1].resolve({ code: 0, nurses: [] });
  pending[2].resolve({ code: 403, message: 'stale third error' });
  await Promise.all(runs.slice(0, 3));
  assert.deepEqual(visibleNurses, [{ nurseName: '当前条件护理员' }]);
  assert.equal(visibleError, '');
});

test('wires the real recommendation panel into family booking and admin order detail', async () => {
  const [component, familyPanel, adminPanel, apiSource] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyNineRecommendationPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTenOrderPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageElevenAdminOrdersPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwentyNine.ts'), 'utf8')
  ]);
  const familyTemplate = familyPanel.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  const recommendationTemplate = component.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  assert.match(component, /recommendationRequestGate/);
  assert.match(component, /getQualificationSkillOptions/);
  assert.match(component, /recommendationSkillDictionaryErrorMessage/);
  assert.match(component, /recommendationRequestGate\.isCurrent/);
  assert.match(component, /watch\(contextKey/);
  assert.match(familyPanel, /StageTwentyNineRecommendationPanel/);
  assert.match(familyPanel, /@invalidated="clearPreferredNurse"/);
  assert.match(familyPanel, /addressRequestGate/);
  assert.match(familyPanel, /addressRequestGate\.isCurrent/);
  assert.match(familyPanel, /addresses\.value = \[\];\s*form\.value\.addressId = '';/);
  assert.doesNotMatch(familyTemplate, /偏好护理员/);
  assert.doesNotMatch(familyTemplate, /traceId|DTO|\/api\/v1/i);
  assert.match(adminPanel, /mode="order"/);
  assert.match(adminPanel, /detailRequestGate/);
  assert.match(adminPanel, /detailRequestGate\.isCurrent/);
  assert.match(adminPanel, /listRequestGate/);
  assert.doesNotMatch(adminPanel, /traceId|DTO|records \/ WAIT_DISPATCH|stage-eleven-endpoints/);
  assert.match(recommendationTemplate, /\{\{ item\.recommendReason \}\}/);
  assert.doesNotMatch(component, /localizedRecommendationReason|recommendationSkillLabel|其他护理技能/);
  assert.doesNotMatch(recommendationTemplate, /\{\{\s*item\.nurseId\s*\}\}|护理编号|API|DTO/);
  assert.doesNotMatch(apiSource, /mock|fallback/i);
  assert.doesNotMatch(apiSource, /preferred-nurse/);
});
