package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationsAplicamNoBancoDeTeste() {
        assertThat(dataSource).isNotNull();
        assertThat(flyway.info().applied()).hasSize(2);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    }
}
