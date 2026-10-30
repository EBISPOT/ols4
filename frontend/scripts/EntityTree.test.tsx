import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import reducer, { setSelectedSubsets, getSubsets } from '../src/pages/ontologies/ontologiesSlice';
import EntityTree from '../src/pages/ontologies/entities/EntityTree';
import Ontology from '../src/model/Ontology';
import { StaticRouter } from 'react-router-dom/server';
import { thingFromJsonProperties } from '../src/app/util';
import { getRootEntities, getTreeMemberCounts, getDirectChildrenCount, hideCounts, showCounts, hideImported, showObsolete } from '../src/pages/ontologies/ontologiesSlice';

const subset = 'http://www.ebi.ac.uk/coho#IHCC_subset';
for (const props of [{ id: 'efo', type: 'classes' }, { id: 'coho', type: 'individuals' }, { id: 'coho', type: 'classes', apiUrl: 'https://other.test/' }]) {
  test(`initial render hides another scope's subset options: ${JSON.stringify(props)}`, () => {
    const s = configureStore({ reducer: { ontologies: reducer }, middleware: g => g({ serializableCheck: false }) });
    const treeScopeKey = '["","coho","classes"]';
    s.dispatch({ type: 'ontologies_set_tree_scope', payload: treeScopeKey });
    s.dispatch({ type: 'ontologies_set_tree_request_context', payload: { scopeKey: treeScopeKey, requestKey: 'coho' } });
    s.dispatch(setSelectedSubsets([subset]));
    const arg = { treeScopeKey, requestKey: 'coho' };
    s.dispatch(getSubsets.pending('subsets', arg));
    s.dispatch(getSubsets.fulfilled([{ iri: subset, label: 'IHCC leaked option', numMembers: 77 }], 'subsets', arg));
    const html = renderToStaticMarkup(<Provider store={s}><EntityTree
      ontology={new Ontology({ ontologyId: props.id, type: ['ontology'], preferredRoot: [] })}
      entityType={props.type as any} apiUrl={props.apiUrl} lang="en"
      onNavigateToEntity={() => {}} onNavigateToOntology={() => {}}
    /></Provider>);
    assert(!html.includes('IHCC leaked option'));
  });
}

for (const fixture of [
  { id: 'bfo', iri: 'entity', stored: 34, native: 34 },
  { id: 'efo', iri: 'mixed-origin', stored: 99, native: 7 },
]) {
  for (const selected of [[], [subset, 'another-subset']]) {
    test(`hidden-import badges use native members and respect Show counts: ${fixture.id}, subsets=${selected.length}`, () => {
      const s = configureStore({ reducer: { ontologies: reducer }, middleware: g => g({ serializableCheck: false }) });
      const scopeKey = JSON.stringify(['https://widget.test/', fixture.id, 'classes']);
      const requestKey = JSON.stringify([scopeKey, 'en', undefined, '', false, false, false, false, selected]);
      const ontology = new Ontology({ ontologyId: fixture.id, type: ['ontology'], preferredRoot: [] });
      s.dispatch({ type: 'ontologies_set_tree_scope', payload: scopeKey });
      s.dispatch(hideImported());
      s.dispatch(setSelectedSubsets(selected));
      s.dispatch({ type: 'ontologies_set_tree_request_context', payload: { scopeKey, requestKey } });
      const arg = { ontology, ontologyId: fixture.id, entityType: 'classes', treeScopeKey: scopeKey, requestKey };
      const root = thingFromJsonProperties({ iri: fixture.iri, label: [fixture.iri], type: ['class'],
        ontologyId: fixture.id, isDefiningOntology: true, numDescendants: fixture.stored,
        numHierarchicalDescendants: fixture.stored, hasChildren: true });
      s.dispatch(getRootEntities.pending('roots', arg));
      s.dispatch(getRootEntities.fulfilled({ rootTerms: [root] }, 'roots', arg));
      const render = () => renderToStaticMarkup(<Provider store={s}><StaticRouter location="/"><EntityTree
        ontology={ontology} entityType="classes" apiUrl="https://widget.test/" lang="en"
        onNavigateToEntity={() => {}} onNavigateToOntology={() => {}}
      /></StaticRouter></Provider>);
      s.dispatch(getTreeMemberCounts.pending('counts', arg));
      const loadingHtml = render();
      s.dispatch(getTreeMemberCounts.fulfilled({ [fixture.iri]: fixture.native }, 'counts', arg));
      assert(render().includes(` (${fixture.native})`));
      assert(loadingHtml.includes('Descendant count loading'));
      assert(!loadingHtml.includes(` (${fixture.stored})`));
      assert(!render().includes('Descendant count loading'));
      if (fixture.stored !== fixture.native) assert(!render().includes(` (${fixture.stored})`));
      s.dispatch(hideCounts());
      assert(!render().includes(` (${fixture.native})`));
      s.dispatch(showCounts());
      assert(render().includes(` (${fixture.native})`));
    });
  }
}

test('ordinary imports-visible badges retain stored totals and obsolete-mode restrictions', () => {
  const s = configureStore({ reducer: { ontologies: reducer }, middleware: g => g({ serializableCheck: false }) });
  const scopeKey = JSON.stringify(['', 'bfo', 'classes']);
  const ontology = new Ontology({ ontologyId: 'bfo', type: ['ontology'], preferredRoot: [] });
  s.dispatch({ type: 'ontologies_set_tree_scope', payload: scopeKey });
  const loadRoots = (obsolete: boolean) => {
    const requestKey = JSON.stringify([scopeKey, 'en', undefined, '', false, obsolete, true, false, []]);
    s.dispatch({ type: 'ontologies_set_tree_request_context', payload: { scopeKey, requestKey } });
    const arg = { ontology, treeScopeKey: scopeKey, requestKey };
    s.dispatch(getRootEntities.pending('roots', arg));
    s.dispatch(getRootEntities.fulfilled({ rootTerms: [thingFromJsonProperties({ iri: 'entity', label: ['entity'],
      type: ['class'], ontologyId: 'bfo', isDefiningOntology: true, numDescendants: 34, numHierarchicalDescendants: 34 })] }, 'roots', arg));
  };
  const render = () => renderToStaticMarkup(<Provider store={s}><StaticRouter location="/"><EntityTree
    ontology={ontology} entityType="classes" lang="en" onNavigateToEntity={() => {}} onNavigateToOntology={() => {}}
  /></StaticRouter></Provider>);
  loadRoots(false);
  assert(render().includes(' (34)'));
  s.dispatch(hideCounts());
  assert(!render().includes(' (34)'));
  s.dispatch(showCounts());
  assert(render().includes(' (34)'));
  s.dispatch(showObsolete());
  loadRoots(true);
  assert(!render().includes(' (34)'));
});

for (const counts of [{ Country: 3, Area: 3 }, {}]) {
  test(`subset badges use selected-member totals only: ${JSON.stringify(counts)}`, () => {
    const s = configureStore({ reducer: { ontologies: reducer }, middleware: g => g({ serializableCheck: false }) });
    const scopeKey = JSON.stringify(['', 'coho', 'individuals']);
    const requestKey = JSON.stringify([scopeKey, 'en', undefined, '', false, false, true, false, [subset]]);
    const ontology = new Ontology({ ontologyId: 'coho', type: ['ontology'], preferredRoot: [] });
    s.dispatch({ type: 'ontologies_set_tree_scope', payload: scopeKey });
    s.dispatch(setSelectedSubsets([subset]));
    s.dispatch({ type: 'ontologies_set_tree_request_context', payload: { scopeKey, requestKey } });
    const arg = { ontology, ontologyId: 'coho', entityType: 'individuals', treeScopeKey: scopeKey, requestKey };
    const roots = ['Country', 'Area'].map(iri => thingFromJsonProperties({
      iri, label: [iri], type: ['class'], ontologyId: 'coho', isDefiningOntology: true,
      numDescendants: 999, numHierarchicalDescendants: 888, hasIndividuals: true,
    }));
    s.dispatch(getRootEntities.pending('roots', arg));
    s.dispatch(getRootEntities.fulfilled({ rootTerms: roots }, 'roots', arg));
    s.dispatch(getTreeMemberCounts.pending('counts', arg));
    s.dispatch(getTreeMemberCounts.fulfilled(counts, 'counts', arg));
    s.dispatch(getDirectChildrenCount.pending('children', arg));
    s.dispatch(getDirectChildrenCount.fulfilled({ entityIri: 'Country', totalElements: 7 }, 'children', arg));
    const render = () => renderToStaticMarkup(<Provider store={s}><StaticRouter location="/"><EntityTree
      ontology={ontology} entityType="individuals" lang="en"
      onNavigateToEntity={() => {}} onNavigateToOntology={() => {}}
    /></StaticRouter></Provider>);
    const html = render();
    assert(html.includes('Country') && html.includes('Area'));
    assert.equal((html.match(/ \(3\)/g) || []).length, 'Country' in counts ? 2 : 0);
    for (const wrong of [' (999)', ' (888)', ' (7)']) assert(!html.includes(wrong));
    s.dispatch(hideCounts());
    assert(!render().includes(' (3)'));
  });
}
