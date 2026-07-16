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
    return { token: 'stage-27-token', user: { userId: 'admin-001', roles: ['ADMIN'], menus: [] } };
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
const api = await vite.ssrLoadModule('/src/api/stageTwentySeven.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageTwentySevenRules.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-27-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

function qualificationApplication(overrides = {}) {
  return {
    applicationId: 'qualification-001',
    nurseId: 'nurse-001',
    nurseName: '李护士',
    auditStatus: 'APPROVED',
    realName: '李明',
    idNoMasked: '**************1234',
    certificateNoMasked: 'CERT-****-027',
    certificateFiles: [{
      fileId: 'file-001', originalName: '护理证书.pdf', mimeType: 'application/pdf', size: 10240, previewable: true
    }],
    serviceSkillCodes: ['BASIC_CARE'],
    reviewComment: '材料完整。',
    submittedAt: '2026-07-15T10:00:00+08:00',
    reviewedAt: '2026-07-15T11:00:00+08:00',
    ...overrides
  };
}

test('submits the selected application review with the frozen payload', async () => {
  enqueue(success({ nurseId: 'nurse-001', qualificationStatus: 'APPROVED' }));
  const payload = { auditStatus: 'APPROVED', reviewComment: '资料完整，审核通过。' };
  const response = await api.reviewQualificationApplication('qualification-001', payload);
  const request = requests.shift();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/admin/nurse-qualification-applications/qualification-001/review');
  assert.equal(request.header.Authorization, 'Bearer stage-27-token');
  assert.deepEqual(request.data, payload);
  assert.equal(response.data.qualificationStatus, 'APPROVED');
});

test('preserves permission, concurrency and malformed response failures', async () => {
  for (const code of [403, 409, 422]) {
    enqueue({ code, message: 'failed', traceId: 'stage-27-test', data: {} }, code);
    const response = await api.reviewQualificationApplication('qualification-001', {
      auditStatus: 'NEED_MORE', reviewComment: '请补充原件扫描件。'
    });
    requests.shift();
    assert.equal(response.code, code);
  }
  enqueue(success({ nurseId: 'nurse-001' }));
  const malformed = await api.reviewQualificationApplication('qualification-001', {
    auditStatus: 'APPROVED', reviewComment: ''
  });
  requests.shift();
  assert.equal(malformed.code, 502);
});

test('requires explicit material review, decision and rejection reason', () => {
  const validBase = {
    applicationStatus: 'PENDING', decision: 'APPROVED', reviewComment: '', allMaterialsReviewed: true
  };
  assert.equal(rules.validateQualificationReview(validBase), '');
  assert.match(rules.validateQualificationReview({ ...validBase, allMaterialsReviewed: false }), /核对全部/);
  assert.match(rules.validateQualificationReview({ ...validBase, decision: '' }), /选择审核决定/);
  assert.match(rules.validateQualificationReview({ ...validBase, decision: 'REJECTED' }), /必须填写原因/);
  assert.match(rules.validateQualificationReview({ ...validBase, decision: 'NEED_MORE' }), /必须填写说明/);
  assert.match(rules.validateQualificationReview({ ...validBase, applicationStatus: 'APPROVED' }), /不能重复审核/);
  assert.match(rules.validateQualificationReview({
    ...validBase, reviewComment: '说'.repeat(rules.QUALIFICATION_REVIEW_COMMENT_MAX_LENGTH + 1)
  }), /不能超过/);
});

test('maps review failures to readable business messages', () => {
  assert.match(rules.qualificationReviewErrorMessage(401), /登录状态/);
  assert.match(rules.qualificationReviewErrorMessage(403), /没有审核/);
  assert.match(rules.qualificationReviewErrorMessage(409), /其他审核人员/);
  assert.match(rules.qualificationReviewErrorMessage(422), /不符合要求/);
  assert.match(rules.qualificationReviewErrorMessage(502), /响应内容不完整/);
  assert.match(rules.qualificationCertificatePreviewErrorMessage(401), /重新登录/);
  assert.match(rules.qualificationCertificatePreviewErrorMessage(403), /没有查看/);
  assert.match(rules.qualificationCertificatePreviewErrorMessage(404), /已不存在/);
  assert.match(rules.qualificationCertificatePreviewErrorMessage(500), /暂时无法打开/);
});

test('resolves every skill against the live dictionary and uses nurse-facing rejection text', () => {
  const options = [{ value: 'BASIC_CARE', label: '基础照护', sort: 1 }];
  assert.equal(rules.areQualificationSkillsResolved(['BASIC_CARE'], options, true), true);
  assert.equal(rules.areQualificationSkillsResolved(['UNKNOWN'], options, true), false);
  assert.equal(rules.areQualificationSkillsResolved(['BASIC_CARE'], options, false), false);
});

test('reads the reviewed application across all real result pages', async () => {
  enqueue(success({
    records: [qualificationApplication({ applicationId: 'qualification-other' })],
    total: 101,
    page: 1,
    size: 100
  }));
  enqueue(success({
    records: [qualificationApplication()],
    total: 101,
    page: 2,
    size: 100
  }));
  const response = await api.findReviewedQualificationApplication(
    'qualification-001', 'APPROVED'
  );
  const firstRequest = requests.shift();
  const secondRequest = requests.shift();
  assert.match(firstRequest.url, /auditStatus=APPROVED.*page=1.*size=100/);
  assert.match(secondRequest.url, /auditStatus=APPROVED.*page=2.*size=100/);
  assert.equal(response.code, 0);
  assert.equal(response.data.applicationId, 'qualification-001');
  assert.equal(response.data.reviewedAt, '2026-07-15T11:00:00+08:00');
});

test('cancels stale review refreshes and preserves refresh failures', async () => {
  enqueue(success({ records: [], total: 101, page: 1, size: 100 }));
  let currentChecks = 0;
  const cancelled = await api.findReviewedQualificationApplication(
    'qualification-001', 'APPROVED', () => ++currentChecks === 1
  );
  requests.shift();
  assert.equal(cancelled.code, 499);

  enqueue({ code: 500, message: 'failed', traceId: 'stage-27-test', data: {} }, 500);
  const failed = await api.findReviewedQualificationApplication(
    'qualification-001', 'APPROVED'
  );
  requests.shift();
  assert.equal(failed.code, 500);
});

test('wires a list-driven three-pane workbench without technical ID input or mock success', async () => {
  const [component, apiSource, adminApp] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentySevenQualificationReviewWorkbench.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwentySeven.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/admin/AdminApp.vue'), 'utf8')
  ]);
  const template = component.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? '';
  assert.match(component, /workbench-grid/);
  assert.match(component, /grid-template-columns:minmax\(250px/);
  assert.match(component, /selectApplication\(record\)/);
  assert.match(component, /const decision = ref<QualificationReviewDecision \| ''>\(''\)/);
  assert.match(component, /reviewRequestSequence/);
  assert.match(component, /selectedApplicationId\.value === applicationId/);
  assert.match(component, /findReviewedQualificationApplication/);
  assert.match(component, /sequence !== reviewRequestSequence/);
  assert.match(component, /selectedSkillsResolved/);
  assert.match(component, /!selectedSkillsResolved \|\| Boolean\(submittingApplicationId\)/);
  assert.match(component, /@click="refreshWorkbench"/);
  assert.ok(
    component.indexOf('const latestResponse = await findReviewedQualificationApplication')
      < component.indexOf('completedReview.value = {'),
    'completion must be shown only after the latest record has been read'
  );
  assert.match(component, /getQualificationCertificatePreview/);
  assert.match(component, /qualificationReviewErrorMessage\(response\.code\)/);
  assert.match(component, /certificateFiles\.length > 0/);
  assert.doesNotMatch(component, /placeholder="[^"]*(?:applicationId|申请编号)/);
  assert.doesNotMatch(template, />[^<{]*(?:API|DTO|applicationId|nurseId|fileId)[^<{]*</);
  assert.doesNotMatch(apiSource, /mock|fallback/i);
  assert.match(adminApp, /StageTwentySevenQualificationReviewWorkbench/);
});
