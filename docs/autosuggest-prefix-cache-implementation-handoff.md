# Autosuggest prefix cache: implementation handoff

**Date:** 2026-08-20
**Branch:** `autosuggest-prefix-cache-poc` (local only, not pushed — 2 commits on top of `dev`)
**Follows on from:** `docs/autosuggest-search-research-handoff.md` (the Redis/full-corpus research doc)

## TL;DR

The Redis research doc's own conclusion — "measure everything, don't commit to new infra" — turned out to point somewhere cheaper than Redis. The actual bottleneck traced back to one thing: `/api/suggest` fires one live PostgreSQL query per keystroke, and the *first few characters* a user types are both (a) guaranteed cold on every request (nothing to cache yet) and (b) the most expensive queries the trigram index ever runs, because short prefixes are the least selective. Verified on the fallback cluster's real 16.5M-row `ols_autosuggest`: `"can"` costs 5.2s, `"canc"` costs 4.4s, live, every time, scoped or not.

This branch adds a **precomputed exact-match cache** for those queries — same `pg_trgm` ranking, computed once offline instead of on every cold request — with a fallback rule that makes it provably impossible for the cache to ever return something different from what the live query would. Length-1 prefixes (every user's literal first keystroke) are cached by default; length 2-3 are scoped out for now with real cost data explaining why, not guessed.

No production system was touched. All testing ran against the **fallback** cluster only, using throwaway tables cleaned up before finishing.

## What's on the branch

Two commits:

1. **`5b01b8018`** — the cache itself: schema (`ols_autosuggest_prefix_cache`, `ols_autosuggest_prefix_cache_meta`, plus a supporting `idx_autosuggest_string` btree), the dataload build script, and the backend routing/fallback logic.
2. **`2f1fb2a74`** — a fix found by actually running the build script against real data: 51 leaked Unicode control/zero-width characters were eating 88% of build time for zero benefit. See below.

Files touched:
- [dataload/create_postgres_schema.py](dataload/create_postgres_schema.py) — new table DDL, plus `idx_autosuggest_string`
- [dataload/build_autosuggest_prefix_cache.py](dataload/build_autosuggest_prefix_cache.py) — new, the precompute job
- [dataload/load_into_postgres.py](dataload/load_into_postgres.py) — wires the build in after indexes + `ANALYZE`, behind `--prefix-cache-max-len` (default 1)
- [backend/.../repository/postgres/JooqSupport.java](backend/src/main/java/uk/ac/ebi/spot/ols/repository/postgres/JooqSupport.java) — table refs
- [backend/.../repository/search/OlsSearchClient.java](backend/src/main/java/uk/ac/ebi/spot/ols/repository/search/OlsSearchClient.java) — `suggestLabelsUncached` now tries the cache first via `tryServeFromCache`, falling back to the renamed `suggestLabelsLive` (byte-for-byte the old `suggestLabelsUncached` body, unchanged)

`mvn compile` passes clean (JDK 21, all 114 source files). No unit tests exist for `OlsSearchClient` in this repo currently (PR #1347 validated the same way: manual run against a live DB) — recommend the same before merge, see "Before merging" below.

## Why not the Redis/full-corpus approach from the research doc

Two things narrowed the problem a lot from where the research doc left it:

1. **PR #1347 (9 days before this work) already tried and reverted the obvious cheap fix.** A prefix-only B-tree lookup was live briefly, got reverted for a correctness bug: it silently drops non-prefix fuzzy matches (`"cancer"` stopped surfacing `"liver cancer"`). It also benchmarked GiST+KNN, tuned `siglen`, and `gin_fuzzy_search_limit` — all disqualified (one regressed a different path 12x, the other returns a *provably wrong* top-10 on short queries). This ruled out "tune the index" as a path entirely, for good documented reasons.

2. **The one assumption left standing — that ontology-scoped queries are already fast — turned out to be wrong.** PR #1347's benchmark only tested `"cancer"` scoped to `efo`. Re-tested `"can"` scoped to `efo` on the fallback cluster: **5.2s, same as unscoped.** The planner applies the ontology filter as a post-filter *after* the same expensive trigram bitmap scan, never touching `idx_autosuggest_onto`. So "just rely on ontology scoping" isn't a mitigation either.

Given both of those, a full Redis Search deployment was still on the table (the user explicitly authorized standing one up if needed) — but building *anything* that covers the full query space requires either (a) Redis's own bulk-index build (one pass over 12M documents, genuinely fast) or (b) precomputing answers to specific queries ourselves. Tried (b) first because it reuses the exact same `pg_trgm` semantics (zero new infra, zero new matching-semantics risk) — and it worked well enough for the highest-value slice (first-keystroke cold start) that Redis was never actually needed. That's not a claim Redis wouldn't also work — it's that this got to "fast enough, correctness-safe, in-place" first.

## The correctness argument (the part that matters most)

The cache stores, per prefix, the same `similarity()`-ranked result the live query computes — same function, same ordering, just computed once instead of per-request. It is **not** a prefix-only shortcut (that's exactly what PR #1347 reverted); a cached prefix like `"can"` still contains true fuzzy substring matches like `"American"` if they clear the similarity threshold, same as live.

Capped at 2000 rows/prefix as a storage bound (config: `ols.search.max-rows=1000`, so 2x headroom). The only thing the cap introduces is *uncertainty about the tail* — never about the head. `OlsSearchClient.tryServeFromCache` handles this with one rule:

- **Cached row count below the cap** → the build captured the complete result set for that prefix (not just top-N). Trust it unconditionally, at any offset/rows/ontology filter.
- **Cached row count at the cap** → possibly truncated. Only trust it if the requested `(start, rows)` window, *after* ontology filtering, is fully contained within what's cached. If not — fall back to the live query.
- **Zero cached rows for a prefix** → could be a genuine cache miss (this exact string was never enumerated — see below) or, in practice never happens, a built prefix with truly zero matches. Both cases are handled identically and correctly by falling back to live.

Verified this empirically, not just on paper: built a throwaway cache for `"can"`, then confirmed via SQL `EXCEPT` that `(cache-filtered-to-efo)` and `(live query filtered to efo)` are exactly the same set in both directions — zero rows either way. That's on real fallback data, not a synthetic case.

One subtlety worth being explicit about: the cache's prefix *keys* are enumerated from `lower(left(string, N))` over real corpus labels — i.e., "strings that are the literal start of some real label." That's a heuristic for *which* queries are worth precomputing, not a restriction on what each cached entry can match (each entry is still a full fuzzy `%` match, unrestricted). A prefix a user types that isn't the literal start of any label (rare, but possible) is a cache miss and correctly falls through to live — this is exactly the "zero cached rows" case above.

## Real numbers from the fallback cluster

All measured against production data (16.5M rows, 2026-08-20), fallback cluster only, cleaned up after.

**Serving speed** (once cached):
| Query | Live (before) | Cached (after) |
|---|---:|---:|
| `"can"` unscoped | 5.2s | **32ms** |
| `"can"` + efo scoped | 5.2s | 2.2s *(see caveat below)* |
| `"cancer"` unscoped | 1.36s | **33ms** |

Caveat on the scoped number: testing used `k8sspotrw` (the same role the backend itself runs as), which turns out to **lack DDL rights on `ols_autosuggest`** on the fallback cluster (`CREATE INDEX` failed: "must be owner of table"). Couldn't create the supporting `idx_autosuggest_string` there to prove the fast path end-to-end. This is **not a blocker for the real implementation** — dataload builds a fresh local Postgres from scratch (full owner rights) and ships it as `postgres.tgz`; the index is created there via the normal schema/index pipeline, same as every other index in this table. It only blocked *my* ad-hoc live-cluster benchmark. Worth a real end-to-end timing run once this deploys through the normal pipeline, to confirm the scoped path actually hits the few-ms range the design predicts.

**Build cost** (this is the one that actually shaped the scope):
- 44-prefix curated stress sample (mix of common/rare, length 1-3): averaged 4.9s/prefix. Naive extrapolation to the full ~104,000 distinct length 1-3 prefix set → multiple days. Not attempted.
- Full length-1 build (3,133 real distinct prefixes): **40m35s**, but **88% of that (2,144s) was 51 zero-width/control-character prefixes matching zero rows** — encoding garbage leaked into the corpus, not real label text (a real user will never type a zero-width joiner as their first character). Excluding those: **3,082 real prefixes, 280s total, 91ms/prefix average.**
- Fixed in the second commit: `SET statement_timeout` per prefix (5s), skip-and-log on timeout. Makes the real cost ~5 minutes for length-1, and — more importantly — protects any future length-2/3 extension from the same pathology, since it wasn't re-run against the full fixed script live (classifier-blocked further live-cluster testing, see below).

**Side finding, logged but not fixed here**: those same 51 pathological characters would make the *live* query path take ~42s today too, if a real request ever contained one (e.g. pasted emoji). Pre-existing, independent of this cache. Worth its own look — flagging, not fixing, since it's out of scope for "make cold-start suggest fast."

## What's NOT done / recommended next steps

1. **Length-2/3 extension**: not attempted with the timeout fix applied (got classifier-blocked from further live-cluster DDL/testing — see below). Given the length-1 result (91ms/prefix average once garbage is filtered), length-2 (~19,310 distinct prefixes) is plausibly in the 20-30 minute range, but that's extrapolation, not measurement. Worth an actual timed run before raising `--prefix-cache-max-len` past 1.

2. **End-to-end backend validation**: `mvn compile` passes, and the logic was manually verified against real data at the SQL level, but the actual Spring Boot app was never run against a live DB with this code in this session (no local dev DB set up here). PR #1347's own test plan is the right template: run the local backend against a real Postgres, verify cache hits return identical results to a live-query baseline for `can`/`cancer`/`liver`/`liver cancer`, confirm ontology-scoped filtering, confirm cache-miss fallback for an uncached prefix.

3. **Scoped-path timing confirmation**: couldn't create the supporting index on the fallback cluster (permission gap above) to prove the ontology-scoped path hits its predicted few-ms speed rather than the 2.2s I actually measured without that index.

4. **Live-path Unicode pathology** (the ~42s-on-garbage-input issue): separate, pre-existing, not fixed here.

5. This session hit an auto-mode classifier block partway through further live-cluster verification (a `CREATE TABLE` using real, non-test-suffixed table names, and later even a read-only `\dt`). Reasonable caution on its part — respected it rather than pushing through. Means the fixed build script's actual timing (with the timeout skip) wasn't re-validated live; only reasoned about from the same data.

## Before merging

- Run the backend locally against a real Postgres snapshot (dev or a fresh fallback-cluster dump) and repeat PR #1347's manual test plan, now including cache-hit and cache-miss cases.
- Run `dataload/build_autosuggest_prefix_cache.py --max-len 1` through the real dataload pipeline once (not ad-hoc) to confirm the ~5-minute number holds when it's not fighting for DB permissions.
- Decide on `--prefix-cache-max-len` for length 2 based on an actual timed run, not the extrapolation above.
- Consider whether the live-path Unicode pathology deserves its own fix (e.g. reject/short-circuit non-printable leading characters at the controller before they ever reach Postgres).
