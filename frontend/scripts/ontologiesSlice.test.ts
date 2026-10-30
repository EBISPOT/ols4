import assert from 'node:assert/strict';
import { test } from 'node:test';
import { configureStore } from '@reduxjs/toolkit';
import reducer, { getOntology, getRootEntities, getNodeChildren, getSubsets, getTreeMemberCounts, getDirectChildrenCount, getDirectChildrenEntities, setSelectedSubsets, resetTreeContent, resetTreeSettings } from '../src/pages/ontologies/ontologiesSlice';
import Ontology from '../src/model/Ontology';
import { thingFromJsonProperties } from '../src/app/util';

const subset = 'http://www.ebi.ac.uk/coho#IHCC_subset';
const scope = (id = 'coho', type = 'classes', api = '') => JSON.stringify([api, id, type]);
const store = () => configureStore({ reducer: { ontologies: reducer }, middleware: g => g({ serializableCheck: false }) });
function context(s: any, key: string, requestKey: string) {
  s.dispatch({ type: 'ontologies_set_tree_scope', payload: key });
  s.dispatch({ type: 'ontologies_set_tree_request_context', payload: { scopeKey: key, requestKey } });
}
function setup() {
  const s = store();
  s.dispatch(getOntology.fulfilled(new Ontology({ ontologyId: 'coho', type: ['ontology'], preferredRoot: [] }), 'ontology', { ontologyId: 'coho', lang: 'en' }));
  context(s, scope(), 'first');
  return s;
}
const args = (requestKey = 'first') => ({ ontology: new Ontology({ ontologyId: 'coho', type: ['ontology'], preferredRoot: [] }), ontologyId: 'coho', entityType: 'classes', treeScopeKey: scope(), requestKey, lang: 'en', showObsoleteEnabled: false, showImportedEnabled: true });
const term = (iri: string) => thingFromJsonProperties({ iri, ontologyId: 'coho', type: ['class'], label: [iri] });

for (const destination of [scope('efo'), scope('coho', 'individuals'), scope('coho', 'classes', 'https://other.test/')]) {
  test(`scope change clears selections, options, counts and paths atomically: ${destination}`, () => {
    const s = setup();
    s.dispatch(setSelectedSubsets([subset]));
    s.dispatch(getSubsets.pending('subsets', args()));
    s.dispatch(getSubsets.fulfilled([{ iri: subset, label: 'IHCC', numMembers: 77 }], 'subsets', args()));
    s.dispatch(getRootEntities.pending('roots', args()));
    s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('COHO root')] }, 'roots', args()));
    s.dispatch(getTreeMemberCounts.pending('counts', args()));
    s.dispatch(getTreeMemberCounts.fulfilled({ root: 77 }, 'counts', args()));
    s.dispatch({ type: 'ontologies_set_tree_scope', payload: destination });
    const state = s.getState().ontologies;
    assert.deepEqual(state.selectedSubsets, []);
    assert.deepEqual(state.subsets, []);
    assert.deepEqual(state.filteredMemberCounts, {});
    assert.deepEqual(state.rootNodes, []);
    assert.deepEqual(state.nodeChildren, {});
    assert.equal(state.numPendingTreeRequests, 0);
  });
}

test('same-scope entity and language changes retain selection but invalidate old requests', () => {
  const s = setup();
  s.dispatch(setSelectedSubsets([subset]));
  s.dispatch(getRootEntities.pending('old', args()));
  s.dispatch(resetTreeSettings({ entityType: 'classes' }));
  context(s, scope(), 'another-entity-fr');
  s.dispatch(getRootEntities.pending('new', args('another-entity-fr')));
  s.dispatch(getRootEntities.rejected(new Error('abort'), 'old', args()));
  assert.deepEqual(s.getState().ontologies.selectedSubsets, [subset]);
  assert.equal(s.getState().ontologies.numPendingTreeRequests, 1);
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('current')] }, 'new', args('another-entity-fr')));
  assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
});

test('late roots, children, subset options and counts cannot overwrite a newer context', () => {
  const s = setup();
  const thunks = [getRootEntities, getNodeChildren, getSubsets, getTreeMemberCounts, getDirectChildrenCount, getDirectChildrenEntities];
  thunks.forEach(t => s.dispatch(t.pending('old', args())));
  context(s, scope('efo'), 'efo');
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('old root')] }, 'old', args()));
  s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: 'old', children: [] }, 'old', args()));
  s.dispatch(getSubsets.fulfilled([{ iri: subset, label: 'IHCC', numMembers: 77 }], 'old', args()));
  s.dispatch(getTreeMemberCounts.fulfilled({ old: 77 }, 'old', args()));
  s.dispatch(getDirectChildrenCount.fulfilled({ entityIri: 'old', totalElements: 77 }, 'old', args()));
  s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('old child')], totalElements: 77 } as any, 'old', args()));
  thunks.forEach(t => s.dispatch(t.rejected(new Error('late'), 'old', args())));
  const state = s.getState().ontologies;
  assert.deepEqual(state.rootNodes, []);
  assert.deepEqual(state.nodeChildren, {});
  assert.deepEqual(state.subsets, []);
  assert.deepEqual(state.filteredMemberCounts, {});
  assert.deepEqual(state.directChildrenCounts, {});
  assert.deepEqual(state.directChildrenEntities, []);
  assert.equal(state.numPendingTreeRequests, 0);
});

test('superseding and aborting requests keeps pending count at zero or above', () => {
  const s = setup();
  s.dispatch(getRootEntities.pending('old', args()));
  s.dispatch(getRootEntities.pending('new', args()));
  assert.equal(s.getState().ontologies.numPendingTreeRequests, 1);
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('stale')] }, 'old', args()));
  assert.deepEqual(s.getState().ontologies.rootNodes, []);
  s.dispatch(getRootEntities.rejected(new Error('abort'), 'new', args()));
  s.dispatch(getRootEntities.rejected(new Error('duplicate'), 'old', args()));
  assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
  s.dispatch(resetTreeContent());
  s.dispatch(getRootEntities.rejected(new Error('late'), 'new', args()));
  assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
});

test('cancellation reaches fetch for multi-request tree thunks', async () => {
  const s = setup();
  const signals: AbortSignal[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = ((_url: any, init: any) => {
    signals.push(init?.signal);
    return new Promise((_resolve, reject) => init?.signal?.addEventListener('abort', () => reject(new Error('aborted'))));
  }) as any;
  try {
    const pending = s.dispatch(getNodeChildren({ ...args(), entityTypePlural: 'classes', entityIri: 'parent', absoluteIdentity: 'parent' }));
    assert.equal(signals.length, 4);
    pending.abort();
    await pending;
    assert(signals.every(signal => signal?.aborted));
    assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
  } finally { globalThis.fetch = originalFetch; }
});

test('old effect arguments issue no fetch after a scope or filter change', async () => {
  const s = setup();
  s.dispatch(setSelectedSubsets([subset]));
  const originalFetch = globalThis.fetch;
  const urls: string[] = [];
  globalThis.fetch = (async (url: any) => {
    urls.push(String(url));
    return { ok: true, json: async () => ({ elements: [], totalElements: 0, facetFieldsToCounts: {} }) };
  }) as any;
  try {
    context(s, scope('efo'), 'efo');
    await s.dispatch(getRootEntities({ ...args(), subsetTree: [subset] }));
    await s.dispatch(getTreeMemberCounts({ ...args(), selectedSubsets: [subset] }));
    assert.deepEqual(urls, []);
    const efo = { ...args('efo'), treeScopeKey: scope('efo'), ontologyId: 'efo' };
    await s.dispatch(getRootEntities(efo));
    assert.equal(urls.length, 1);
    assert(urls[0].includes('/efo/classes?'));
    assert.equal(new URL(urls[0]).searchParams.get('subsetTree'), null);
  } finally { globalThis.fetch = originalFetch; }
});

test('a delayed response after a language or subset change cannot replace counts or options', () => {
  const s = setup();
  s.dispatch(setSelectedSubsets([subset]));
  s.dispatch(getTreeMemberCounts.pending('old-counts', args()));
  s.dispatch(getSubsets.pending('old-subsets', args()));
  context(s, scope(), 'french-other-subset');
  s.dispatch(getTreeMemberCounts.pending('new-counts', args('french-other-subset')));
  s.dispatch(getTreeMemberCounts.fulfilled({ current: 3 }, 'new-counts', args('french-other-subset')));
  s.dispatch(getTreeMemberCounts.fulfilled({ stale: 77 }, 'old-counts', args()));
  s.dispatch(getSubsets.fulfilled([], 'old-subsets', args()));
  assert.deepEqual(s.getState().ontologies.filteredMemberCounts, { current: 3 });
  assert.deepEqual(s.getState().ontologies.selectedSubsets, [subset]);
});

test('remounting the same tree invalidates cached paths and old requests without clearing subsets', () => {
  const s = setup();
  s.dispatch(setSelectedSubsets([subset]));
  s.dispatch(getNodeChildren.pending('children', { ...args(), absoluteIdentity: 'parent' }));
  s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: 'parent', children: [] }, 'children', { ...args(), absoluteIdentity: 'parent' }));
  s.dispatch(getRootEntities.pending('old-roots', args()));
  // Tree -> list/graph -> tree mounts a new request owner with the same signature.
  context(s, scope(), 'first');
  assert.deepEqual(s.getState().ontologies.selectedSubsets, [subset]);
  assert.deepEqual(s.getState().ontologies.nodesWithChildrenLoaded, []);
  assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('old root')] } as any, 'old-roots', args()));
  assert.deepEqual(s.getState().ontologies.rootNodes, []);
});

for (const imports of [true, false]) {
  test(`member counts request the exact batched tree union with candidate filters: imports=${imports}`, async () => {
    const s = setup();
    const urls: URL[] = [];
    const originalFetch = globalThis.fetch;
    globalThis.fetch = (async (input: any) => {
      urls.push(new URL(String(input)));
      return { ok: true, json: async () => ({ elements: [], totalElements: 5, facetFieldsToCounts: {
        treeAncestor: { Country: 3, Area: 3 },
        hierarchicalAncestor: { Country: 2 }, directAncestor: { Country: 2 },
      } }) };
    }) as any;
    try {
      const counts = await s.dispatch(getTreeMemberCounts({ ...args(), entityType: 'individuals',
        selectedSubsets: [subset, 'another-subset'], showImportedEnabled: imports, apiUrl: 'https://widget.test/' })).unwrap();
      assert.deepEqual(counts, { Country: 3, Area: 3 });
      assert.equal(urls.length, 1);
      assert.equal(urls[0].origin, 'https://widget.test');
      const params = urls[0].searchParams;
      assert.equal(params.get('facetFields'), 'treeAncestor');
      assert.equal(params.get('type'), 'individual');
      assert.equal(params.get('subset'), `${subset},another-subset`);
      assert.equal(params.get('includeObsoleteEntities'), 'false');
      assert.equal(params.get('isDefiningOntology'), imports ? null : 'true');
    } finally { globalThis.fetch = originalFetch; }
  });
}

test('an absent tree count facet never falls back to the old approximate facets', async () => {
  const s = setup();
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async () => ({ ok: true, json: async () => ({ elements: [], totalElements: 1,
    facetFieldsToCounts: { directAncestor: { Country: 9 } } }) })) as any;
  try {
    assert.deepEqual(await s.dispatch(getTreeMemberCounts({ ...args(), selectedSubsets: [subset] })).unwrap(), {});
  } finally { globalThis.fetch = originalFetch; }
});

for (const imports of [true, false]) for (const obsolete of [true, false]) {
  test(`subset availability uses the same member filters as counts: imports=${imports}, obsolete=${obsolete}`, async () => {
    const s = setup();
    const originalFetch = globalThis.fetch;
    const urls: URL[] = [];
    globalThis.fetch = (async (input: any) => {
      const url = new URL(String(input));
      urls.push(url);
      const params = url.searchParams;
      // Native members may use an imported subset definition.
      const definitions = [{ iri: subset, label: ['IHCC'], type: ['property'], ontologyId: 'coho', isDefiningOntology: false }];
      const count = (params.get('isDefiningOntology') === 'true' ? 2 : 4) + (params.get('includeObsoleteEntities') === 'true' ? 1 : 0);
      return { ok: true, json: async () => ({ elements: url.pathname.includes('/properties/') ? definitions : [],
        totalElements: 1, totalPages: 1, facetFieldsToCounts: { subset: { [subset]: count }, treeAncestor: { Country: count } } }) };
    }) as any;
    try {
      const input = { ...args(), showImportedEnabled: imports, showObsoleteEnabled: obsolete, apiUrl: 'https://widget.test/' };
      const subsets = await s.dispatch(getSubsets(input)).unwrap();
      const counts = await s.dispatch(getTreeMemberCounts(input)).unwrap();
      const expected = (imports ? 4 : 2) + (obsolete ? 1 : 0);
      assert.deepEqual(subsets, [{ iri: subset, label: 'IHCC', numMembers: expected }]);
      assert.deepEqual(counts, { Country: expected });
      assert.equal(urls.length, 3); // One batched facet each; definition lookup is shared by all subsets.
      for (const url of urls.filter(u => u.pathname.endsWith('/entities'))) {
        assert.equal(url.origin, 'https://widget.test');
        assert.equal(url.searchParams.get('type'), 'class,individual');
        assert.equal(url.searchParams.get('includeObsoleteEntities'), String(obsolete));
        assert.equal(url.searchParams.get('isDefiningOntology'), imports ? null : 'true');
        assert.equal(url.searchParams.get('subset'), null);
        assert.equal(url.searchParams.get('includeImportedEntities'), null);
      }
    } finally { globalThis.fetch = originalFetch; }
  });
}

test('native counts distinguish pending, confirmed zero and failure and reject late import-filter responses', () => {
  const s = setup();
  const native = { ...args('native'), showImportedEnabled: false };
  s.dispatch(getTreeMemberCounts.pending('old', args()));
  context(s, scope(), 'native');
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'idle');
  s.dispatch(getTreeMemberCounts.pending('native', native));
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'loading');
  s.dispatch(getTreeMemberCounts.fulfilled({}, 'native', native));
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'succeeded');
  s.dispatch(getTreeMemberCounts.fulfilled({ stale: 99 }, 'old', args()));
  s.dispatch(getTreeMemberCounts.rejected(new Error('late'), 'old', args()));
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'succeeded');
  assert.deepEqual(s.getState().ontologies.filteredMemberCounts, {});
  s.dispatch(getTreeMemberCounts.pending('retry', native));
  s.dispatch(getTreeMemberCounts.rejected(new Error('failed'), 'retry', native));
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'failed');
  s.dispatch(resetTreeContent());
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'idle');
});
