# Tree preference navigation browser regression

From `frontend`, run `node scripts/tree-preferences/build.mjs`, serve `dist`
(for example `python3 -m http.server 3058 --directory dist`), and open
`/tree-preferences/`. Click **Run tree preference regressions** and wait for
`GREEN`. A failed assertion reports `RED` in the page and browser console.

This mounted React test uses deterministic in-memory HTTP responses and the
production component, effects, thunks, reducers and public widget API. It checks
term changes, remounts and browse return, complete selected paths outside
preferred roots, a selected obsolete term's temporary visibility override, API
and entity-type scope resets, and independent widget stores. It catches the
former selection-dependent settings reset. It complements the Node tree suite
and separate browser checks against real ontology API data.
