# Tree preference navigation browser regression

From `frontend`, run `node scripts/tree-preferences/build.mjs`, serve `dist`
(for example `python3 -m http.server 3058 --directory dist`), and open
`/tree-preferences/`. Click **Run tree preference regressions** and wait for
`GREEN`. A failed assertion reports `RED` in the page and browser console.

This mounted React test uses deterministic in-memory HTTP responses and the
production component, effects, thunks, reducers and public widget API. It checks
term changes, remounts and browse return, embedded class-to-individual table
navigation, individual-to-class and individual-to-individual tree clicks, and
browser Back/Forward. It also checks complete selected paths outside preferred
roots, a selected obsolete term's temporary visibility override, cancellation
and stale responses during type changes, API/ontology resets, first entry via
Individuals/Properties, and independent widget stores.

Display settings belong to the current API and ontology, so ordinary mixed
navigation and explicit type tabs preserve them. The preferred-root choice is
remembered across tabs but only filters Classes. Results and subset selection
remain entity-type scoped. These mounted checks complement the Node tree suite
and separate browser checks against real ontology API data.
