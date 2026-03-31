package uk.ac.ebi.spot.ols.service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
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

    private HikariDataSource dataSource;
    private Gson gson = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(PostgresClient.class);

    @PostConstruct
    public void init() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
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

    public long returnNodeCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM ols_entities")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count nodes", e);
        }
    }

    public List<Map<String, Object>> rawQuery(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParam(stmt, i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Raw query failed: " + sql, e);
        }
    }

    public List<JsonElement> query(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParam(stmt, i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<JsonElement> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(JsonParser.parseString(rs.getString("_json")));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }

    public Page<JsonElement> queryPaginated(String sql, String countSql, Object[] params, Pageable pageable) {
        String paginatedSql = sql + " ORDER BY iri ASC OFFSET " + pageable.getOffset() + " LIMIT " + pageable.getPageSize();

        logger.debug(paginatedSql);

        try (Connection conn = getConnection()) {
            // Run count query
            int count;
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                for (int i = 0; i < params.length; i++) {
                    setParam(countStmt, i + 1, params[i]);
                }
                try (ResultSet rs = countStmt.executeQuery()) {
                    rs.next();
                    count = rs.getInt(1);
                }
            }

            // Run data query
            List<JsonElement> results = new ArrayList<>();
            try (PreparedStatement dataStmt = conn.prepareStatement(paginatedSql)) {
                for (int i = 0; i < params.length; i++) {
                    setParam(dataStmt, i + 1, params[i]);
                }
                try (ResultSet rs = dataStmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(JsonParser.parseString(rs.getString("_json")));
                    }
                }
            }

            return new PageImpl<>(results, pageable, count);
        } catch (SQLException e) {
            throw new RuntimeException("Paginated query failed: " + sql, e);
        }
    }

    public JsonElement queryOne(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParam(stmt, i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException();
                }
                return JsonParser.parseString(rs.getString("_json"));
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("QueryOne failed: " + sql, e);
        }
    }

    private void setParam(PreparedStatement stmt, int index, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.VARCHAR);
        } else if (value instanceof String) {
            stmt.setString(index, (String) value);
        } else if (value instanceof Integer) {
            stmt.setInt(index, (Integer) value);
        } else if (value instanceof Long) {
            stmt.setLong(index, (Long) value);
        } else if (value instanceof Boolean) {
            stmt.setBoolean(index, (Boolean) value);
        } else if (value instanceof String[]) {
            stmt.setArray(index, stmt.getConnection().createArrayOf("text", (String[]) value));
        } else if (value instanceof Double) {
            stmt.setDouble(index, (Double) value);
        } else {
            stmt.setObject(index, value);
        }
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }
}
