import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Provider } from 'react-redux';
import { StaticRouter } from 'react-router-dom/server';
import { configureStore } from '@reduxjs/toolkit';
import reducer, {
  getOntology, getAncestors, getNodeChildren, getRootEntities, getDirectChildrenCount,
  setTreeScope, setTreeRequestContext, resetTreeContent, hideImported,
  enablePreferredRoots, showSiblings, closeNode, openNode,
} from '../src/pages/ontologies/ontologiesSlice';
import EntityTree from '../src/pages/ontologies/entities/EntityTree';
import Ontology from '../src/model/Ontology';
import { thingFromJsonProperties } from '../src/app/util';
import efoPath from './fixtures/efoAncestry.json';

// Minimal tree fields from the wwwdev EFO_0006784 response, 28 October 2026.
const rootIri = 'http://www.ebi.ac.uk/efo/EFO_0000001';
const connectorIri = 'http://purl.obolibrary.org/obo/IAO_0000030';
const measurementIri = 'http://www.ebi.ac.uk/efo/EFO_0001444';
const selectedIri = 'http://www.ebi.ac.uk/efo/EFO_0006784';
const ontology = new Ontology({ ontologyId: 'efo', type: ['ontology'], preferredRoot: [rootIri] });
const scopeKey = JSON.stringify(['', 'efo', 'classes']);
const connectorId = `${rootIri};${connectorIri}`;
const greyConnectorLink = /<a [^>]*class="link-default imported-tree-link">information entity<\/a>/;

function setup({ imports = false, preferred = false, siblings = false, selected = selectedIri, specifiedRootIri = undefined as string | undefined } = {}) {
  const s = configureStore({ reducer: { ontologies: reducer }, middleware: g => g({ serializableCheck: false }) });
  const selectedEntity = selected ? thingFromJsonProperties(efoPath.find(e => e.iri === selected)!) : undefined;
  const requestKey = JSON.stringify([scopeKey, 'en', selectedEntity, specifiedRootIri || '', preferred, false, imports, siblings, []]);
  s.dispatch(getOntology.fulfilled(ontology, 'ontology', { ontologyId: 'efo', lang: 'en' }));
  s.dispatch(setTreeScope(scopeKey));
  if (!imports) s.dispatch(hideImported());
  if (preferred) s.dispatch(enablePreferredRoots());
  if (siblings) s.dispatch(showSiblings());
  s.dispatch(setTreeRequestContext({ scopeKey, requestKey }));
  const args = { ontology, ontologyId: 'efo', entityType: 'classes', selectedEntity, entityIri: selected || specifiedRootIri,
    specifiedRootIri, preferredRoots: preferred, lang: 'en', showObsoleteEnabled: false, showImportedEnabled: imports,
    treeScopeKey: scopeKey, requestKey };
  const childArgs = (absoluteIdentity = rootIri) => ({ ...args, absoluteIdentity, entityIri: absoluteIdentity.split(';').pop(),
    entityTypePlural: 'classes', includeImportedEntities: imports, includeObsoleteEntities: false });
  const render = () => renderToStaticMarkup(<Provider store={s}><StaticRouter location="/"><EntityTree ontology={ontology} entityType="classes"
    lang="en" selectedEntity={selectedEntity} specifiedRootIri={specifiedRootIri}
    onNavigateToEntity={() => {}} onNavigateToOntology={() => {}} /></StaticRouter></Provider>);
  return { s, args, childArgs, render };
}

function page(elements: any[]) {
  return { ok: true, json: async () => ({ elements, totalElements: elements.length, totalPages: 1 }) };
}

// Model the public API's import filtering at the fetch boundary; exercise the
// actual thunk, model conversion, reducers and component above it.
async function withApi(run: (urls: URL[]) => Promise<void>) {
  const original = globalThis.fetch;
  const urls: URL[] = [];
  globalThis.fetch = (async (input: any) => {
    const url = new URL(String(input));
    urls.push(url);
    const path = url.pathname;
    const includesImports = path.endsWith('/treeAncestors') || url.searchParams.get('includeImportedEntities') === 'true';
    const iri = decodeURIComponent(decodeURIComponent(path.split('/').at(-2)!));
    if (path.endsWith('/treeAncestors')) {
      const wanted = iri === connectorIri ? [rootIri] : efoPath.filter(e => e.iri !== iri).map(e => e.iri);
      return page(efoPath.filter(e => wanted.includes(e.iri) && (includesImports || e.isDefiningOntology)));
    }
    if (path.endsWith('/treeChildren') || path.endsWith('/hierarchicalChildren') || path.endsWith('/children')) {
      const optional = [true, false].map(native => ({ iri: native ? 'native sibling' : 'imported sibling',
        label: [native ? 'native sibling' : 'imported sibling'], ontologyId: 'efo', type: ['class'],
        isDefiningOntology: native, hierarchicalParent: [iri] }));
      return page([...efoPath.filter(e => e.hierarchicalParent.includes(iri)), ...optional]
        .filter(e => includesImports || e.isDefiningOntology));
    }
    if (path.endsWith('/individuals')) return page([]);
    if (path.endsWith('/classes')) {
      const includesImportedRoots = url.searchParams.get('isDefiningOntology') !== 'true';
      return page([efoPath.find(e => e.iri === rootIri)!, ...(includesImportedRoots ? [{
        iri: 'imported root', label: ['imported root'], type: ['class'], ontologyId: 'efo', isDefiningOntology: false,
      }] : [])]);
    }
    if (path.includes('/entities/')) return { ok: true, json: async () => efoPath.find(e => e.iri === decodeURIComponent(decodeURIComponent(path.split('/').at(-1)!))) };
    throw new Error(`Unexpected test URL: ${url}`);
  }) as any;
  try { await run(urls); } finally { globalThis.fetch = original; }
}

for (const imports of [false, true]) for (const preferred of [false, true]) for (const siblings of [false, true]) {
  test(`EFO selected path stays connected: imports=${imports}, preferred=${preferred}, siblings=${siblings}`, async () => {
    await withApi(async urls => {
      const { s, args, childArgs, render } = setup({ imports, preferred, siblings });
      await s.dispatch(getAncestors(args)).unwrap();
      assert.deepEqual(s.getState().ontologies.rootNodes.map(n => n.title), ['experimental factor']);
      assert.deepEqual(s.getState().ontologies.nodeChildren[rootIri].map(n => n.iri), [connectorIri]);
      assert.deepEqual(s.getState().ontologies.nodeChildren[connectorId].map(n => n.iri), [measurementIri]);
      if (siblings) {
        for (const id of s.getState().ontologies.automaticallyExpandedNodes) {
          await s.dispatch(getNodeChildren(childArgs(id))).unwrap();
        }
        const children = s.getState().ontologies.nodeChildren[rootIri];
        assert.equal(children.filter(n => n.iri === connectorIri).length, 1);
        assert.equal(children.some(n => n.title === 'imported sibling'), imports);
        assert(children.some(n => n.title === 'native sibling'));
      }
      const html = render();
      assert(html.includes('body height at birth'));
      assert(html.includes('information entity'));
      assert.equal(greyConnectorLink.test(html), !imports);
      assert(!html.includes('(imported ancestry context)'));
      if (!imports) assert(!html.includes('imported sibling'));
      assert.equal(urls[0].searchParams.get('includeImportedEntities'), null);
      assert(urls.slice(1).every(u => u.searchParams.get('includeImportedEntities') === String(imports)));
    });
  });
}

test('a delayed filtered children response cannot erase the required connector after collapse and reopen', async () => {
  await withApi(async () => {
    const { s, args, childArgs, render } = setup();
    await s.dispatch(getAncestors(args)).unwrap();
    const root = s.getState().ontologies.rootNodes[0];
    s.dispatch(closeNode(root));
    s.dispatch(openNode(root));
    const apiFetch = globalThis.fetch;
    const release: (() => void)[] = [];
    globalThis.fetch = ((...a: any[]) => new Promise(resolve => release.push(() => resolve((apiFetch as any)(...a))))) as any;
    const pending = s.dispatch(getNodeChildren(childArgs()));
    assert.equal(release.length, 1);
    release.forEach(resolve => resolve());
    await pending.unwrap();
    assert.deepEqual(s.getState().ontologies.nodeChildren[rootIri].map(n => n.iri).sort(), [connectorIri, 'native sibling'].sort());
    assert(render().includes('body height at birth'));
  });
});

test('an explicitly selected imported entity retains its path while optional imported roots stay filtered', async () => {
  await withApi(async urls => {
    const { s, args, render } = setup({ selected: connectorIri });
    await s.dispatch(getAncestors(args)).unwrap();
    assert.deepEqual(s.getState().ontologies.rootNodes.map(n => n.iri), [rootIri]);
    assert.match(render(), greyConnectorLink);
    const browse = setup({ selected: '' });
    await browse.s.dispatch(getRootEntities(browse.args)).unwrap();
    assert.deepEqual(browse.s.getState().ontologies.rootNodes.map(n => n.iri), [rootIri]);
    assert.equal(urls.at(-1)!.searchParams.get('treeContext'), 'true');
  });
});

test('a specified imported root is retained and grey when imports are hidden', async () => {
  await withApi(async () => {
    const { s, args, render } = setup({ selected: '', specifiedRootIri: connectorIri });
    await s.dispatch(getAncestors(args)).unwrap();
    assert.deepEqual(s.getState().ontologies.rootNodes.map(n => n.iri), [connectorIri]);
    assert.match(render(), greyConnectorLink);
  });
});

test('required edges retain reified relationships after filtered child replacement', () => {
  const { s, args, childArgs } = setup();
  const reified = efoPath.map(e => thingFromJsonProperties(e.iri === measurementIri ? { ...e,
    hierarchicalParent: [{ type: ['reification'], value: connectorIri, axioms: [{
      childRelationToParent: 'http://purl.obolibrary.org/obo/BFO_0000050', parentRelationToChild: 'http://example.org/hasPart',
    }] }],
  } : e));
  s.dispatch(getAncestors.pending('path', args));
  s.dispatch(getAncestors.fulfilled(reified, 'path', args));
  s.dispatch(getNodeChildren.pending('children', childArgs(connectorId)));
  s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: connectorId, children: [] }, 'children', childArgs(connectorId)));
  const children = s.getState().ontologies.nodeChildren[connectorId];
  assert.equal(children.length, 1);
  assert.equal(children[0].childRelationToParent, 'http://purl.obolibrary.org/obo/BFO_0000050');
  assert.equal(children[0].parentRelationToChild, 'http://example.org/hasPart');
});

test('required connectors stay attached to each of their actual parent paths', () => {
  const { s, args, childArgs } = setup();
  const entities = [...efoPath.map(e => e.iri === connectorIri
    ? { ...e, hierarchicalParent: [rootIri, 'alternate parent'] } : e),
    { iri: 'alternate parent', label: ['alternate parent'], type: ['class'], ontologyId: 'efo',
      isDefiningOntology: true, hierarchicalParent: [rootIri], hasDirectChildren: true },
  ].map(thingFromJsonProperties);
  s.dispatch(getAncestors.pending('path', args));
  s.dispatch(getAncestors.fulfilled(entities, 'path', args));
  for (const id of [rootIri, `${rootIri};alternate parent`]) {
    s.dispatch(getNodeChildren.pending(id, childArgs(id)));
    s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: id, children: [] }, id, childArgs(id)));
    const connector = s.getState().ontologies.nodeChildren[id].find(n => n.iri === connectorIri);
    assert(connector);
    assert.equal(connector.absoluteIdentity, `${id};${connectorIri}`);
    assert.deepEqual(s.getState().ontologies.nodeChildren[connector.absoluteIdentity].map(n => n.iri), [measurementIri]);
  }
});

for (const entityType of ['properties', 'individuals']) {
  test(`${entityType} fetch complete imported ancestry through every ancestor endpoint`, async () => {
    const { s, args } = setup();
    const property = entityType === 'properties';
    const parentKey = property ? 'http://www.w3.org/2000/01/rdf-schema#subPropertyOf' : 'directParent';
    const entity = (iri: string, native: boolean, parents: string[], type: string) => ({
      iri, label: [iri], type: [type], ontologyId: 'efo', isDefiningOntology: native,
      [parentKey]: parents, hierarchicalParent: parents,
    });
    const root = entity('root', true, [], property ? 'property' : 'class');
    const connector = entity('imported connector', false, ['root'], property ? 'property' : 'individual');
    const selected = thingFromJsonProperties(entity('selected', true, ['imported connector'], property ? 'property' : 'individual'));
    const original = globalThis.fetch;
    const urls: URL[] = [];
    globalThis.fetch = (async (input: any) => {
      const url = new URL(String(input));
      urls.push(url);
      const candidates = [root, connector];
      return page(candidates.filter(e => url.pathname.endsWith('/treeAncestors') || e.isDefiningOntology || url.searchParams.get('includeImportedEntities') === 'true'));
    }) as any;
    try {
      await s.dispatch(getAncestors({ ...args, entityType, entityIri: 'selected', selectedEntity: selected })).unwrap();
      assert.deepEqual(s.getState().ontologies.rootNodes.map(n => n.iri), ['root']);
      assert.deepEqual(s.getState().ontologies.nodeChildren.root.map(n => n.iri), ['imported connector']);
      assert.deepEqual(s.getState().ontologies.nodeChildren['root;imported connector'].map(n => n.iri), ['selected']);
      assert.equal(urls.length, 1);
      assert(urls.every(u => u.searchParams.get('includeImportedEntities') === (property ? 'true' : null)));
    } finally { globalThis.fetch = original; }
  });
}

for (const reset of ['scope', 'context', 'content']) {
  test(`required paths reset with ${reset} and late ancestors cannot restore them`, async () => {
    await withApi(async () => {
      const { s, args } = setup();
      await s.dispatch(getAncestors(args)).unwrap();
      assert(Object.keys(s.getState().ontologies.requiredPathChildren || {}).length > 0);
      s.dispatch(getAncestors.pending('late', args));
      s.dispatch(reset === 'scope' ? setTreeScope('other') : reset === 'context'
        ? setTreeRequestContext({ scopeKey, requestKey: 'other' }) : resetTreeContent());
      s.dispatch(getAncestors.fulfilled(efoPath.map(thingFromJsonProperties), 'late', args));
      assert.deepEqual(s.getState().ontologies.requiredPathChildren, {});
      assert.deepEqual(s.getState().ontologies.rootNodes, []);
    });
  });
}

test('the selected path stays visible when an expanded ancestor uses the large-children list', async () => {
  await withApi(async () => {
    const { s, args, render } = setup({ imports: true });
    await s.dispatch(getAncestors(args)).unwrap();
    s.dispatch(getDirectChildrenCount.pending('count', args));
    s.dispatch(getDirectChildrenCount.fulfilled({ entityIri: rootIri, totalElements: 2001 }, 'count', args));
    assert(render().includes('body height at birth'));
  });
});

for (const count of [1000, 1001]) {
  test(`combined expansion sets the strict table threshold for automatically selected paths: ${count}`, async () => {
    await withApi(async () => {
      const { s, args, childArgs, render } = setup({ imports: true, siblings: true });
      await s.dispatch(getAncestors(args)).unwrap();
      const input = childArgs();
      s.dispatch(getNodeChildren.pending('combined', input));
      s.dispatch(getNodeChildren.fulfilled({ absoluteIdentity: rootIri, entityIri: rootIri,
        children: [], totalElements: count }, 'combined', input));
      const html = render();
      assert.equal(html.includes('Direct Children'), count > 1000);
      assert(html.includes('body height at birth'));
    });
  });
}

// Generated RDF fixture: P <-hier C <-type I <-hier J <-hier K <-type L.
import mixedFixture from '../../backend/src/test/resources/tree-reachability/generated.json';
const mixed = mixedFixture.ontologies[0];
// Match API JsonTransformer label normalization; preserve generated relation objects.
const mixedRows = ([...mixed.classes, ...mixed.individuals] as any[]).map(e=>({...e,
  label: (Array.isArray(e.label)?e.label:[e.label]).map(v=>typeof v==='object'?v.value:v)}));
for (const selectedName of ['I','K','L']) for (const preferred of [false,true]) {
  test(`complete mixed selected rendering: ${selectedName}, preferred=${preferred}`, async () => {
    const names = ['P','C','I','J','K','L'].slice(0, ['I','K','L'].includes(selectedName) ? ['P','C','I','J','K','L'].indexOf(selectedName)+1 : 0);
    const iri = (name: string) => 'https://example.org/mixed#'+name;
    const entities = names.map(n=>thingFromJsonProperties(mixedRows.find(e=>e.iri===iri(n))));
    const {s,args} = setup({preferred});
    const ont = new Ontology({ontologyId:'mixed',type:['ontology'],preferredRoot:[iri('P')]});
    const selectedEntity=entities.at(-1)!;
    const scopeKey=JSON.stringify(['','mixed', selectedName==='K'?'classes':'individuals']);
    const entityType=selectedName==='K'?'classes':'individuals';
    s.dispatch(setTreeScope(scopeKey)); s.dispatch(hideImported());
    if(preferred) s.dispatch(enablePreferredRoots());
    const requestKey=JSON.stringify([scopeKey,'en',selectedEntity,'',preferred,false,false,false,[]]);
    s.dispatch(setTreeRequestContext({scopeKey,requestKey}));
    const input = {...args, ontology:ont, ontologyId:'mixed', entityType, selectedEntity, entityIri:iri(selectedName), treeScopeKey:scopeKey, requestKey};
    const original=globalThis.fetch;
    globalThis.fetch=(async(input:any)=> {
      assert(new URL(String(input)).pathname.endsWith('/treeAncestors'));
      return page(entities.slice(0,-1).map(e=>e.properties));
    }) as any;
    try {
      await s.dispatch(getAncestors(input)).unwrap();
      const state=s.getState().ontologies;
      assert.deepEqual(state.rootNodes.map(n=>n.iri),[iri('P')]);
      for(let i=1;i<names.length;i++) {
        const parent=names.slice(0,i).map(iri).join(';');
        assert.deepEqual(state.nodeChildren[parent].map(n=>n.iri),[iri(names[i])]);
      }
      const html=renderToStaticMarkup(<Provider store={s}><StaticRouter location="/"><EntityTree ontology={ont}
        entityType={entityType} lang="en" selectedEntity={selectedEntity} onNavigateToEntity={()=>{}} onNavigateToOntology={()=>{}} /></StaticRouter></Provider>);
      for(const entity of entities) assert(html.includes(entity.getName()), `missing rendered ${entity.getName()}`);
    } finally {globalThis.fetch=original;}
  });
}

test('preferred roots outside an explicitly selected path cannot erase its context', () => {
  const {s,args}=setup({preferred:true});
  const input={...args,ontology:new Ontology({ontologyId:'efo',type:['ontology'],preferredRoot:['other preferred root']})};
  s.dispatch(getAncestors.pending('path',input));
  s.dispatch(getAncestors.fulfilled(efoPath.map(thingFromJsonProperties),'path',input));
  assert.deepEqual(s.getState().ontologies.rootNodes.map(n=>n.iri),[rootIri]);
});

test('selected path deeper than 100 edges remains complete and renders', () => {
  const {s,args,render}=setup();
  const rows=Array.from({length:126},(_,i)=>({iri:i===0?rootIri:i===125?selectedIri:`deep-${i}`,
    type:['class'],ontologyId:'efo',label:[`deep node ${i}`],isDefiningOntology:true,
    hierarchicalParent:i===0?[]:[i===1?rootIri:`deep-${i-1}`],hasHierarchicalChildren:i<125}));
  s.dispatch(getAncestors.pending('deep',args));
  s.dispatch(getAncestors.fulfilled(rows.map(thingFromJsonProperties),'deep',args));
  assert.equal(Object.keys(s.getState().ontologies.nodeChildren).length,126);
  assert(render().includes('deep node 125'));
});

test('selected direct-only class edge stays attached to its shared-reachability parent', () => {
  const {s,args,render}=setup();
  const rows=[{iri:rootIri,type:['class'],ontologyId:'efo',label:['direct root'],hasDirectChildren:true},
    {iri:selectedIri,type:['class'],ontologyId:'efo',label:['direct selected'],directParent:[rootIri]}];
  s.dispatch(getAncestors.pending('direct',args));
  s.dispatch(getAncestors.fulfilled(rows.map(thingFromJsonProperties),'direct',args));
  assert.deepEqual(s.getState().ontologies.rootNodes.map(n=>n.iri),[rootIri]);
  assert.deepEqual(s.getState().ontologies.nodeChildren[rootIri].map(n=>n.iri),[selectedIri]);
  assert(render().includes('direct selected'));
});

test('actual selected-path cycles terminate without hiding their distinct nodes', () => {
  const {s,args,render}=setup();
  const rows=[{iri:rootIri,type:['class'],label:['cycle root'],hasHierarchicalChildren:true,hierarchicalParent:[selectedIri]},
    {iri:selectedIri,type:['class'],label:['cycle selected'],hasHierarchicalChildren:true,hierarchicalParent:[rootIri]}];
  s.dispatch(getAncestors.pending('cycle',args));
  s.dispatch(getAncestors.fulfilled(rows.map(thingFromJsonProperties),'cycle',args));
  assert.equal(Object.keys(s.getState().ontologies.nodeChildren).length,2);
  const html=render();assert(html.includes('cycle root')&&html.includes('cycle selected'));
});
