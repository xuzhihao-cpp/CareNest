import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import path from 'node:path';
import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const frontendRoot = fileURLToPath(new URL('..', import.meta.url));
const requests = [];
const responses = [];
const uploads = [];
const uploadResponses = [];
const originalFetch = globalThis.fetch;

globalThis.uni = {
  getStorageSync() {
    return {
      token: 'stage-22-test-token',
      user: { userId: 'elder-001', displayName: '张淑兰', roles: ['ELDER'], menus: [] }
    };
  },
  request(options) {
    requests.push(options);
    const response = responses.shift();
    if (!response) throw new Error(`Missing response for ${options.method} ${options.url}`);
    options.success({ statusCode: response.statusCode ?? 200, data: response.body });
  },
  uploadFile(options) {
    uploads.push(options);
    const response = uploadResponses.shift();
    if (!response) throw new Error(`Missing upload response for ${options.url}`);
    queueMicrotask(() => options.success({ statusCode: response.statusCode ?? 200, data: response.body }));
    return {
      onProgressUpdate(callback) {
        callback({ progress: 64 });
      }
    };
  }
};

const vite = await createServer({
  root: frontendRoot,
  configFile: false,
  server: { middlewareMode: true },
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } }
});
const rules = await vite.ssrLoadModule('/src/utils/stageTwentyTwoRules.ts');
const api = await vite.ssrLoadModule('/src/api/stageTwentyTwo.ts');

after(async () => {
  await vite.close();
  delete globalThis.uni;
  globalThis.fetch = originalFetch;
});

function apiSuccess(data) {
  return { code: 0, message: 'success', traceId: 'stage-22-test', data };
}

function enqueue(body, statusCode = 200) {
  responses.push({ body, statusCode });
}

function takeRequest() {
  const request = requests.shift();
  assert.ok(request, 'expected an API request');
  return request;
}

test('creates elder feedback from login identity without accepting an elder id', async () => {
  const payload = {
    feedbackType: 'DIZZINESS', severity: 'MEDIUM', content: '起床后有些头晕', inputType: 'TEXT', fileId: null
  };
  enqueue(apiSuccess({ feedbackId: 'feedback-001', createdAt: '2026-07-13T09:30:00+08:00', aiAdvice: '请先休息并留意变化。' }));
  const response = await api.createElderHealthFeedback(payload);
  const request = takeRequest();

  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/api/v1/elder/health-feedback');
  assert.deepEqual(request.data, payload);
  assert.equal(Object.hasOwn(request.data, 'elderId'), false);
  assert.equal(response.data.feedbackId, 'feedback-001');
  assert.equal(response.data.aiAdvice, '请先休息并留意变化。');
});

test('reads an encoded family resource and removes inactive filters', async () => {
  enqueue(apiSuccess({ records: [], total: 0, page: 1, size: 20 }));
  await api.getFamilyHealthFeedback('elder/001', {
    page: 1, size: 20, feedbackType: '', severity: '', dateFrom: '', dateTo: ''
  });
  const request = takeRequest();
  assert.equal(request.method, 'GET');
  assert.equal(request.url, '/api/v1/family/elders/elder%2F001/health-feedback');
  assert.deepEqual(request.data, { page: 1, size: 20 });
});

test('passes every active family timeline filter and normalizes voice links', async () => {
  enqueue(apiSuccess({
    records: [{
      feedbackId: 'feedback-voice', elderId: 'elder-001', elderName: '张淑兰', feedbackType: 'PAIN',
      severity: 'HIGH', content: '膝盖疼痛明显', inputType: 'VOICE', audioUrl: 'https://files.example/voice',
      createdAt: '2026-07-13T10:00:00+08:00'
    }],
    total: 1, page: 2, size: 10
  }));
  const query = {
    page: 2, size: 10, feedbackType: 'PAIN', severity: 'HIGH', dateFrom: '2026-07-01', dateTo: '2026-07-13'
  };
  const response = await api.getFamilyHealthFeedback('elder-001', query);
  const request = takeRequest();
  assert.deepEqual(request.data, query);
  assert.equal(response.data.records[0].voiceUrl, 'https://files.example/voice');
  assert.equal(response.data.records[0].severity, 'HIGH');
});

test('only grants family reading to active HEALTH_VIEW bindings', () => {
  assert.equal(rules.canViewFamilyHealthFeedback({ bindingStatus: 'ACTIVE', scopeCodes: ['HEALTH_VIEW'] }), true);
  assert.equal(rules.canViewFamilyHealthFeedback({ bindingStatus: 'PENDING', scopeCodes: ['HEALTH_VIEW'] }), false);
  assert.equal(rules.canViewFamilyHealthFeedback({ bindingStatus: 'ACTIVE', scopeCodes: ['ORDER_CREATE'] }), false);
  assert.equal(rules.sameElderResource('elder-001', 'elder_001'), true);
});

test('rejects a stale response after the selected elder changes', () => {
  assert.equal(rules.isCurrentFeedbackRequest(4, 4, 'elder-001', 'elder_001'), true);
  assert.equal(rules.isCurrentFeedbackRequest(3, 4, 'elder-001', 'elder-001'), false);
  assert.equal(rules.isCurrentFeedbackRequest(4, 4, 'elder-001', 'elder-002'), false);
});

test('derives button, text and voice input without inventing diagnosis fields', () => {
  assert.equal(rules.resolveHealthFeedbackInputType('', false), 'BUTTON');
  assert.equal(rules.resolveHealthFeedbackInputType('睡眠不好', false), 'TEXT');
  assert.equal(rules.resolveHealthFeedbackInputType('补充语音', true), 'VOICE');
  const payload = { feedbackType: 'SLEEP', severity: 'HIGH', content: '', inputType: 'BUTTON', fileId: null };
  assert.equal(rules.validateHealthFeedback(payload), '');
  assert.equal(Object.hasOwn(payload, 'diagnosis'), false);
  assert.equal(Object.hasOwn(payload, 'medicationChange'), false);
});

test('validates feedback content and voice ownership token requirements', () => {
  assert.match(rules.validateHealthFeedback({
    feedbackType: 'PAIN', severity: 'LOW', content: '字'.repeat(513), inputType: 'TEXT', fileId: null
  }), /512/);
  assert.match(rules.validateHealthFeedback({
    feedbackType: 'PAIN', severity: 'LOW', content: '', inputType: 'VOICE', fileId: null
  }), /尚未完成上传/);
  assert.match(rules.validateHealthFeedback({
    feedbackType: 'PAIN', severity: 'LOW', content: '', inputType: 'BUTTON', fileId: 'file-001'
  }), /不应携带/);
});

test('validates voice extension, MIME, size and duration together', () => {
  assert.equal(rules.validateVoiceFileDescriptor(
    { name: 'feedback.mp3', size: 1024, mimeType: 'audio/mpeg', durationSeconds: 59 }, 2048, 0.002
  ), '');
  assert.equal(rules.validateVoiceFileDescriptor(
    { name: 'feedback.webm', size: 1024, mimeType: 'audio/webm;codecs=opus', durationSeconds: 12 }, 2048, 0.002
  ), '');
  assert.equal(rules.normalizeVoiceMimeType(' Audio/WebM; codecs=opus '), 'audio/webm');
  assert.match(rules.validateVoiceFileDescriptor(
    { name: 'feedback.pdf', size: 1024, mimeType: 'application/pdf' }, 2048, 0.002
  ), /录音格式/);
  assert.match(rules.validateVoiceFileDescriptor(
    { name: 'feedback.mp3', size: 1024, mimeType: 'application/pdf' }, 2048, 0.002
  ), /真实格式/);
  assert.match(rules.validateVoiceFileDescriptor(
    { name: 'feedback.mp3', size: 4096, mimeType: 'audio/mpeg' }, 2048, 0.002
  ), /不能超过/);
  assert.match(rules.validateVoiceFileDescriptor(
    { name: 'feedback.mp3', size: 1024, mimeType: 'audio/mpeg', durationSeconds: 61 }, 2048, 0.002
  ), /60 秒/);
});

test('uploads voice with authorization and rejects a successful response missing fileId', async () => {
  uploadResponses.push({ body: JSON.stringify(apiSuccess({ fileId: 'voice-file-001' })) });
  const progress = [];
  const uploaded = await api.uploadHealthFeedbackVoice({
    path: '/tmp/feedback.mp3', name: 'feedback.mp3', size: 1024, mimeType: 'audio/mpeg'
  }, (value) => progress.push(value));
  const firstUpload = uploads.shift();
  assert.equal(firstUpload.url, '/api/v1/files');
  assert.equal(firstUpload.header.Authorization, 'Bearer stage-22-test-token');
  assert.equal(uploaded.data.fileId, 'voice-file-001');
  assert.deepEqual(progress, [64]);

  uploadResponses.push({ body: JSON.stringify(apiSuccess({})) });
  const malformed = await api.uploadHealthFeedbackVoice({
    path: '/tmp/feedback.mp3', name: 'feedback.mp3', size: 1024, mimeType: 'audio/mpeg'
  }, () => {});
  uploads.shift();
  assert.equal(malformed.code, 502);
});

test('uploads a browser recording blob as authenticated multipart data', async () => {
  let capturedUrl = '';
  let capturedAuthorization = '';
  let capturedFile = null;
  globalThis.fetch = async (url, options) => {
    capturedUrl = String(url);
    capturedAuthorization = options.headers.Authorization;
    capturedFile = options.body.get('file');
    return new Response(JSON.stringify(apiSuccess({ fileId: 'voice-browser-001' })), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    });
  };

  const progress = [];
  const blob = new Blob(['recorded-voice'], { type: 'audio/webm' });
  const uploaded = await api.uploadHealthFeedbackVoice({
    path: '',
    name: 'health-feedback.webm',
    size: blob.size,
    mimeType: blob.type,
    durationSeconds: 3,
    blob
  }, (value) => progress.push(value));

  assert.equal(capturedUrl, '/api/v1/files');
  assert.equal(capturedAuthorization, 'Bearer stage-22-test-token');
  assert.ok(capturedFile instanceof Blob);
  assert.equal(capturedFile.name, 'health-feedback.webm');
  assert.equal(capturedFile.type, 'audio/webm');
  assert.equal(uploaded.data.fileId, 'voice-browser-001');
  assert.deepEqual(progress, [20, 100]);
  globalThis.fetch = originalFetch;
});

test('downloads protected voice with bearer token and returns a local blob URL', async () => {
  let capturedUrl = '';
  let capturedAuthorization = '';
  globalThis.fetch = async (url, options) => {
    capturedUrl = String(url);
    capturedAuthorization = options.headers.Authorization;
    return new Response(new Blob(['voice-content'], { type: 'audio/mpeg' }), { status: 200 });
  };
  const response = await api.getAuthorizedHealthFeedbackVoice('/files/voice-001/content');
  assert.equal(capturedUrl, 'http://localhost/api/v1/files/voice-001/content');
  assert.equal(capturedAuthorization, 'Bearer stage-22-test-token');
  assert.equal(response.code, 0);
  assert.match(response.data.playbackUrl, /^blob:/);
  assert.equal(response.data.revokeOnRelease, true);
  URL.revokeObjectURL(response.data.playbackUrl);
  globalThis.fetch = originalFetch;
});

test('separates same-origin protected media from trusted signed media without leaking tokens', async () => {
  assert.deepEqual(
    rules.classifyHealthFeedbackVoiceUrl('/api/v1/files/voice', 'https://care.example', []),
    { mode: 'PROTECTED_SAME_ORIGIN', url: 'https://care.example/api/v1/files/voice' }
  );
  assert.deepEqual(
    rules.classifyHealthFeedbackVoiceUrl(
      'https://media.example/voice.mp3?signature=abc',
      'https://care.example',
      ['https://media.example']
    ),
    { mode: 'SIGNED_TRUSTED_ORIGIN', url: 'https://media.example/voice.mp3?signature=abc' }
  );
  assert.equal(
    rules.classifyHealthFeedbackVoiceUrl('https://unknown.example/voice', 'https://care.example', []).mode,
    'REJECTED'
  );
  assert.equal(
    rules.classifyHealthFeedbackVoiceUrl('http://media.example/voice', 'https://care.example', ['http://media.example']).mode,
    'REJECTED'
  );

  let externalFetchCalled = false;
  globalThis.fetch = async () => {
    externalFetchCalled = true;
    throw new Error('external fetch must not run');
  };
  const rejected = await api.getAuthorizedHealthFeedbackVoice('https://unknown.example/voice');
  assert.equal(rejected.code, 403);
  assert.equal(externalFetchCalled, false);
  globalThis.fetch = originalFetch;
});

test('provides a real emergency view with family, platform and 120 actions', async () => {
  const elderApp = await fs.readFile(path.join(frontendRoot, 'src/apps/elder/ElderApp.vue'), 'utf8');
  const assistancePanel = await fs.readFile(path.join(frontendRoot, 'src/components/EmergencyAssistancePanel.vue'), 'utf8');
  assert.match(elderApp, /requested === 'emergency'/);
  assert.match(elderApp, /homeView\.value = 'assistance'/);
  assert.match(assistancePanel, /makePhoneCall/);
  assert.match(assistancePanel, /VITE_PLATFORM_ASSISTANCE_PHONE/);
  assert.match(assistancePanel, /拨打 120/);
});

test('keeps real permission and malformed response failures', async () => {
  enqueue({ code: 403, message: 'forbidden', traceId: 'stage-22-test', data: {} }, 403);
  const denied = await api.getFamilyHealthFeedback('elder-001', {
    page: 1, size: 20, feedbackType: '', severity: '', dateFrom: '', dateTo: ''
  });
  takeRequest();
  assert.equal(denied.code, 403);
  assert.deepEqual(denied.data.records, []);

  enqueue(apiSuccess({ createdAt: '2026-07-13T09:30:00+08:00', aiAdvice: '请休息。' }));
  const malformed = await api.createElderHealthFeedback({
    feedbackType: 'DIET', severity: 'LOW', content: '', inputType: 'BUTTON', fileId: null
  });
  takeRequest();
  assert.equal(malformed.code, 502);
});

test('elder feedback uses direct browser recording and displays returned AI advice', async () => {
  const component = await fs.readFile(path.join(frontendRoot, 'src/components/StageTwentyTwoHealthFeedbackPanel.vue'), 'utf8');
  assert.match(component, /navigator\.mediaDevices\?\.getUserMedia/);
  assert.match(component, /new MediaRecorder/);
  assert.match(component, /AI 照护建议/);
  assert.doesNotMatch(component, /选择已有语音/);
  assert.doesNotMatch(component, /chooseVoice/);
});
