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
    return { token: 'stage-28-token', user: { userId: 'admin-001', roles: ['ADMIN'], menus: [] } };
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
const api = await vite.ssrLoadModule('/src/api/stageTwentyEight.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageTwentyEightRules.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-28-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

const richTraining = {
  nurseId: 'nurse-valid-028',
  nurseName: '张护士',
  qualificationStatus: 'APPROVED',
  trainingStatus: 'APPROVED',
  trainingBatch: '2026-07-A',
  passedAt: '2026-07-15T10:00:00+08:00',
  expiredAt: '2027-07-15T23:59:59+08:00',
  remark: '年度培训通过'
};

test('reads the current nurse rich training model and rejects the legacy minimal DTO', async () => {
  enqueue(success(richTraining));
  const response = await api.getCurrentTrainingStatus();
  let request = requests.shift();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/nurse/training-status');
  assert.equal(request.header.Authorization, 'Bearer stage-28-token');
  assert.equal(response.data.trainingBatch, '2026-07-A');

  enqueue(success({ nurseId: 'nurse-valid-028', trainingStatus: 'APPROVED', expiredAt: '2027-07-15T23:59:59+08:00' }));
  const malformed = await api.getCurrentTrainingStatus();
  request = requests.shift();
  assert.equal(malformed.code, 502);
});

test('reads each admin training state and preserves a not-yet-trained response', async () => {
  enqueue(success(richTraining));
  const trained = await api.getAdminTrainingStatus('nurse-valid-028');
  let request = requests.shift();
  assert.equal(request.url, '/api/v1/admin/nurses/nurse-valid-028/training-status');
  assert.equal(trained.data.trainingStatus, 'APPROVED');

  enqueue({ code: 404, message: 'not found', traceId: 'stage-28-test', data: {} }, 404);
  const notStarted = await api.getAdminTrainingStatus('nurse-new-028');
  request = requests.shift();
  assert.equal(request.url, '/api/v1/admin/nurses/nurse-new-028/training-status');
  assert.equal(notStarted.code, 404);
});

test('submits the selected nurse training review with the exact frozen payload', async () => {
  const payload = {
    status: 'APPROVED',
    trainingBatch: '2026-07-A',
    expiredAt: '2099-07-15T23:59:00+08:00',
    remark: '年度培训通过'
  };
  enqueue(success({
    nurseId: 'nurse-valid-028', trainingStatus: 'APPROVED', expiredAt: payload.expiredAt
  }));
  const response = await api.reviewNurseTraining('nurse-valid-028', payload);
  const request = requests.shift();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/admin/nurses/nurse-valid-028/training-review');
  assert.equal(request.header.Authorization, 'Bearer stage-28-token');
  assert.deepEqual(request.data, payload);
  assert.equal(response.data.trainingStatus, 'APPROVED');
});

test('preserves permission, validation and malformed response failures', async () => {
  for (const code of [401, 403, 404, 409, 422, 500]) {
    enqueue({ code, message: 'failed', traceId: 'stage-28-test', data: {} }, code);
    const response = await api.reviewNurseTraining('nurse-valid-028', {
      status: 'REJECTED', trainingBatch: '2026-07-A', expiredAt: '', remark: '未通过'
    });
    requests.shift();
    assert.equal(response.code, code);
  }
  enqueue(success({ nurseId: 'nurse-valid-028', trainingStatus: 'APPROVED', expiredAt: '' }));
  const malformed = await api.reviewNurseTraining('nurse-valid-028', {
    status: 'APPROVED', trainingBatch: '2026-07-A', expiredAt: '2099-07-15T23:59:00+08:00', remark: ''
  });
  requests.shift();
  assert.equal(malformed.code, 502);
});

test('enforces permissions, Shanghai dates and review requirements', () => {
  const now = new Date('2026-07-15T12:00:00+08:00');
  const base = {
    qualificationStatus: 'APPROVED',
    status: 'APPROVED',
    trainingBatch: '2026-07-A',
    expiredAt: '2026-07-15T12:01:00+08:00',
    remark: '',
    now
  };
  assert.equal(rules.validateTrainingReview(base), '');
  assert.match(rules.validateTrainingReview({ ...base, qualificationStatus: 'PENDING' }), /资质已通过/);
  assert.match(rules.validateTrainingReview({ ...base, status: '' }), /选择培训审核结果/);
  assert.match(rules.validateTrainingReview({ ...base, trainingBatch: '' }), /培训批次/);
  assert.match(rules.validateTrainingReview({ ...base, expiredAt: '2026-07-15T12:00:00+08:00' }), /晚于当前时间/);
  assert.match(rules.validateTrainingReview({ ...base, status: 'REJECTED', expiredAt: '', remark: '' }), /必须填写原因/);
  assert.match(rules.validateTrainingReview({ ...base, status: 'NEED_MORE', expiredAt: '', remark: '' }), /必须填写具体说明/);
  assert.equal(rules.combineTrainingExpiry('2024-02-29', '09:30'), '2024-02-29T09:30:00+08:00');
  assert.equal(rules.combineTrainingExpiry('2025-02-29', '09:30'), '');
  assert.equal(rules.localDateValue(new Date('2026-07-14T16:30:00Z')), '2026-07-15');
  assert.equal(
    rules.trainingDateTimeTimestamp('2026-07-15T12:00:00'),
    Date.parse('2026-07-15T12:00:00+08:00')
  );
  assert.equal(rules.canReviewTrainingByPermission({
    roleCode: 'CUSTOMER_SERVICE', permissions: ['NURSE_TRAINING_REVIEW']
  }), true);
  assert.equal(rules.canReviewTrainingByPermission({ roleCode: 'ADMIN', permissions: [] }), false);
});

test('computes expired display state and formal-order eligibility without writing EXPIRED', () => {
  const beforeExpiry = new Date('2026-07-15T11:00:00+08:00');
  const atExpiry = new Date('2026-07-15T12:00:00+08:00');
  const expiredAt = '2026-07-15T12:00:00';
  assert.equal(rules.effectiveTrainingDisplayStatus('APPROVED', expiredAt, beforeExpiry), 'APPROVED');
  assert.equal(rules.effectiveTrainingDisplayStatus('APPROVED', expiredAt, atExpiry), 'EXPIRED');
  assert.equal(rules.trainingStatusLabel('APPROVED'), '通过');
  assert.equal(rules.trainingStatusLabel('EXPIRED'), '培训已过期');
  assert.equal(rules.canAcceptFormalOrders({
    qualificationStatus: 'APPROVED', trainingStatus: 'APPROVED', expiredAt, now: beforeExpiry
  }), true);
  assert.equal(rules.canAcceptFormalOrders({
    qualificationStatus: 'APPROVED', trainingStatus: 'APPROVED', expiredAt, now: atExpiry
  }), false);
  assert.deepEqual(
    rules.expiryFieldsForTrainingStatus('APPROVED', '2027-07-15', '18:00'),
    { expiryDate: '2027-07-15', expiryTime: '18:00' }
  );
  assert.deepEqual(
    rules.expiryFieldsForTrainingStatus('REJECTED', '2027-07-15', '18:00'),
    { expiryDate: '', expiryTime: '' }
  );
});

test('wires approved-list selection, nurse eligibility and hides technical IDs', async () => {
  const [component, nursePanel, adminApp, apiSource] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyEightTrainingReviewWorkbench.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentySixQualificationPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/admin/AdminApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwentyEight.ts'), 'utf8')
  ]);
  const template = component.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  assert.match(component, /auditStatus: 'APPROVED'/);
  assert.match(component, /selectNurse\(record\)/);
  assert.match(component, /const status = ref<TrainingReviewStatus \| ''>\(''\)/);
  assert.match(component, /reviewRequestSequence/);
  assert.match(component, /selectedNurseId\.value === nurseId/);
  assert.match(component, /expiryFieldsForTrainingStatus/);
  assert.match(component, /本次提交结果/);
  assert.match(component, /getAdminTrainingStatus/);
  assert.match(component, /loadTrainingListStates/);
  assert.match(component, /NOT_STARTED/);
  assert.match(component, /待培训/);
  assert.match(component, /training-chip/);
  assert.doesNotMatch(component, /class="qualification-chip"/);
  assert.match(component, /当前培训状态/);
  assert.doesNotMatch(component, /最新结果/);
  assert.doesNotMatch(component, /response\.code === 409[\s\S]{0,180}loadApprovedNurses/);
  assert.match(component, /mode="date" fields="month"/);
  assert.match(component, /mode="selector"[^>]*trainingBatchCodes/);
  assert.match(component, /批次编号：\{\{ trainingBatch \}\}/);
  assert.doesNotMatch(component, /<input[^>]*v-model="trainingBatch"/);
  assert.match(component, /mode="date"[^>]*changeExpiryDate/);
  assert.match(component, /mode="time"[^>]*changeExpiryTime/);
  assert.doesNotMatch(component, /value: 'EXPIRED'/);
  assert.doesNotMatch(template, /placeholder="[^"]*(?:nurseId|护理编号)/);
  assert.doesNotMatch(template, />[^<{]*(?:API|DTO|nurseId|applicationId|fileId)[^<{]*</);
  assert.match(nursePanel, /formalOrderEligibilityText/);
  assert.match(nursePanel, /可接正式订单/);
  assert.match(adminApp, /StageTwentyEightTrainingReviewWorkbench/);
  assert.doesNotMatch(apiSource, /mock|fallback/i);
  assert.match(apiSource, /method:\s*'GET'[\s\S]*\/admin\/nurses\/\$\{encodeURIComponent\(nurseId\.trim\(\)\)\}\/training-status/);
});
