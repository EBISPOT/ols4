# Property obsolete fixture

This hand-authored six-property ontology isolates the property hierarchy visibility
contract. Tests insert gzip JSON and explicit PostgreSQL hierarchy columns; the
same JSON supplies the task-owned local API/browser fixture. This is not evidence
of RDF generation, Rust linking or the production loader.

- R is an active native root. A is an active native child; X an active imported child.
- K is an obsolete imported child of R, required to reach active native C.
- O is an obsolete native leaf under R with no active descendant.
- C/A/X belong to subset S; O belongs to S2. K belongs to neither.

With imports and obsolete hidden, treeContext children of R are A/K, and K
expands to C. Ordinary children are A. S2 alone returns no children; S/S2 union
returns A/K once each. Showing obsolete adds O. Native eligible descendant counts
are R=2/K=1 with obsolete hidden and R=3/K=1 with obsolete shown; retained K is
never counted as a native member.

Property children and ancestors historically included obsolete properties without
an obsolete parameter. Omitted includeObsoleteEntities therefore remains true;
explicit false now applies the predicate. Existing Java overloads delegate true.
Selected property ancestry intentionally requests true for both obsolete/imported
visibility so required selected paths can still be reconstructed.
