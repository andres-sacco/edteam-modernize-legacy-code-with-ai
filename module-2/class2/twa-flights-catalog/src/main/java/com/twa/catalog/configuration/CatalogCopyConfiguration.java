package com.twa.catalog.configuration;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

import com.twa.catalog.repository.CatalogCopyRepository;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Own database of the catalog service (catalog), with its own Flyway (db/copy) and its own pool. The pool is not a
 * bean of type DataSource on purpose: the datasource of Spring (flights, read-only) and the JPA repositories stay as
 * they are, and the copy is only written by CatalogOutboxPublisher.
 *
 * Only when catalog.copy.enabled=true (ADR-002 D2: first a copy of reading, the reads of the service change later).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "catalog.copy.enabled", havingValue = "true")
public class CatalogCopyConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogCopyConfiguration.class);

    @Bean(destroyMethod = "close")
    public CatalogCopyRepository catalogCopyRepository(@Value("${catalog.copy.datasource.url}") String url,
            @Value("${catalog.copy.datasource.username}") String username,
            @Value("${catalog.copy.datasource.password:}") String password,
            @Value("${catalog.copy.datasource.maximum-pool-size:2}") int maximumPoolSize) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("The password of the database catalog is required: set"
                    + " CATALOG_COPY_DB_PASSWORD (in docker compose it comes from .env, created by ./scripts/init-env.sh)");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("catalog-copy");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);

        try {
            Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/copy").load();
            int applied = flyway.migrate().migrationsExecuted;
            LOGGER.info("Database of the copy of the catalog migrated ({} migrations applied)", applied);
        } catch (RuntimeException e) {
            dataSource.close();
            throw e;
        }
        return new CatalogCopyRepository(dataSource);
    }
}
