import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { createEntityTree } from '../../src/widgets';
import { createAppStore } from '../../src/app/store';
import EntityTree from '../../src/pages/ontologies/entities/EntityTree';
import { getOntology } from '../../src/pages/ontologies/ontologiesSlice';
import { thingFromJsonProperties } from '../../src/app/util';
const left = document.querySelector<HTMLElement>('#left')!;
const right = document.querySelector<HTMLElement>('#right')!;
const result = document.querySelector<HTMLElement>('#result')!;
const root = 'https://example.org/native#R';
const child = 'https://example.org/native#D';
function assert(value: unknown, message: string) { if (!value) throw Error(message); }
const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
async function until(predicate: () => unknown, message: string) {
  const deadline = Date.now() + 8000;
  while (!predicate()) { if (Date.now() > deadline) throw Error(message); await sleep(25); }
}
const text = (node = left) => node.textContent || '';
const link = (iri: string, node = left) => Array.from(node.querySelectorAll<HTMLAnchorElement>('a[title]')).find(a => a.title.includes(`[${iri}]`));
const alert = (node = left) => node.querySelector('[role="alert"]');
function button(name: string, node = left) {
  const b = Array.from(node.querySelectorAll<HTMLButtonElement>('button')).find(b => b.textContent === name);
  assert(b, `missing button: ${name}`); return b!;
}
function checkbox(name: string, node = left) {
  return Array.from(node.querySelectorAll<HTMLInputElement>('label input')).find(i => i.closest('label')!.textContent === name)!;
}
function expand(iri: string, node = left) {
  const li = link(iri, node)?.closest('li'); assert(li, `missing node: ${iri}`);
  if (!li!.classList.contains('jstree-open')) (li!.querySelector('.jstree-ocl') as HTMLElement).click();
}
async function settled() { await until(() => !document.querySelector('.spinner-default') && !document.querySelector('[aria-label="Descendant count loading"]'), 'requests did not settle'); await sleep(50); }
const arm = (kind: string, mode = 'fail', delay = '0', channel = 'left') => fetch(`/__test/arm?${new URLSearchParams({ kind, mode, delay, channel })}`);
const logs = () => fetch('/__test/log').then(r => r.json()) as Promise<{ channel: string; kind: string; path: string }[]>;
const mount = (id: string, node = left, extra = {}) => createEntityTree({ apiUrl: location.origin + (node === left ? '/left/' : '/right/'), ontologyId: id, ...extra }, node);
const clear = () => { ReactDOM.unmountComponentAtNode(left); ReactDOM.unmountComponentAtNode(right); };
function type(input: HTMLInputElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
  input.dispatchEvent(new Event('input', { bubbles: true }));
}
document.querySelector<HTMLButtonElement>('#run')!.addEventListener('click', async function () {
  this.disabled = true; result.textContent = 'Running';
  const passed: string[] = [];
  const pass = (message: string) => { passed.push('PASS ' + message); result.textContent = passed.join('\n'); };
  try {
    clear(); await fetch('/__test/reset'); mount('native'); mount('mixed', right);
    await until(() => link(root) && link('https://example.org/mixed#P', right), 'initial widgets did not load'); await settled();
    await arm('roots'); checkbox('All classes').click();
    await until(() => alert() && link('https://example.org/mixed#P', right), 'failed roots must show an accessible error beside a healthy second widget');
    await settled(); assert(!text().includes('No terms match'), 'failed roots shown as empty');
    const sibling = right.innerHTML;
    button('Retry tree').click(); await until(() => link(root) && !alert(), 'root retry must recover'); await settled();
    assert(right.innerHTML === sibling, 'root retry changed sibling widget'); pass('root failure/retry and independent second widget');
    expand(root); await until(() => link(child), 'root expansion failed'); await settled();
    checkbox('Show counts').click(); await arm('counts'); checkbox('Show counts').click();
    await until(() => text().includes('Unable to load descendant counts.'), 'eligible count failure was silent');
    const expanded = link(child)!;
    button('Retry counts').click(); await until(() => !alert() && text().includes('(5)'), 'count retry must recover eligible counts'); await settled();
    assert(link(child) === expanded, 'count retry replaced expanded nodes'); assert(right.innerHTML === sibling, 'count retry changed sibling');
    const countCalls = (await logs()).filter(r => r.channel === 'left' && r.kind === 'counts');
    assert(countCalls.slice(-2).every(r => r.path.includes('includeObsoleteEntities=false') && !r.path.includes('isDefiningOntology')), 'default count retry lost settings');
    pass('default eligible-count retry preserves loaded expansion and sibling');
    await arm('directCount'); await arm('children'); expand(child);
    await until(() => text().includes('Unable to load the child count.') && text().includes('Unable to load children.'), 'child and direct-count failures were silent');
    button('Retry child count').click();
    await until(() => !text().includes('Unable to load the child count.'), 'direct-count retry did not recover');
    assert(text().includes('Unable to load children.'), 'count retry hid the independent child failure');
    button('Retry children').click();
    await until(() => !text().includes('Unable to load children.') && link(child)!.closest('li')!.querySelectorAll('a[title]').length > 1, 'child retry did not recover real child nodes'); await settled();
    assert(right.innerHTML === sibling, 'child retries altered sibling widget');
    pass('direct-child count and child-expansion retry controls recover independently');
    await arm('subsets'); checkbox('Show obsolete terms').click();
    await until(() => text().includes('Unable to load subset filters.'), 'subset failure was silent');
    button('Retry subset filters').click();
    await until(() => !text().includes('Unable to load subset filters.') && text().includes('Filter by subset'), 'subset retry did not recover controls');
    checkbox('Show obsolete terms').click(); await settled();
    assert(right.innerHTML === sibling, 'subset retry altered sibling widget');
    pass('subset-filter retry recovers available filter controls');
    await arm('roots'); checkbox('Show imported terms').click();
    await until(() => text().includes('Unable to load the tree.'), 'filter failure was silent');
    await arm('roots', 'pass', '350'); button('Retry tree').click();
    await until(() => text().includes('Loading tree'), 'retry loading state absent');
    await until(() => link(root) && !alert(), 'filtered root retry failed'); await settled();
    assert(!checkbox('Show imported terms').checked, 'retry reset filter');
    assert((await logs()).filter(r => r.channel === 'left' && r.kind === 'roots').slice(-2).every(r => r.path.includes('isDefiningOntology=true')), 'retry used stale root settings');
    pass('filtered failure retries current settings with distinct loading');
    await arm('roots', 'empty'); checkbox('Show imported terms').click();
    await until(() => text().includes('No terms match the current tree settings.'), 'successful empty result must be explicit');
    assert(!alert() && !link(root), 'empty success retained error or old tree'); pass('successful empty result is distinct from failure');
    await arm('roots', 'fail', '600'); checkbox('Show imported terms').click();
    await until(() => text().includes('Loading tree'), 'old request did not begin');
    mount('mixed'); await until(() => link('https://example.org/mixed#P'), 'new scope failed'); await sleep(750); await settled();
    assert(!alert() && !link(root), 'cancelled old failure contaminated new scope'); pass('scope replacement suppresses cancelled late failure');
    clear();
    const selectedStore = createAppStore();
    const selectedOntology = await selectedStore.dispatch(getOntology({ ontologyId: 'native', lang: 'en', apiUrl: location.origin + '/left/' })).unwrap();
    const selectedEntity = thingFromJsonProperties(await fetch('/api/v2/ontologies/native/entities/' + encodeURIComponent(encodeURIComponent(child))).then(r => r.json()));
    ReactDOM.render(React.createElement(Provider, { store: selectedStore }, React.createElement(MemoryRouter, {}, React.createElement(EntityTree, {
      ontology: selectedOntology, selectedEntity: selectedEntity as any, entityType: 'classes', lang: 'en', apiUrl: location.origin + '/left/', onNavigateToEntity: () => {}, onNavigateToOntology: () => {},
    }))), left);
    await until(() => link(root) && link(child), 'initial selected path missing'); await settled();
    await arm('ancestry'); checkbox('All classes').click();
    await until(() => alert(), 'selected ancestry failure was silent'); button('Retry tree').click();
    await until(() => link(root) && link(child) && !alert(), 'ancestry retry did not recover selected path'); await settled();
    pass('selected ancestry retries without dropping the path');
    clear();
    const pagingStore = createAppStore();
    const ontology = await pagingStore.dispatch(getOntology({ ontologyId: 'paging', lang: 'en', apiUrl: location.origin + '/left/' })).unwrap();
    ReactDOM.render(React.createElement(Provider, { store: pagingStore }, React.createElement(MemoryRouter, {}, React.createElement(EntityTree, {
      ontology, entityType: 'classes', lang: 'en', apiUrl: location.origin + '/left/', onNavigateToEntity: () => {}, onNavigateToOntology: () => {},
    }))), left);
    const a = 'https://example.org/paging#A', b = 'https://example.org/paging#B';
    await until(() => link(a) && link(b), 'paging roots missing'); expand(a); expand(b);
    const parent = (iri: string) => link(iri)!.closest('li') as HTMLElement;
    await until(() => parent(a).querySelectorAll('tbody tr').length === 10 && parent(b).querySelectorAll('tbody tr').length === 10, 'both embedded tables did not load'); await settled();
    const tableSibling = parent(b).innerHTML;
    const search = parent(a).querySelector<HTMLInputElement>('input[placeholder="Search children..."]')!;
    search.focus();
    await arm('table', 'pass', '250'); type(search, 'A');
    await until(() => text(parent(a)).includes('Loading direct'), 'search loading state missing');
    assert(search.getClientRects().length > 0 && !search.disabled && document.activeElement === search, 'search lost focus or visibility during request');
    await until(() => !text(parent(a)).includes('Loading direct'), 'first search character did not settle');
    assert(document.activeElement === search, 'search lost focus after first character request');
    type(document.activeElement as HTMLInputElement, 'A0');
    await until(() => parent(a).querySelectorAll('tbody tr').length === 10 && !text(parent(a)).includes('Loading direct'), 'second character search did not load');
    assert(document.activeElement === search && search.value === 'A0', 'sequential search failed without refocusing');
    pass('search remains visible and focused between completed character requests');
    await arm('table'); button('Next', parent(a)).click();
    await until(() => text(parent(a)).includes('Unable to load direct children.'), 'embedded table failure was silent');
    assert(parent(b).innerHTML === tableSibling, 'table failure altered sibling');
    assert(search.getClientRects().length > 0 && !search.disabled && !parent(a).querySelector('tbody'), 'failed table must retain usable controls instead of empty rows');
    button('Retry direct children', parent(a)).click();
    await until(() => !alert(parent(a)) && !text(parent(a)).includes('Loading direct'), 'embedded retry did not settle');
    assert(parent(a).querySelector('input') === search && search.value === 'A0', 'retry erased search input');
    const tableCalls = (await logs()).filter(r => r.channel === 'left' && r.kind === 'table');
    assert(tableCalls.slice(-2).every(r => r.path.includes('page=1') && r.path.includes('searchQuery=A0')), 'table retry lost current page/search');
    assert(parent(a).querySelectorAll('tbody tr').length === 10, 'table retry returned no rows');
    assert(parent(b).innerHTML === tableSibling, 'table retry altered sibling'); pass('per-parent table failure/retry preserves page/search and sibling');
    search.focus(); type(search, 'no-such-child-for-retry-regression');
    await until(() => text(parent(a)).includes('No direct children match the current settings.'), 'empty table search did not show success state');
    assert(!alert(parent(a)) && !parent(a).querySelector('tbody'), 'empty table search looked failed or retained rows');
    assert(search.getClientRects().length > 0 && document.activeElement === search, 'empty search lost usable controls');
    assert(parent(b).innerHTML === tableSibling, 'empty search altered sibling');
    pass('empty child search is distinct from failure and retains focused controls');
    pass('GREEN: 11 mounted retry checks passed');
  } catch (e) { result.textContent = `RED: ${String(e)}\n${passed.join('\n')}`; console.error(e); }
  finally { this.disabled = false; }
});
