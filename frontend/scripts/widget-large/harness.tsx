import React from 'react';
import ReactDOM from 'react-dom';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { Provider } from 'react-redux';
import { createEntityTree } from '../../src/widgets';
import { createAppStore } from '../../src/app/store';
import EntityList from '../../src/pages/ontologies/entities/EntityList';
const left = document.querySelector<HTMLElement>('#left')!, right = document.querySelector<HTMLElement>('#right')!;
const result = document.querySelector<HTMLElement>('#result')!;
const apiUrl = location.origin + '/';
const iri = 'https://example.org/paging#A';
function assert(value: unknown, message: string) { if (!value) throw new Error(message); }
async function until(predicate: () => unknown, message: string) {
  const end = Date.now()+15000;
  while (!predicate()) { if (Date.now()>end) throw new Error(message); await new Promise(r=>setTimeout(r,30)); }
}
function link(host: HTMLElement, value: string) { return Array.from(host.querySelectorAll<HTMLAnchorElement>('a[title]')).find(a=>a.title.includes(`[${value}]`)); }
function Location() { return <output id="route">{useLocation().pathname}</output>; }
document.querySelector<HTMLButtonElement>('#run')!.onclick = async function() {
  this.disabled=true; const passed:string[]=[]; const pass=(s:string)=>{passed.push(s);result.textContent=passed.join('\n');};
  try {
    ReactDOM.unmountComponentAtNode(left); ReactDOM.unmountComponentAtNode(right);
    let selected:any;
    createEntityTree({ ontologyId:'paging', apiUrl, specifiedRootIri:iri,
      onNavigateToEntity:(ontology,entity)=>{selected={ontology,entity};} },left);
    createEntityTree({ ontologyId:'mixed', apiUrl, specifiedRootIri:'https://example.org/mixed#P' },right);
    await until(()=>link(left,iri)&&link(right,'https://example.org/mixed#P'),'public roots failed');
    await until(()=>!right.querySelector('.spinner-default')&&!right.textContent!.includes('Descendant count loading'),'sibling did not settle');
    const sibling=right.innerHTML;
    const node=link(left,iri)!.closest('li')!;
    (node.querySelector('.jstree-ocl') as HTMLElement).click();
    await until(()=>left.querySelector('input[placeholder="Search children..."]') && left.querySelector('tbody'),'standalone large table failed to mount (Router regression)');
    pass('PASS actual public standalone large table mounts without a host Router');
    const input=left.querySelector<HTMLInputElement>('input[placeholder="Search children..."]')!;
    const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')!.set!;
    setter.call(input,'A2241'); input.dispatchEvent(new Event('input',{bubbles:true}));
    await until(()=>left.querySelector('tbody')?.textContent?.includes('A2241'),'late row search failed');
    const row=Array.from(left.querySelectorAll('tbody tr')).find(r=>r.textContent?.includes('A2241')) as HTMLElement;
    row.click();
    assert(selected?.ontology.getOntologyId()==='paging','callback ontology was lost');
    assert(selected?.entity.properties.iri==='https://example.org/paging#A2241'&&selected.entity.getTypePlural()==='individuals','late individual callback was lost');
    assert(right.innerHTML===sibling,'large widget changed sibling state');
    pass('PASS late individual A2241 reaches documented public navigation callback; sibling unchanged');
    // Ordinary app lists retain Router navigation; use a real table row and actual Router event.
    const s=createAppStore();
    s.dispatch({type:'ontologies_set_tree_scope',payload:'hosted'});
    s.dispatch({type:'ontologies_set_tree_request_context',payload:{scopeKey:'hosted',requestKey:'hosted'}});
    ReactDOM.render(<Provider store={s}><MemoryRouter><Location/><EntityList ontologyId="paging" entityType="classes" parentEntityIri={iri} tableKey="hosted" apiUrl={apiUrl} treeScopeKey="hosted" requestKey="hosted" /></MemoryRouter></Provider>,document.querySelector('#hosted'));
    await until(()=>document.querySelector('#hosted tbody tr'),'host Router real-API rows missing');
    const hostedInput=document.querySelector<HTMLInputElement>('#hosted input')!;
    setter.call(hostedInput,'A2241'); hostedInput.dispatchEvent(new Event('input',{bubbles:true}));
    await until(()=>document.querySelector('#hosted tbody')?.textContent?.includes('A2241'),'host Router late row missing');
    const routed=document.querySelector<HTMLElement>('#hosted tbody tr')!;
    routed.click();
    assert(document.querySelector('#route')!.textContent==='/ontologies/paging/individuals/https%253A%252F%252Fexample.org%252Fpaging%2523A2241','ordinary app navigation changed');
    pass('PASS ordinary app list preserves host Router navigation and individual URL');
    pass('GREEN: 3 mounted integration checks passed');
  } catch(error) {result.textContent='RED: '+String(error)+'\n'+passed.join('\n'); console.error(error);}
  finally {this.disabled=false;}
};
