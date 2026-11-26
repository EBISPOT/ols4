package uk.ac.ebi.spot.ols.service;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class PostgresClient {

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.host:localhost}")
    private String host;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.port:5432}")
    private int port;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.db:ols4}")
    private String database;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.user:ols}")
    private String user;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.password:}")
    private String password;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.schema:}")
    private String schema;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.max-pool-size:30}")
    private int maxPoolSize;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.min-idle:5}")
    private int minIdle;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.connection-timeout-ms:2000}")
    private int connectionTimeoutMillis = 2000;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.expensive-query-concurrency:4}")
    private int expensiveQueryConcurrency = 4;

    @org.springframework.beans.factory.annotation.Value("${ols.postgres.expensive-query-timeout-seconds:25}")
    private int expensiveQueryTimeoutSeconds = 25;

    private Semaphore expensiveQueryPermits;
    private Semaphore bulkQueryPermits;
    private HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(PostgresClient.class);

    /**
     * Decompress a gzip-compressed _json BYTEA column and parse as JSON.
     */
    public static String decompressJson(ResultSet rs, String column) throws SQLException {
        return decompressJson(rs.getBytes(column));
    }

    /**
     * Decompress a gzip-compressed BYTEA column by position index.
     */
    public static String decompressJson(ResultSet rs, int columnIndex) throws SQLException {
        return decompressJson(rs.getBytes(columnIndex));
    }

    public static String decompressJson(byte[] compressed) throws SQLException {
        if (compressed == null) return null;
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
             Reader reader = new InputStreamReader(gis, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder(compressed.length * 4);
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (java.io.IOException e) {
            throw new SQLException("Failed to decompress _json", e);
        }
    }

    @PostConstruct
    public void init() {
        if (connectionTimeoutMillis <= 250 || expensiveQueryConcurrency < 1
                || TimeUnit.SECONDS.toMillis(expensiveQueryTimeoutSeconds) <= connectionTimeoutMillis) {
            throw new IllegalArgumentException("Configure a positive query concurrency and a query deadline "
                    + "greater than the connection acquisition timeout (which must exceed 250 ms)");
        }
        int permits = Math.min(expensiveQueryConcurrency, Math.max(1, maxPoolSize - 1));
        expensiveQueryPermits = new Semaphore(permits);
        // Keep one expensive slot available for interactive traversal when the pool can spare it.
        bulkQueryPermits = new Semaphore(Math.max(1, permits - 1));
        HikariConfig config = new HikariConfig();
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        if (schema != null && !schema.isEmpty()) {
            // currentSchema replaces search_path outright (it doesn't append to the default), so a
            // non-public schema must list public explicitly or extension functions installed there
            // by default (pg_trgm's similarity()/%, pgvector's <=>) stop resolving unqualified.
            String searchPath = "public".equals(schema) ? schema : schema + ",public";
            url += "?currentSchema=" + searchPath;
        }
        config.setJdbcUrl(url);
        config.setUsername(user);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeoutMillis);
        config.setValidationTimeout(Math.min(1000, connectionTimeoutMillis - 1));
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        dataSource = new HikariDataSource(config);
        logger.info("PostgreSQL connection pool initialized: {}:{}/{} (maxPoolSize={}, minIdle={})",
                host, port, database, maxPoolSize, minIdle);
    }

    @PreDestroy
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public Connection getConnection() throws SQLException {
        try {
            return dataSource.getConnection();
        } catch (SQLTransientConnectionException e) {
            throw new DatabaseUnavailableException("The database is temporarily busy. Try again shortly.", e);
        }
    }

    public DSLContext dsl(Connection conn) {
        return DSL.using(conn, SQLDialect.POSTGRES);
    }

    @FunctionalInterface
    public interface QueryOperation<T> {
        T execute(DSLContext dsl) throws SQLException;
    }

    public enum QueryPriority {
        INTERACTIVE, BULK
    }

    /** Bound expensive work before it can take a pooled connection. */
    public <T> T executeExpensiveQuery(QueryPriority priority, QueryOperation<T> operation) {
        if (priority == QueryPriority.BULK && !bulkQueryPermits.tryAcquire()) {
            throw new DatabaseUnavailableException("Expensive queries are busy. Try again shortly.", null);
        }
        try {
            if (!expensiveQueryPermits.tryAcquire()) {
                throw new DatabaseUnavailableException("Expensive queries are busy. Try again shortly.", null);
            }
            try {
                return executeWithDeadline(operation);
            } finally {
                expensiveQueryPermits.release();
            }
        } finally {
            if (priority == QueryPriority.BULK) {
                bulkQueryPermits.release();
            }
        }
    }

    private <T> T executeWithDeadline(QueryOperation<T> operation) {
        QueryDeadline deadline = new QueryDeadline(expensiveQueryTimeoutSeconds);
        try (Connection conn = getConnection()) {
            return dsl(conn).transactionResult(transaction -> {
                DSLContext tx = DSL.using(transaction.deriveAppending(deadline));
                T result = operation.execute(tx);
                deadline.remainingMillis();
                return result;
            });
        } catch (DataAccessException e) {
            if ("57014".equals(e.sqlState())) {
                throw new DatabaseUnavailableException("The database query was cancelled or timed out.", e);
            }
            throw e;
        } catch (SQLException e) {
            throw new DatabaseUnavailableException("The database is temporarily unavailable.", e);
        }
    }

    private static final class QueryDeadline implements ExecuteListener {

        // A transaction-local timeout follows the remaining budget across statements and is
        // discarded on commit or rollback. Keep any stricter PostgreSQL timeout already in force.
        private static final String SET_TIMEOUT = """
                SELECT pg_catalog.set_config('statement_timeout',
                    least(?::bigint, coalesce(nullif(setting::bigint, 0), ?::bigint))::text, true)
                FROM pg_catalog.pg_settings WHERE name = 'statement_timeout'
                """;

        private final long deadlineNanos;

        QueryDeadline(int timeoutSeconds) {
            deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        }

        long remainingMillis() {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new DatabaseUnavailableException("The database query timed out.", null);
            }
            return Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        }

        @Override
        public void executeStart(ExecuteContext context) {
            long remaining = remainingMillis();
            // Use the same transaction and plain JDBC so installing the timeout does not
            // itself invoke this listener recursively.
            try (PreparedStatement timeout = context.connection().prepareStatement(SET_TIMEOUT)) {
                timeout.setQueryTimeout(1);
                timeout.setLong(1, remaining);
                timeout.setLong(2, remaining);
                timeout.execute();
            } catch (SQLException e) {
                throw new DataAccessException("Could not set the database query deadline", e);
            }
        }
    }

    private static final class DatabaseUnavailableException extends ResponseStatusException {

        DatabaseUnavailableException(String reason, Throwable cause) {
            super(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, "2");
            return headers;
        }
    }

    public boolean hasLoadedOntologyData() throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement query = conn.prepareStatement(
                     "SELECT 1 FROM ols_entities WHERE search_type = 'ontology' LIMIT 1")) {
            query.setQueryTimeout(1);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    public long returnNodeCount() {
        try (Connection conn = getConnection()) {
            Long count = dsl(conn)
                    .selectCount()
                    .from(DSL.table(DSL.name("ols_entities")))
                    .fetchOne(0, Long.class);
            return count == null ? 0 : count;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count nodes", e);
        }
    }
}
