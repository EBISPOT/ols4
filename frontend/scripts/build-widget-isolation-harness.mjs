// Development-only mounted regression. Uses the public widget factory and real API.
import { build } from 'esbuild';
import { mkdir, copyFile } from 'node:fs/promises';
const outdir = 'dist/widget-isolation';
await mkdir(outdir, { recursive: true });
await build({
  entryPoints: ['scripts/widget-isolation/harness.ts'],
  outfile: `${outdir}/harness.js`, bundle: true, platform: 'browser',
  define: { 'process.env.PUBLIC_URL': '"/"', 'process.env.REACT_APP_APIURL': '"/"', 'process.env.NODE_ENV': '"development"' },
  sourcemap: true, logLevel: 'info',
});
await copyFile('scripts/widget-isolation/index.html', `${outdir}/index.html`);
await copyFile('src/widgets/styles/treestyles.css', `${outdir}/treestyles.css`);
console.log('Open http://127.0.0.1:13043/widget-isolation/index.html and click Run regression. Requires the task-owned local fixture API.');
