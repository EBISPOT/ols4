import assert from 'node:assert/strict';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import reducer, { setSelectedSubsets, getSubsets } from '../src/pages/ontologies/ontologiesSlice';
import EntityTree from '../src/pages/ontologies/entities/EntityTree';
import Ontology from '../src/model/Ontology';

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
