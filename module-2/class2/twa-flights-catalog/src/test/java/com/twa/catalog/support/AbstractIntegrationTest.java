package com.twa.catalog.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base of the integration tests: the whole service with a real MySQL. The schema and the seed data are a copy of the
 * migrations of the catalog of the monolith (src/test/resources/db/catalog), applied with Flyway only in the tests and
 * with its own connection: the datasource of the service stays read-only like in production.
 *
 * The container is started only once and the Spring context is shared by all the subclasses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0").withDatabaseName("flights");

    static {
        MYSQL.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        // Flyway of Spring is off in the service (the monolith migrates flights): the tests turn it on to create flights
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/catalog");
    }

    // The root URI of the TestRestTemplate already has the context path (/api/catalog)
    protected <T> ResponseEntity<T> get(String path, Class<T> type) {
        return restTemplate.getForEntity(path, type);
    }
}
