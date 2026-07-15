import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const frontendRoot = fileURLToPath(new URL('..', import.meta.url));
const requests = [];
const responses = [];

globalThis.uni = {
  getStorageSync() {
    return {
      token: 'stage-21-test-token',
      user: { userId: 'admin-001', displayName: '审核员', roles: ['ADMIN'], menus: [] }
    };
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
  server: { middlewareMode: true },
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } }
});
const rules = await vite.ssrLoadModule('/src/utils/stageTwentyOneRules.ts');
const stageTwentyOneApi = await vite.ssrLoadModule('/src/api/stageTwentyOne.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function apiSuccess(data) {
  return { code: 0, message: 'success', traceId: 'stage-21-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

function takeRequest() {
  const request = requests.shift();
  assert.ok(request, 'expected an API request');
  return request;
}

test('reads permissions from the existing permissions endpoint and denies missing permission data', async () => {
  enqueue(apiSuccess({ roleCode: 'ADMIN', permissions: ['ADMIN_DASHBOARD_VIEW'] }));
  const response = await stageTwentyOneApi.getMedicalFileReviewPermissions();
  const request = takeRequest();

  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/auth/permissions');
  assert.equal(response.code, 0);
  assert.equal(response.data.includes('ADMIN_DASHBOARD_VIEW'), true);
  assert.equal(rules.canEnterMedicalFileReview(['ADMIN'], response.data), true);
  assert.equal(rules.canEnterMedicalFileReview(['ADMIN'], ['ADMIN_DASHBOARD_VIEW']), true);
  assert.equal(rules.canEnterMedicalFileReview(['CUSTOMER_SERVICE'], ['CUSTOMER_SERVICE_TICKET_HANDLE']), true);
  assert.equal(rules.canEnterMedicalFileReview(['ADMIN'], []), false);
  assert.equal(rules.canEnterMedicalFileReview(['FAMILY'], ['health:review']), false);

  enqueue(apiSuccess({ roleCode: 'ADMIN', permissions: [1] }));
  const malformed = await stageTwentyOneApi.getMedicalFileReviewPermissions();
  takeRequest();
  assert.equal(malformed.code, 502);
});

test('passes only the pagination and status parameters supported by the real backend', async () => {
  enqueue(apiSuccess({
    records: [{
      medicalFileId: 'medical-file-001',
      fileId: 'file-001',
      elderId: 'elder-001',
      fileType: 'PRESCRIPTION',
      title: '门诊处方',
      occurredAt: '2026-07-10',
      auditStatus: 'PENDING_REVIEW'
    }],
    total: 21,
    page: 2,
    size: 10
  }));
  const query = {
    page: 2,
    size: 10,
    auditStatus: 'PENDING'
  };
  const response = await stageTwentyOneApi.getAdminMedicalFiles(query);
  const request = takeRequest();

  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/admin/medical-files');
  assert.deepEqual(request.data, { page: 2, size: 10, auditStatus: 'PENDING' });
  assert.equal(response.data.total, 21);
  assert.equal(response.data.page, 2);
  assert.equal(response.data.size, 10);
  assert.equal(response.data.records[0].auditStatus, 'PENDING');
  assert.equal(response.data.records[0].elderName, undefined);
  assert.equal(response.data.records[0].createdAt, undefined);
});

test('removes inactive filters without dropping pagination parameters', async () => {
  enqueue(apiSuccess({ records: [], total: 0, page: 1, size: 20 }));
  await stageTwentyOneApi.getAdminMedicalFiles({
    page: 1,
    size: 20,
    auditStatus: ''
  });
  const request = takeRequest();
  assert.deepEqual(request.data, { page: 1, size: 20 });
});

test('loads the selected detail through an encoded path and ignores stale detail responses', async () => {
  enqueue(apiSuccess({
    medicalFileId: 'medical-file-021',
    fileId: 'file/021',
    elderId: 'elder-001',
    fileType: 'EXAMINATION_REPORT',
    title: '心电图报告',
    createdAt: '2026-07-13T09:00:00',
    auditStatus: 'PENDING'
  }));
  const response = await stageTwentyOneApi.getAdminMedicalFileDetail('file/021');
  const request = takeRequest();

  assert.equal(request.url, '/api/v1/admin/medical-files/file%2F021');
  assert.equal(response.data.title, '心电图报告');
  assert.equal(rules.isCurrentMedicalFileSelection('file/021', 'file/021'), true);
  assert.equal(rules.isCurrentMedicalFileSelection('file/021', 'file/022'), false);
});

test('submits the exact review payload and preserves structured extracted items', async () => {
  const payload = {
    auditStatus: 'APPROVED',
    reviewComment: '资料内容完整。',
    extractToArchive: true,
    extractedItems: [{ fieldName: 'riskTag', fieldLabel: '风险提示', value: '注意跌倒风险' }]
  };
  enqueue(apiSuccess({ fileId: 'file-001', auditStatus: 'APPROVED', reviewedAt: '2026-07-13T12:00:00' }));
  const response = await stageTwentyOneApi.reviewAdminMedicalFile('file-001', payload);
  const request = takeRequest();

  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/admin/medical-files/file-001/review');
  assert.deepEqual(request.data, payload);
  assert.equal(response.data.auditStatus, 'APPROVED');
});

test('refreshes the filtered list and then reloads the reviewed detail by its original id', async () => {
  const sequence = [];
  const result = await rules.refreshReviewedMedicalFile(
    'file-reviewed',
    async () => { sequence.push('list'); },
    async (fileId) => { sequence.push(`detail:${fileId}`); return fileId; }
  );
  assert.deepEqual(sequence, ['list', 'detail:file-reviewed']);
  assert.equal(result, 'file-reviewed');
});

test('validates review comments and archive extraction completeness', () => {
  assert.match(rules.validateMedicalFileReview('REJECTED', '  '), /必须填写审核意见/);
  assert.match(rules.validateMedicalFileReview('NEED_MORE', '  '), /必须填写审核意见/);
  assert.match(rules.validateMedicalFileReview('APPROVED', '意'.repeat(256)), /255/);
  assert.match(rules.validateArchiveExtraction(true, 0, 0), /没有可进入/);
  assert.match(rules.validateArchiveExtraction(true, 2, 0), /至少选择一项/);
  assert.equal(rules.validateArchiveExtraction(true, 2, 1), '');
  assert.equal(rules.validateArchiveExtraction(false, 0, 0), '');
});

test('keeps real permission and API errors instead of manufacturing successful data', async () => {
  enqueue({ code: 403, message: 'forbidden', traceId: 'stage-21-test', data: {} }, 403);
  const denied = await stageTwentyOneApi.getMedicalFileReviewPermissions();
  takeRequest();
  assert.equal(denied.code, 403);

  enqueue({ code: 409, message: 'state conflict', traceId: 'stage-21-test', data: {} }, 409);
  const conflict = await stageTwentyOneApi.reviewAdminMedicalFile('file-001', {
    auditStatus: 'REJECTED', reviewComment: '资料模糊', extractToArchive: false, extractedItems: []
  });
  takeRequest();
  assert.equal(conflict.code, 409);

  enqueue(apiSuccess({ auditStatus: 'APPROVED', reviewedAt: '2026-07-13T12:00:00' }));
  const malformed = await stageTwentyOneApi.reviewAdminMedicalFile('file-001', {
    auditStatus: 'APPROVED', reviewComment: '', extractToArchive: false, extractedItems: []
  });
  takeRequest();
  assert.equal(malformed.code, 502);

  enqueue(apiSuccess({ records: null, total: 1, page: 1, size: 10 }));
  const malformedList = await stageTwentyOneApi.getAdminMedicalFiles({
    page: 1, size: 10, auditStatus: 'PENDING'
  });
  takeRequest();
  assert.equal(malformedList.code, 502);
});
