#!/usr/bin/env bash
# Stop PostgreSQL if running and remove data
pg_ctl -D "$PGDATA" stop 2>/dev/null || true
if [ -n "$PGDATA" ]; then
    find "$PGDATA" -mindepth 1 -delete 2>/dev/null || true
fi
