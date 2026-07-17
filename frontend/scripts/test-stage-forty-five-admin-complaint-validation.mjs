import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const component = readFileSync(
  path.join(root, 'src/components/StageFortyFiveToFortyEightAdminPanel.vue'),
  'utf8'
);
const nurseComponent = readFileSync(
  path.join(root, 'src/components/StageFortySixToFortyEightNurseScorePanel.vue'),
  'utf8'
);
const complaintDecision = component.slice(
  component.indexOf('async function decideComplaint'),
  component.indexOf('async function decideAppeal')
);
const appealDecision = component.slice(
  component.indexOf('async function decideAppeal'),
  component.indexOf('async function recalculate')
);

test('complaint decisions accept any non-empty handling note', () => {
  assert.ok(complaintDecision.includes("comment.value.trim().length === 0"));
  assert.ok(!complaintDecision.includes('comment.value.trim().length < 2'));
});

test('complaint validation is displayed next to the decision controls', () => {
  assert.ok(component.includes('class="complaint-field-error"'));
});

test('admin appeal decisions accept any non-empty review note', () => {
  assert.ok(appealDecision.includes("comment.value.trim().length === 0"));
  assert.ok(!appealDecision.includes('comment.value.trim().length < 2'));
});

test('admin appeal validation is displayed next to the decision controls', () => {
  assert.ok(component.includes('class="admin-appeal-field-error"'));
});

test('nurse appeals accept any non-empty reason', () => {
  assert.ok(nurseComponent.includes("reason.value.trim().length === 0"));
  assert.ok(!nurseComponent.includes('reason.value.trim().length < 5'));
});

test('appeal validation is displayed next to the submit controls', () => {
  assert.ok(nurseComponent.includes('class="appeal-field-error"'));
});
