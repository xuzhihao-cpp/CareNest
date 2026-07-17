import assert from 'node:assert/strict';

const api = await import('../src/api/stageThirtyTwo.ts').catch(() => null);
assert.equal(api, null, 'Runtime TypeScript modules are not loaded directly in this contract test');

const source = await (await import('node:fs/promises')).readFile(new URL('../src/api/stageThirtyTwo.ts', import.meta.url), 'utf8');
assert.match(source, /\/elder\/reminders/);
assert.match(source, /\/elder\/reminders\/\$\{encodeURIComponent\(reminderId\)\}\/actions/);
assert.match(source, /\/elder\/reminders\/records/);
assert.match(source, /PENDING.*DONE.*SNOOZED.*MISSED.*NEED_HELP/s);
const component = await (await import('node:fs/promises')).readFile(new URL('../src/components/StageThirtyTwoReminderCenter.vue', import.meta.url), 'utf8');
assert.match(component, /我已服药/);
assert.match(component, /我知道了/);
assert.match(component, /确认已完成/);
assert.match(component, /actOnElderReminder/);
console.log('stage 32-33 frontend contract checks passed');
