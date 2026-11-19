import ReactDOM from 'react-dom';
import { createEntityTree } from '../../src/widgets';
import { EntityTreeWidgetProps } from '../../src/widgets/EntityTreeWidget';

const left = document.querySelector<HTMLElement>('#left')!;
const right = document.querySelector<HTMLElement>('#right')!;
const result = document.querySelector<HTMLElement>('#result')!;
const base = { apiUrl: location.origin + '/', lang: 'en' };
const native = { ontologyId: 'native', specifiedRootIri: 'https://example.org/native#R' };
const mixed = { ontologyId: 'mixed', specifiedRootIri: 'https://example.org/mixed#P' };
let leftProps = native;
let navigations = 0;
const props = (scope: typeof native): EntityTreeWidgetProps => ({ ...base, ...scope,
  onNavigateToEntity: () => { navigations++; },
});
function mountLeft() { createEntityTree(props(leftProps), left); }
function mountBoth() { mountLeft(); createEntityTree(props(mixed), right); }
function reset() { ReactDOM.unmountComponentAtNode(left); ReactDOM.unmountComponentAtNode(right); leftProps = native; navigations = 0; }
function link(container: HTMLElement, iri: string) {
  return Array.from(container.querySelectorAll<HTMLAnchorElement>('a[title]')).find(a => a.title.includes(`[${iri}]`));
}
function assert(value: unknown, message: string) { if (!value) throw new Error(message); }
async function until(predicate: () => unknown, message: string) {
  const deadline = Date.now() + 8000;
  while (!predicate()) { if (Date.now() > deadline) throw new Error(message); await new Promise(resolve => setTimeout(resolve, 25)); }
}
async function idle() {
  await until(() => !document.querySelector('.spinner-default'), 'tree requests did not settle');
}
function counts(container: HTMLElement) {
  return Array.from(container.querySelectorAll<HTMLInputElement>('label input')).find(i => i.closest('label')!.textContent === 'Show counts')!;
}
function expand(container: HTMLElement, iri: string) {
  const node = link(container, iri)?.closest('li');
  assert(node, `node not found: ${iri}`);
  if (!node!.classList.contains('jstree-open')) (node!.querySelector('.jstree-ocl') as HTMLElement).click();
}
const nativeRoot = native.specifiedRootIri, mixedRoot = mixed.specifiedRootIri;
document.querySelector('#navigate')!.addEventListener('click', () => { leftProps = mixed; mountLeft(); });
document.querySelector('#remount')!.addEventListener('click', () => { ReactDOM.unmountComponentAtNode(left); leftProps = native; mountLeft(); });
document.querySelector<HTMLButtonElement>('#run')!.addEventListener('click', async function () {
  this.disabled = true;
  result.textContent = 'Running';
  const passed: string[] = [];
  const pass = (message: string) => { passed.push(message); result.textContent = passed.join('\n'); };
  try {
    reset(); mountBoth();
    await until(() => link(left, nativeRoot) && link(right, mixedRoot), 'two mounted widgets did not retain their different roots/scopes');
    await idle();
    assert(!link(left, mixedRoot) && !link(right, nativeRoot), 'ontology data leaked between widgets');
    pass('PASS different roots/scopes coexist');
    expand(left, nativeRoot); expand(right, mixedRoot);
    await until(() => link(left, 'https://example.org/native#D') && link(right, 'https://example.org/mixed#C'), 'both widgets must remain expandable');
    await idle(); pass('PASS both widgets expand using real child requests');
    counts(right).click(); await idle();
    assert(counts(left).checked && !counts(right).checked, 'right settings affected left');
    counts(left).click(); await idle();
    assert(!counts(left).checked && !counts(right).checked, 'left settings affected right');
    pass('PASS settings are independent in both directions');
    link(left, nativeRoot)!.click(); link(right, mixedRoot)!.click();
    assert(navigations === 2, 'documented navigation callbacks were not forwarded');
    pass('PASS public navigation callbacks');
    let rendered = false;
    createEntityTree(props(leftProps), left, () => { rendered = true; });
    assert(rendered && !counts(left).checked, 'same-scope render lost state or render callback');
    assert(link(left, 'https://example.org/native#D'), 'same-scope render collapsed the tree');
    pass('PASS same-scope rerender retains expansion/settings and render callback');
    const sibling = right.innerHTML;
    leftProps = mixed; mountLeft();
    await until(() => link(left, mixedRoot) && !link(left, nativeRoot), 'left navigation did not replace its scope');
    await idle(); assert(right.innerHTML === sibling, 'navigation changed sibling state');
    ReactDOM.unmountComponentAtNode(left); leftProps = native; mountLeft();
    await until(() => link(left, nativeRoot), 'left remount did not load');
    await idle(); assert(right.innerHTML === sibling, 'remount changed sibling state');
    assert(!counts(right).checked && counts(left).checked, 'remount did not keep sibling settings/fresh left defaults');
    pass('PASS navigation/remount preserve sibling DOM and settings');
    // Supersede ontology loads immediately; the final scope must win.
    leftProps = mixed; mountLeft(); leftProps = native; mountLeft();
    await until(() => link(left, nativeRoot) && !link(left, mixedRoot), 'superseded scope replaced latest widget');
    await idle();
    assert(right.innerHTML === sibling, 'superseded navigation affected sibling');
    expand(left, nativeRoot); expand(right, mixedRoot);
    await until(() => link(left, 'https://example.org/native#D') && link(right, 'https://example.org/mixed#C'), 'widgets lost interactivity after navigation/remount');
    pass('PASS latest scope wins; both widgets remain interactive after remount');
    pass('GREEN: 7 mounted widget checks passed');
  } catch (error) { result.textContent = `RED: ${String(error)}\n${passed.join('\n')}`; console.error(error); }
  finally { this.disabled = false; }
});
mountBoth();
