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
    return { token: 'stage-34-token', user: { userId: 'admin-001', roles: ['ADMIN'], menus: [] } };
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
const api = await vite.ssrLoadModule('/src/api/stageThirtyFourToForty.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageThirtyFourToFortyRules.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-34-40-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

function nextRequest() {
  const request = requests.shift();
  assert.ok(request, 'expected request to be captured');
  return request;
}

test('config endpoints use the frozen phase 34 path and return active metric items', async () => {
  const activeItems = [{
    metricCode: 'SERVICE_PHOTO',
    metricName: '服务照片',
    metricType: 'SERVICE_PROCESS',
    required: true,
    evidenceType: 'PHOTO',
    scoreWeight: 10,
    description: '服务完成后拍摄照片'
  }];
  enqueue(success({ configVersion: 1, items: activeItems }));
  const readResponse = await api.getCareMetricConfig('service/001');
  let request = nextRequest();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/admin/service-items/service%2F001/care-metric-config');
  assert.equal(request.header.Authorization, 'Bearer stage-34-token');
  assert.equal(readResponse.data.configVersion, 1);
  assert.deepEqual(readResponse.data.items, activeItems);

  enqueue(success({ configVersion: 2, items: activeItems }));
  const saveResponse = await api.saveCareMetricConfig('service-001', {
    items: [{
      metricCode: 'SERVICE_PHOTO',
      metricName: ' 服务照片 ',
      metricType: 'SERVICE_PROCESS',
      required: true,
      evidenceType: 'PHOTO',
      scoreWeight: 10,
      description: ' 留档 '
    }]
  });
  request = nextRequest();
  assert.equal(request.method, 'PUT');
  assert.equal(request.url, '/api/v1/admin/service-items/service-001/care-metric-config');
  assert.deepEqual(request.data.items[0], {
    metricCode: 'SERVICE_PHOTO',
    metricName: '服务照片',
    metricType: 'SERVICE_PROCESS',
    required: true,
    evidenceType: 'PHOTO',
    scoreWeight: 10,
    description: '留档'
  });
  assert.equal(saveResponse.data.configVersion, 2);
  assert.deepEqual(saveResponse.data.items, activeItems);

  enqueue(success({ version: 3 }));
  assert.equal((await api.getCareMetricConfig('service-001')).code, 502);
  nextRequest();
});

test('checklist and metric check endpoints preserve phase 35 and 38 response contracts', async () => {
  const checklist = {
    items: [{
      itemId: 'order_metric_1',
      metricCode: 'SERVICE_PHOTO',
      required: true,
      evidenceType: 'PHOTO',
      expectedAction: null,
      status: 'PENDING',
      scoreWeight: 10
    }]
  };
  enqueue(success(checklist));
  assert.equal((await api.generateMetricChecklist('order/001')).data.items[0].expectedAction, '');
  let request = nextRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/admin/orders/order%2F001/metric-checklist/generate');

  enqueue(success(checklist));
  await api.getNurseMetricChecklist('order-001');
  request = nextRequest();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/nurse/orders/order-001/metric-checklist');

  const check = {
    items: [{
      metricItemId: 'order_metric_1',
      metricName: '服务照片',
      checkResult: 'MISSING',
      scoreImpact: -10,
      missingEvidence: true
    }]
  };
  enqueue(success(check));
  assert.equal((await api.runMetricCheck('order-001')).data.items[0].checkResult, 'MISSING');
  request = nextRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/orders/order-001/metric-check');

  enqueue(success(check));
  await api.getMetricCheckResult('order-001');
  request = nextRequest();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/orders/order-001/metric-check-result');
});

test('evidence endpoints validate files, review comments and fixed list shapes', async () => {
  enqueue(success({ evidenceId: 'evidence_1', auditStatus: 'PENDING' }));
  const submitResponse = await api.submitCareEvidence('order-001', {
    metricItemId: 'order_metric_1',
    fileId: 'file_1',
    evidenceType: 'PHOTO',
    description: '服务照片'
  });
  let request = nextRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/nurse/orders/order-001/evidences');
  assert.equal(request.data.fileId, 'file_1');
  assert.equal(submitResponse.data.auditStatus, 'PENDING');

  enqueue(success([{ evidenceId: 'evidence_1', auditStatus: 'PENDING' }]));
  assert.equal((await api.getOrderEvidences('order-001')).data.length, 1);
  request = nextRequest();
  assert.equal(request.url, '/api/v1/orders/order-001/evidences');

  enqueue(success([{ evidenceId: 'evidence_1', auditStatus: 'PENDING' }]));
  await api.getAdminEvidences();
  request = nextRequest();
  assert.equal(request.url, '/api/v1/admin/evidences');

  const before = requests.length;
  assert.equal((await api.reviewCareEvidence('evidence_1', { auditStatus: 'REJECTED', reviewComment: '' })).code, 422);
  assert.equal(requests.length, before);

  enqueue(success({ evidenceId: 'evidence_1', auditStatus: 'APPROVED' }));
  await api.reviewCareEvidence('evidence_1', { auditStatus: 'APPROVED', reviewComment: '材料有效' });
  request = nextRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/admin/evidences/evidence_1/review');
  assert.deepEqual(request.data, { auditStatus: 'APPROVED', reviewComment: '材料有效' });
});

test('exception proof endpoints enforce score decision rules and frozen paths', async () => {
  enqueue(success({ proofId: 'proof_1', reviewStatus: 'PENDING' }));
  await api.submitMetricExceptionProof('metric/001', {
    reasonType: 'ELDER_REFUSED',
    reasonText: '长辈拒绝拍照',
    fileIds: ['file_1']
  });
  let request = nextRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/nurse/metric-items/metric%2F001/exception-proofs');
  assert.deepEqual(request.data.fileIds, ['file_1']);

  enqueue(success([{ proofId: 'proof_1', reviewStatus: 'PENDING' }]));
  await api.getNurseExceptionProofs('order-001');
  request = nextRequest();
  assert.equal(request.url, '/api/v1/nurse/orders/order-001/exception-proofs');

  enqueue(success([{ proofId: 'proof_1', reviewStatus: 'PENDING', scoreDecision: 'NO_DEDUCTION' }]));
  await api.getAdminExceptionProofs();
  request = nextRequest();
  assert.equal(request.url, '/api/v1/admin/metric-exception-proofs');

  const before = requests.length;
  assert.equal((await api.reviewMetricExceptionProof('proof_1', {
    reviewResult: 'APPROVED',
    reviewComment: '材料有效',
    scoreDecision: 'DEDUCT'
  })).code, 422);
  assert.equal(requests.length, before);

  enqueue(success({ proofId: 'proof_1', reviewStatus: 'REJECTED', scoreDecision: 'DEDUCT' }));
  await api.reviewMetricExceptionProof('proof_1', {
    reviewResult: 'REJECTED',
    reviewComment: '证明不足',
    scoreDecision: 'DEDUCT'
  });
  request = nextRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/admin/metric-exception-proofs/proof_1/review');
});

test('rules expose business labels and block invalid local submissions', () => {
  assert.equal(rules.CARE_METRIC_STATUS_LABELS.MISSING, '未完成');
  assert.equal(rules.EVIDENCE_AUDIT_STATUS_LABELS.NEED_MORE, '需补材料');
  assert.equal(rules.validateMetricConfigItems([]), '请至少配置一个护理指标。');
  assert.equal(rules.validateProofReview({
    reviewResult: 'APPROVED',
    reviewComment: '',
    scoreDecision: 'DEDUCT'
  }), '豁免通过必须选择不扣分。');
});

test('wires stages 34-40 into admin and nurse apps without runtime mock fallback', async () => {
  const [apiSource, adminApp, nurseApp, adminPanel, nursePanel] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/api/stageThirtyFourToForty.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/admin/AdminApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/nurse/NurseApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageThirtyFourToFortyAdminPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageThirtyFiveToFortyNursePanel.vue'), 'utf8')
  ]);
  assert.doesNotMatch(apiSource, /isMockEnabled|@\/mock|mock\s*:|mockFallback/);
  assert.match(adminApp, /StageThirtyFourToFortyAdminPanel/);
  assert.match(adminApp, /CARE_METRIC_CONFIG_MANAGE/);
  assert.match(adminApp, /CARE_EVIDENCE_REVIEW/);
  assert.match(adminApp, /care-metrics/);
  assert.match(nurseApp, /StageThirtyFiveToFortyNursePanel/);
  assert.match(nurseApp, /activeTab === 'quality'/);
  assert.match(nurseApp, /openQuality/);
  assert.match(adminPanel, /saveCareMetricConfig/);
  assert.match(adminPanel, /reviewCareEvidence/);
  assert.doesNotMatch(adminPanel, /generateMetricChecklist|reviewMetricExceptionProof|清单生成|豁免审核/);
  assert.match(nursePanel, /uploadMedicalFileAsset/);
  assert.match(nursePanel, /submitCareEvidence/);
  assert.match(nursePanel, /submitMetricExceptionProof/);
});
