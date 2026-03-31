#!/usr/bin/env bash
pg_ctl -D "$PGDATA" -l "$PGDATA/postgres.log" start
