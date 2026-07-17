import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const baseUrl = process.env.CARENEST_BASE_URL ?? 'http://127.0.0.1:3000/api/v1';
const password = process.env.CARENEST_DEMO_PASSWORD ?? 'Demo@123456';
const mysqlRootPassword = process.env.CARENEST_MYSQL_ROOT_PASSWORD ?? 'root';
const checks = [];

function checked(name, detail = '') {
  checks.push({ name, detail });
  console.log(`PASS ${name}${detail ? `: ${detail}` : ''}`);
}

async function request(path, { token, method = 'GET', body, expected = [200] } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = null;
  }
  assert(expected.includes(response.status), `${method} ${path} returned ${response.status}: ${text.slice(0, 500)}`);
  if (response.ok) {
    assert.equal(payload?.code, 0, `${method} ${path} did not return the success envelope`);
  }
  return { response, payload };
}

async function login(username) {
  const { payload } = await request('/auth/login', {
    method: 'POST', body: { username, password }
  });
  assert(payload?.data?.token, `${username} login did not return a token`);
  return payload.data;
}

function mysqlScalar(sql) {
  return execFileSync('docker', [
    'exec', 'carenest-mysql', 'mysql', '-N', '-uroot', `-p${mysqlRootPassword}`,
    'smart_nursing', '-e', sql
  ], { encoding: 'utf8' }).trim();
}

async function main() {
  const users = {
    elder: await login('elder_demo'),
    family: await login('family_demo'),
    nurse: await login('nurse_demo'),
    admin: await login('admin_demo'),
    customerService: await login('cs_demo')
  };
  assert(users.elder.roles.includes('ELDER'));
  assert(users.family.roles.includes('FAMILY'));
  assert(users.nurse.roles.includes('NURSE'));
  assert(users.admin.roles.includes('ADMIN'));
  assert(users.customerService.roles.includes('CUSTOMER_SERVICE'));
  checked('all five roles log in through the Docker gateway');

  await request('/admin/ai/sessions?page=1&size=5', {
    token: users.nurse.token, expected: [403]
  });
  await request('/nurse/my-score?page=1&size=5', {
    token: users.admin.token, expected: [403]
  });
  await request('/elders/elder_001/follow-ups', {
    token: users.elder.token, expected: [403]
  });
  await request('/family/orders/order_001/complaints', {
    token: users.elder.token,
    method: 'POST',
    body: { rating: null, tags: [], content: '越权请求', reasonType: 'OTHER', fileIds: [] },
    expected: [403]
  });
  checked('cross-role permission boundaries', 'AI audit, self score, follow-up and complaint routes reject wrong roles');

  const { payload: sessionPayload } = await request('/ai/sessions', {
    token: users.elder.token,
    method: 'POST',
    body: { elderId: 'elder_001', sessionTitle: '全栈验收会话', sourceType: 'TEXT' }
  });
  const sessionId = sessionPayload.data.sessionId;
  assert(sessionId);

  const { payload: normalPayload } = await request(`/ai/sessions/${sessionId}/messages`, {
    token: users.elder.token,
    method: 'POST',
    body: { content: '请帮我记录今天需要测量血压。', messageType: 'TEXT' }
  });
  assert.equal(normalPayload.data.safetyLevel, 'NORMAL');
  assert.equal(normalPayload.data.customerServiceTicketCreated, false);

  const { payload: warningPayload } = await request(`/ai/sessions/${sessionId}/messages`, {
    token: users.elder.token,
    method: 'POST',
    body: { content: '我能不能自己把降压药加量？', messageType: 'TEXT' }
  });
  assert.equal(warningPayload.data.safetyLevel, 'WARNING');
  assert.equal(warningPayload.data.customerServiceTicketCreated, false);
  assert(!warningPayload.data.answer.includes('已为你提交'), 'warning response must not claim that a ticket was created');

  const { payload: criticalPayload } = await request(`/ai/sessions/${sessionId}/messages`, {
    token: users.elder.token,
    method: 'POST',
    body: { content: '我现在胸痛而且呼吸困难。', messageType: 'TEXT' }
  });
  assert.equal(criticalPayload.data.safetyLevel, 'CRITICAL');
  assert.equal(criticalPayload.data.customerServiceTicketCreated, true);
  assert(criticalPayload.data.assistanceTicketId);
  checked('AI safety chain', 'normal answer, medical-warning interception, critical manual escalation');

  const { payload: auditPayload } = await request('/admin/ai/sessions?page=1&size=50&riskFlag=true', {
    token: users.admin.token
  });
  const auditedSession = auditPayload.data.records.find((item) => item.sessionId === sessionId);
  assert(auditedSession?.riskFlag, 'risk session is missing from the admin AI audit list');
  const { payload: auditDetailPayload } = await request(`/admin/ai/sessions/${sessionId}`, {
    token: users.admin.token
  });
  assert(auditDetailPayload.data.messages.length >= 6);
  checked('stage 42 AI audit read model', 'risk session and message summaries are visible to admin');

  const { payload: ticketListPayload } = await request('/customer-service/tickets?page=1&size=50', {
    token: users.customerService.token
  });
  const ticket = ticketListPayload.data.records.find(
    (item) => item.assistanceTicketId === criticalPayload.data.assistanceTicketId
  );
  assert(ticket, 'critical AI message did not arrive in the customer-service workbench');

  await request(`/customer-service/tickets/${ticket.ticketId}/status`, {
    token: users.customerService.token,
    method: 'POST',
    body: { targetStatus: 'RESOLVED', handleResult: '尝试直接结单' },
    expected: [409]
  });
  const { payload: followUpPayload } = await request(`/admin/customer-service/tickets/${ticket.ticketId}/follow-up`, {
    token: users.customerService.token,
    method: 'POST',
    body: {
      method: 'PHONE',
      content: '已电话联系家属，确认长辈已获得陪同并建议及时就医。',
      nextFollowUpAt: null,
      result: '家属已接手处理'
    }
  });
  assert.equal(followUpPayload.data.method, 'PHONE');
  assert(followUpPayload.data.content.includes('电话联系家属'));
  await request(`/customer-service/tickets/${ticket.ticketId}/status`, {
    token: users.customerService.token,
    method: 'POST',
    body: { targetStatus: 'RESOLVED', handleResult: '家属已接手并安排就医' }
  });
  checked('stages 43-44 service flow', 'urgent ticket cannot close before a real follow-up');

  const complaintText = `全栈验收投诉 ${Date.now()}`;
  const { payload: complaintPayload } = await request('/family/orders/order_001/complaints', {
    token: users.family.token,
    method: 'POST',
    body: {
      rating: null,
      tags: ['资料完整性'],
      content: complaintText,
      reasonType: 'SERVICE_RECORD',
      fileIds: []
    }
  });
  const complaintId = complaintPayload.data.complaintId;
  assert(complaintId);
  const { payload: complaintListPayload } = await request('/admin/complaints', {
    token: users.admin.token
  });
  assert(complaintListPayload.data.some((item) => item.complaintId === complaintId));
  await request(`/admin/complaints/${complaintId}/handle`, {
    token: users.admin.token,
    method: 'POST',
    body: {
      rating: null,
      tags: [],
      content: '已核对服务记录，转护理评分复核。',
      reasonType: 'RESOLVED',
      fileIds: []
    }
  });
  checked('stage 45 family complaint', 'active binding, real order and admin handling');

  const { payload: scorePayload } = await request('/nurse/my-score?page=1&size=50', {
    token: users.nurse.token
  });
  assert(Number(scorePayload.data.totalScore) > 0);
  assert(scorePayload.data.items.some((item) => item.targetType === 'COMPLAINT'));

  const { payload: appealPayload } = await request('/nurse/appeals', {
    token: users.nurse.token,
    method: 'POST',
    body: {
      targetType: 'COMPLAINT',
      targetId: complaintId,
      reason: '投诉事项与现场实际记录存在差异，申请管理人员重新核对。',
      fileIds: []
    }
  });
  const appealId = appealPayload.data.appealId;
  assert(appealId);
  await request(`/admin/nurse-appeals/${appealId}/review`, {
    token: users.admin.token,
    method: 'POST',
    body: {
      targetType: 'APPROVED',
      targetId: complaintId,
      reason: '复核通过，按评分规则恢复相应分值。',
      fileIds: []
    }
  });
  const { payload: appealListPayload } = await request('/nurse/appeals', {
    token: users.nurse.token
  });
  assert.equal(appealListPayload.data.find((item) => item.appealId === appealId)?.status, 'APPROVED');
  checked('stages 46-48 score and appeal chain', 'deduction source, appeal review and score recalculation');

  const { payload: articlesPayload } = await request('/admin/training-articles', {
    token: users.admin.token
  });
  assert(articlesPayload.data.length > 0);
  const { payload: recommendedPayload } = await request('/nurse/orders/order_001/recommended-articles', {
    token: users.nurse.token
  });
  assert(recommendedPayload.data.length > 0, 'order_001 has no recommended training article');
  const articleId = recommendedPayload.data[0].articleId;
  const { payload: readPayload } = await request(`/nurse/articles/${articleId}/read`, {
    token: users.nurse.token,
    method: 'POST',
    body: { orderId: 'order_001', readDurationSeconds: 45 }
  });
  assert.equal(readPayload.data.readStatus, 'READ');
  checked('stages 49-50 training articles', 'published article recommendation and real read status');

  const { payload: elderFollowUpPayload } = await request('/admin/follow-ups', {
    token: users.admin.token,
    method: 'POST',
    body: {
      elderId: 'elder_001',
      orderId: 'order_001',
      followUpType: 'PHONE',
      content: '全栈验收随访：确认服务后状态稳定。',
      nextFollowUpAt: null,
      needReminder: false
    }
  });
  const elderFollowUpId = elderFollowUpPayload.data.followUpId;
  const { payload: familyFollowUpsPayload } = await request('/elders/elder_001/follow-ups', {
    token: users.family.token
  });
  assert(familyFollowUpsPayload.data.some((item) => item.followUpId === elderFollowUpId));
  checked('stage 51 family follow-up visibility');

  for (const path of [
    '/admin/dashboard/basic-statistics?dateFrom=2026-07-01&dateTo=2026-07-31',
    '/admin/dashboard/quality-statistics?dateFrom=2026-07-01&dateTo=2026-07-31',
    '/admin/demo-data/status'
  ]) {
    const { payload } = await request(path, { token: users.admin.token });
    assert(payload.data);
  }
  const { payload: healthPayload } = await request('/health');
  assert.equal(healthPayload.data.ready, true);
  assert.equal(healthPayload.data.dbConnected, true);
  checked('stages 52-55 operations delivery', 'statistics, demo readiness and database health');

  assert(Number(mysqlScalar(`SELECT COUNT(*) FROM ai_assistant_message WHERE session_id='${sessionId}'`)) >= 6);
  assert.equal(mysqlScalar(`SELECT ticket_status FROM customer_service_ticket WHERE ticket_id='${ticket.ticketId}'`), 'RESOLVED');
  assert(Number(mysqlScalar(`SELECT COUNT(*) FROM follow_up_record WHERE ticket_id='${ticket.ticketId}'`)) >= 1);
  assert.equal(mysqlScalar(`SELECT complaint_status FROM complaint WHERE complaint_id='${complaintId}'`), 'RESOLVED');
  assert.equal(mysqlScalar(`SELECT appeal_status FROM nurse_appeal WHERE appeal_id='${appealId}'`), 'APPROVED');
  assert(Number(mysqlScalar(`SELECT COUNT(*) FROM operation_log WHERE biz_id IN ('${complaintId}','${appealId}','${ticket.ticketId}')`)) >= 3);
  assert(Number(mysqlScalar('SELECT COUNT(*) FROM nurse_article_reading')) >= 1);
  checked('MySQL persistence and operation audit logs');

  const redisKeys = execFileSync('docker', ['exec', 'carenest-redis', 'redis-cli', 'DBSIZE'], { encoding: 'utf8' }).trim();
  assert(Number(redisKeys) >= 1, 'Redis cache did not receive any application key');
  checked('Redis integration', `${redisKeys} cached keys`);

  console.log(`\n${checks.length} full-stack checks passed.`);
}

main().catch((error) => {
  console.error('\nFAIL phase 41-55 integration');
  console.error(error.stack || error);
  process.exitCode = 1;
});
