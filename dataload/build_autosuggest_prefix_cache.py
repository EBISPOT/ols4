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
trigrams are, not its length. Measured against the fallback cluster:
length-1 prefixes (~3,100 distinct) took under 10 minutes total; a 44-
prefix stress sample of length 1-3 averaged 4.9s/prefix, which would put
the full ~104,000 distinct length-1..3 prefix set at multiple hours-to-days
of sustained heavy scanning of the base table if run against the same
data after it's already live and being queried by other consumers. That's
why --max-len defaults to a conservative value and is left as an explicit,
measured decision at each dataload -- not a hardcoded "cover everything"
assumption. Extend it once real build-time data at higher lengths exists
for the target hardware/dataset size.

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
    # One prefix per statement (not one giant transaction): keeps any single
    # slow/failed prefix from holding a long-lived transaction open against
    # a table other consumers may be reading, and lets progress be visible
    # and resumable rather than all-or-nothing.
    for i, prefix in enumerate(prefixes, 1):
        escaped = prefix.replace("'", "''")
        sql = f"""
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
        run_psql(sql, env)
        if i % 100 == 0 or i == total:
            elapsed = time.time() - t0
            rate = i / elapsed if elapsed > 0 else 0
            eta = (total - i) / rate if rate > 0 else 0
            print(f"  [{i}/{total}] {elapsed:.0f}s elapsed, {rate:.2f} prefix/s, "
                  f"eta {eta:.0f}s", flush=True)

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
