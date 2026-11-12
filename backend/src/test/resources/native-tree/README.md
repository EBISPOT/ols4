# ECTO-shaped native tree fixture

`fixture.json` is a hand-authored post-linker entity fixture. It models ECTO's
failure shape, not the ECTO dataset. Both original class roots are imported.
R -> K -> C/I needs imported connector K; K is also obsolete. C is an active
native class in S; I is an active native individual in S2. R -> D -> X/N contains
imported selected member X (S) and active native N outside both subsets. Thus
native S must retain R/K/C and exclude D, despite N. Z -> O has only an obsolete
native member in S. P -> Q models imported property root to active native property.
S and S2 are children of oboInOwl:SubsetProperty; membership uses oboInOwl:inSubset.

NativeTreeIT loads generated-schema columns and gzip `_json` explicitly. The local
browser fixture uses the existing task2 manual SQL/gzip helper. Neither is an
execution of RDF2JSON, the linker, or the Rust production loader. Those pipeline
checks remain a separate integration gate. Native flags, closures, obsolete flags,
and counts are explicit test inputs; production data provenance is not claimed.

The API regression compares eligible members, connectors and distinct-member
counts. The focused fixture includes class/individual/property routes and keeps
candidate class filters separate from eligible member selection.
