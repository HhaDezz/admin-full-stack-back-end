package com.atelier.api.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs the database migrations in src/main/resources/db/migration on startup,
 * before JPA starts, so the schema always matches the code.
 *
 * Configured explicitly (rather than through a starter) so it works the same way
 * on every Spring Boot 4 setup.
 */
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    /** Makes JPA wait for the migrations. */
    @Bean
    public static BeanFactoryPostProcessor entityManagerAfterFlyway() {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition("entityManagerFactory")) return;
            BeanDefinition def = beanFactory.getBeanDefinition("entityManagerFactory");
            String[] existing = def.getDependsOn();
            String[] merged = existing == null ? new String[] { "flyway" } : append(existing, "flyway");
            def.setDependsOn(merged);
        };
    }

    private static String[] append(String[] arr, String value) {
        String[] out = new String[arr.length + 1];
        System.arraycopy(arr, 0, out, 0, arr.length);
        out[arr.length] = value;
        return out;
    }
}
