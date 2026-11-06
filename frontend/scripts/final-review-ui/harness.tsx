import ReactDOM from 'react-dom';
import { createEntityTree } from '../../src/widgets';
const left=document.querySelector<HTMLElement>('#left')!,right=document.querySelector<HTMLElement>('#right')!;
const result=document.querySelector<HTMLElement>('#result')!;
const apiUrl=location.origin+'/';
const ns='https://example.org/finalpaging#';
function assert(value:unknown,message:string){if(!value)throw Error(message);}
async function until(predicate:()=>unknown,message:string){const end=Date.now()+20000;while(!predicate()){if(Date.now()>end)throw Error(message);await new Promise(r=>setTimeout(r,30));}}
const link=(host:HTMLElement,iri:string)=>Array.from(host.querySelectorAll<HTMLAnchorElement>('a[title]')).find(a=>a.title.includes(`[${iri}]`));
const text=(host:HTMLElement)=>host.textContent||'';
function check(host:HTMLElement,label:string){const input=Array.from(host.querySelectorAll<HTMLInputElement>('label input')).find(i=>i.closest('label')!.textContent!.trim().startsWith(label));assert(input,`missing ${label}`);return input!;}
async function settled(host:HTMLElement){await until(()=>!host.querySelector('.spinner-default')&&!host.querySelector('[aria-label="Descendant count loading"]')&&!text(host).includes('Loading direct children'),'pending requests');await new Promise(r=>setTimeout(r,80));}
function type(input:HTMLInputElement,value:string){input.focus();Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')!.set!.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));}
function button(host:HTMLElement,label:string){const b=Array.from(host.querySelectorAll<HTMLButtonElement>('button')).find(b=>b.textContent===label);assert(b,`missing button ${label}`);return b!;}
function expand(host:HTMLElement,iri:string){const node=link(host,iri)!.closest('li')!;if(!node.classList.contains('jstree-open'))(node.querySelector('.jstree-ocl') as HTMLElement).click();}
document.querySelector<HTMLButtonElement>('#run')!.onclick=async function(){
 this.disabled=true;const passed:string[]=[];const pass=(s:string)=>{passed.push('PASS '+s);result.textContent=passed.join('\n');};
 try{
  ReactDOM.unmountComponentAtNode(left);ReactDOM.unmountComponentAtNode(right);
  createEntityTree({ontologyId:'finalsubsets',apiUrl},left);
  await until(()=>checkIfPresent('Imported only'),'real subset definitions not loaded');await settled(left);
  check(left,'Imported only').click();await settled(left);
  assert(link(left,'https://example.org/finalsubsets#Imported'),'selected imported member');
  check(left,'Show imported terms').click();await settled(left);
  assert(check(left,'Imported only').checked,'import selection cleared');
  assert(check(left,'Imported only').closest('label')!.textContent!.includes('(0)'),'zero count missing');
  assert(text(left).includes('No terms match'),'hidden imported subset broadened');pass('imported-only subset stays checked at zero with empty tree');
  check(left,'Show imported terms').click();await settled(left);assert(link(left,'https://example.org/finalsubsets#Imported'),'import restoration');
  check(left,'Imported only').click();await settled(left);assert(link(left,'https://example.org/finalsubsets#Native'),'manual uncheck did not broaden');pass('visibility restoration and explicit uncheck');
  check(left,'Show obsolete terms').click();await settled(left);check(left,'Obsolete only').click();await settled(left);
  assert(link(left,'https://example.org/finalsubsets#Obsolete'),'obsolete selection');
  check(left,'Show obsolete terms').click();await settled(left);assert(check(left,'Obsolete only').checked&&text(left).includes('No terms match'),'obsolete selection broadened');
  check(left,'Imported only').click();check(left,'Overlapping').click();await settled(left);check(left,'Show imported terms').click();await settled(left);
  for(const s of ['Imported only','Obsolete only','Overlapping'])assert(check(left,s).checked&&check(left,s).closest('label')!.textContent!.includes('(0)'),`lost overlapping ${s}`);
  assert(text(left).includes('No terms match'),'zero overlapping union broadened');
  check(left,'Show imported terms').click();check(left,'Show obsolete terms').click();await settled(left);
  assert(link(left,'https://example.org/finalsubsets#Imported')&&link(left,'https://example.org/finalsubsets#Obsolete'),'union restoration');pass('obsolete-only and overlapping union zero/restoration');
  ReactDOM.unmountComponentAtNode(left);let selected:any;
  createEntityTree({ontologyId:'finalpaging',apiUrl,entityType:'individuals',specifiedRootIri:ns+'IR',onNavigateToEntity:(_,e)=>{selected=e;}},left);
  createEntityTree({ontologyId:'finalpaging',apiUrl,entityType:'properties',specifiedRootIri:ns+'PR'},right);
  await until(()=>link(left,ns+'IR')&&link(right,ns+'PR'),'paging roots');await settled(left);await settled(right);
  expand(left,ns+'IR');expand(right,ns+'PR');
  await until(()=>left.querySelector('tbody')&&right.querySelector('tbody'),'tables did not mount');await settled(left);await settled(right);
  assert(left.querySelector('input[placeholder="Search individuals..."]'),'Individuals table missing');
  button(left,'101').click();await until(()=>left.querySelector('tbody')?.textContent?.includes('item1001'),'hierarchical item1001 not reachable');await settled(left);
  pass('hierarchical-only Individuals final page reaches item1001');
  const sibling=left.innerHTML;
  const propertySearch=right.querySelector<HTMLInputElement>('input[placeholder="Search properties..."]')!;
  type(propertySearch,'property1001');await until(()=>right.querySelector('tbody')?.textContent?.includes('property1001'),'property search ignored');await settled(right);
  assert(right.querySelectorAll('tbody tr').length===1,'property count/page mismatch');
  type(propertySearch,'missing-property');await until(()=>text(right).includes('No direct children match'),'property no-match ignored');
  assert(right.querySelector('input')===propertySearch&&document.activeElement===propertySearch,'property search remounted or lost focus');
  type(propertySearch,'');await until(()=>right.querySelector('tbody')?.textContent?.includes('property0001'),'property clear failed');await settled(right);
  assert(left.innerHTML===sibling,'property search changed sibling page');pass('Properties search, empty, clear retain focus and sibling page');
  const propertyState=right.innerHTML;
  const individualSearch=left.querySelector<HTMLInputElement>('input[placeholder="Search individuals..."]')!;
  type(individualSearch,'item1001');await until(()=>left.querySelector('tbody')?.textContent?.includes('item1001')&&left.querySelectorAll('tbody tr').length===1,'individual search failed');await settled(left);
  (Array.from(left.querySelectorAll<HTMLTableRowElement>('tbody tr')).find(r=>r.textContent!.includes('item1001'))!).click();
  assert(selected?.getIri()===ns+'item1001'&&selected.getType()==='individual','standalone navigation identity');
  type(individualSearch,'missing-individual');await until(()=>text(left).includes('No direct children match'),'individual no match failed');
  assert(document.activeElement===individualSearch,'individual search focus lost');
  type(individualSearch,'');await until(()=>left.querySelector('tbody')?.textContent?.includes('item0001'),'individual clear failed');await settled(left);
  assert(right.innerHTML===propertyState,'individual search changed property sibling');pass('Individuals search, empty, clear and standalone callback preserve identity and sibling state');
  result.dataset.status='passed';
 }catch(error:any){result.textContent=passed.join('\n')+'\nFAIL '+error.message;result.dataset.status='failed';console.error(error);}
 function checkIfPresent(label:string){return Array.from(left.querySelectorAll('label')).some(l=>l.textContent?.startsWith(label));}
};
