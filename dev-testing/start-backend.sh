#!/usr/bin/env bash
java -jar -Dols.postgres.host=${OLS_POSTGRES_HOST:-localhost} -Dols.postgres.port=${OLS_POSTGRES_PORT:-5432} -Dols.postgres.db=${OLS_POSTGRES_DB:-ols4} -Dols.postgres.user=${OLS_POSTGRES_USER:-postgres} $OLS4_HOME/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar
