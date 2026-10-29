import assert from 'node:assert/strict';
import { test } from 'node:test';
import { configureStore } from '@reduxjs/toolkit';
import reducer, { getOntology, getRootEntities, getNodeChildren, getSubsets, getSubsetMemberCounts, getDirectChildrenCount, getDirectChildrenEntities, setSelectedSubsets, resetTreeContent, resetTreeSettings } from '../src/pages/ontologies/ontologiesSlice';
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
    s.dispatch(getSubsetMemberCounts.pending('counts', args()));
    s.dispatch(getSubsetMemberCounts.fulfilled({ root: 77 }, 'counts', args()));
    s.dispatch({ type: 'ontologies_set_tree_scope', payload: destination });
    const state = s.getState().ontologies;
    assert.deepEqual(state.selectedSubsets, []);
    assert.deepEqual(state.subsets, []);
    assert.deepEqual(state.subsetMemberCounts, {});
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
  const thunks = [getRootEntities, getNodeChildren, getSubsets, getSubsetMemberCounts, getDirectChildrenCount, getDirectChildrenEntities];
  thunks.forEach(t => s.dispatch(t.pending('old', args())));
  context(s, scope('efo'), 'efo');
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('old root')] }, 'old', args()));
  s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: 'old', children: [] }, 'old', args()));
  s.dispatch(getSubsets.fulfilled([{ iri: subset, label: 'IHCC', numMembers: 77 }], 'old', args()));
  s.dispatch(getSubsetMemberCounts.fulfilled({ old: 77 }, 'old', args()));
  s.dispatch(getDirectChildrenCount.fulfilled({ entityIri: 'old', totalElements: 77 }, 'old', args()));
  s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('old child')], totalElements: 77 } as any, 'old', args()));
  thunks.forEach(t => s.dispatch(t.rejected(new Error('late'), 'old', args())));
  const state = s.getState().ontologies;
  assert.deepEqual(state.rootNodes, []);
  assert.deepEqual(state.nodeChildren, {});
  assert.deepEqual(state.subsets, []);
  assert.deepEqual(state.subsetMemberCounts, {});
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
    await s.dispatch(getSubsetMemberCounts({ ...args(), selectedSubsets: [subset] }));
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
  s.dispatch(getSubsetMemberCounts.pending('old-counts', args()));
  s.dispatch(getSubsets.pending('old-subsets', args()));
  context(s, scope(), 'french-other-subset');
  s.dispatch(getSubsetMemberCounts.pending('new-counts', args('french-other-subset')));
  s.dispatch(getSubsetMemberCounts.fulfilled({ current: 3 }, 'new-counts', args('french-other-subset')));
  s.dispatch(getSubsetMemberCounts.fulfilled({ stale: 77 }, 'old-counts', args()));
  s.dispatch(getSubsets.fulfilled([], 'old-subsets', args()));
  assert.deepEqual(s.getState().ontologies.subsetMemberCounts, { current: 3 });
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
