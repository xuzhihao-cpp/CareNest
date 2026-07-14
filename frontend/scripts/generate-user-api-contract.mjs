import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const check = process.argv.includes('--check');
const cliPath = path.join(
  frontendRoot,
  'node_modules',
  'openapi-typescript',
  'bin',
  'cli.js'
);
const args = [
  cliPath,
  '../contracts/user-api-v1.json',
  '-o',
  'src/types/generated/user-api.ts',
  '--alphabetize',
  '--properties-required-by-default',
  ...(check ? ['--check'] : [])
];

const result = spawnSync(process.execPath, args, { cwd: frontendRoot, stdio: 'inherit' });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
