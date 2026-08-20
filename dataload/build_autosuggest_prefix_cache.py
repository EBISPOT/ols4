#!/usr/bin/env python3
"""
Populate ols_autosuggest_prefix_cache: a precomputed, exact-match cache of
the coldest and most common /api/suggest queries -- short unscoped prefixes.

Run once, after ols_autosuggest and its indexes (idx_autosuggest_trgm,
idx_autosuggest_string) exist and ANALYZE has run.

WHY THIS EXISTS
----------------
/api/suggest fires one live request per keystroke. The backend's exact
pg_trgm query (OlsSearchClient.suggestLabelsUncached) is the correctness-
preserving design chosen in PR #1347 -- it deliberately does NOT use a
prefix-only B-tree shortcut, because that silently drops non-prefix fuzzy
matches (e.g. "cancer" must still surface "liver cancer"). That correctness
requirement is exactly what makes short prefixes slow: they're the least
selective trigram queries, so pg_trgm's GIN index returns the largest
candidate set and pays the largest recheck cost. Measured on the fallback
cluster (16.5M-row ols_autosuggest, 2026-08-20): "can" 5.2s, "canc" 4.4s --
and ontology-scoped queries are NOT faster, because the planner applies the
ontology filter as a post-filter on the same expensive bitmap scan rather
than using idx_autosuggest_onto.

This script precomputes the *exact same* similarity()-ranked result for
every prefix (up to --max-len characters) that actually occurs in the
corpus, so the backend can serve those cold-by-construction first
keystrokes from a simple indexed lookup instead of a live trigram scan.
It changes nothing about match semantics: it's the same query, computed
once instead of on every cold request. See OlsSearchClient.suggestLabelsUncached
for the serving-side fallback that keeps this purely additive: any prefix
this cache doesn't cover, or an ontology filter that needs more matches
than were cached, falls straight through to the unchanged live query.

WHY --max-len DEFAULTS SMALL, AND WHY THIS ISN'T A NAIVE "PRECOMPUTE
EVERYTHING" JOB
-----------------------------------------------------------------------
Building this cache means literally running the live query once per
distinct prefix -- there is no cheaper single-pass way to get an answer
pg_trgm-identical to the live query without reimplementing pg_trgm's
matching and ranking from scratch (a correctness-risk that isn't worth
taking for a caching layer). Per-prefix cost is heavy-tailed: common
letters (measured: "s" 16.3s, "a" 15.3s) cost far more than rare ones
("q" 0.6s, "x" 0.4s), because cost tracks how UNselective the prefix's
trigrams are, not its length.

Full length-1 build measured against the fallback cluster (16.5M-row
ols_autosuggest, 2026-08-20): 3,133 distinct prefixes, 40m35s total. But
51 of those prefixes -- all zero-width/control/format Unicode characters
(U+200B, U+FEFF, C1 controls, etc.) that leaked into the corpus as
encoding artifacts, not real label text -- accounted for 2,144s of that
(88% of total build time) while matching zero rows each: their near-
absence of real trigrams means the GIN index can't narrow the candidate
set at all, so each one degenerates into a near-full-table scan (~42.5s,
suspiciously uniform across all 51 -- that's what a full-table recheck
looks like). No real user will ever type a zero-width joiner as their
first character, so PER_PREFIX_TIMEOUT_MS below skips any prefix that
can't produce a result within a few seconds rather than letting a handful
of degenerate inputs dominate the build -- excluding those 51, the
remaining 3,082 real prefixes took 280s total (91ms/prefix average).
NOTE: this same pathology exists in the *live* query path today,
independent of this cache -- a real request for one of these leaked
characters would take ~42s cold there too. That's a separate, pre-
existing issue worth its own look; this script works around it for the
build, not for live traffic.

A 44-prefix stress sample of length 1-3 (deliberately biased toward
common real letters) averaged 4.9s/prefix, which would put the full
~104,000 distinct length-1..3 prefix set at multiple hours of sustained
scanning if naively run against a live table other consumers are also
querying. That's why --max-len defaults to a conservative value and is
left as an explicit, measured decision at each dataload -- not a
hardcoded "cover everything" assumption. Extend it once real build-time
data (now cheaper to gather thanks to PER_PREFIX_TIMEOUT_MS) justifies
covering length 2 or 3 too.

Usage:
    python build_autosuggest_prefix_cache.py [--max-len N] [--limit-per-prefix N] [pg_env...]

Reads PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD/PGOPTIONS from the
environment, same as the rest of the dataload pipeline.
"""

import os
import subprocess
import sys
import time

DEFAULT_MAX_LEN = 1
DEFAULT_LIMIT_PER_PREFIX = 2000

# See the module docstring: a handful of leaked zero-width/control-character
# "prefixes" degenerate into a near-full-table scan (measured ~42.5s each,
# zero rows). No real user types these as a first character, so any prefix
# that can't produce a result within this budget is skipped rather than
# left to dominate the build -- it just falls through to the live query if
# ever actually requested (OlsSearchClient.tryServeFromCache treats a
# missing prefix as a cache miss, same as one that was never enumerated).
PER_PREFIX_TIMEOUT_MS = 5000


def run_psql(sql: str, env: dict) -> str:
    proc = subprocess.run(
        ["psql", "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, env=env,
    )
    if proc.returncode != 0:
        msg = proc.stderr.strip() or proc.stdout.strip()
        raise RuntimeError(f"psql failed: {msg}")
    return proc.stdout


def get_distinct_prefixes(max_len: int, env: dict) -> list[str]:
    """One prefix list per length, built from a single sequential pass per length
    (cheap: it's a GROUP BY over the existing data, not a trigram query)."""
    prefixes: list[str] = []
    for length in range(1, max_len + 1):
        sql = f"""
            SELECT DISTINCT lower(left(string, {length}))
            FROM ols_autosuggest
            WHERE length(string) >= {length};
        """
        out = run_psql(sql, env)
        prefixes.extend(line for line in out.splitlines() if line.strip())
    return prefixes


def build_cache(max_len: int, limit_per_prefix: int, env: dict) -> None:
    prefixes = get_distinct_prefixes(max_len, env)
    total = len(prefixes)
    print(f"=== Building autosuggest prefix cache: {total} distinct prefixes, "
          f"length 1-{max_len}, cap {limit_per_prefix}/prefix ===")

    run_psql("TRUNCATE ols_autosuggest_prefix_cache;", env)

    t0 = time.time()
    skipped: list[str] = []
    # One prefix per statement (not one giant transaction): keeps any single
    # slow/failed prefix from holding a long-lived transaction open against
    # a table other consumers may be reading, and lets progress be visible
    # and resumable rather than all-or-nothing.
    for i, prefix in enumerate(prefixes, 1):
        escaped = prefix.replace("'", "''")
        sql = f"""
            SET statement_timeout = '{PER_PREFIX_TIMEOUT_MS}';
            INSERT INTO ols_autosuggest_prefix_cache (prefix, rank, string, sim)
            SELECT '{escaped}', row_number() OVER (ORDER BY sim DESC, string ASC), string, sim
            FROM (
                SELECT DISTINCT string, similarity(string, '{escaped}') AS sim
                FROM ols_autosuggest
                WHERE string % '{escaped}'
                ORDER BY sim DESC, string ASC
                LIMIT {limit_per_prefix}
            ) sub;
        """
        try:
            run_psql(sql, env)
        except RuntimeError as e:
            # Almost always "canceling statement due to statement timeout" on one of the
            # leaked zero-width/control-character prefixes (see module docstring). Left
            # out of the cache: the backend falls back to the live query for it, same as
            # any prefix that was never built.
            print(f"  [{i}/{total}] SKIPPED {prefix!r}: {e}", flush=True)
            skipped.append(prefix)
        if i % 100 == 0 or i == total:
            elapsed = time.time() - t0
            rate = i / elapsed if elapsed > 0 else 0
            eta = (total - i) / rate if rate > 0 else 0
            print(f"  [{i}/{total}] {elapsed:.0f}s elapsed, {rate:.2f} prefix/s, "
                  f"eta {eta:.0f}s", flush=True)

    if skipped:
        print(f"  {len(skipped)} prefix(es) skipped (timed out at {PER_PREFIX_TIMEOUT_MS}ms): "
              f"{skipped}")

    run_psql(f"TRUNCATE ols_autosuggest_prefix_cache_meta; "
              f"INSERT INTO ols_autosuggest_prefix_cache_meta (max_prefix_len) VALUES ({max_len});", env)
    run_psql("ANALYZE ols_autosuggest_prefix_cache;", env)

    elapsed = time.time() - t0
    print(f"=== Done: {total} prefixes in {elapsed:.0f}s ({elapsed/max(total,1):.2f}s/prefix avg) ===")


def main():
    max_len = DEFAULT_MAX_LEN
    limit_per_prefix = DEFAULT_LIMIT_PER_PREFIX
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--max-len" and i + 1 < len(args):
            max_len = int(args[i + 1])
            i += 2
        elif args[i] == "--limit-per-prefix" and i + 1 < len(args):
            limit_per_prefix = int(args[i + 1])
            i += 2
        else:
            i += 1

    if max_len < 1:
        print("Nothing to do: --max-len < 1", file=sys.stderr)
        return

    env = dict(os.environ)
    build_cache(max_len, limit_per_prefix, env)


if __name__ == "__main__":
    main()
