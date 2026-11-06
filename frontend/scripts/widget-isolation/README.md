# Mounted widget isolation regression

This is a development-only browser harness, outside the product entry points.
It compiles the real exported `createEntityTree` factory together with its host
ReactDOM renderer. Requests go unchanged to the real API on the current origin;
there are no component, store, network or API mocks.

From `frontend`, run `node scripts/build-widget-isolation-harness.mjs`, then open
`/widget-isolation/index.html` on the local frontend server and click **Run regression**.
The server must serve `dist` and proxy `/api` to the task-owned local backend.
The existing `native` and `mixed` fixture ontologies must be loaded, with roots
`https://example.org/native#R` and `https://example.org/mixed#P` respectively.
No dependency installation is needed.

Expected result: **GREEN: 7 mounted widget checks passed**. The harness covers
separate roots/scopes, child loading in both trees, independent settings, public
navigation/render callbacks, preserved same-scope state, navigation/remount with
an unchanged sibling, and rapid scope replacement followed by interactivity.
On the pre-fix widget it reports **RED: Error: two mounted widgets did not retain
their different roots/scopes**.

The **Navigate left to mixed** and **Remount left** controls also support manual
verification. Toggle a right-hand checkbox and expand its tree first; neither
control should reset the right tree or change its settings.

Generated harness bundles stay under `dist/widget-isolation` and are not product
assets or committed source. This harness requires the local reproducing data and
is deliberately separate from the dependency-free Node tree test suite.
