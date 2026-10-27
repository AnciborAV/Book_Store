package ru.aav;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** The same HTTP contract must hold for the default PostgreSQL database. */
class PostgresSelectionTest extends SelectionTest {
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:15.3");
    static { DATABASE.start(); }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
}
