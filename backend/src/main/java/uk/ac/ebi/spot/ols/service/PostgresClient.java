package uk.ac.ebi.spot.ols.service;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        dataSource = new HikariDataSource(config);
        logger.info("PostgreSQL connection pool initialized: {}:{}/{}", host, port, database);
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
