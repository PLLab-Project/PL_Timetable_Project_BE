package com.example.pl_timetable_project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class PlTimetableProjectApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesTheSharedDatabaseContract() {
        String version = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success IS TRUE",
                Integer.class);
        String graduationProfiles = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.graduation_credit_profiles')::text",
                String.class);
        String socialIdentities = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.social_identities')::text",
                String.class);

        assertThat(version).startsWith("18.4");
        assertThat(successfulMigrations).isEqualTo(4);
        assertThat(graduationProfiles).isEqualTo("graduation_credit_profiles");
        assertThat(socialIdentities).isEqualTo("social_identities");
    }

}
