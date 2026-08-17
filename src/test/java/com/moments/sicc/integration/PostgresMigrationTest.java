package com.moments.sicc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.repository.RegistroAuditoriaRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@EnabledIfEnvironmentVariable(named = "SICC_POSTGRES_TEST", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest
class PostgresMigrationTest {
    @Autowired
    private Flyway flyway;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private UsuarioInternoRepository usuarios;
    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Test
    void baselineAplicaEValidaNoPostgresqlReal() throws Exception {
        assertThat(dataSource.getConnection().getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(usuarios.findByLoginIgnoreCase("admin")).hasValueSatisfying(admin -> {
            assertThat(admin.getEmail()).isEqualTo("admin@sicc.test");
            assertThat(admin.getSenhaHash()).startsWith("$2");
            assertThat(admin.isSenhaTemporaria()).isTrue();
            assertThat(admin.getVersaoAcesso()).isZero();
        });
        assertThat(auditoria.findAll())
                .anySatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo("CRIAR_USUARIO");
                    assertThat(registro.getDetalhes()).doesNotContain("Temporaria123!");
                });
    }
}
