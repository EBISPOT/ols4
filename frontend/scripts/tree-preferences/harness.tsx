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
  row('Imported', 'Preferred', { isDefiningOntology: false }), row('Obsolete', 'Outside', { isObsolete: true }),
  row('Cohort'), row('Cohort member', 'Cohort', { type: ['individual'], hasHierarchicalChildren: true }),
  row('Cohort child', 'Cohort member', { type: ['individual'] })];
const ontology = new Ontology({ ontologyId: 'preferences', type: ['ontology'], preferredRoot: [iri('Preferred')] });
const page = (elements: any[]) => ({ elements, totalElements: elements.length, totalPages: 1 });
const requests: URL[] = [];
let holdNextAncestors = false;
let heldRequest: { signal?: AbortSignal | null; release: () => void } | undefined;
// Fixtures remain at the HTTP boundary: actual component effects and request
// ownership run unchanged, including remount cleanup and cancellation.
window.fetch = (async (input: RequestInfo, init?: RequestInit) => {
  const url = new URL(String(input), location.href);
  requests.push(url);
  const path = url.pathname;
  if (path.endsWith('/treeAncestors') && holdNextAncestors) {
    holdNextAncestors = false;
    // Deliberately let this response arrive after abort to verify both the
    // component cleanup signal and the reducer's stale-response protection.
    await new Promise<void>(release => { heldRequest = { signal: init?.signal, release }; });
  }
  const selectedIri = decodeURIComponent(decodeURIComponent(path.split('/').at(-2)!));
  let data: any = page([]);
  if (/\/ontologies\/[^/]+$/.test(path)) data = ontology.properties;
  else if (/\/entities\/[^/]+$/.test(path)) {
    data = rows.find(r => r.iri === decodeURIComponent(decodeURIComponent(path.split('/').at(-1)!)));
  }
  else if (path.endsWith('/treeAncestors')) {
    const selected = rows.find(r => r.iri === selectedIri)!;
    const ancestors = new Set<string>();
    const collect = (r: typeof rows[number]) => r.hierarchicalParent.forEach(parent => {
      if (!ancestors.has(parent)) { ancestors.add(parent); collect(rows.find(row => row.iri === parent)!); }
    });
    collect(selected);
    data = page(rows.filter(r => ancestors.has(r.iri)));
  } else if (path.endsWith('/treeChildren')) {
    data = page(rows.filter(r => r.hierarchicalParent.includes(selectedIri) &&
      (url.searchParams.get('includeImportedEntities') !== 'false' || r.isDefiningOntology)));
    // Exercise the real embedded table used by the large COHO cohort class.
    if (selectedIri === iri('Cohort')) data.totalElements = 1001;
  } else if (path.endsWith('/entities')) {
    data = { ...page([]), facetFieldsToCounts: { subset: {}, treeAncestor: {} } };
  } else if (/\/(classes|individuals|properties)$/.test(path)) {
    data = page(rows.filter(r => r.type[0] === 'class' && !r.hierarchicalParent.length &&
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
  type EntityType = 'classes' | 'individuals' | 'properties';
  let mount = 0, navigation: Promise<void> = Promise.resolve();
  const navigate = (_ontology: Ontology, entity: ReturnType<typeof thingFromJsonProperties>) => {
    const state = { selected: entity.getName(), type: entity.getTypePlural() as EntityType };
    history.pushState(state, '', `#${state.type}/${encodeURIComponent(state.selected)}`);
    navigation = render(state.selected, true, state.type);
  };
  window.onpopstate = event => { navigation = render(event.state.selected, true, event.state.type); };
  async function render(selected?: string, remount = false, type: EntityType = 'classes', apiUrl = '/', currentOntology = ontology, settle = true) {
    if (remount) ++mount;
    ReactDOM.render(<Provider store={s}><EntityTree key={mount} ontology={currentOntology} entityType={type} apiUrl={apiUrl}
      selectedEntity={selected ? thingFromJsonProperties(rows.find(r => r.label[0] === selected)!) : undefined}
      lang="en" onNavigateToEntity={navigate} onNavigateToOntology={() => {}} /></Provider>, host);
    await new Promise(resolve => setTimeout(resolve, 0));
    if (!settle) return;
    await until(() => s.getState().ontologies.treeScopeKey === JSON.stringify([apiUrl, currentOntology.getOntologyId(), type]) &&
      s.getState().ontologies.rootStatus === 'succeeded' &&
      s.getState().ontologies.numPendingTreeRequests === 0 &&
      (selected ? host.querySelector('.jstree-clicked a')?.textContent === selected : host.querySelector('[role="tree"]')?.textContent), `Tree did not settle: ${selected || 'browse'}`);
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
    const mixedChecks = () => {
      assert(!checkbox(host, imports).checked && !checkbox(host, 'Show counts').checked, 'Mixed class/individual navigation reset display preferences');
      assert(!s.getState().ontologies.preferredRoots, 'Mixed navigation reset All classes');
    };
    const link = (name: string) => Array.from(host.querySelectorAll<HTMLAnchorElement>('[role="tree"] a')).find(a => a.textContent === name)!;
    const clickLink = async (name: string) => { assert(link(name), `Missing tree link ${name}`); link(name).click(); await navigation; mixedChecks(); };
    await render();
    link('Cohort').closest('li')!.querySelector<HTMLElement>('.jstree-ocl')!.click();
    await until(() => !!host.querySelector('tbody tr'), 'Embedded cohort table did not load');
    host.querySelector<HTMLElement>('tbody tr')!.click();
    await navigation;
    assert(location.hash.startsWith('#individuals/'), 'Embedded table did not navigate to individual route');
    mixedChecks();
    await clickLink('Cohort');
    assert(location.hash.startsWith('#classes/'), 'Individual tree did not navigate to class route');
    const backForward = async (direction: 'back' | 'forward', selected: string) => {
      history[direction]();
      await until(() => decodeURIComponent(location.hash).endsWith('/' + selected), 'History route did not change');
      await navigation;
      mixedChecks();
      assert(host.querySelector('.jstree-clicked a')?.textContent === selected, 'History retained an old selected tree');
    };
    await backForward('back', 'Cohort member');
    await backForward('forward', 'Cohort');
    await render('Cohort child', true, 'individuals'); mixedChecks();
    await clickLink('Cohort member');
    await clickLink('Cohort');
    pass('PASS embedded class to individual, individual to class, individual to individual, remounts and browser back/forward retain preferences');
    holdNextAncestors = true;
    await render('Cohort member', true, 'individuals', '/', ontology, false);
    await until(() => !!heldRequest, 'Pending individual ancestry request did not start');
    await render('Native', true);
    assert(heldRequest!.signal?.aborted, 'Changing entity type did not cancel the old ancestry fetch');
    heldRequest!.release();
    await new Promise(resolve => setTimeout(resolve, 30));
    mixedChecks();
    assert(host.querySelector('.jstree-clicked a')?.textContent === 'Native', 'Late individual response replaced the selected class');
    pass('PASS type changes abort pending ancestry and ignore late responses without resetting preferences');
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
    assert(!checkbox(host, imports).checked && !checkbox(host, 'Show counts').checked && !checkbox(host, 'Show obsolete terms').checked, 'Explicit type tab reset common preferences');
    assert(s.getState().ontologies.preferredRoots, 'Properties tab overwrote the class preferred-root choice');
    await render(undefined, true);
    assert(checkbox(host, 'Preferred roots').checked, 'Returning to Classes lost preferred roots');
    await render(undefined, true, 'properties', '/other-api/');
    assert(checkbox(host, imports).checked, 'API scope did not reset');
    checkbox(host, imports).click(); checkbox(host, 'Show counts').click();
    await render(undefined, true, 'properties', '/other-api/', new Ontology({ ontologyId: 'another', type: ['ontology'], preferredRoot: [] }));
    assert(checkbox(host, imports).checked && checkbox(host, 'Show counts').checked, 'Ontology scope did not reset');
    for (const type of ['individuals', 'properties'] as const) {
      // Enter a fresh API/ontology through a non-class tab first.
      const freshApi = `/${type}-first/`;
      requests.length = 0;
      await render(undefined, true, type, freshApi);
      assert(!requests.some(url => url.searchParams.has('isPreferredRoot')), 'Preferred-root filtering leaked outside Classes');
      await render(undefined, true, 'classes', freshApi);
      assert(checkbox(host, 'Preferred roots').checked, `${type} first entry lost default preferred roots`);
      checkbox(host, 'All classes').click();
      await render(undefined, true, type, freshApi);
      await render(undefined, true, 'classes', freshApi);
      assert(checkbox(host, 'All classes').checked, `${type} round trip reset All classes`);
    }
    pass('PASS explicit type tabs preserve common settings and class root choices; API and ontology changes reset preferences');

    const left = document.querySelector<HTMLElement>('#widget-left')!, right = document.querySelector<HTMLElement>('#widget-right')!;
    const widget = (container: HTMLElement, specifiedRootIri?: string, entityType: EntityType = 'classes') => createEntityTree({ ontologyId: 'preferences', apiUrl: '/', specifiedRootIri, entityType }, container);
    widget(left); widget(right);
    await until(() => left.textContent!.includes(imports) && right.textContent!.includes(imports), 'Public widgets did not mount');
    checkbox(left, imports).click();
    widget(left, iri('Native'));
    await until(() => left.textContent!.includes('Native'), 'Widget root navigation did not settle');
    assert(!checkbox(left, imports).checked && checkbox(right, imports).checked, 'Widget preferences reset or leaked');
    widget(left, iri('Cohort member'), 'individuals');
    await until(() => left.querySelector('[role="tree"]')?.textContent?.includes('Cohort member'), 'Widget type navigation did not settle');
    assert(!checkbox(left, imports).checked && checkbox(right, imports).checked, 'Widget type navigation reset or leaked preferences');
    pass('PASS public widget navigation and independent preferences');
    result.textContent += '\nGREEN: 6 mounted regression groups passed';
  } catch (error) {
    result.textContent = `RED: ${String(error)}\n${passed.join('\n')}`;
    console.error(error);
  } finally { this.disabled = false; }
};
