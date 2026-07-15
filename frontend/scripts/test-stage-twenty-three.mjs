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
    return {
      token: 'stage-23-test-token',
      user: { userId: 'nurse-001', displayName: '护理员', roles: ['NURSE'], menus: [] }
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
  server: { middlewareMode: true, hmr: false },
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } }
});
const rules = await vite.ssrLoadModule('/src/utils/stageTwentyThreeRules.ts');
const api = await vite.ssrLoadModule('/src/api/stageTwentyThree.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function apiSuccess(data) {
  return { code: 0, message: 'success', traceId: 'stage-23-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

function takeRequest() {
  const request = requests.shift();
  assert.ok(request, 'expected an API request');
  return request;
}

const validDiseasePayload = {
  fieldName: 'diseases',
  newValue: { diseaseName: '高血压', diagnosedAt: '2025-01-02', status: 'MONITORING', remark: '服务中血压偏高' },
  sourceType: 'SERVICE_RECORD',
  sourceId: 'record-001',
  reason: '本次服务连续测得血压偏高，建议持续观察。'
};

test('submits an exact structured suggestion to the encoded order resource', async () => {
  enqueue(apiSuccess({ suggestionId: 'suggestion-001', status: 'PENDING' }));
  const response = await api.createHealthUpdateSuggestion('order/001', validDiseasePayload);
  const request = takeRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/orders/order%2F001/health-update-suggestions');
  assert.deepEqual(request.data, validDiseasePayload);
  assert.equal(response.data.status, 'PENDING');
});

test('rejects a successful create response without a suggestion id or valid status', async () => {
  enqueue(apiSuccess({ status: 'PENDING' }));
  const missingId = await api.createHealthUpdateSuggestion('order-001', validDiseasePayload);
  takeRequest();
  assert.equal(missingId.code, 502);

  enqueue(apiSuccess({ suggestionId: 'suggestion-001', status: 'UNKNOWN' }));
  const badStatus = await api.createHealthUpdateSuggestion('order-001', validDiseasePayload);
  takeRequest();
  assert.equal(badStatus.code, 502);
});

test('keeps suggestion approval distinct from archive completion', async () => {
  enqueue(apiSuccess({ suggestionId: 'suggestion-002', status: 'APPROVED' }));
  const response = await api.createHealthUpdateSuggestion('order-002', validDiseasePayload);
  takeRequest();
  assert.equal(response.code, 0);
  assert.equal(response.data.status, 'APPROVED');
  assert.equal(rules.normalizeHealthReviewStatus('APPROVED'), 'ARCHIVED');
  assert.equal(rules.normalizeHealthReviewStatus('ARCHIVED'), 'ARCHIVED');
});

test('reads raw health-review permissions without inheriting dashboard aliases', async () => {
  enqueue(apiSuccess({ roleCode: 'ADMIN', permissions: ['ADMIN_DASHBOARD_VIEW'] }));
  const dashboardOnly = await api.getHealthReviewPermissions();
  let request = takeRequest();
  assert.equal(request.url, '/api/v1/auth/permissions');
  assert.deepEqual(dashboardOnly.data, ['ADMIN_DASHBOARD_VIEW']);
  assert.equal(rules.canViewHealthReviewTasks(['ADMIN'], dashboardOnly.data), false);

  enqueue(apiSuccess({ roleCode: 'ADMIN', permissions: ['HEALTH_ARCHIVE_REVIEW'] }));
  const reviewer = await api.getHealthReviewPermissions();
  request = takeRequest();
  assert.equal(request.method, 'GET');
  assert.equal(rules.canViewHealthReviewTasks(['ADMIN'], reviewer.data), true);
});

test('reads admin tasks with active filters only and normalizes business data', async () => {
  enqueue(apiSuccess({
    records: [{
      reviewTaskId: 'task-001', status: 'PENDING_REVIEW', elderName: '张淑兰', orderServiceName: '基础上门护理',
      createdAt: '2026-07-13T10:00:00+08:00', suggestion: {
        suggestionId: 'suggestion-001', sourceType: 'SERVICE_REPORT', sourceSummary: '护理建议继续观察血压',
        fieldName: 'riskTags', originalValue: [], newValue: { tagCode: 'FALL_RISK', tagName: '跌倒风险' }, reason: '步态不稳需关注'
      }
    }], total: 1, page: 1, size: 20
  }));
  const response = await api.getAdminHealthReviewTasks({ page: 1, size: 20, status: 'PENDING', sourceType: '', keyword: '' });
  const request = takeRequest();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/admin/health-review-tasks');
  assert.deepEqual(request.data, { page: 1, size: 20, status: 'PENDING' });
  assert.equal(response.data.records[0].status, 'PENDING');
  assert.equal(response.data.records[0].serviceName, '基础上门护理');
  assert.equal(response.data.records[0].suggestedValue.tagName, '跌倒风险');
});

test('keeps real permission and malformed-list failures', async () => {
  enqueue({ code: 403, message: 'forbidden', traceId: 'stage-23-test', data: {} }, 403);
  const denied = await api.getAdminHealthReviewTasks({ page: 1, size: 20, status: '', sourceType: '', keyword: '' });
  takeRequest();
  assert.equal(denied.code, 403);
  assert.deepEqual(denied.data.records, []);

  enqueue(apiSuccess({ records: [{}], total: 1, page: 1, size: 20 }));
  const malformed = await api.getAdminHealthReviewTasks({ page: 1, size: 20, status: '', sourceType: '', keyword: '' });
  takeRequest();
  assert.equal(malformed.code, 502);
});

test('allows admin or customer service only when health review permission is present', () => {
  assert.equal(rules.canViewHealthReviewTasks(['ADMIN'], ['health:review']), true);
  assert.equal(rules.canViewHealthReviewTasks(['ADMIN'], []), false);
  assert.equal(rules.canViewHealthReviewTasks(['CUSTOMER_SERVICE'], ['health:review']), true);
  assert.equal(rules.canViewHealthReviewTasks(['CUSTOMER_SERVICE'], []), false);
  assert.equal(rules.canViewHealthReviewTasks(['NURSE'], ['health:review']), false);
});

test('does not hide a service report failure behind an empty-source message', () => {
  assert.match(
    rules.healthSuggestionSourceLoadError(
      { code: 404, message: 'no records' },
      { code: 500, message: '报告服务异常' }
    ),
    /服务报告读取失败.*报告服务异常/
  );
  assert.equal(
    rules.healthSuggestionSourceLoadError(
      { code: 404, message: 'no records' },
      { code: 404, message: 'no report' }
    ),
    ''
  );
});

test('validates every business value type without accepting arbitrary fields', () => {
  assert.equal(rules.validateHealthSuggestion(validDiseasePayload), '');
  assert.match(rules.validateHealthSuggestion({ ...validDiseasePayload, sourceId: '' }), /依据/);
  assert.match(rules.validateHealthSuggestion({ ...validDiseasePayload, reason: '短' }), /至少 5/);
  assert.match(rules.validateHealthSuggestion({ ...validDiseasePayload, reason: '原'.repeat(256) }), /255/);
  assert.match(rules.validateHealthSuggestion({ ...validDiseasePayload, newValue: { ...validDiseasePayload.newValue, diagnosedAt: 'not-a-date' } }), /有效/);

  const medication = {
    ...validDiseasePayload, fieldName: 'medications', sourceType: 'SERVICE_REPORT', sourceId: 'report-001',
    newValue: { medicationName: '阿司匹林', dosage: '半片', frequency: 'ONCE_DAILY', timePoints: ['08:00'], startDate: '2026-07-13', endDate: '2026-07-12' }
  };
  assert.match(rules.validateHealthSuggestion(medication), /不能早于/);
  assert.equal(rules.isHealthSuggestionField('rawSql'), false);
});

test('formats archive values as readable Chinese instead of raw object text', () => {
  assert.equal(rules.formatHealthSuggestionValue('riskTags', { tagCode: 'FALL_RISK', tagName: '跌倒风险' }), '跌倒风险');
  assert.match(rules.formatHealthSuggestionValue('medications', {
    medicationName: '阿司匹林', dosage: '半片', frequency: 'ONCE_DAILY', timePoints: ['08:00'], startDate: '2026-07-13'
  }), /每日一次/);
  assert.doesNotMatch(rules.formatHealthSuggestionValue('medications', {
    medicationName: '阿司匹林', frequency: 'ONCE_DAILY', timePoints: [], startDate: '2026-07-13'
  }), /ONCE_DAILY/);
});

test('wires independent nurse and admin entries without mock fallback or internal id inputs', async () => {
  const [nurseApp, adminApp, nursePanel, adminPanel, stageApi] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/apps/nurse/NurseApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/admin/AdminApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyThreeSuggestionPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyThreeReviewTaskList.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwentyThree.ts'), 'utf8')
  ]);
  assert.match(nurseApp, /档案建议/);
  assert.match(nurseApp, /建议更新健康档案/);
  assert.match(adminApp, /health-review/);
  assert.match(adminApp, /档案建议/);
  assert.match(adminApp, /item\.key === 'medical-files' \|\| item\.key === 'health-review'/);
  assert.doesNotMatch(adminApp, /view === 'health-review' && isAdmin/);
  assert.doesNotMatch(stageApi, /mock/i);
  assert.doesNotMatch(nursePanel, /v-model="[^\"]*(orderId|sourceId|fieldName)/);
  const adminTemplate = adminPanel.split('<template>')[1]?.split('</template>')[0] ?? '';
  assert.doesNotMatch(adminTemplate, /\{\{\s*record\.(suggestionId|taskId)\s*\}\}/);
});
