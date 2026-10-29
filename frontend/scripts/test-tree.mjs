import { build } from 'esbuild';
import { mkdtemp, rm } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const pkg = JSON.parse(readFileSync(new URL('../package.json', import.meta.url)));
const dir = await mkdtemp(new URL('../node_modules/.tree-tests-', import.meta.url));
try {
  await build({
    entryPoints: ['scripts/ontologiesSlice.test.ts', 'scripts/EntityTree.test.tsx', 'scripts/ancestry.test.tsx'],
    outdir: dir, outbase: 'scripts', outExtension: { '.js': '.cjs' },
    bundle: true, platform: 'node', format: 'cjs',
    external: Object.keys({ ...pkg.dependencies, ...pkg.devDependencies }).filter(name => name !== 'url-join'),
    define: { 'process.env.REACT_APP_APIURL': '"https://ols.test/"', 'process.env.PUBLIC_URL': '"/"' },
  });
  const result = spawnSync(process.execPath, ['--test', `${dir}/ontologiesSlice.test.cjs`, `${dir}/EntityTree.test.cjs`, `${dir}/ancestry.test.cjs`], { stdio: 'inherit' });
  process.exitCode = result.status ?? 1;
} finally {
  await rm(dir, { recursive: true, force: true });
}
