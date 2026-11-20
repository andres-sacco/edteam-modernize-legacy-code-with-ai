package com.twa.catalog.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twa.catalog.repository.CatalogCopyRepository;

/**
 * The database of the reads of the cities (M2 C5, stage 7 of the extraction plan). With catalog.reads.database=catalog
 * the entities City, Country and Continent are read from the own database of the service (catalog.city...) instead of
 * the tables of the monolith, and Hibernate validates the schema of that database, migrated by the service
 * (db/copy), not by the Flyway of the monolith. The outbox is still read from flights with the datasource of Spring.
 *
 * It needs catalog.copy.enabled=true: the copy is what fills that database, and its Flyway runs before the JPA
 * validation. Without a value the reads are from the database of the datasource (flights), as before. Rollback:
 * CATALOG_READS_DATABASE empty and restart.
 */
@Configuration
@ConditionalOnExpression("!'${catalog.reads.database:}'.isBlank()")
public class CatalogReadsConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogReadsConfiguration.class);

    private static final String COPY_BEAN = "catalogCopyRepository";

    @Bean
    public HibernatePropertiesCustomizer catalogReadsDatabase(@Value("${catalog.reads.database}") String database,
            @Value("${catalog.copy.enabled:false}") boolean copyEnabled) {
        if (!copyEnabled) {
            throw new IllegalStateException("catalog.reads.database=" + database
                    + " needs catalog.copy.enabled=true: the copy fills that database (CATALOG_COPY_ENABLED)");
        }
        LOGGER.info("The cities are read from the database {}", database);
        return properties -> properties.put("hibernate.default_catalog", database);
    }

    // DOWN until the copy applied the first events of the outbox: an empty copy would answer 404 for every city
    @Bean
    public HealthIndicator catalogReadsHealthIndicator(@Value("${catalog.reads.database}") String database,
            CatalogCopyRepository copyRepository) {
        return () -> {
            long checkpoint = copyRepository.readCheckpoint();
            Health.Builder health = checkpoint > 0 ? Health.up() : Health.down();
            return health.withDetail("database", database).withDetail("lastEventApplied", checkpoint).build();
        };
    }

    // The Flyway of the copy creates the tables before Hibernate validates them
    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor catalogReadsAfterCopy() {
        return new EntityManagerFactoryDependsOnPostProcessor(COPY_BEAN);
    }
}
