package uk.ac.ebi.spot.ols.service;

import java.util.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    private DSLContext dsl;
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
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        logger.info("PostgreSQL connection pool initialized: {}:{}/{}", host, port, database);
    }

    public DSLContext dsl() {
        return dsl;
    }

    @PreDestroy
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public long returnNodeCount() {
        return dsl.selectCount().from(DSL.table("ols_entities")).fetchOne(0, Long.class);
    }

    public Page<JsonElement> queryPaginated(ResultQuery<?> dataQuery, ResultQuery<?> countQuery, Pageable pageable) {
        logger.debug("{}", dataQuery);

        int count;
        Record countRecord = dsl.fetchOne(countQuery);
        count = countRecord.get(0, int.class);

        List<JsonElement> results = new ArrayList<>();
        for (Record record : dsl.fetch(dataQuery)) {
            results.add(JsonParser.parseString(record.get("_json", String.class)));
        }

        return new PageImpl<>(results, pageable, count);
    }
}
