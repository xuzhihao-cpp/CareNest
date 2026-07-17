import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const root = fileURLToPath(new URL('..', import.meta.url));
const requests = [];
const responses = [];
globalThis.uni = {
  getStorageSync: () => ({ token: 'real-role-token' }),
  request(options) {
    requests.push(options);
    options.success({ statusCode: 200, data: responses.shift() });
    return { abort() {} };
  }
};
const vite = await createServer({
  root, configFile: false, server: { middlewareMode: true, hmr: false },
  optimizeDeps: { noDiscovery: true, include: [] }, resolve: { alias: { '@': path.join(root, 'src') } }
});
const api = await vite.ssrLoadModule('/src/api/stageFortyFourToFortyEight.ts');
after(async () => { await vite.close(); delete globalThis.uni; });
const ok = (data) => ({ code: 0, message: 'success', traceId: 'stage44-48', data });

test('stage 44 stores structured follow-up fields without internal input', async () => {
  const payload = { method: 'PHONE', content: '已电话沟通', nextFollowUpAt: null, result: '继续跟进' };
  responses.push(ok({ followUpId: 'follow-1', ticketStatus: 'PROCESSING', ...payload, createdAt: '2026-07-17T10:00:00' }));
  await api.addTicketFollowUp('ticket/1', payload);
  const request = requests.shift();
  assert.equal(request.url, '/api/v1/admin/customer-service/tickets/ticket%2F1/follow-up');
  assert.deepEqual(request.data, payload);
});

test('stage 45 review and complaint use selected order route', async () => {
  responses.push(ok({ reviewId: 'review-1' }));
  await api.submitFamilyReview('order-1', { rating: 5, tags: ['服务专业'], content: '满意', reasonType: null, fileIds: [] });
  assert.equal(requests.shift().url, '/api/v1/family/orders/order-1/reviews');
  responses.push(ok({ complaintId: 'complaint-1' }));
  await api.submitFamilyComplaint('order-1', { rating: null, tags: [], content: '需要处理', reasonType: 'SERVICE_QUALITY', fileIds: [] });
  assert.equal(requests.shift().url, '/api/v1/family/orders/order-1/complaints');
});

test('stage 46 appeal review preserves hidden target ownership data', async () => {
  responses.push(ok({ appealId: 'appeal-1', status: 'APPROVED' }));
  await api.reviewNurseAppeal('appeal-1', 'metric-1', 'APPROVED', '证据核对通过');
  const request = requests.shift();
  assert.equal(request.url, '/api/v1/admin/nurse-appeals/appeal-1/review');
  assert.equal(request.data.targetType, 'APPROVED');
  assert.equal(request.data.targetId, 'metric-1');
});

test('stage 47 and 48 read score facts and recalculation logs', async () => {
  responses.push(ok({ totalScore: 91, level: 'EXCELLENT', monthDelta: 2, items: [] }));
  const mine = await api.getMyScore();
  assert.equal(mine.data.totalScore, 91);
  assert.equal(requests.shift().url, '/api/v1/nurse/my-score?page=1&size=50');
  responses.push(ok({ nurseId: 'nurse-1', totalScore: 91, level: 'EXCELLENT', changeLogs: [] }));
  await api.recalculateNurseScore('nurse-1', 'appeal-1');
  assert.equal(requests.shift().url, '/api/v1/admin/nurses/nurse-1/score/recalculate');
});

test('AI audit consumes real read model and does not expose message IDs', async () => {
  responses.push(ok({ records: [], total: 0, page: 1, size: 50 }));
  await api.listAiAuditSessions(true);
  assert.equal(requests.shift().url, '/api/v1/admin/ai/sessions?page=1&size=50&riskFlag=true');
  const component = readFileSync(path.join(root, 'src/components/StageFortyThreeCustomerServicePanel.vue'), 'utf8');
  assert.ok(component.includes('AI 风险审阅'));
  assert.ok(!component.includes('{{ item.sessionId }}'));
});

test('all three role surfaces mount stage 44-48 product pages', () => {
  const family = readFileSync(path.join(root, 'src/apps/family/FamilyApp.vue'), 'utf8');
  const nurse = readFileSync(path.join(root, 'src/apps/nurse/NurseApp.vue'), 'utf8');
  const admin = readFileSync(path.join(root, 'src/apps/admin/AdminApp.vue'), 'utf8');
  assert.ok(family.includes('StageFortyFiveFamilyFeedbackPanel'));
  assert.ok(nurse.includes('StageFortySixToFortyEightNurseScorePanel'));
  assert.ok(admin.includes('StageFortyFiveToFortyEightAdminPanel'));
});

test('product forms select business records instead of asking for IDs', () => {
  const files = [
    'StageFortyFiveFamilyFeedbackPanel.vue',
    'StageFortySixToFortyEightNurseScorePanel.vue',
    'StageFortyFiveToFortyEightAdminPanel.vue'
  ].map((name) => readFileSync(path.join(root, 'src/components', name), 'utf8')).join('\n');
  assert.ok(!/placeholder=["'][^"']*(订单|投诉|申诉|护理).*(ID|编号)/i.test(files));
  assert.ok(files.includes('无需填写订单编号'));
});
