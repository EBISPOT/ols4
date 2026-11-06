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
class/individual/property list routes. Property hierarchy routes preserve the
historical `includeObsoleteEntities=true` default when omitted; explicitly passing
`false` filters obsolete members, while tree context retains required connectors.
Property child `searchQuery` filters both page and total using literal,
case-insensitive label containment (blank means unfiltered; `%` and `_` are literal).

The frontend uses one context option across root, expansion and paginated child
requests. For Classes and Individuals, selected ancestry uses the additive
`/ontologies/{onto}/entities/{iri}/treeAncestors` route. It reuses complete mixed
reachability across direct/rdf:type and hierarchical edges, returns class and
individual intermediate nodes, and excludes the selected IRI itself. Results are
IRI-deduplicated (class wins punning), ordered by IRI and paged; the frontend
exhausts every page. Selected/specified-root context includes hidden imported and
obsolete connectors and is retained when no preferred root is on that path.
Ordinary public `/ancestors` and `/hierarchicalAncestors` semantics are unchanged. Optional branches still use eligible-member
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

Classes and Individuals count, expansion and table search all use
`/classes/{iri}/treeChildren`. The parent IRI may name a class or individual.
The default `memberType=all` unions direct/hierarchical classes and rdf:type/hierarchical
individuals, with class-first punning. `memberType=individual` uses only the two
individual routes and individual seed members, retaining individual identity even
for punned IRIs. Both modes deduplicate by IRI before search, count and pagination,
using stable type, case-insensitive label and IRI ordering. The legacy
`/classes/{iri}/individuals` endpoint remains rdf:type-only.

Subset definition identity is independent of currently eligible member counts.
A selected imported-only or obsolete-only subset remains checked with count zero
when visibility hides its last member; the tree is explicitly empty. Restoring
visibility restores its members, and manually unchecking the filter can broaden
the tree. Selections reset on ontology/type/API scope changes or a successfully
loaded definition set that no longer contains them; failures and stale responses
cannot clear them.
