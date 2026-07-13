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
    return { token: 'stage-25-token', user: { userId: 'nurse-001', displayName: '护理员', roles: ['NURSE'], menus: [] } };
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
const api = await vite.ssrLoadModule('/src/api/stageTwentyFive.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageTwentyFiveRules.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-25-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

const summary = {
  elderProfile: {
    elderName: '张明',
    age: 78,
    careLevel: 'LEVEL_2',
    carePlan: { dailyCare: '起身时陪同', precautions: '夜间保持照明' }
  },
  riskTags: [{ tagCode: 'FALL_RISK', tagName: '跌倒风险' }],
  medications: [{
    medicationName: '阿司匹林', dosage: '半片', frequency: 'ONCE_DAILY',
    timePoints: ['08:00'], startDate: '2026-07-01'
  }],
  diseases: [{ diseaseName: '高血压', status: 'MONITORING' }],
  allergies: [{ allergenName: '青霉素', reaction: '皮疹', severity: 'SEVERE' }],
  approvedMedicalFiles: [{ title: '近期检查报告', fileType: 'EXAMINATION_REPORT', occurredAt: '2026-07-10' }],
  recentReports: [{ serviceName: '基础上门护理', summary: '生命体征平稳', generatedAt: '2026-07-11T12:00:00+08:00' }]
};

test('reads the exact assigned-order health summary endpoint with bearer auth', async () => {
  enqueue(success(summary));
  const response = await api.getPreServiceHealthSummary('order/001');
  const request = requests.shift();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/nurse/orders/order%2F001/pre-service-health-summary');
  assert.equal(request.header.Authorization, 'Bearer stage-25-token');
  assert.equal(response.code, 0);
  assert.equal(response.data.riskTags[0].tagName, '跌倒风险');
});

test('rejects a success response that omits a required summary collection', async () => {
  const malformed = structuredClone(summary);
  delete malformed.approvedMedicalFiles;
  enqueue(success(malformed));
  const response = await api.getPreServiceHealthSummary('order-001');
  requests.shift();
  assert.equal(response.code, 502);
});

test('rejects malformed collection items before the page renders them', async () => {
  const malformed = structuredClone(summary);
  delete malformed.medications[0].timePoints;
  enqueue(success(malformed));
  const response = await api.getPreServiceHealthSummary('order-001');
  requests.shift();
  assert.equal(response.code, 502);
});

test('keeps permission failures real instead of replacing them with local data', async () => {
  enqueue({ code: 403, message: 'forbidden', traceId: 'stage-25-test', data: {} }, 403);
  const response = await api.getPreServiceHealthSummary('order-001');
  requests.shift();
  assert.equal(response.code, 403);
  assert.match(rules.preServiceSummaryError(response.code), /不是该订单的服务护理人员/);
});

test('formats profile and care points as business-facing text', () => {
  assert.equal(rules.elderProfileName(summary.elderProfile), '张明');
  assert.equal(rules.elderProfileSummary(summary.elderProfile), '78 岁 · 二级照护');
  assert.deepEqual(rules.carePointList(summary.elderProfile), ['日常照护：起身时陪同', '注意事项：夜间保持照明']);
});

test('wires a read-only mobile summary and invalidates stale order responses', async () => {
  const [apiSource, component, nurseApp] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwentyFive.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyFivePreServiceSummary.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/nurse/NurseApp.vue'), 'utf8')
  ]);
  assert.doesNotMatch(apiSource, /mock/i);
  assert.doesNotMatch(apiSource, /method:\s*['"](?:POST|PUT|DELETE)['"]/);
  assert.match(component, /summary\.value = null;[\s\S]*getPreServiceHealthSummary\(orderId\)/);
  assert.match(component, /sequence !== requestSequence \|\| orderId !== props\.orderId/);
  assert.match(component, /watch\(\(\) => props\.orderId, loadSummary, \{ immediate: true \}\)/);
  assert.match(component, /target\.origin !== window\.location\.origin[\s\S]*isSignedUrl/);
  assert.match(component, /new AbortController\(\)/);
  assert.match(component, /signal: controller\.signal/);
  assert.match(component, /onBeforeUnmount\(\(\) => \{[\s\S]*cancelPendingPreview\(\)/);
  assert.match(nurseApp, /StageTwentyFivePreServiceSummary/);
  assert.match(nurseApp, /查看健康摘要/);
  assert.match(nurseApp, /summaryTaskId\.value = ''/);
  assert.match(nurseApp, /\['DISPATCHED', 'ACCEPTED', 'ON_THE_WAY'\]/);
  assert.match(nurseApp, /v-if="preServiceSummaryStatuses\.includes\(task\.taskStatus\)"/);
  assert.doesNotMatch(nurseApp, /service-name="[^\n"]*serviceId/);

  const template = component.split('<template>')[1]?.split('<style')[0] ?? '';
  for (const heading of ['重点风险', '过敏信息', '当前用药', '慢病与照护要点', '审核通过病历', '近期服务摘要']) {
    assert.match(template, new RegExp(heading));
  }
  assert.ok(template.indexOf('重点风险') < template.indexOf('过敏信息'));
  assert.ok(template.indexOf('过敏信息') < template.indexOf('当前用药'));
  assert.doesNotMatch(template, /archiveVersion|traceId|storagePath|auditOpinion|\{\{\s*orderId/);
  assert.doesNotMatch(template, /input|textarea|switch/);
});
