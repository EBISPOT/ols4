import { build } from 'esbuild';
import { mkdir, copyFile } from 'node:fs/promises';

const out = 'dist/tree-preferences';
await mkdir(out, { recursive: true });
await build({
  entryPoints: ['scripts/tree-preferences/harness.tsx'], outfile: `${out}/harness.js`,
  bundle: true, platform: 'browser',
  define: { 'process.env.PUBLIC_URL': '"/"', 'process.env.REACT_APP_APIURL': '"/"', 'process.env.NODE_ENV': '"development"' },
});
await copyFile('scripts/tree-preferences/index.html', `${out}/index.html`);
