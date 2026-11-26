import assert from 'node:assert/strict';
import { test } from 'node:test';
import { configureStore } from '@reduxjs/toolkit';
import reducer, { getAllOntologies, getOntology, getAncestors, getRootEntities, getNodeChildren, getSubsets, getTreeMemberCounts, getDirectChildrenEntities, setSelectedSubsets, resetTreeContent, setTreeScope as initializeTreeScope, treePreferencesScopeKey, hideImported, showObsolete, showSiblings, hideCounts, disablePreferredRoots, openNode, closeNode } from '../src/pages/ontologies/ontologiesSlice';
import Ontology from '../src/model/Ontology';
import { createAppStore, store as appStore } from '../src/app/store';
import Individual from '../src/model/Individual';
import overlapIndividuals from './fixtures/individual-overlap.json';
import { thingFromJsonProperties } from '../src/app/util';

// Initialize the same two ownership scopes used by the mounted component.
const setTreeScope = (scopeKey: string, preferredRoots = false) => {
  const preferencesScopeKey = scopeKey.startsWith('[')
    ? treePreferencesScopeKey(...JSON.parse(scopeKey).slice(0, 2) as [string, string]) : scopeKey;
  return initializeTreeScope({ scopeKey, preferencesScopeKey, preferredRoots });
};
const subset = 'http://www.ebi.ac.uk/coho#IHCC_subset';
const scope = (id = 'coho', type = 'classes', api = '') => JSON.stringify([api, id, type]);
const store = () => configureStore({ reducer: { ontologies: reducer }, middleware: g => g({ serializableCheck: false }) });

test('the ontology catalogue loads all pages behind the API row cap', async () => {
  const s = store();
  const catalogue = Array.from({ length: 117 }, (_, index) => ({ ontologyId: `ontology-${index}` }));
  const requests: [number, number][] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (url: string) => {
    const { pathname, searchParams } = new URL(url);
    assert.equal(pathname, '/api/v2/ontologies');
    const page = Number(searchParams.get('page') ?? 0);
    const requestedSize = Number(searchParams.get('size'));
    const size = Math.min(requestedSize, 50);
    requests.push([page, requestedSize]);
    const elements = catalogue.slice(page * size, (page + 1) * size);
    return new Response(JSON.stringify({
      page,
      elements,
      numElements: elements.length,
      totalElements: catalogue.length,
      totalPages: Math.ceil(catalogue.length / size),
    }));
  }) as typeof fetch;

  try {
    const loading = s.dispatch(getAllOntologies());
    assert.equal(s.getState().ontologies.loadingOntologies, true);
    // Remounting while the catalogue loads should reuse the in-flight load.
    await s.dispatch(getAllOntologies());
    await loading.unwrap();
    assert.deepEqual(
      s.getState().ontologies.ontologies.map(ontology => ontology.getOntologyId()),
      catalogue.map(ontology => ontology.ontologyId),
    );
    assert.deepEqual(requests, [[0, 50], [1, 50], [2, 50]]);
    assert.equal(s.getState().ontologies.loadingOntologies, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

function context(s: any, key: string, requestKey: string) {
  s.dispatch(setTreeScope(key));
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

const preferences = (s: any) => {
  const { preferredRoots, displayImported, displayObsolete, displaySiblings, displayCounts } = s.getState().ontologies;
  return { preferredRoots, displayImported, displayObsolete, displaySiblings, displayCounts };
};

for (const preferredRoots of [true, false]) {
  test(`browse, term navigation and remounts retain display preferences: preferred=${preferredRoots}`, () => {
    const s = store();
    s.dispatch(setTreeScope(scope(), true));
    if (!preferredRoots) s.dispatch(disablePreferredRoots());
    s.dispatch(hideImported());
    s.dispatch(showObsolete());
    s.dispatch(showSiblings());
    s.dispatch(hideCounts());
    s.dispatch(setSelectedSubsets([subset]));
    const expected = { preferredRoots, displayImported: false, displayObsolete: true, displaySiblings: true, displayCounts: false };
    // Same actions as a new EntityTree mount: scope initialization, then fresh
    // content ownership. Include term changes, browse return and back/forward.
    for (const requestKey of ['browse', 'native term', 'imported term', 'browse', 'imported term', 'native term', 'native term']) {
      s.dispatch(setTreeScope(scope(), true));
      s.dispatch({ type: 'ontologies_set_tree_request_context', payload: { scopeKey: scope(), requestKey } });
      assert.deepEqual(preferences(s), expected, requestKey);
      assert.deepEqual(s.getState().ontologies.selectedSubsets, [subset]);
      assert.deepEqual(s.getState().ontologies.rootNodes, []);
      assert.deepEqual(s.getState().ontologies.automaticallyExpandedNodes, []);
      assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
      s.dispatch(getRootEntities.pending('old', args(requestKey)));
      s.dispatch(getRootEntities.fulfilled({ rootTerms: [term(requestKey)] }, 'old', args(requestKey)));
    }
  });
}

for (const destination of [scope('efo'), scope('coho', 'individuals'), scope('coho', 'classes', 'https://other.test/')]) {
  test(`scope change clears selections, options, counts and paths atomically: ${destination}`, () => {
    const s = setup();
    s.dispatch(hideImported());
    s.dispatch(showObsolete());
    s.dispatch(showSiblings());
    s.dispatch(hideCounts());
    s.dispatch(setSelectedSubsets([subset]));
    s.dispatch(getSubsets.pending('subsets', args()));
    s.dispatch(getSubsets.fulfilled([{ iri: subset, label: 'IHCC', numMembers: 77 }], 'subsets', args()));
    s.dispatch(getRootEntities.pending('roots', args()));
    s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('COHO root')] }, 'roots', args()));
    s.dispatch(getTreeMemberCounts.pending('counts', args()));
    s.dispatch(getTreeMemberCounts.fulfilled({ root: 77 }, 'counts', args()));
    s.dispatch(setTreeScope(destination));
    const state = s.getState().ontologies;
    assert.deepEqual(state.selectedSubsets, []);
    assert.deepEqual(state.subsets, []);
    assert.deepEqual(state.filteredMemberCounts, {});
    assert.deepEqual(state.rootNodes, []);
    assert.deepEqual(state.nodeChildren, {});
    assert.equal(state.numPendingTreeRequests, 0);
    assert.deepEqual(preferences(s), destination === scope('coho', 'individuals')
      ? { preferredRoots: false, displayImported: false, displayObsolete: true, displaySiblings: true, displayCounts: false }
      : { preferredRoots: false, displayImported: true, displayObsolete: false, displaySiblings: false, displayCounts: true });
  });
}

test('a new scope initializes preferred roots from its own ontology and resets previous preferences', () => {
  const s = setup();
  s.dispatch(hideImported());
  s.dispatch(setTreeScope(scope('efo'), true));
  assert.deepEqual(preferences(s), { preferredRoots: true, displayImported: true, displayObsolete: false, displaySiblings: false, displayCounts: true });
  s.dispatch(disablePreferredRoots());
  s.dispatch(setTreeScope(scope('efo'), true));
  assert.equal(s.getState().ontologies.preferredRoots, false);
});

for (const [sourceType, destinationType] of [['classes', 'individuals'], ['individuals', 'classes']]) {
  for (const preferredRoots of [false, true]) {
    test(`mixed ${sourceType} to ${destinationType} retains preferences but rejects old type data: preferred=${preferredRoots}`, async () => {
      const s = store();
      const sourceScope = scope('coho', sourceType), destinationScope = scope('coho', destinationType);
      s.dispatch(setTreeScope(sourceScope, preferredRoots));
      context(s, sourceScope, 'source');
      s.dispatch(hideImported()); s.dispatch(hideCounts()); s.dispatch(showObsolete()); s.dispatch(showSiblings());
      s.dispatch(setSelectedSubsets([subset]));
      const expected = preferences(s);
      const old = { ...args('source'), treeScopeKey: sourceScope, entityType: sourceType, entityIri: 'old', absoluteIdentity: 'old' };
      const thunks = [getRootEntities, getNodeChildren, getSubsets, getTreeMemberCounts, getDirectChildrenEntities];
      thunks.forEach(thunk => s.dispatch(thunk.pending('old', old)));
      context(s, destinationScope, 'destination');
      assert.deepEqual(preferences(s), expected);
      assert.deepEqual(s.getState().ontologies.selectedSubsets, []);
      assert.deepEqual(s.getState().ontologies.subsets, []);
      const current = { ...args('destination'), treeScopeKey: destinationScope, entityType: destinationType };
      s.dispatch(getRootEntities.pending('current', current));
      s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('stale')] }, 'old', old));
      s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: 'old', entityIri: 'old', totalElements: 77, children: [] }, 'old', old));
      s.dispatch(getSubsets.fulfilled([{ iri: subset, label: 'stale', numMembers: 77 }], 'old', old));
      s.dispatch(getTreeMemberCounts.fulfilled({ stale: 77 }, 'old', old));
      s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('stale')], totalElements: 77 } as any, 'old', old));
      thunks.forEach(thunk => s.dispatch(thunk.rejected(new Error('late abort'), 'old', old)));
      const state = s.getState().ontologies;
      assert.deepEqual(state.rootNodes, []);
      assert.deepEqual(state.nodeChildren, {});
      assert.deepEqual(state.subsets, []);
      assert.deepEqual(state.filteredMemberCounts, {});
      assert.deepEqual(state.directChildrenTables, {});
      assert.deepEqual(state.directChildrenCounts, {});
      assert.equal(state.numPendingTreeRequests, 1, 'old type completion cannot decrement the new request');
      // An effect captured before the type change is rejected before fetch.
      const rejected = await s.dispatch(getRootEntities(old));
      assert.equal(rejected.meta.condition, true);
      s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('current')] }, 'current', current));
      assert.deepEqual(s.getState().ontologies.rootNodes.map(node => node.title), ['current']);
      assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
    });
  }
}

test('same-scope entity and language changes retain selection but invalidate old requests', () => {
  const s = setup();
  s.dispatch(setSelectedSubsets([subset]));
  s.dispatch(getRootEntities.pending('old', args()));
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
  const thunks = [getRootEntities, getNodeChildren, getSubsets, getTreeMemberCounts, getDirectChildrenEntities];
  thunks.forEach(t => s.dispatch(t.pending('old', args())));
  context(s, scope('efo'), 'efo');
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('old root')] }, 'old', args()));
  s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: 'old', entityIri: 'old', totalElements: 77, children: [] }, 'old', args()));
  s.dispatch(getSubsets.fulfilled([{ iri: subset, label: 'IHCC', numMembers: 77 }], 'old', args()));
  s.dispatch(getTreeMemberCounts.fulfilled({ old: 77 }, 'old', args()));
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

test('cancelling an old child request aborts its in-flight pages without touching the new context', { timeout: 3000 }, async () => {
  const s = setup();
  const input = { ...args(), entityTypePlural: 'classes', entityIri: 'parent', absoluteIdentity: 'parent' };
  const signals: AbortSignal[] = [];
  let signalPagesStarted!: () => void;
  const pagesStarted = new Promise<void>(resolve => { signalPagesStarted = resolve; });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = ((url: string, init: RequestInit) => {
    const page = new URL(url).searchParams.get('page');
    if (!page) {
      return Promise.resolve({ ok: true, json: async () => ({
        elements: [{ iri: 'old-child', type: ['class'], label: ['Old child'] }],
        totalElements: 4, totalPages: 4,
      }) });
    }
    const signal = init.signal!;
    signals.push(signal);
    if (signals.length === 3) signalPagesStarted();
    return new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
    });
  }) as any;
  try {
    const pending = s.dispatch(getNodeChildren(input));
    await pagesStarted;
    context(s, scope(), 'replacement');
    const current = args('replacement');
    s.dispatch(getRootEntities.pending('new-roots', current));
    pending.abort();
    await pending;
    assert(signals.every(signal => signal.aborted));
    assert.deepEqual(s.getState().ontologies.treeRequestErrors, {});
    assert.deepEqual(s.getState().ontologies.nodeChildren, {});
    assert.equal(s.getState().ontologies.numPendingTreeRequests, 1);
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

test('collapsing a branch retains its children for reopening in the same tree context', async () => {
  const s = setup();
  const rootArg = args();
  s.dispatch(getRootEntities.pending('roots', rootArg));
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('parent')] }, 'roots', rootArg));
  const root = s.getState().ontologies.rootNodes[0];
  const input = { ...rootArg, entityTypePlural: 'classes', entityIri: root.iri, absoluteIdentity: root.absoluteIdentity };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async () => ({ ok: true, json: async () => ({
    elements: [{ iri: 'child', type: ['class'], label: ['Child'] }], totalElements: 1, totalPages: 1,
  }) })) as any;
  try {
    s.dispatch(openNode(root));
    await s.dispatch(getNodeChildren(input)).unwrap();
    s.dispatch(closeNode(root));
    assert.deepEqual(s.getState().ontologies.manuallyExpandedNodes, []);
    s.dispatch(openNode(root));
    const state = s.getState().ontologies;
    assert.deepEqual(state.manuallyExpandedNodes, [root.absoluteIdentity]);
    assert.deepEqual(state.nodesWithChildrenLoaded, [root.absoluteIdentity]);
    assert.deepEqual(state.nodeChildren[root.absoluteIdentity].map(node => node.iri), ['child']);
    assert.equal(state.directChildrenCounts[root.iri], 1);
  } finally { globalThis.fetch = originalFetch; }
});

test('remounting the same tree invalidates cached paths and old requests without clearing subsets', () => {
  const s = setup();
  s.dispatch(setSelectedSubsets([subset]));
  s.dispatch(getNodeChildren.pending('children', { ...args(), absoluteIdentity: 'parent' }));
  s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: 'parent', entityIri: 'parent', totalElements: 0, children: [] }, 'children', { ...args(), absoluteIdentity: 'parent' }));
  s.dispatch(getRootEntities.pending('old-roots', args()));
  // Tree -> list/graph -> tree mounts a new request owner with the same signature.
  context(s, scope(), 'first');
  assert.deepEqual(s.getState().ontologies.selectedSubsets, [subset]);
  assert.deepEqual(s.getState().ontologies.nodesWithChildrenLoaded, []);
  assert.equal(s.getState().ontologies.numPendingTreeRequests, 0);
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [term('old root')] } as any, 'old-roots', args()));
  assert.deepEqual(s.getState().ontologies.rootNodes, []);
});

test('starting another selected path reuses completed counts for the same filters', () => {
  const s = setup();
  const resultKey = 'count-filters';
  const countArgs = { ...args(), resultKey };
  s.dispatch(getTreeMemberCounts.pending('counts', countArgs));
  s.dispatch(getTreeMemberCounts.fulfilled({ parent: 7 }, 'counts', countArgs));
  const startPath = (requestKey: string, memberCountsResultKey: string) => {
    s.dispatch({ type: 'ontologies_set_tree_request_context',
      payload: { scopeKey: scope(), requestKey, memberCountsResultKey } });
  };
  startPath('new-selection', resultKey);
  assert.deepEqual(s.getState().ontologies.filteredMemberCounts, { parent: 7 });
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'succeeded');
  startPath('new-filters', 'different-count-filters');
  assert.deepEqual(s.getState().ontologies.filteredMemberCounts, {});
  assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'idle');
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

test('ontologies without subset definitions skip the subset count facet', async () => {
  const s = setup();
  const requests: URL[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: string) => {
    requests.push(new URL(input));
    return { ok: true, json: async () => ({ elements: [], totalElements: 0, totalPages: 0 }) };
  }) as any;
  try {
    assert.deepEqual(await s.dispatch(getSubsets(args())).unwrap(), []);
    assert.equal(requests.length, 1);
    assert(requests[0].pathname.endsWith('/children'));
    assert.equal(requests[0].searchParams.get('view'), 'tree');
    assert.equal(requests[0].searchParams.has('facetFields'), false);
  } finally { globalThis.fetch = originalFetch; }
});

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

// Snapshot generated by the packaged rdf2json executable from
// dataload/rdf2json/src/test/resources/relations/individual-overlap.{ttl,json}.
for (const individual of overlapIndividuals) {
  test(`generated overlap metadata survives the frontend model: ${individual.iri}`, () => {
    const entity = new Individual(individual);
    const metadata = entity.getHierarchicalParentReificationAxioms('https://example.org/relations#Target');
    assert.deepEqual(metadata.childRelationToParent, ['https://example.org/relations#p', 'https://example.org/relations#q']);
    assert.deepEqual(metadata.parentRelationToChild, ['https://example.org/relations#pInverse', 'https://example.org/relations#qInverse']);
  });
}

test('generated overlapping individual parents keep the relation in the actual child thunk', async () => {
  const s = setup();
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: any) => ({ ok: true, json: async () => ({
    elements: String(input).includes('/treeChildren') ? overlapIndividuals : [],
    totalElements: String(input).includes('/treeChildren') ? overlapIndividuals.length : 0,
    totalPages: 1,
  }) })) as any;
  try {
    const result = await s.dispatch(getNodeChildren({ ...args(), entityTypePlural: 'classes',
      entityIri: 'https://example.org/relations#Target', absoluteIdentity: 'Target' })).unwrap();
    assert.equal(result.children.length, 2);
    for (const child of result.children) {
      assert.equal(child.childRelationToParent, 'https://example.org/relations#p');
      assert.equal(child.parentRelationToChild, 'https://example.org/relations#pInverse');
    }
  } finally { globalThis.fetch = originalFetch; }
});

for (const entityType of ['classes', 'individuals', 'properties']) {
  test(`native tree roots and every expanded child route opt in together: ${entityType}`, async () => {
    const s = setup();
    const originalFetch = globalThis.fetch;
    const urls: URL[] = [];
    globalThis.fetch = (async (input: any) => {
      urls.push(new URL(String(input)));
      return { ok: true, json: async () => ({ elements: [], totalElements: 0, totalPages: 1 }) };
    }) as any;
    try {
      await s.dispatch(getRootEntities({ ...args(), entityType, showImportedEnabled: false, subsetTree: [subset] })).unwrap();
      await s.dispatch(getNodeChildren({ ...args(), entityTypePlural: entityType, entityIri: 'parent',
        absoluteIdentity: 'parent', includeImportedEntities: false, includeObsoleteEntities: false,
        subsetTree: [subset] })).unwrap();
      assert.equal(urls.length, entityType === 'classes' ? 2 : entityType === 'individuals' ? 3 : 2);
      for (const url of urls) {
        assert.equal(url.searchParams.get('treeContext'), 'true');
        assert.equal(url.searchParams.get('subsetTree'), subset);
        assert.equal(url.searchParams.get('includeObsoleteEntities'), 'false');
      }
      urls.length = 0;
      await s.dispatch(getRootEntities({ ...args(), entityType, showImportedEnabled: true })).unwrap();
      assert(urls.every(url => url.searchParams.get('treeContext') === 'true' && !url.searchParams.has('isDefiningOntology')));
    } finally { globalThis.fetch = originalFetch; }
  });
}

test('native inline and table child requests use the same connector context', async () => {
  const s = setup(); const originalFetch = globalThis.fetch; const urls: URL[] = [];
  globalThis.fetch = (async (input: any) => {
    urls.push(new URL(String(input)));
    return { ok: true, json: async () => ({ elements: [], totalElements: 0, totalPages: 1 }) };
  }) as any;
  try {
    const input = { ...args(), entityIri: 'R', showImportedEnabled: false, subsetTree: [subset], page: 0, size: 10 };
    await s.dispatch(getNodeChildren({ ...input, entityTypePlural: 'classes', absoluteIdentity: 'R',
      includeImportedEntities: false, includeObsoleteEntities: false })).unwrap();
    await s.dispatch(getDirectChildrenEntities(input)).unwrap();
    assert.equal(urls.length, 2);
    for (const url of urls) {
      assert.equal(url.searchParams.get('treeContext'), 'true');
      assert.equal(url.searchParams.get('subsetTree'), subset);
      assert.equal(url.searchParams.get('includeImportedEntities'), 'false');
    }
    assert.equal(urls[0].searchParams.get('maxInlineChildren'), '1000');
    assert.equal(urls[1].searchParams.get('maxInlineChildren'), null);
  } finally { globalThis.fetch = originalFetch; }
});

for (const imports of [true, false]) {
  test(`ordinary active-member badges exclude connector context: imports=${imports}`, async () => {
    const s = setup();
    const originalFetch = globalThis.fetch;
    const urls: URL[] = [];
    const expected = imports ? { R: 5, D: 2, K: 2 } : { R: 3, D: 1, K: 2 };
    globalThis.fetch = (async (input: any) => {
      urls.push(new URL(String(input)));
      return { ok: true, json: async () => ({ elements: [], facetFieldsToCounts: { treeAncestor: expected } }) };
    }) as any;
    try {
      assert.deepEqual(await s.dispatch(getTreeMemberCounts({ ...args(), ontologyId: 'native',
        showImportedEnabled: imports, showObsoleteEnabled: false })).unwrap(), expected);
      assert.equal(urls.length, 1);
      const params = urls[0].searchParams;
      assert.equal(params.get('includeObsoleteEntities'), 'false');
      assert.equal(params.get('isDefiningOntology'), imports ? null : 'true');
      assert.equal(params.get('subset'), null);
      assert.equal(params.get('treeContext'), null);
      assert.equal(params.get('type'), 'class,individual');
      assert.equal(params.get('facetFields'), 'treeAncestor');
    } finally { globalThis.fetch = originalFetch; }
  });
}

test('root browsing reads additional pages when the server caps the compact page size', async () => {
  const s = setup();
  const pages: number[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: string) => {
    const url = new URL(input);
    const page = Number(url.searchParams.get('page') || 0);
    pages.push(page);
    const iri = 'root-' + page;
    return { ok: true, json: async () => ({
      elements: [{ iri, label: [iri], type: ['class'] }], totalElements: 3, totalPages: 3,
    }) };
  }) as any;
  try {
    await s.dispatch(getRootEntities(args())).unwrap();
    assert.deepEqual(pages, [0, 1, 2]);
    assert.deepEqual(s.getState().ontologies.rootNodes.map(node => node.iri), ['root-0', 'root-1', 'root-2']);
  } finally { globalThis.fetch = originalFetch; }
});

test('exactly 1,000 children remain inline when the server caps the page size', async () => {
  const s = setup();
  const pageSize = 200;
  const total = 1000;
  const calls: URL[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: string) => {
    const url = new URL(input);
    calls.push(url);
    const page = Number(url.searchParams.get('page') || 0);
    const elements = Array.from({ length: pageSize }, (_, index) => {
      const iri = 'child-' + (page * pageSize + index);
      return { iri, label: [iri], type: ['class'] };
    });
    return { ok: true, json: async () => ({ elements, totalElements: total, totalPages: 5 }) };
  }) as any;
  try {
    const branch = await s.dispatch(getNodeChildren({ ...args(), entityTypePlural: 'classes',
      entityIri: 'parent', absoluteIdentity: 'parent' })).unwrap();
    assert.equal(branch.totalElements, total);
    assert.deepEqual(branch.children.map(child => child.iri),
      Array.from({ length: total }, (_, index) => 'child-' + index));
    assert.deepEqual(calls.map(url => Number(url.searchParams.get('page') || 0)), [0, 1, 2, 3, 4]);
    assert(calls.every(url => url.searchParams.get('size') === '1000' &&
      url.searchParams.get('maxInlineChildren') === '1000'));
  } finally { globalThis.fetch = originalFetch; }
});

test('wide Classes expansions and late table pages use the same combined endpoint', async () => {
  const s = setup(); const originalFetch = globalThis.fetch; const urls: URL[] = [];
  globalThis.fetch = (async (input: any) => {
    const url = new URL(String(input)); urls.push(url);
    const combined = url.pathname.endsWith('/treeChildren');
    return { ok: true, json: async () => ({ elements: combined ? [{ iri: 'child1001', label: ['late child'], type: ['individual'], ontologyId: 'coho' }] : [], totalElements: combined ? 2241 : 0, totalPages: 225 }) };
  }) as any;
  try {
    const input = { ...args(), entityIri: 'cohort', absoluteIdentity: 'cohort', subsetTree: [subset], showImportedEnabled: false };
    const children = await s.dispatch(getNodeChildren({ ...input, entityTypePlural: 'classes' })).unwrap();
    const page = await s.dispatch(getDirectChildrenEntities({ ...input, page: 100, size: 10, search: 'late' })).unwrap();
    assert.equal(s.getState().ontologies.directChildrenCounts.cohort, 2241);
    // Older servers may still send an inline page. Wide branches discard it.
    assert.deepEqual(children.children, []);
    assert.equal(page.elements[0].getIri(), 'child1001');
    assert.equal(urls.length, 2);
    assert(urls.every(url => url.pathname.endsWith('/treeChildren') && url.searchParams.get('subsetTree') === subset));
    assert.equal(urls[0].searchParams.get('maxInlineChildren'), '1000');
    assert.equal(urls[1].searchParams.get('page'), '100');
    assert.equal(urls[1].searchParams.get('searchQuery'), 'late');
    assert.equal(urls[1].searchParams.get('maxInlineChildren'), null);
  } finally { globalThis.fetch = originalFetch; }
});

test('wide Properties expansions get an exact total and fetch rows only for the table', async () => {
  const s = setup();
  const calls: URL[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: string) => {
    const url = new URL(input);
    calls.push(url);
    const compact = url.searchParams.has('maxInlineChildren');
    const elements = compact ? [] : [{ iri: 'child-property', label: ['Child property'], type: ['property'] }];
    return { ok: true, json: async () => ({ elements, totalElements: 1001, totalPages: 101 }) };
  }) as any;
  try {
    const input = { ...args(), entityType: 'properties', entityTypePlural: 'properties',
      entityIri: 'parent', absoluteIdentity: 'parent' };
    const branch = await s.dispatch(getNodeChildren(input)).unwrap();
    assert.equal(branch.totalElements, 1001);
    assert.deepEqual(branch.children, []);
    assert.equal(s.getState().ontologies.directChildrenCounts.parent, 1001);
    const page = await s.dispatch(getDirectChildrenEntities({ ...input, page: 0, size: 10 })).unwrap();
    assert.deepEqual(page.elements.map(entity => entity.getIri()), ['child-property']);
    assert.equal(calls.length, 2);
    assert(calls.every(url => url.pathname.endsWith('/properties/parent/children')));
    assert.equal(calls[0].searchParams.get('maxInlineChildren'), '1000');
    assert.equal(calls[1].searchParams.has('maxInlineChildren'), false);
  } finally { globalThis.fetch = originalFetch; }
});

test('two expanded tables retain independent rows while same-table stale responses are ignored', () => {
  const s = setup();
  const a = { ...args(), entityIri: 'A', tableKey: 'root;A', page: 100, size: 10 };
  const b = { ...args(), entityIri: 'B', tableKey: 'root;B', page: 0, size: 10, search: 'needle' };
  s.dispatch(getDirectChildrenEntities.pending('a-old', a));
  s.dispatch(getDirectChildrenEntities.pending('b', b));
  s.dispatch(getDirectChildrenEntities.pending('a-new', { ...a, page: 101 }));
  s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('B needle')], totalElements: 1 } as any, 'b', b));
  s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('A 1011')], totalElements: 2241 } as any, 'a-new', a));
  s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('stale')], totalElements: 2 } as any, 'a-old', a));
  const tables = (s.getState().ontologies as any).directChildrenTables;
  assert.equal(tables['root;A'].elements[0].getIri(), 'A 1011');
  assert.equal(tables['root;A'].totalElements, 2241);
  assert.equal(tables['root;B'].elements[0].getIri(), 'B needle');
  assert.equal(tables['root;B'].totalElements, 1);
});


for (const thunk of [getAncestors, getRootEntities, getTreeMemberCounts, getSubsets, getNodeChildren, getDirectChildrenEntities]) {
  test(`request failure, retry, cancellation and stale rejection ownership: ${thunk.typePrefix}`, () => {
    const s = setup();
    const arg = { ...args(), entityIri: 'parent', absoluteIdentity: 'parent', tableKey: 'parent' };
    const slot = thunk.typePrefix + ([getNodeChildren, getDirectChildrenEntities].includes(thunk as any) ? ':parent' : '');
    s.dispatch(thunk.pending('first', arg));
    s.dispatch(thunk.rejected(new Error('offline'), 'first', arg));
    assert.equal(s.getState().ontologies.treeRequestErrors[slot], true);
    s.dispatch(thunk.pending('retry', arg));
    assert.equal(s.getState().ontologies.treeRequestErrors[slot], undefined);
    s.dispatch(thunk.rejected(new Error('late'), 'first', arg));
    assert.equal(s.getState().ontologies.treeRequestErrors[slot], undefined);
    s.dispatch(thunk.rejected({ name: 'AbortError', message: 'Aborted' }, 'retry', arg));
    assert.deepEqual(s.getState().ontologies.treeRequestErrors, {});
    if (thunk === getTreeMemberCounts) assert.equal(s.getState().ontologies.filteredMemberCountsStatus, 'idle');
    s.dispatch(thunk.pending('context-old', arg));
    context(s, scope(), 'new-settings');
    s.dispatch(thunk.rejected(new Error('old settings'), 'context-old', arg));
    assert.deepEqual(s.getState().ontologies.treeRequestErrors, {});
    context(s, scope(), 'first');
    s.dispatch(thunk.pending('old', arg));
    context(s, scope('efo'), 'efo');
    s.dispatch(thunk.rejected(new Error('old scope'), 'old', arg));
    assert.deepEqual(s.getState().ontologies.treeRequestErrors, {});
  });
}

test('a successful empty root response is distinct from failure and cancellation', () => {
  const s = setup();
  s.dispatch(getRootEntities.pending('roots', args()));
  assert.equal(s.getState().ontologies.rootStatus, 'loading');
  s.dispatch(getRootEntities.rejected(new Error('offline'), 'roots', args()));
  assert.equal(s.getState().ontologies.rootStatus, 'failed');
  s.dispatch(getRootEntities.pending('retry', args()));
  s.dispatch(getRootEntities.fulfilled({ rootTerms: [] }, 'retry', args()));
  assert.equal(s.getState().ontologies.rootStatus, 'succeeded');
  assert.deepEqual(s.getState().ontologies.rootNodes, []);
});


test('per-parent table failures and retries leave the sibling table intact', () => {
  const s = setup();
  const a = { ...args(), entityIri: 'a', tableKey: 'a', page: 2, search: 'needle' };
  const b = { ...args(), entityIri: 'b', tableKey: 'b' };
  s.dispatch(getDirectChildrenEntities.pending('b', b));
  s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('sibling')], totalElements: 1001 } as any, 'b', b));
  const sibling = s.getState().ontologies.directChildrenTables.b;
  s.dispatch(getDirectChildrenEntities.pending('a', a));
  s.dispatch(getDirectChildrenEntities.rejected(new Error('offline'), 'a', a));
  assert.equal(s.getState().ontologies.treeRequestErrors['ontologies_direct_children_entities:a'], true);
  assert.equal(s.getState().ontologies.directChildrenTables.b, sibling);
  s.dispatch(getDirectChildrenEntities.pending('a-retry', a));
  s.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('recovered')], totalElements: 1100 } as any, 'a-retry', a));
  assert.equal(s.getState().ontologies.treeRequestErrors['ontologies_direct_children_entities:a'], undefined);
  assert.equal(s.getState().ontologies.directChildrenTables.b, sibling);
});


test('actual app-store factory isolates simultaneous widget requests, settings and discarded mounts', () => {
  const left = createAppStore(), right = createAppStore(), replacement = createAppStore();
  const appBefore = appStore.getState();
  // Identical scope and request IDs must still belong to separate widget stores.
  for (const s of [left, right, replacement]) context(s, scope(), 'first');
  const rightBefore = right.getState();
  const replacementBefore = replacement.getState();
  left.dispatch(setSelectedSubsets([subset]));
  left.dispatch(getRootEntities.pending('same-id', args()));
  right.dispatch(getRootEntities.pending('same-id', args()));
  right.dispatch(getRootEntities.fulfilled({ rootTerms: [term('right')] }, 'same-id', args()));
  const rightSettled = right.getState();
  // A response into the old mount cannot affect a new mount or a sibling.
  left.dispatch(getRootEntities.fulfilled({ rootTerms: [term('discarded-left')] }, 'same-id', args()));
  left.dispatch(getTreeMemberCounts.pending('counts', args()));
  left.dispatch(getTreeMemberCounts.fulfilled({ root: 77 }, 'counts', args()));
  const tableArg = { ...args(), entityIri: 'parent', tableKey: 'parent' };
  left.dispatch(getDirectChildrenEntities.pending('table', tableArg));
  left.dispatch(getDirectChildrenEntities.fulfilled({ elements: [term('late-row')], totalElements: 2241 } as any, 'table', tableArg));
  assert.notStrictEqual(rightBefore, rightSettled);
  assert.strictEqual(right.getState(), rightSettled);
  assert.strictEqual(replacement.getState(), replacementBefore);
  assert.strictEqual(appStore.getState(), appBefore);
  assert.deepEqual(right.getState().ontologies.selectedSubsets, []);
  assert.deepEqual(left.getState().ontologies.selectedSubsets, [subset]);
  assert.deepEqual(right.getState().ontologies.filteredMemberCounts, {});
  assert.deepEqual(right.getState().ontologies.directChildrenTables, {});
  assert.equal(left.getState().ontologies.directChildrenTables.parent.totalElements, 2241);
});

// Final review: visibility counts must not erase user-selected subset identity.
test('zero-count subset definitions stay selected across visibility changes and retries', async () => {
  const s = setup(); s.dispatch(setSelectedSubsets([subset, 'S2']));
  const original = globalThis.fetch; let fail = false;
  globalThis.fetch = (async (input: any) => {
    if (fail) throw new Error('facet unavailable');
    const u = new URL(String(input));
    const definitions = [{iri: subset, label: ['IHCC'], type: ['property']}, {iri: 'S2', label: ['Obsolete only'], type: ['property']}];
    return {ok: true, json: async () => ({elements: u.pathname.endsWith('/children') ? definitions : [],
      totalPages: 1, facetFieldsToCounts: {subset: {}}})};
  }) as any;
  try {
    const options = await s.dispatch(getSubsets({...args(), showImportedEnabled: false, showObsoleteEnabled: false})).unwrap();
    assert.deepEqual(options.map(o => [o.label, o.numMembers]), [['IHCC', 0], ['Obsolete only', 0]]);
    assert.deepEqual(s.getState().ontologies.selectedSubsets, [subset, 'S2']);
    fail = true; await s.dispatch(getSubsets(args()));
    assert.deepEqual(s.getState().ontologies.selectedSubsets, [subset, 'S2']);
  } finally {globalThis.fetch = original;}
});

test('wide Individuals expansions and table pages use only individual members', async () => {
  const s = setup(); const original = globalThis.fetch; const urls: URL[] = [];
  globalThis.fetch = (async (input: any) => {
    const u = new URL(String(input)); urls.push(u);
    const combined = u.pathname.endsWith('/treeChildren') && u.searchParams.get('memberType') === 'individual';
    const rows = combined ? [{iri: 'item1001', label: ['item1001'], type: ['individual'], hierarchicalParent: ['P']}] : [];
    return {ok: true, json: async () => ({elements: rows, totalElements: combined ? 1001 : 0, totalPages: combined ? 101 : 0})};
  }) as any;
  try {
    const input = {...args(), entityType: 'individuals', entityTypePlural: 'individuals', entityIri: 'P', absoluteIdentity: 'P', page: 100, size: 10};
    const branch = await s.dispatch(getNodeChildren(input)).unwrap();
    assert.equal(branch.totalElements, 1001);
    assert.deepEqual(branch.children, []);
    assert.equal(s.getState().ontologies.directChildrenCounts.P, 1001);
    const page = await s.dispatch(getDirectChildrenEntities(input)).unwrap();
    assert.deepEqual(page.elements.map(e => e.getIri()), ['item1001']);
    assert.equal(urls.length, 2);
    assert(urls.every(url => url.searchParams.get('memberType') === 'individual'));
    assert.equal(urls[0].searchParams.get('maxInlineChildren'), '1000');
    assert.equal(urls[1].searchParams.get('maxInlineChildren'), null);
  } finally {globalThis.fetch = original;}
});

test('selected mixed ancestry uses complete backend closure and exhausts its pages', async () => {
  const s = setup(); const original = globalThis.fetch; const urls: URL[] = [];
  const names = ['P','C','I','J','K'];
  globalThis.fetch = (async (input: any) => {
    const u = new URL(String(input)); urls.push(u);
    const full = u.pathname.endsWith('/treeAncestors');
    const page = Number(u.searchParams.get('page') || 0);
    const rows = full ? names.slice(page * 2, page * 2 + 2).map(iri => ({iri, label:[iri], type:[['I','J'].includes(iri)?'individual':'class']})) : [];
    return {ok:true,json:async()=>({elements:rows,totalElements:full?5:0,totalPages:full?3:1})};
  }) as any;
  try {
    const result = await s.dispatch(getAncestors({...args(), entityIri:'L', selectedEntity:term('L')})).unwrap();
    assert.deepEqual(result.map(e=>e.getIri()), ['L',...names]);
    assert.equal(urls.length, 3);
  } finally {globalThis.fetch = original;}
});

test('incomplete definition paging cannot clear selections; only successful removed definitions can', async () => {
  const s=setup();s.dispatch(setSelectedSubsets([subset,'late definition']));const original=globalThis.fetch;
  let fail=true,removed=false;
  globalThis.fetch=(async(input:any)=>{
    const u=new URL(String(input));
    if(!u.pathname.endsWith('/children'))return {ok:true,json:async()=>({facetFieldsToCounts:{subset:{}}})};
    const second=u.searchParams.get('page')==='1';if(second&&fail)throw Error('definition page failed');
    return {ok:true,json:async()=>({elements:[{iri:second?'late definition':subset,type:['property'],label:[second?'Late':'IHCC']}],totalPages:removed?1:2})};
  }) as any;
  try {
    await s.dispatch(getSubsets(args()));assert.deepEqual(s.getState().ontologies.selectedSubsets,[subset,'late definition']);
    fail=false;await s.dispatch(getSubsets(args())).unwrap();assert.deepEqual(s.getState().ontologies.selectedSubsets,[subset,'late definition']);
    removed=true;await s.dispatch(getSubsets(args())).unwrap();assert.deepEqual(s.getState().ontologies.selectedSubsets,[subset]);
  } finally {globalThis.fetch=original;}
});

test('wide child tables preserve 100-row pages when the API caps requests at 50', async () => {
  const store = setup();
  const originalFetch = globalThis.fetch;
  const totalElements = 241;
  globalThis.fetch = (async (input: string) => {
    const params = new URL(input).searchParams;
    const page = Number(params.get('page'));
    const size = Math.min(Number(params.get('size')), 50);
    const start = page * size;
    const end = Math.min(start + size, totalElements);
    const elements = Array.from({ length: Math.max(0, end - start) }, (_, i) => ({
      iri: 'child-' + (start + i), type: ['individual'],
    }));
    return { ok: true, json: async () => ({
      page, elements, totalElements, numElements: elements.length, totalPages: Math.ceil(totalElements / size),
    }) };
  }) as any;
  try {
    for (const page of [0, 1, 2]) {
      const result = await store.dispatch(getDirectChildrenEntities({
        ...args(), entityType: 'individuals', entityIri: 'cohort', page, size: 100,
      })).unwrap();
      assert.equal(result.page, page);
      assert.equal(result.totalPages, 3);
      assert.equal(result.totalElements, totalElements);
      assert.deepEqual(result.elements.map(entity => entity.getIri()),
        Array.from({ length: page < 2 ? 100 : 41 }, (_, index) => 'child-' + (page * 100 + index)));
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});
