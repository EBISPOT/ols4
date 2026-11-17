import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { createAppStore } from '../../src/app/store';
import { thingFromJsonProperties } from '../../src/app/util';
import Ontology from '../../src/model/Ontology';
import EntityTree from '../../src/pages/ontologies/entities/EntityTree';
import { getOntology } from '../../src/pages/ontologies/ontologiesSlice';
import { createEntityTree } from '../../src/widgets/EntityTreeWidget';

const host = document.querySelector<HTMLElement>('#tree')!;
const result = document.querySelector<HTMLElement>('#result')!;
const iri = (name: string) => `https://example.org/preferences#${name}`;
const row = (name: string, parent?: string, extra = {}) => ({
  iri: iri(name), label: [name], type: ['class'], ontologyId: 'preferences',
  isDefiningOntology: true, hasHierarchicalChildren: !parent,
  hierarchicalParent: parent ? [iri(parent)] : [], ...extra,
});
const rows = [row('Preferred'), row('Outside'), row('Native', 'Preferred'),
  row('Imported', 'Preferred', { isDefiningOntology: false }), row('Obsolete', 'Outside', { isObsolete: true })];
const ontology = new Ontology({ ontologyId: 'preferences', type: ['ontology'], preferredRoot: [iri('Preferred')] });
const page = (elements: any[]) => ({ elements, totalElements: elements.length, totalPages: 1 });
const requests: URL[] = [];
// Fixtures remain at the HTTP boundary: actual component effects and request
// ownership run unchanged, including remount cleanup and cancellation.
window.fetch = (async (input: RequestInfo) => {
  const url = new URL(String(input), location.href);
  requests.push(url);
  const path = url.pathname;
  const selectedIri = decodeURIComponent(decodeURIComponent(path.split('/').at(-2)!));
  let data: any = page([]);
  if (/\/ontologies\/preferences$/.test(path)) data = ontology.properties;
  else if (/\/entities\/[^/]+$/.test(path)) {
    data = rows.find(r => r.iri === decodeURIComponent(decodeURIComponent(path.split('/').at(-1)!)));
  }
  else if (path.endsWith('/treeAncestors')) {
    const selected = rows.find(r => r.iri === selectedIri)!;
    data = page(rows.filter(r => selected.hierarchicalParent.includes(r.iri)));
  } else if (path.endsWith('/treeChildren')) {
    data = page(rows.filter(r => r.hierarchicalParent.includes(selectedIri) &&
      (url.searchParams.get('includeImportedEntities') !== 'false' || r.isDefiningOntology)));
  } else if (path.endsWith('/entities')) {
    data = { ...page([]), facetFieldsToCounts: { subset: {}, treeAncestor: {} } };
  } else if (/\/(classes|individuals|properties)$/.test(path)) {
    data = page(rows.filter(r => !r.hierarchicalParent.length &&
      (url.searchParams.get('isPreferredRoot') !== 'true' || r.iri === iri('Preferred'))));
  }
  return new Response(JSON.stringify(data), { status: 200, headers: { 'content-type': 'application/json' } });
}) as typeof fetch;

function assert(value: unknown, message: string) { if (!value) throw new Error(message); }
async function until(predicate: () => unknown, message: string) {
  const deadline = Date.now() + 10000;
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error(message);
    await new Promise(resolve => setTimeout(resolve, 20));
  }
}
function checkbox(container: HTMLElement, label: string) {
  const control = Array.from(container.querySelectorAll('label')).find(el => el.textContent === label)?.querySelector<HTMLInputElement>('input');
  assert(control, `Missing checkbox: ${label}`);
  return control!;
}
const imports = 'Show imported terms in tree';
document.querySelector<HTMLButtonElement>('#run')!.onclick = async function () {
  this.disabled = true;
  for (const id of ['tree', 'widget-left', 'widget-right']) ReactDOM.unmountComponentAtNode(document.getElementById(id)!);
  const passed: string[] = [];
  const pass = (message: string) => { passed.push(message); result.textContent = passed.join('\n'); };
  const s = createAppStore();
  s.dispatch(getOntology.fulfilled(ontology, 'fixture', { ontologyId: 'preferences', lang: 'en' }));
  let mount = 0;
  async function render(selected?: string, remount = false, type: 'classes' | 'properties' = 'classes', apiUrl = '/') {
    if (remount) ++mount;
    ReactDOM.render(<Provider store={s}><EntityTree key={mount} ontology={ontology} entityType={type} apiUrl={apiUrl}
      selectedEntity={selected ? thingFromJsonProperties(rows.find(r => r.label[0] === selected)!) : undefined}
      lang="en" onNavigateToEntity={() => {}} onNavigateToOntology={() => {}} /></Provider>, host);
    await until(() => s.getState().ontologies.rootStatus === 'succeeded' &&
      s.getState().ontologies.numPendingTreeRequests === 0 &&
      (!selected || host.querySelector('[role="tree"]')?.textContent?.includes(selected)), `Tree did not settle: ${selected || 'browse'}`);
    await new Promise(resolve => setTimeout(resolve, 30));
  }
  try {
    await render();
    checkbox(host, imports).click(); checkbox(host, 'Show counts').click();
    checkbox(host, 'All classes').click();
    for (const [selected, remount] of [['Native', true], ['Imported', false], [undefined, true], ['Native', true], ['Imported', true]] as const) {
      await render(selected, remount);
      assert(!checkbox(host, imports).checked && !checkbox(host, 'Show counts').checked, `Display preferences reset at ${selected || 'browse'}`);
      assert(!s.getState().ontologies.preferredRoots, 'All classes preference reset');
      if (selected === 'Imported') assert(host.querySelector('.imported-tree-link'), 'Imported selected context lost grey styling');
    }
    pass('PASS browse, selected term changes, remounts and browse return retain preferences');
    checkbox(host, 'Preferred roots').click();
    checkbox(host, 'Show all siblings').click(); checkbox(host, 'Show obsolete terms').click();
    await render('Native', true);
    assert(checkbox(host, 'Show all siblings').checked && checkbox(host, 'Show obsolete terms').checked, 'Sibling or obsolete preference reset');
    checkbox(host, 'Show obsolete terms').click();
    requests.length = 0;
    await render('Obsolete', true);
    assert(host.textContent!.includes('Outside') && host.textContent!.includes('Obsolete'), 'Preferred roots hid selected outside path');
    assert(checkbox(host, 'Show obsolete terms').checked && !s.getState().ontologies.displayObsolete, 'Obsolete override became a stored preference');
    assert(requests.some(url => url.searchParams.get('includeObsoleteEntities') === 'true'), 'Obsolete selected requests were filtered');
    requests.length = 0;
    await render('Native', true);
    assert(!checkbox(host, 'Show obsolete terms').checked && !s.getState().ontologies.displayObsolete, 'Obsolete override leaked to active selection');
    assert(requests.some(url => url.searchParams.get('includeObsoleteEntities') === 'false'), 'Active requests retained obsolete override');
    pass('PASS preferred-root fallback and selected obsolete visibility preserve user settings');
    await render(undefined, true, 'properties');
    assert(checkbox(host, imports).checked && checkbox(host, 'Show counts').checked && !checkbox(host, 'Show obsolete terms').checked, 'Entity-type scope did not reset');
    checkbox(host, imports).click();
    await render(undefined, true, 'properties', '/other-api/');
    assert(checkbox(host, imports).checked, 'API scope did not reset');
    pass('PASS entity-type and API scope changes reset preferences');

    const left = document.querySelector<HTMLElement>('#widget-left')!, right = document.querySelector<HTMLElement>('#widget-right')!;
    const widget = (container: HTMLElement, specifiedRootIri?: string) => createEntityTree({ ontologyId: 'preferences', apiUrl: '/', specifiedRootIri }, container);
    widget(left); widget(right);
    await until(() => left.textContent!.includes(imports) && right.textContent!.includes(imports), 'Public widgets did not mount');
    checkbox(left, imports).click();
    widget(left, iri('Native'));
    await until(() => left.textContent!.includes('Native'), 'Widget root navigation did not settle');
    assert(!checkbox(left, imports).checked && checkbox(right, imports).checked, 'Widget preferences reset or leaked');
    pass('PASS public widget navigation and independent preferences');
    result.textContent += '\nGREEN: 4 mounted regression groups passed';
  } catch (error) {
    result.textContent = `RED: ${String(error)}\n${passed.join('\n')}`;
    console.error(error);
  } finally { this.disabled = false; }
};
