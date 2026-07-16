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
    return { token: 'stage-30-token', user: { userId: 'family-001', roles: ['FAMILY'], menus: [] } };
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
const api = await vite.ssrLoadModule('/src/api/stageThirty.ts');
const stageTenApi = await vite.ssrLoadModule('/src/api/stageTen.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageThirtyRules.ts');
const { createAsyncActionLock, createLatestRequestGate } = await vite.ssrLoadModule('/src/utils/latestRequestGate.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-30-test', data };
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

const preference = {
  orderId: 'order-030',
  preferredNurseId: 'nurse-030',
  recommendReason: '资质和培训均在有效期内，具备基础照护能力，当前预约时段可提供服务。'
};

const recommendations = [
  {
    nurseId: 'nurse-030',
    nurseName: '李护士',
    score: 96,
    matchedSkills: ['BASIC_CARE'],
    recommendReason: preference.recommendReason,
    available: true
  }
];

test('updates a preferred nurse through the exact frozen family endpoint', async () => {
  enqueue(success(preference));
  const response = await api.updatePreferredNurse('order/030', 'nurse-030');
  const request = requests.shift();
  assert.equal(request.method, 'PUT');
  assert.equal(request.url, '/api/v1/family/orders/order%2F030/preferred-nurse');
  assert.equal(request.header.Authorization, 'Bearer stage-30-token');
  assert.deepEqual(request.data, { preferredNurseId: 'nurse-030' });
  assert.equal(response.data.preferredNurseId, 'nurse-030');
});

test('reads the preference permission and sends the selected nurse in a new order request', async () => {
  enqueue(success({ roleCode: 'FAMILY', permissions: ['NURSE_PREFERENCE_SELECT', 'ORDER_CREATE'] }));
  const permissionResponse = await api.getPreferredNursePermissions();
  const permissionRequest = requests.shift();
  assert.equal(permissionRequest.method, 'GET');
  assert.equal(permissionRequest.url, '/api/v1/auth/permissions');
  assert.deepEqual(permissionResponse.data, ['NURSE_PREFERENCE_SELECT', 'ORDER_CREATE']);

  enqueue(success([{
    bindingId: 'binding-030',
    elderId: 'elder-001',
    elderName: '张瑞嘉',
    relationType: 'DAUGHTER',
    bindingStatus: 'ACTIVE',
    scopeCodes: ['ORDER_CREATE'],
    pendingScopeCodes: [],
    scopeUpdatePending: false
  }]));
  const bindingResponse = await api.getPreferredNurseBindings();
  const bindingRequest = requests.shift();
  assert.equal(bindingRequest.method, 'GET');
  assert.equal(bindingRequest.url, '/api/v1/family/bindings');
  assert.equal(bindingResponse.data[0].bindingStatus, 'ACTIVE');

  enqueue(success({
    orderId: 'order-030',
    orderNo: 'NO-030',
    orderStatus: 'WAIT_DISPATCH',
    elderId: 'elder-001',
    scheduledStart: '2099-07-22T09:00',
    remark: ''
  }));
  await stageTenApi.createFamilyOrder({
    elderId: 'elder-001',
    serviceId: 'service-001',
    addressId: 'address-001',
    scheduledStart: '2099-07-22T09:00',
    preferredNurseId: 'nurse-030',
    remark: ''
  });
  const createRequest = requests.shift();
  assert.equal(createRequest.method, 'POST');
  assert.equal(createRequest.url, '/api/v1/family/orders');
  assert.equal(createRequest.data.preferredNurseId, 'nurse-030');
});

test('re-reads the family preference view without exposing ids in query parameters', async () => {
  enqueue(success(preference));
  const response = await api.getPreferredNurse('order/030');
  const request = requests.shift();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/family/orders/order%2F030/recommendation-view');
  assert.equal(response.data.recommendReason, preference.recommendReason);
});

test('rejects incomplete or technical preference responses', async () => {
  enqueue(success({ ...preference, preferredNurseId: '' }));
  assert.equal((await api.getPreferredNurse('order-030')).code, 502);
  requests.shift();

  enqueue(success({ ...preference, recommendReason: '匹配技能：BASIC_CARE' }));
  assert.equal((await api.getPreferredNurse('order-030')).code, 502);
  requests.shift();

  assert.equal((await api.updatePreferredNurse('', '')).code, 422);
  assert.equal(requests.length, 0);
});

test('preserves real permission, conflict, validation and service failures', async () => {
  for (const code of [401, 403, 404, 409, 422, 500]) {
    enqueue({ code, message: 'failed', traceId: 'stage-30-test', data: {} }, code);
    const response = await api.updatePreferredNurse('order-030', 'nurse-030');
    requests.shift();
    assert.equal(response.code, code);
    assert.equal(response.data.preferredNurseId, '');
  }
  enqueue({ code: 404, message: 'not found', traceId: 'stage-30-test', data: {} }, 404);
  assert.equal((await api.getPreferredNurse('order-030')).code, 404);
  requests.shift();
});

test('resolves a preference to a business name and only edits wait-dispatch orders', () => {
  assert.equal(rules.canEditPreferredNurse('WAIT_DISPATCH'), true);
  for (const status of ['DISPATCHED', 'ACCEPTED', 'ON_THE_WAY', 'SERVING', 'COMPLETED']) {
    assert.equal(rules.canEditPreferredNurse(status), false);
  }
  const resolved = rules.resolvePreferredNurse(preference, recommendations);
  assert.equal(resolved.presentation.nurseName, '李护士');
  assert.equal(resolved.unresolved, false);
  assert.equal(rules.resolvePreferredNurse(preference, []).unresolved, true);
  const bindings = [{
    bindingId: 'binding-030',
    elderId: 'elder-001',
    bindingStatus: 'ACTIVE',
    scopeCodes: ['ORDER_CREATE']
  }];
  assert.equal(rules.hasActiveOrderBinding('elder-001', bindings), true);
  assert.equal(rules.hasActiveOrderBinding('elder-002', bindings), false);
  assert.equal(rules.hasActiveOrderBinding('elder-001', [{ ...bindings[0], bindingStatus: 'PENDING' }]), false);
  assert.equal(rules.hasActiveOrderBinding('elder-001', [{ ...bindings[0], scopeCodes: ['HEALTH_VIEW'] }]), false);
  assert.equal(rules.preferredNurseAccessMessage('', ['NURSE_PREFERENCE_SELECT'], bindings), '');
  assert.equal(rules.preferredNurseAccessMessage('elder-001', ['NURSE_PREFERENCE_SELECT'], bindings), '');
  assert.match(rules.preferredNurseAccessMessage('elder-002', ['NURSE_PREFERENCE_SELECT'], bindings), /代下单授权/);
  assert.equal(rules.canSelectPreferredNurse('WAIT_DISPATCH', ['NURSE_PREFERENCE_SELECT'], true), true);
  assert.equal(rules.canSelectPreferredNurse('WAIT_DISPATCH', [], true), false);
  assert.equal(rules.canSelectPreferredNurse('WAIT_DISPATCH', ['NURSE_PREFERENCE_SELECT'], false), false);
  assert.equal(rules.canSelectPreferredNurse('DISPATCHED', ['NURSE_PREFERENCE_SELECT'], true), false);
  const nameOnlyAdminOrder = {
    orderId: 'order-030',
    orderStatus: 'WAIT_DISPATCH',
    preferredNurseName: '李护士',
    preferredNurseReason: preference.recommendReason
  };
  const adminResolution = rules.resolveAdminPreferredNurse(nameOnlyAdminOrder, []);
  assert.equal(adminResolution.presentation.nurseName, '李护士');
  assert.equal(adminResolution.unresolved, false);
  const readModelPresentation = rules.preferredNurseReadModelPresentation(
    'order-030',
    '李护士',
    preference.recommendReason
  );
  assert.equal(readModelPresentation.nurseName, '李护士');
  assert.equal(readModelPresentation.recommendReason, preference.recommendReason);
  assert.equal(rules.preferredNurseReadModelPresentation('order-030', '李护士', ''), null);
  assert.equal(rules.currentPreferredRecommendation('nurse-030', recommendations).nurseName, '李护士');
  assert.equal(rules.currentPreferredRecommendation('nurse-030', [{ ...recommendations[0], available: false }]), null);
  assert.equal(rules.currentPreferredRecommendation('nurse-missing', recommendations), null);
  assert.match(rules.preferredNurseErrorMessage(409, 'save'), /不能再修改/);
  assert.match(rules.preferredNurseErrorMessage(422, 'save'), /重新获取推荐/);
});

test('locks repeated submissions until the active request finishes', async () => {
  const lock = createAsyncActionLock();
  const pending = deferred();
  let calls = 0;
  const first = lock.run(async () => {
    calls += 1;
    await pending.promise;
    return 'created';
  });
  const duplicate = lock.run(async () => {
    calls += 1;
    return 'duplicate';
  });
  assert.equal(lock.isLocked(), true);
  assert.equal(await duplicate, undefined);
  assert.equal(calls, 1);
  pending.resolve();
  assert.equal(await first, 'created');
  assert.equal(lock.isLocked(), false);
});

test('stops a saved preference flow after switching orders during the server reread', async () => {
  const saveGate = createLatestRequestGate();
  let currentOrderId = 'order-a';
  let recommendationRefreshes = 0;
  let parentUpdates = 0;
  let visibleMessage = '';
  const updateResponse = deferred();
  const rereadResponse = deferred();

  async function savePreference() {
    const orderId = currentOrderId;
    const ticket = saveGate.begin(orderId);
    await updateResponse.promise;
    if (!saveGate.isCurrent(ticket, currentOrderId)) return;
    await rereadResponse.promise;
    if (!saveGate.isCurrent(ticket, currentOrderId)) return;
    recommendationRefreshes += 1;
    parentUpdates += 1;
    visibleMessage = '订单A偏好已保存';
  }

  const saving = savePreference();
  updateResponse.resolve({ code: 0 });
  await Promise.resolve();
  currentOrderId = 'order-b';
  saveGate.invalidate();
  rereadResponse.resolve({ code: 0 });
  await saving;
  assert.equal(recommendationRefreshes, 0);
  assert.equal(parentUpdates, 0);
  assert.equal(visibleMessage, '');
});

test('drops stale preference, list and order-detail responses when switching rapidly', async () => {
  const gate = createLatestRequestGate();
  let currentOrderId = 'order-a';
  let visiblePreference = null;
  let visibleError = '';
  const orderA = deferred();
  const orderB = deferred();

  async function applyPreference(orderId, pending) {
    const ticket = gate.begin(orderId);
    const response = await pending.promise;
    if (!gate.isCurrent(ticket, currentOrderId)) return;
    visiblePreference = response.code === 0 ? response.data : null;
    visibleError = response.code === 0 ? '' : response.message;
  }

  const staleRun = applyPreference('order-a', orderA);
  currentOrderId = 'order-b';
  const currentRun = applyPreference('order-b', orderB);
  orderB.resolve({ code: 0, data: { ...preference, orderId: 'order-b' } });
  await currentRun;
  orderA.resolve({ code: 500, message: 'stale preference error' });
  await staleRun;
  assert.equal(visiblePreference.orderId, 'order-b');
  assert.equal(visibleError, '');

  const detailGate = createLatestRequestGate();
  let currentDetail = null;
  let requestedOrderId = 'order-a';
  const detailA = deferred();
  const detailB = deferred();
  async function applyDetail(orderId, pending) {
    const ticket = detailGate.begin(orderId);
    const response = await pending.promise;
    if (!detailGate.isCurrent(ticket, requestedOrderId)) return;
    currentDetail = response;
  }
  const staleDetail = applyDetail('order-a', detailA);
  requestedOrderId = 'order-b';
  const currentDetailRequest = applyDetail('order-b', detailB);
  detailB.resolve({ orderId: 'order-b' });
  await currentDetailRequest;
  detailA.resolve({ orderId: 'order-a' });
  await staleDetail;
  assert.equal(currentDetail.orderId, 'order-b');

  const listGate = createLatestRequestGate();
  let visibleOrders = [];
  const oldList = deferred();
  const latestList = deferred();
  async function applyList(requestKey, pending) {
    const ticket = listGate.begin(requestKey);
    const response = await pending.promise;
    if (!listGate.isCurrent(ticket, requestKey)) return;
    visibleOrders = response;
  }
  const staleListRequest = applyList('initial', oldList);
  const currentListRequest = applyList('updated', latestList);
  latestList.resolve([{ orderId: 'order-new' }]);
  await currentListRequest;
  oldList.resolve([{ orderId: 'order-old' }]);
  await staleListRequest;
  assert.equal(visibleOrders[0].orderId, 'order-new');
});

test('wires preference selection, cross-role display and refresh without technical ids', async () => {
  const [recommendationPanel, familyPanel, familyPreference, badge, adminOrders, dispatch, adminSummary, apiSource, stageTenSource, stageElevenSource, stageTwelveSource, adminApp, appSurface] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyNineRecommendationPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTenOrderPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageThirtyFamilyPreferencePanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageThirtyPreferenceBadge.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageElevenAdminOrdersPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwelveDispatchPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageThirtyAdminPreferenceSummary.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageThirty.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageTen.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageEleven.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwelve.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/admin/AdminApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/AppSurface.vue'), 'utf8')
  ]);
  const familyTemplate = familyPanel.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  const preferenceTemplate = familyPreference.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  const badgeTemplate = badge.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  const adminTemplate = adminSummary.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';

  assert.match(recommendationPanel, /selectable/);
  assert.match(recommendationPanel, /已选为偏好/);
  assert.match(recommendationPanel, /偏好护理用于表达服务意愿/);
  assert.match(familyPanel, /@selected="selectPreferredNurse"/);
  assert.match(familyPanel, /@recommendations-loaded="reconcilePreferredNurse"/);
  assert.match(familyPanel, /currentPreferredRecommendation/);
  assert.match(familyPanel, /form\.preferredNurseId/);
  assert.match(familyPanel, /getPreferredNursePermissions/);
  assert.match(familyPanel, /getPreferredNurseBindings/);
  assert.match(familyPanel, /createAsyncActionLock/);
  assert.match(familyPanel, /detailRequestGate/);
  assert.match(familyPanel, /ordersRequestGate/);
  assert.match(familyPanel, /preferredNurseAccessMessage/);
  assert.match(familyPanel, /await viewOrderDetail\(response\.data\.orderId, true\)/);
  assert.match(familyPanel, /StageThirtyFamilyPreferencePanel/);
  assert.match(familyPanel, /StageThirtyPreferenceBadge/);
  assert.match(familyPreference, /await loadPreference\(\)/);
  assert.match(familyPreference, /response\.code === 409/);
  assert.match(familyPreference, /NURSE_PREFERENCE_SELECT/);
  assert.match(familyPreference, /hasActiveOrderBinding/);
  assert.match(familyPreference, /ORDER_CREATE|代下单授权/);
  assert.match(familyPreference, /await loadPreference\(\);[\s\S]*saveGate\.isCurrent/);
  assert.match(adminOrders, /StageThirtyAdminPreferenceSummary/);
  assert.match(dispatch, /StageThirtyAdminPreferenceSummary/);
  assert.match(dispatch, /getOrderRecommendations/);
  assert.match(dispatch, /props\.canViewRecommendations/);
  assert.match(dispatch, /filter\(\(item\) => item\.available\)/);
  assert.doesNotMatch(dispatch, /nurse-001|nurse-002|护理演示账号/);
  assert.doesNotMatch(familyTemplate, /traceId|DTO|\/api\/v1/i);
  assert.doesNotMatch(preferenceTemplate, /\{\{\s*.*preferredNurseId|护理编号|traceId|DTO|API/);
  assert.doesNotMatch(badgeTemplate, /\{\{\s*.*preferredNurseId|护理编号/);
  assert.doesNotMatch(adminTemplate, /\{\{\s*.*preferredNurseId|护理编号|traceId|DTO|API/);
  assert.doesNotMatch(apiSource, /mock|fallback/i);
  assert.match(badgeTemplate, /推荐依据/);
  assert.match(badge, /preferredNurseReadModelPresentation/);
  assert.match(adminApp, /getPreferredNursePermissions/);
  assert.match(adminApp, /NURSE_RECOMMEND_VIEW/);
  assert.match(adminOrders, /canViewRecommendations/);
  assert.match(appSurface, /permissionSet\.has\('NURSE_RECOMMEND_VIEW'\)/);
  for (const source of [stageTenSource, stageElevenSource, stageTwelveSource]) {
    assert.doesNotMatch(source, /isMockEnabled|@\/mock|\bmock\s*:/);
  }
});
