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
import { getRootEntities, getTreeMemberCounts, getDirectChildrenCount, hideCounts } from '../src/pages/ontologies/ontologiesSlice';

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
