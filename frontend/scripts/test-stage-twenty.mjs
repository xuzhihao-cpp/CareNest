import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { transformWithEsbuild } from 'vite';

const source = await readFile(new URL('../src/utils/stageTwentyRules.ts', import.meta.url), 'utf8');
const transformed = await transformWithEsbuild(source, 'stageTwentyRules.ts', {
  loader: 'ts',
  format: 'esm',
  target: 'es2020'
});
const rules = await import(`data:text/javascript;charset=utf-8,${encodeURIComponent(transformed.code)}`);

const maxBytes = 20 * 1024 * 1024;

test('rejects oversized, unsupported and disguised files', () => {
  assert.match(
    rules.validateMedicalFileDescriptor(
      { name: 'report.pdf', size: maxBytes + 1, mimeType: 'application/pdf' },
      maxBytes,
      20
    ),
    /超过 20 MB/
  );
  assert.match(
    rules.validateMedicalFileDescriptor(
      { name: 'report.exe', size: 10, mimeType: 'application/octet-stream' },
      maxBytes,
      20
    ),
    /PDF、JPG 或 PNG/
  );
  assert.match(
    rules.validateMedicalFileDescriptor(
      { name: 'renamed.pdf', size: 10, mimeType: 'image/png' },
      maxBytes,
      20
    ),
    /扩展名与实际类型不一致/
  );
  assert.match(
    rules.validateMedicalFileSignature('pdf', new Uint8Array([0x74, 0x65, 0x78, 0x74])),
    /文件内容与所选格式不一致/
  );
  assert.equal(
    rules.validateMedicalFileSignature('pdf', new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d])),
    ''
  );
});

test('keeps an uploaded file locked and retries registration only', () => {
  assert.equal(rules.nextMedicalFileSubmitStep(''), 'UPLOAD_AND_REGISTER');
  assert.equal(rules.nextMedicalFileSubmitStep('file-001'), 'REGISTER_ONLY');
  assert.equal(rules.canDiscardSelectedMedicalFile('', false), true);
  assert.equal(rules.canDiscardSelectedMedicalFile('file-001', false), false);
  assert.equal(rules.canDiscardSelectedMedicalFile('', true), false);
});

test('requires ACTIVE binding and the correct scopes', () => {
  const activeView = { bindingStatus: 'ACTIVE', scopeCodes: ['HEALTH_VIEW'] };
  const activeEdit = { bindingStatus: 'ACTIVE', scopeCodes: ['HEALTH_VIEW', 'HEALTH_EDIT'] };
  const revoked = { bindingStatus: 'REVOKED', scopeCodes: ['HEALTH_VIEW', 'HEALTH_EDIT'] };
  assert.equal(rules.canViewMedicalFiles(activeView), true);
  assert.equal(rules.canUploadMedicalFiles(activeView), false);
  assert.equal(rules.canUploadMedicalFiles(activeEdit), true);
  assert.equal(rules.canViewMedicalFiles(revoked), false);
});

test('normalizes repository and phase document status names', () => {
  assert.equal(rules.normalizeMedicalFileAuditStatus('PENDING'), 'PENDING_REVIEW');
  assert.equal(rules.normalizeMedicalFileAuditStatus('PENDING_REVIEW'), 'PENDING_REVIEW');
  assert.equal(rules.normalizeMedicalFileAuditStatus('NEEDS_SUPPLEMENT'), 'NEED_MORE');
  assert.equal(rules.normalizeMedicalFileAuditStatus('NEED_MORE'), 'NEED_MORE');
  assert.equal(rules.normalizeMedicalFileAuditStatus('UNKNOWN'), null);
});

test('rejects successful upload payloads without a usable fileId', () => {
  assert.equal(rules.hasValidUploadedFileId({}), false);
  assert.equal(rules.hasValidUploadedFileId({ fileId: '   ' }), false);
  assert.equal(rules.hasValidUploadedFileId({ fileId: 'file-001' }), true);
});
