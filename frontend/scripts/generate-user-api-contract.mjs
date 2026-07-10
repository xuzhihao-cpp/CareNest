import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const check = process.argv.includes('--check');
const command = path.join(
  frontendRoot,
  'node_modules',
  '.bin',
  process.platform === 'win32' ? 'openapi-typescript.CMD' : 'openapi-typescript'
);
const args = [
  '../contracts/user-api-v1.json',
  '-o',
  'src/types/generated/user-api.ts',
  '--alphabetize',
  '--properties-required-by-default',
  ...(check ? ['--check'] : [])
];

const result = spawnSync(command, args, { cwd: frontendRoot, stdio: 'inherit', shell: true });
process.exit(result.status ?? 1);
