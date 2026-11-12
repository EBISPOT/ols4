// Local-only fault injection; all ordinary API responses come from the real backend.
import http from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { resolve, extname } from 'node:path';
const port = Number(process.env.PORT || 13049);
const backend = process.env.TREE_TEST_BACKEND || 'http://127.0.0.1:18043';
if (new URL(backend).hostname !== '127.0.0.1') throw Error('Use a task-owned localhost backend');
const rules = [], requests = [];
function kind(u) {
  const q = u.searchParams;
  if (q.get('facetFields') === 'treeAncestor') return 'counts';
  if (q.get('facetFields') === 'subset') return 'subsets';
  if (q.has('hasDirectParents') || q.has('isPreferredRoot')) return 'roots';
  if (u.pathname.endsWith('hierarchicalAncestors')) return 'ancestry';
  if (u.pathname.endsWith('treeChildren')) return q.has('page') ? 'table' : q.get('size') === '1' ? 'directCount' : 'children';
  return 'other';
}
http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${port}`);
  const json = (body, code = 200) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };
  if (url.pathname === '/__test/arm') { rules.push(Object.fromEntries(url.searchParams)); return json({ armed: true }); }
  if (url.pathname === '/__test/log') return json(requests);
  if (url.pathname === '/__test/reset') { rules.length = 0; requests.length = 0; return json({ reset: true }); }
  const channel = url.pathname.startsWith('/left/') ? 'left' : url.pathname.startsWith('/right/') ? 'right' : 'app';
  url.pathname = url.pathname.replace(/^\/(left|right)\//, '/');
  if (url.pathname.startsWith('/api/')) {
    const record = { channel, kind: kind(url), path: url.pathname + url.search };
    requests.push(record); console.log(JSON.stringify(record));
    const index = rules.findIndex(r => r.channel === channel && r.kind === record.kind);
    if (index >= 0) {
      const [rule] = rules.splice(index, 1);
      if (rule.delay) await new Promise(r => setTimeout(r, Number(rule.delay)));
      if (rule.mode !== 'pass') return rule.mode === 'empty'
        ? json({ elements: [], totalElements: 0, totalPages: 0, page: 0 })
        : json({ message: 'Deliberate fail-once test response' }, 503);
    }
    try {
      const upstream = await fetch(backend + url.pathname + url.search);
      res.writeHead(upstream.status, { 'Content-Type': upstream.headers.get('content-type') || 'application/json' });
      return res.end(Buffer.from(await upstream.arrayBuffer()));
    } catch (e) { return json({ message: String(e) }, 502); }
  }
  // Runtime override is local to this proxy; the shared frontend configuration stays intact.
  if (url.pathname === '/runtime-config.js') {
    res.writeHead(200, { 'Content-Type': 'text/javascript' });
    return res.end('window.__OLS_RUNTIME_CONFIG__ = Object.freeze({PUBLIC_URL:"/",REACT_APP_APIURL:location.origin+"/"});');
  }
  let file = resolve('dist', '.' + url.pathname);
  if (!file.startsWith(resolve('dist') + '/')) return json({}, 404);
  if (!existsSync(file) || !extname(file)) file = resolve('dist/index.html');
  const mime = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml' };
  try { res.writeHead(200, { 'Content-Type': mime[extname(file)] || 'application/octet-stream' }); res.end(readFileSync(file)); }
  catch { json({}, 404); }
}).listen(port, '127.0.0.1', () => console.log(`Tree retry fixture http://127.0.0.1:${port}/tree-retry/index.html -> ${backend}`));
