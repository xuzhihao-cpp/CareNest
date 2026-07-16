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
    return { token: 'stage-26-token', user: { userId: 'nurse-001', displayName: '护理员', roles: ['NURSE'], menus: [] } };
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
const api = await vite.ssrLoadModule('/src/api/stageTwentySix.ts');
const rules = await vite.ssrLoadModule('/src/utils/stageTwentySixRules.ts');
const retry = await vite.ssrLoadModule('/src/utils/stageTwentySixRetry.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
});

function success(data) {
  return { code: 0, message: 'success', traceId: 'stage-26-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

const application = {
  applicationId: 'qualification-001',
  nurseId: 'nurse-001',
  nurseName: '李护士',
  auditStatus: 'PENDING',
  realName: '李明',
  idNoMasked: '**************1234',
  certificateNoMasked: 'CERT-****-026',
  certificateFiles: [{
    fileId: 'file-001', originalName: '护理证书.pdf', mimeType: 'application/pdf', size: 10240, previewable: true
  }],
  serviceSkillCodes: ['BASIC_CARE'],
  reviewComment: '',
  submittedAt: '2026-07-15T10:00:00+08:00',
  reviewedAt: ''
};

test('reads permissions and the real nurse skill dictionary without fallback data', async () => {
  enqueue(success({ roleCode: 'NURSE', permissions: ['NURSE_QUALIFICATION_SUBMIT'] }));
  const permissionResponse = await api.getQualificationPermissions();
  let request = requests.shift();
  assert.equal(request.url, '/api/v1/auth/permissions');
  assert.equal(request.header.Authorization, 'Bearer stage-26-token');
  assert.equal(permissionResponse.data.permissions[0], 'NURSE_QUALIFICATION_SUBMIT');

  enqueue(success({ dictCode: 'nurseServiceSkill', items: [{ value: 'BASIC_CARE', label: '基础照护', sort: 1, enabled: true }] }));
  const skillResponse = await api.getQualificationSkillOptions();
  request = requests.shift();
  assert.equal(request.url, '/api/v1/dictionaries/nurseServiceSkill');
  assert.deepEqual(skillResponse.data, [{ value: 'BASIC_CARE', label: '基础照护', sort: 1 }]);
});

test('reads the current rich application and rejects a minimal backend DTO', async () => {
  enqueue(success(application));
  const response = await api.getCurrentQualificationApplication();
  let request = requests.shift();
  assert.equal(request.url, '/api/v1/nurse/qualification-applications/current');
  assert.equal(response.data.realName, '李明');

  enqueue(success({ applicationId: 'qualification-001', auditStatus: 'PENDING' }));
  const malformed = await api.getCurrentQualificationApplication();
  request = requests.shift();
  assert.equal(malformed.code, 502);
  assert.match(malformed.message, /响应不完整/);
});

test('submits exact stage 26 payload and preserves real failures', async () => {
  enqueue(success({ applicationId: 'qualification-002', auditStatus: 'PENDING' }));
  const payload = {
    realName: '李明', idNoMasked: '**************1234', certificateNo: 'CERT-2026',
    certificateFileIds: ['file-001'], serviceSkillCodes: ['BASIC_CARE']
  };
  const response = await api.submitQualificationApplication(payload);
  let request = requests.shift();
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/nurse/qualification-applications');
  assert.deepEqual(request.data, payload);
  assert.equal(response.data.auditStatus, 'PENDING');

  enqueue({ code: 409, message: 'state conflict', traceId: 'stage-26-test', data: {} }, 409);
  const conflict = await api.submitQualificationApplication(payload);
  request = requests.shift();
  assert.equal(conflict.code, 409);
});

test('reads a strict rich admin page and keeps permission failures real', async () => {
  enqueue(success({ records: [application], total: 1, page: 1, size: 10 }));
  const page = await api.getQualificationApplications({ auditStatus: 'PENDING', page: 1, size: 10 });
  let request = requests.shift();
  assert.equal(request.url, '/api/v1/admin/nurse-qualification-applications?auditStatus=PENDING&page=1&size=10');
  assert.equal(page.data.records[0].nurseName, '李护士');

  enqueue({ code: 403, message: 'forbidden', traceId: 'stage-26-test', data: {} }, 403);
  const forbidden = await api.getQualificationApplications({ auditStatus: 'ALL', page: 1, size: 10 });
  request = requests.shift();
  assert.equal(forbidden.code, 403);
  assert.deepEqual(forbidden.data.records, []);
});

test('enforces masked identity, dictionary skills, files and duplicate-state rules', () => {
  const draft = {
    clientKey: 'draft-1', path: '/tmp/certificate.pdf', name: 'certificate.pdf', size: 1024,
    mimeType: 'application/pdf', uploadedFileId: '', progress: 0, uploadState: 'READY', uploadError: ''
  };
  const valid = {
    realName: '李明', idNoMasked: '**************1234', certificateNo: 'CERT-2026',
    serviceSkillCodes: ['BASIC_CARE'], availableSkillCodes: ['BASIC_CARE'], files: [draft]
  };
  assert.equal(rules.validateQualificationForm(valid), '');
  assert.match(rules.validateQualificationForm({ ...valid, idNoMasked: '430101200001011234' }), /脱敏证件号/);
  assert.match(rules.validateQualificationForm({ ...valid, serviceSkillCodes: ['FREE_TEXT'] }), /技能已发生变化/);
  assert.match(rules.validateQualificationForm({ ...valid, files: [] }), /至少上传/);
  assert.equal(rules.canSubmitQualification(null), true);
  assert.equal(rules.canSubmitQualification('NEED_MORE'), true);
  assert.equal(rules.canSubmitQualification('PENDING'), false);
  assert.equal(rules.canSubmitQualification('APPROVED'), false);
  assert.equal(rules.qualificationNurseStatusLabel('REJECTED'), '未通过');
  assert.equal(rules.qualificationStatusLabel('REJECTED'), '已驳回');
  assert.equal(rules.canSubmitQualificationByPermission({ roleCode: 'NURSE', permissions: ['NURSE_QUALIFICATION_SUBMIT'] }), true);
  assert.equal(rules.canReviewQualificationByPermission({ roleCode: 'CUSTOMER_SERVICE', permissions: ['NURSE_QUALIFICATION_REVIEW'] }), true);
});

test('persists uploaded files across component disposal and clears them after server registration', () => {
  const values = new Map();
  const storage = {
    getStorageSync(key) { return values.get(key); },
    setStorageSync(key, value) { values.set(key, value); },
    removeStorageSync(key) { values.delete(key); }
  };
  const uploadedDraft = {
    clientKey: 'draft-uploaded', path: '', name: '护理证书.pdf', size: 4096,
    mimeType: 'application/pdf', uploadedFileId: 'file-uploaded-026', progress: 100,
    uploadState: 'UPLOADED', uploadError: ''
  };

  retry.writeQualificationRetryFiles(storage, 'nurse-001', [uploadedDraft]);
  const restoredAfterDisposal = retry.readQualificationRetryFiles(storage, 'nurse-001');
  assert.deepEqual(restoredAfterDisposal, [{
    fileId: 'file-uploaded-026', name: '护理证书.pdf', size: 4096, mimeType: 'application/pdf'
  }]);

  assert.equal(retry.reconcileQualificationRetryFiles(storage, 'nurse-001', 'PENDING'), true);
  assert.deepEqual(retry.readQualificationRetryFiles(storage, 'nurse-001'), []);

  retry.writeQualificationRetryFiles(storage, 'nurse-001', [uploadedDraft]);
  assert.equal(retry.reconcileQualificationRetryFiles(storage, 'nurse-001', 'APPROVED'), true);
  assert.deepEqual(retry.readQualificationRetryFiles(storage, 'nurse-001'), []);
  assert.equal(retry.reconcileQualificationRetryFiles(storage, 'nurse-001', 'REJECTED'), false);
  assert.equal(retry.reconcileQualificationRetryFiles(storage, 'nurse-001', 'NEED_MORE'), false);
});

test('keeps training and skill dictionary failures distinguishable from empty data', () => {
  assert.equal(rules.qualificationTrainingErrorMessage(404), '');
  assert.match(rules.qualificationTrainingErrorMessage(401), /登录状态/);
  assert.match(rules.qualificationTrainingErrorMessage(403), /没有查看/);
  assert.match(rules.qualificationTrainingErrorMessage(500), /暂时无法读取/);
  assert.match(rules.qualificationTrainingErrorMessage(502), /内容不完整/);
  assert.equal(rules.qualificationSkillDictionaryErrorMessage(0), '');
  assert.match(rules.qualificationSkillDictionaryErrorMessage(500), /申请列表仍可查看/);
});

test('preserves protected certificate preview HTTP failures', async () => {
  const previousWindow = globalThis.window;
  const previousFetch = globalThis.fetch;
  globalThis.window = { location: { origin: 'http://localhost:5173' } };
  try {
    for (const status of [401, 403, 404, 500]) {
      globalThis.fetch = async () => ({ ok: false, status });
      const response = await api.getQualificationCertificatePreview('qualification-001', 'file-001');
      assert.equal(response.code, status);
      assert.equal(response.blob, null);
    }
  } finally {
    globalThis.window = previousWindow;
    globalThis.fetch = previousFetch;
  }
});

test('wires nurse submission and the upgraded admin review entry without exposing technical IDs', async () => {
  const [apiSource, nurseComponent, adminComponent, nurseApp, adminApp] = await Promise.all([
    fs.readFile(path.join(frontendRoot, 'src/api/stageTwentySix.ts'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentySixQualificationPanel.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/components/StageTwentySevenQualificationReviewWorkbench.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/nurse/NurseApp.vue'), 'utf8'),
    fs.readFile(path.join(frontendRoot, 'src/apps/admin/AdminApp.vue'), 'utf8')
  ]);
  assert.doesNotMatch(apiSource, /mock|fallback/i);
  assert.match(nurseComponent, /if \(file\.uploadedFileId\) continue/);
  assert.match(nurseComponent, /重新登记申请/);
  assert.match(nurseComponent, /登记完成前不能更换或移除/);
  assert.match(nurseComponent, /persistUploadedDrafts/);
  assert.match(nurseComponent, /restoreUploadedDrafts/);
  assert.match(nurseComponent, /reconcileQualificationRetryFiles/);
  assert.match(nurseComponent, /validateMedicalFileSignature/);
  assert.match(nurseComponent, /canSubmitQualificationByPermission/);
  assert.match(adminComponent, /canReviewQualificationByPermission/);
  assert.match(adminComponent, /getQualificationCertificatePreview/);
  assert.match(adminComponent, /查看证明/);
  assert.match(apiSource, /Authorization: `Bearer \$\{session\.token\}`/);
  assert.match(apiSource, /nurse-qualification-applications\/\$\{encodeURIComponent\(applicationId\)\}\/files/);
  assert.match(adminComponent, /canReviewQualificationByPermission/);
  assert.match(adminComponent, /reviewQualificationApplication/);
  assert.match(nurseApp, /StageTwentySixQualificationPanel/);
  assert.match(nurseApp, />准入资格</);
  assert.match(adminApp, /StageTwentySevenQualificationReviewWorkbench/);
  assert.match(adminApp, /护理资质/);

  const nurseTemplate = nurseComponent.split('<template>')[1]?.split('<style')[0] ?? '';
  const adminTemplate = adminComponent.split('<template>')[1]?.split('<style')[0] ?? '';
  assert.doesNotMatch(nurseTemplate, /\{\{\s*(?:current\.)?(?:applicationId|nurseId|fileId)/);
  assert.doesNotMatch(adminTemplate, /\{\{\s*(?:record\.)?(?:applicationId|nurseId|fileId)/);
  assert.doesNotMatch(nurseTemplate, />[^<]*(?:API|DTO)[^<]*</);
  assert.doesNotMatch(adminTemplate, />[^<]*(?:API|DTO)[^<]*</);
});
