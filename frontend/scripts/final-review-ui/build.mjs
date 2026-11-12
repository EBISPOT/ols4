import { build } from 'esbuild';
import { mkdir,copyFile } from 'node:fs/promises';
const out='dist/final-review-ui';await mkdir(out,{recursive:true});
await build({entryPoints:['scripts/final-review-ui/harness.tsx'],outfile:out+'/harness.js',bundle:true,platform:'browser',define:{'process.env.PUBLIC_URL':'"/"','process.env.REACT_APP_APIURL':'"/"','process.env.NODE_ENV':'"development"'},logLevel:'info'});
await copyFile('scripts/final-review-ui/index.html',out+'/index.html');
await copyFile('src/widgets/styles/treestyles.css',out+'/treestyles.css');
