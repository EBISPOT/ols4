# Tree browsing context

V2 entity list routes and child routes accept optional `treeContext=true`.
The default is `false`; existing list filtering behavior is unchanged.
This option is intended for tree roots, expansions, and their paginated child
fallback, not counts of members.

With tree context enabled, import and obsolete filters select eligible **members**.
Optional `subsetTree` narrows those same members before ancestry is computed.
The response contains candidates on paths to at least one eligible member,
including imported or obsolete connectors needed to reach that member. Candidate
root flags, entity type, parent IRI and search text still apply to returned rows;
they do not narrow the member seeds. In particular, an individual selected by a
subset can retain its class ancestors. Paths never cross ontology boundaries.

Examples:

- Native roots: `/api/v2/ontologies/ecto/classes?hasDirectParents=false&isDefiningOntology=true&includeObsoleteEntities=false&treeContext=true`
- Native children: `/api/v2/ontologies/ecto/classes/{doubleEncodedIri}/hierarchicalChildren?includeImportedEntities=false&includeObsoleteEntities=false&treeContext=true`
- Selected subset: add `subsetTree={subsetIri}` to either request. Multiple IRIs are comma separated; the member sets are unioned and deduplicated.

The option is executed by class direct/hierarchical children, individual
hierarchical children, class individuals and property children routes, as well as
class/individual/property list routes. Existing property-child obsolete-filter
behavior is unchanged; this parameter does not add missing visibility controls.

The frontend uses one context option across root, expansion and paginated child
requests. Selected ancestry requests retrieve complete context and label hidden
imported and obsolete connectors. Optional branches still use eligible-member
selection. Root/child context does not mean imported members are shown as eligible:
a branch with only hidden members disappears unless it is needed by another
eligible member in the same selected subset set.

Descendant badges use `/entities?facetFields=treeAncestor` with ordinary native,
obsolete and direct `subset` member filters, without `treeContext`. They count
eligible members and exclude connectors. Direct-child page totals use context so
that pagination agrees with the rows being displayed.

Implementation reuses JooqSupport.treeReachability with perMember=false for one
ontology/IRI closure. Count facets retain perMember=true. The same seed visibility
whitelist is used for query and child paths; there are no independent native and
subset closures to accidentally retain unrelated branches.
