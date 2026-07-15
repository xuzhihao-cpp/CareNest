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
      token: 'stage-24-test-token',
      user: { userId: 'admin-001', displayName: '管理员', roles: ['ADMIN'], menus: [] }
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
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } }
});
const rules = await vite.ssrLoadModule('/src/utils/stageTwentyFourRules.ts');
const api = await vite.ssrLoadModule('/src/api/stageTwentyFour.ts');
const stageTwentyThreeApi = await vite.ssrLoadModule('/src/api/stageTwentyThree.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function apiSuccess(data) {
  return { code: 0, message: 'success', traceId: 'stage-24-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

function takeRequest() {
  const request = requests.shift();
  assert.ok(request, 'expected an API request');
  return request;
}

const taskDetail = {
  reviewTaskId: 'task-001',
  reviewStatus: 'PENDING',
  elderId: 'elder-001',
  elderName: '张淑芬',
  orderServiceName: '基础上门护理',
  createdAt: '2026-07-13T10:00:00+08:00',
  archiveVersion: 4,
  evidence: {
    sourceType: 'SUGGESTION',
    title: '护理建议',
    summary: '护理记录提示夜间起身不稳。',
    occurredAt: '2026-07-12T20:00:00+08:00'
  },
  reviewItems: [{
    sourceField: 'riskObservation',
    targetField: 'riskTags',
    fieldLabel: '健康风险',
    currentValue: [{ tagCode: 'FALL_RISK', tagName: '跌倒风险' }],
    suggestedValue: { tagCode: 'NIGHT_FALL_RISK', tagName: '夜间跌倒风险' },
    normalizedValue: { tagCode: 'NIGHT_FALL_RISK', tagName: '夜间跌倒风险' },
    normalizationNote: '已转换为标准风险标签'
  }]
};

test('loads an encoded review task detail with normalized business fields', async () => {
  enqueue(apiSuccess(taskDetail));
  const response = await api.getHealthReviewTaskDetail('task/001');
  const request = takeRequest();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/admin/health-review-tasks/task%2F001');
  assert.equal(response.code, 0);
  assert.equal(response.data.evidence.sourceType, 'SUGGESTION');
  assert.equal(response.data.fields[0].targetField, 'riskTags');
  assert.equal(response.data.fields[0].normalizedValue.tagName, '夜间跌倒风险');
});

test('rejects detail responses without a server normalized value', async () => {
  const missingNormalized = structuredClone(taskDetail);
  delete missingNormalized.reviewItems[0].normalizedValue;
  enqueue(apiSuccess(missingNormalized));
  const response = await api.getHealthReviewTaskDetail('task-001');
  takeRequest();
  assert.equal(response.code, 502);
});

test('adapts the implemented backend task and suggestion detail contract', async () => {
  enqueue(apiSuccess({
    taskId: 'task-actual-001', elderId: 'elder-001', status: 'PENDING', archiveVersion: '4',
    suggestions: [{
      suggestionId: 'suggestion-actual-001', fieldName: 'riskTags', oldValue: '[]',
      newValue: '{"tagCode":"FALL_RISK","tagName":"跌倒风险"}', sourceType: 'SERVICE_RECORD',
      sourceId: 'record-001', reason: '护理服务中发现步态不稳。', status: 'PENDING'
    }]
  }));
  const context = {
    taskId: 'task-actual-001', status: 'PENDING', elderName: '张淑芬', serviceName: '基础上门护理',
    sourceType: 'SERVICE_RECORD', sourceSummary: '本次上门护理记录', fieldName: 'riskTags',
    currentValue: [], suggestedValue: {}, reason: '护理服务中发现步态不稳。',
    submittedAt: '2026-07-13T10:00:00'
  };
  const response = await api.getHealthReviewTaskDetail('task-actual-001', context);
  takeRequest();
  assert.equal(response.code, 0);
  assert.equal(response.data.elderName, '张淑芬');
  assert.equal(response.data.evidence.sourceType, 'SERVICE_RECORD');
  assert.equal(response.data.fields[0].normalizedValue.tagName, '跌倒风险');
});

test('submits the exact per-field archive decisions and keeps the returned version', async () => {
  const payload = {
    decisions: [{
      sourceField: 'riskObservation',
      targetField: 'riskTags',
      normalizedValue: { tagCode: 'NIGHT_FALL_RISK', tagName: '夜间跌倒风险' },
      decision: 'APPROVE',
      comment: '护理证据充分，同意归档。'
    }]
  };
  enqueue(apiSuccess({ taskId: 'task-001', status: 'APPROVED', archiveVersion: '5' }));
  const response = await api.archiveHealthReviewTask('task/001', payload);
  const request = takeRequest();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/admin/health-review-tasks/task%2F001/archive');
  assert.deepEqual(request.data, {
    decisions: [{
      sourceField: 'riskObservation', targetField: 'riskTags',
      normalizedValue: '{"tagCode":"NIGHT_FALL_RISK","tagName":"夜间跌倒风险"}',
      decision: 'APPROVED', comment: '护理证据充分，同意归档。'
    }]
  });
  assert.equal(response.data.status, 'ARCHIVED');
  assert.equal(response.data.archiveVersion, '5');
});

test('preserves a real concurrent archive conflict', async () => {
  enqueue({ code: 409, message: 'state conflict', traceId: 'stage-24-test', data: {} }, 409);
  const response = await api.archiveHealthReviewTask('task-001', { decisions: [] });
  takeRequest();
  assert.equal(response.code, 409);
});

test('accepts phase 21 review tasks that are not backed by a stage 23 suggestion id', async () => {
  enqueue(apiSuccess({
    records: [{
      reviewTaskId: 'medical-review-task-001', status: 'PENDING', elderName: '张淑芬',
      orderServiceName: '病历资料审核', createdAt: '2026-07-13T10:00:00+08:00',
      sourceType: 'MEDICAL_FILE', fieldName: 'medications', originalValue: [],
      newValue: [{ medicationName: '阿司匹林' }], reason: '病历审核后进入档案归档流程'
    }], total: 1, page: 1, size: 20
  }));
  const response = await stageTwentyThreeApi.getAdminHealthReviewTasks({ page: 1, size: 20, status: 'PENDING', sourceType: '', keyword: '' });
  takeRequest();
  assert.equal(response.code, 0);
  assert.equal(response.data.records[0].taskId, 'medical-review-task-001');
  assert.equal(response.data.records[0].sourceType, 'MEDICAL_FILE');
  assert.equal(response.data.records[0].suggestionId, undefined);
});

test('reads change logs from the exact elder resource without exposing reviewer data', async () => {
  enqueue(apiSuccess({
    records: [{
      changeLogId: 'log-001',
      fieldName: 'medications',
      fieldLabel: '当前用药',
      beforeValue: [],
      afterValue: [{ medicationName: '阿司匹林', frequency: 'ONCE_DAILY', timePoints: ['08:00'], startDate: '2026-07-13' }],
      sourceType: 'MEDICAL_FILE',
      sourceSummary: '近期门诊处方',
      reviewComment: '资料完整',
      archiveVersion: 5,
      createdAt: '2026-07-13T12:00:00+08:00',
      reviewerId: 'admin-001'
    }], total: 1, page: 1, size: 20
  }));
  const response = await api.getHealthArchiveChangeLogs('elder/001');
  const request = takeRequest();
  assert.equal(request.url, '/api/v1/admin/elders/elder%2F001/health-archive/change-logs');
  assert.equal(response.data.records[0].fieldLabel, '当前用药');
  assert.equal(response.data.records[0].comment, '资料完整');
  assert.equal('reviewerId' in response.data.records[0], false);
});

test('requires comments for rejection and supplementation and strips display-only labels', () => {
  const fields = [{
    sourceField: 'riskObservation', targetField: 'riskTags', fieldLabel: '健康风险',
    currentValue: [], suggestedValue: {}, normalizedValue: { tagCode: 'FALL_RISK', tagName: '跌倒风险' }
  }];
  const drafts = rules.buildArchiveDecisionDrafts(fields);
  assert.equal(drafts[0].decision, '');
  assert.match(rules.validateArchiveDecisions(drafts), /请选择/);
  drafts[0].decision = 'REJECT';
  assert.match(rules.validateArchiveDecisions(drafts), /至少 5/);
  drafts[0].comment = '现有证据不足，暂不采纳。';
  assert.equal(rules.validateArchiveDecisions(drafts), '');
  const requests = rules.toArchiveDecisionRequests(drafts);
  assert.equal('fieldLabel' in requests[0], false);
  assert.equal(requests[0].normalizedValue.tagName, '跌倒风险');
});

test('formats whole-archive logs as readable categories instead of object text', () => {
  const text = rules.formatArchiveChangeValue('', JSON.stringify({
    riskTags: [{ tagCode: 'FALL_RISK', tagName: '跌倒风险' }],
    carePlan: { careGoals: '安全活动', dailyCare: '起身搀扶', precautions: '夜间开灯' }
  }));
  assert.match(text, /健康风险：跌倒风险/);
  assert.match(text, /照护计划/);
  assert.doesNotMatch(text, /\[object Object\]/);
});

test('wires the admin workbench and user history without mock or direct archive PUT', async () => {
  const [apiSource, workbench, history, healthArchive] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwentyFour.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyThreeReviewTaskList.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyFourChangeHistory.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageNineteenHealthArchivePanel.vue'), 'utf8')
  ]);
  assert.doesNotMatch(apiSource, /mock/i);
  assert.match(workbench, /档案当前内容/);
  assert.match(workbench, /来源建议内容/);
  assert.match(workbench, /规范化后内容/);
  assert.match(workbench, /response\.code === 409/);
  assert.match(workbench, /if \(response\.code !== 0\) \{\s*if \(selectedTaskId\.value !== taskId\) return;/);
  assert.match(workbench, /selectedTaskId\.value !== taskId/);
  assert.match(workbench, /historyError\.value = businessError\(response\.code, 'history'\)/);
  assert.match(workbench, /!allDecisionsSelected/);
  assert.match(workbench, /function clearTaskSelection\(\)[\s\S]*\+\+detailRequestSequence;[\s\S]*detailLoading\.value = false;/);
  assert.match(workbench, /if \(!nextTaskId\) \{\s*clearTaskSelection\(\);/);
  assert.match(workbench, /if \(response\.code !== 0\) \{\s*records\.value = \[\];\s*total\.value = 0;\s*clearTaskSelection\(\);/);
  const historyTemplate = history.split('<template>')[1]?.split('</template>')[0] ?? '';
  assert.doesNotMatch(historyTemplate, /archiveVersion|档案版本/);
  assert.doesNotMatch(workbench, /v-model="[^"]*(sourceField|targetField|normalizedValue|taskId)/);
  assert.match(history, /sequence !== requestSequence \|\| elderId !== props\.elderId/);
  assert.doesNotMatch(history, /reviewerId|changedBy/);
  assert.match(healthArchive, /StageTwentyFourChangeHistory/);
  assert.doesNotMatch(apiSource, /method:\s*'PUT'/);
});
