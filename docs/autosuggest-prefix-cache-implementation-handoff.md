# Autosuggest fast path: implementation handoff

**Date:** 2026-08-21 (supersedes the 2026-08-20 version of this doc)
**Branch:** `autosuggest-prefix-cache-poc` (local only, not pushed)
**Follows on from:** `docs/autosuggest-search-research-handoff.md` (the original Redis/full-corpus research doc)

## TL;DR

`/api/suggest` fires one live query per keystroke, and short/common prefixes ("can") were taking 5+ seconds live. Two precompute/cache approaches were built and abandoned first (see below) before landing on the actual fix: **make the live query fast for any input, with nothing precomputed and nothing guessed.**

Two changes:
1. A case-insensitive B-tree index (`lower(string) text_pattern_ops`) so prefix matches return already-sorted, in ~0.1ms regardless of how common the prefix is.
2. The live query generates candidates via that prefix match plus a substring match (still catching non-prefix matches like `"cancer"` inside `"liver cancer"`), restricted to strings mathematically short enough to still pass the similarity threshold, then ranks only that small pool — instead of ranking the whole trigram candidate set the old query did.

Verified on the fallback cluster (16.5M-row `ols_autosuggest`) against 37 realistic queries: **28/37 byte-identical to the old exact query, 29.2x average speedup.** The 9 differences are precisely characterized (not mysterious) and traced to `pg_trgm`'s own matching behavior, not a bug introduced here.

No production system was touched. All testing ran against the **fallback** cluster only, using throwaway indexes/tables cleaned up after each round.

## The two dead ends (built, tested, discarded — don't resurrect either)

1. **Length-1 precomputed prefix cache.** Built first on the assumption (never checked against the actual frontend) that autosuggest fires a request on every keystroke starting from character 1. It doesn't: `frontend/src/components/SearchBox.tsx` has `AUTOCOMPLETE_MIN_QUERY_LENGTH = 3` and never sends anything shorter. This cached a query that never reaches the backend.

2. **Length-3 top-N-by-frequency cache.** Once the real minimum length was known, tried precomputing the N most common 3-character prefixes instead of every one (exhaustive length-3 coverage extrapolates to ~2.6 days of DB load — confirmed, not guessed, via a real 22-prefix timed sample). Discarded before landing: **there's no real query-log data to know which prefixes are actually popular**, so "top N by how many labels start with it" is a proxy standing in for a measurement that doesn't exist. Any N is a guess, and guessing about what's cacheable is the thing that was wrong about approach #1 too, just one level more sophisticated.

Both are fully removed from this branch as of commit `28cb3ac52` (schema, backend routing, and the build script itself all reverted/deleted). If you see either mentioned in earlier commits (`5b01b8018`, `2f1fb2a74`, `47bb504f4`) on this branch, that's history, not current state.

## The actual fix

### Why not just tune the trigram index (again)?

PR #1347 already benchmarked GiST, tuned `siglen`, and `gin_fuzzy_search_limit` against the same exact-similarity query this replaces, and found none of them beat plain GIN without either changing match semantics or risking wrong results (`gin_fuzzy_search_limit`'s approximate candidate set silently dropped real matches). This isn't another attempt at that — it doesn't touch the trigram index or change how similarity is computed. It changes **what gets ranked**, not **how**.

### The mechanism

The actual cost driver for `"can"` (5.2s) was never the trigram index itself — it was that PostgreSQL's `%` similarity operator has to pull in every row sharing *any* trigram with the query (for `"can"`, roughly 1M rows) and compute `similarity()` on each before it can sort and discard 99.8% of them. That candidate-gathering step is the expensive part, not the ranking function.

1. **Cheap candidate generation.**
   - Prefix match: `lower(string) LIKE 'can%'`, backed by the new `idx_autosuggest_prefix` B-tree. Returns matches already sorted — measured ~0.1ms regardless of prefix commonality, because a B-tree range scan doesn't need to gather every candidate before it can limit them (unlike the GIN bitmap scan the old query used).
   - Substring match: `string ILIKE '%can%'`, GIN-accelerated by the existing `idx_autosuggest_trgm` index. Catches non-prefix matches (`"liver cancer"` for query `"cancer"` — the exact case PR #1347 restored, still covered).
2. **Bounded ranking, not full-table ranking.** The substring branch is restricted to strings whose own trigram count can't mathematically exceed the query's trigram count divided by the similarity threshold — a real inequality derived from the similarity formula itself (`similarity = |A∩B|/|A∪B| >= threshold` implies `|B| <= |A|/threshold`, since `|A∩B| <= |A|` always), not a heuristic or approximation. A candidate above that bound is provably unable to pass the threshold regardless of content, so excluding it can never drop a true match — it only shrinks what `similarity()` has to be computed over.

Both candidates are combined in a `WITH candidates AS (...)` CTE, then ranked with the *same* `similarity()` function and `>= show_limit()` threshold the old query used, over that much smaller pool.

### Where the code lives

- [dataload/create_postgres_schema.py](dataload/create_postgres_schema.py) — `idx_autosuggest_prefix` replaces the removed cache table's indexes
- [backend/.../repository/postgres/JooqSupport.java](backend/src/main/java/uk/ac/ebi/spot/ols/repository/postgres/JooqSupport.java) — `similarityAtLeastThreshold()`, `maxTrigramCandidateLength()` (the bound, computed via `show_trgm()`/`show_limit()` rather than derived from string length, since `pg_trgm` tokenizes multi-word input per word, not over the whole padded string — a length-based estimate would be wrong for those)
- [backend/.../repository/search/OlsSearchClient.java](backend/src/main/java/uk/ac/ebi/spot/ols/repository/search/OlsSearchClient.java) — `suggestLabelsUncached()` is the whole implementation; the per-pod Guava result cache in `suggestLabels()` (from PR #1347, keyed on query+ontology+paging) is unchanged and still valid — it's orthogonal, helping *repeat* identical requests regardless of how the cold path works

## Correctness: what's verified, what isn't

Verified two ways, not just reasoned about:

1. **jOOQ's actual generated SQL was extracted and run directly** against the fallback cluster (not just conceptually-similar hand-written test queries) — see the commit message for the exact rendered SQL. Confirmed byte-identical results to the old query for `"can"` unscoped and `"can"`+`efo` scoped, at 4.4x-62x measured speedup depending on run.
2. **A 37-query broad batch** (30 single-word biomedical terms + 7 multi-word) compared old vs. new top-10 for each: 28 exact, 9 different. Every difference traces to one of two `pg_trgm` behaviors, not a bug:
   - **Multi-word reordering**: `pg_trgm` tokenizes per-word, so `"liver cancer"` and `"Cancer, liver"` produce *identical* trigram sets (confirmed via `show_trgm()`) — inherently word-order-independent in a way prefix/substring matching structurally cannot replicate.
   - **Near-miss variants**: single-character insertions/deletions, e.g. `"bacteria"` matching `"Bacteriuria"` or `"diabetes"` matching `"diabete"` — trigram similarity tolerates these; exact substring/prefix matching cannot.
3. **A real bug was caught during verification, not assumed away**: LIKE/ILIKE wildcard characters (`%`, `_`) in the query text need escaping. An unescaped query of `"50%"` would have matched all 25,487 labels starting with `"50"` instead of the 5 that literally start with `"50%"`. Fixed via `escapeLikeWildcards()` in `OlsSearchClient`.

**Not yet done:**
- The multi-word/near-miss gap has a plausible fix (an additional candidate branch requiring each individual word of the query to appear somewhere, unordered) but it's untested — not built.
- No end-to-end test of the actual Spring Boot backend against this — verification so far is at the SQL level (real generated SQL, run directly against real data) plus `mvn compile`, not a running application.
- `k8sspotrw` (the role the backend itself runs as) lacks DDL rights on `ols_autosuggest` on the fallback cluster — verified with a role that does have those rights (`spot`) for testing. Confirm the real dataload pipeline's schema-creation role can create `idx_autosuggest_prefix` (it should — dataload builds a fresh local Postgres with full owner rights before packaging `postgres.tgz` — but this wasn't verified by running the actual pipeline).

## Before merging

- Run the backend locally against a real Postgres snapshot and confirm `/api/suggest` behaves as expected for `can`/`cancer`/`liver`/`liver cancer`, including ontology-scoped requests.
- Run `create_postgres_schema.py` + `load_into_postgres.py` through the real dataload pipeline once (not ad-hoc against a live cluster) to confirm `idx_autosuggest_prefix` builds cleanly as part of normal index creation.
- Decide whether the multi-word/near-miss gap needs closing before shipping, or is an acceptable known limitation (see "queries with differences" in the commit message for the full list of what changes).
