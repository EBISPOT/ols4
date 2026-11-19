import { build } from 'esbuild';
import { execFileSync } from 'node:child_process';
import { relative } from 'node:path';
import { mkdir, copyFile } from 'node:fs/promises';
const baseline = process.env.TREE_RETRY_BASELINE;
const out = baseline ? 'dist/tree-retry-baseline' : 'dist/tree-retry';
await mkdir(out, { recursive: true });
await build({ entryPoints: ['scripts/tree-retry/harness.ts'], outfile: `${out}/harness.js`, bundle: true, platform: 'browser',
  plugins: baseline ? [{ name: 'baseline-tree', setup(b) {
    b.onLoad({ filter: /src\/pages\/ontologies\/(ontologiesSlice\.ts|entities\/(EntityTree|EntityList)\.tsx)$/ }, args => ({
      contents: execFileSync('git', ['show', `${baseline}:frontend/${relative(process.cwd(), args.path)}`], { encoding: 'utf8' }),
      loader: args.path.endsWith('tsx') ? 'tsx' : 'ts',
    }));
  } }] : [],
  define: { 'process.env.PUBLIC_URL': '"/"', 'process.env.REACT_APP_APIURL': '"/"', 'process.env.NODE_ENV': '"development"' }, logLevel: 'info' });
await copyFile('scripts/tree-retry/index.html', `${out}/index.html`);
await copyFile('src/widgets/styles/treestyles.css', `${out}/treestyles.css`);
