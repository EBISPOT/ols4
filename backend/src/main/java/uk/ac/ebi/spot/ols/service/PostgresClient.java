package uk.ac.ebi.spot.ols.service;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class PostgresClient {

    private static final String QUERY_CANCELLED_SQLSTATE = "57014";
    private static final String CAP_STATEMENT_TIMEOUT = """
            SELECT pg_catalog.set_config('statement_timeout',
                least(coalesce(nullif(setting::integer, 0), 60000), 60000)::text, false)
            FROM pg_catalog.pg_settings WHERE name = 'statement_timeout'
            """;

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
        config.setConnectionTimeout(30000);
        // Apply the ceiling to every new serving connection, preserving a stricter server default.
        config.setConnectionInitSql(CAP_STATEMENT_TIMEOUT);
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
        return dataSource.getConnection();
    }

    public DSLContext dsl(Connection conn) {
        return DSL.using(conn, SQLDialect.POSTGRES);
    }

    public static boolean isStatementCancelled(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && QUERY_CANCELLED_SQLSTATE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
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
