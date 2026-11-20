package com.twa.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.StringUtils;

@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CatalogServiceApplication.class);
        application.addListeners(requiredPassword());
        application.run(args);
    }

    // The password of the database has no default value: without DB_PASSWORD the service does not start, before
    // trying to connect with an empty password
    static ApplicationListener<ApplicationEnvironmentPreparedEvent> requiredPassword() {
        return event -> {
            if (!StringUtils.hasText(event.getEnvironment().getProperty("spring.datasource.password"))) {
                throw new IllegalStateException("The password of the database is required: set DB_PASSWORD"
                        + " (in docker compose it comes from .env, created by ./scripts/init-env.sh)");
            }
        };
    }
}
