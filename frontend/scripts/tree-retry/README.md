# Mounted tree failure and retry regression

From `frontend`, with the task-owned `native`, `mixed` and `paging` fixtures on
`127.0.0.1:18043`:

```sh
node scripts/tree-retry/build.mjs
PORT=13049 node scripts/tree-retry/proxy.mjs
```

Open `http://127.0.0.1:13049/tree-retry/index.html` and click **Run regression**.
Expected: **GREEN: 7 mounted retry checks passed**. The harness mounts the actual
public widgets, plus app-context `EntityTree` instances inside a real store and
router for selected ancestry and large tables. It clicks real mounted controls.
No component/store/thunk mocks or additional dependencies are used.

The localhost-only proxy forwards successful requests to the real backend. Its
explicit test endpoint arms one 503, delayed response, or successful empty page.
It does not alter database contents. The empty page is a UI-state fixture; it is
not evidence about backend filtering. API requests and parameters are logged.

Checks cover root failure/retry and two-widget isolation; default eligible-count
retry retaining the exact expanded DOM node; filter retry using current settings
and showing loading; empty success; cancelled old scope failures; selected
ancestry recovery; and independent large-parent table retry preserving page,
search text and sibling DOM. Initial setting effects can cancel first requests,
so faults are armed after initialization settles.

For a reproducible pre-fix counterexample:

```sh
TREE_RETRY_BASELINE=4c759e3f8 node scripts/tree-retry/build.mjs
```

Open `/tree-retry-baseline/index.html` on the same proxy and run it. Only the three
changed production tree/list/slice modules are loaded from that Git revision;
the identical test should fail because root errors are silent. Working files
are never changed by this baseline build.

The proxy also serves the built application from `dist`, overriding runtime API
configuration only on port13049. For manual built-app verification, open
`/ontologies/native?tab=classes`, arm a fault via
`/__test/arm?channel=app&kind=roots&mode=fail`, toggle **Show imported terms**,
then click **Retry tree**. For default eligible-count retry, keep imports on and
obsolete off, turn counts off, arm `kind=counts`, then turn counts on and click
**Retry counts**. Screenshots should show errors disappearing and the real data
returning. `kind=roots&mode=empty` exercises the separate successful-empty state.

Generated bundles stay in `dist/tree-retry*` and are not committed. Stop only
this proxy when finished; do not restart the shared backend/frontend services.
